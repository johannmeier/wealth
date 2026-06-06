package de.wsc.wealth.service;

import de.wsc.wealth.domain.*;
import de.wsc.wealth.repository.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.CookieManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class TradeRepublicService {

    private static final Logger log = LoggerFactory.getLogger(TradeRepublicService.class);

    private static final String TR_BASE    = "https://api.traderepublic.com";
    private static final String TR_LOGIN   = TR_BASE + "/api/v1/auth/web/login";
    private static final String TR_SESSION = TR_BASE + "/api/v1/auth/web/session";
    private static final String TR_ACCOUNT = TR_BASE + "/api/v2/auth/account";
    private static final String TR_WS      = "wss://api.traderepublic.com";

    private static final String USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    private final ObjectMapper objectMapper;
    private final AssetSearchService assetSearchService;
    private final TradeRepublicConfigRepository configRepository;
    private final BankRepository bankRepository;
    private final AccountRepository accountRepository;
    private final AccountBalanceRepository balanceRepository;
    private final DepotRepository depotRepository;
    private final AssetRepository assetRepository;
    private final AssetQuantityRepository quantityRepository;

    public TradeRepublicService(ObjectMapper objectMapper,
                                AssetSearchService assetSearchService,
                                TradeRepublicConfigRepository configRepository,
                                BankRepository bankRepository,
                                AccountRepository accountRepository,
                                AccountBalanceRepository balanceRepository,
                                DepotRepository depotRepository,
                                AssetRepository assetRepository,
                                AssetQuantityRepository quantityRepository) {
        this.objectMapper       = objectMapper;
        this.assetSearchService = assetSearchService;
        this.configRepository   = configRepository;
        this.bankRepository     = bankRepository;
        this.accountRepository  = accountRepository;
        this.balanceRepository  = balanceRepository;
        this.depotRepository    = depotRepository;
        this.assetRepository    = assetRepository;
        this.quantityRepository = quantityRepository;
    }

    public record SyncResult(
        int balancesUpdated,
        List<String> newAccounts,
        int positionsUpdated,
        List<String> newAssets,
        Long bankId
    ) {}

    @Transactional(readOnly = true)
    public Optional<TradeRepublicConfig> findConfig() {
        return configRepository.findAll().stream().findFirst();
    }

    /**
     * Step 1: Initiates OTP login. Returns processId for step 2.
     * PIN is used only here and never stored.
     */
    public String requestOtp(String phoneNumber, String pin) {
        try {
            HttpClient client = HttpClient.newBuilder()
                .cookieHandler(new CookieManager())
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();

            String body = objectMapper.writeValueAsString(
                Map.of("phoneNumber", phoneNumber, "pin", pin));

            HttpResponse<String> resp = client.send(
                HttpRequest.newBuilder()
                    .uri(URI.create(TR_LOGIN))
                    .header("Content-Type", "application/json")
                    .header("User-Agent", USER_AGENT)
                    .header("Origin", "https://app.traderepublic.com")
                    .header("Referer", "https://app.traderepublic.com/login")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build(),
                HttpResponse.BodyHandlers.ofString()
            );

            if (resp.statusCode() != 200) {
                throw new IllegalStateException(
                    "Anmeldung fehlgeschlagen (HTTP " + resp.statusCode() + "): Telefonnummer oder PIN falsch?");
            }

            JsonNode json = objectMapper.readTree(resp.body());
            if (!json.has("processId")) {
                throw new IllegalStateException("Unerwartete Antwort von Trade Republic: " + resp.body());
            }
            log.info("Trade Republic OTP angefordert für {}", phoneNumber);
            return json.get("processId").asText();

        } catch (IllegalStateException e) {
            throw e;
        } catch (IOException e) {
            throw new IllegalStateException("Verbindung zu Trade Republic fehlgeschlagen: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Verbindung unterbrochen.");
        }
    }

    /**
     * Step 2: Completes OTP login and syncs portfolio + cash.
     * No credentials are stored; session is closed after the sync.
     */
    @Transactional
    public SyncResult sync(String phoneNumber, String processId, String otp) {
        try {
            CookieManager cookieManager = new CookieManager();
            HttpClient client = HttpClient.newBuilder()
                .cookieHandler(cookieManager)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();

            // Complete OTP verification — session cookies set by server
            HttpResponse<String> otpResp = client.send(
                HttpRequest.newBuilder()
                    .uri(URI.create(TR_LOGIN + "/" + processId + "/" + otp.strip()))
                    .header("User-Agent", USER_AGENT)
                    .header("Origin", "https://app.traderepublic.com")
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build(),
                HttpResponse.BodyHandlers.ofString()
            );
            if (otpResp.statusCode() != 200) {
                throw new IllegalStateException(
                    "OTP-Verifikation fehlgeschlagen (HTTP " + otpResp.statusCode() + "): OTP falsch oder abgelaufen?");
            }

            // Refresh session before WebSocket use
            client.send(
                HttpRequest.newBuilder()
                    .uri(URI.create(TR_SESSION))
                    .header("User-Agent", USER_AGENT)
                    .GET().build(),
                HttpResponse.BodyHandlers.ofString()
            );

            // Get securities account number (needed for portfolio subscription)
            HttpResponse<String> accountResp = client.send(
                HttpRequest.newBuilder()
                    .uri(URI.create(TR_ACCOUNT))
                    .header("User-Agent", USER_AGENT)
                    .GET().build(),
                HttpResponse.BodyHandlers.ofString()
            );
            JsonNode accountJson = objectMapper.readTree(accountResp.body());
            String secAccNo = accountJson.get("securitiesAccountNumber").asText();
            log.info("Trade Republic Depotnummer: {}", secAccNo);

            // Fetch cash + positions via WebSocket
            Map<String, BigDecimal> cashBalances = new LinkedHashMap<>();
            Map<String, BigDecimal> positions    = new LinkedHashMap<>();
            fetchViaWebSocket(client, secAccNo, cashBalances, positions);

            // Persist to database
            return persistData(phoneNumber, cashBalances, positions);

        } catch (IllegalStateException e) {
            throw e;
        } catch (IOException e) {
            throw new IllegalStateException("Verbindung zu Trade Republic fehlgeschlagen: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Verbindung unterbrochen.");
        }
    }

    // -------------------------------------------------------------------------
    // WebSocket data fetch
    // -------------------------------------------------------------------------

    private void fetchViaWebSocket(HttpClient client, String secAccNo,
                                    Map<String, BigDecimal> cashBalances,
                                    Map<String, BigDecimal> positions) {
        Map<Integer, CompletableFuture<String>> pending = new ConcurrentHashMap<>();
        AtomicInteger idCounter = new AtomicInteger(1);
        CompletableFuture<Void> connected = new CompletableFuture<>();

        WebSocket ws = client.newWebSocketBuilder()
            .header("User-Agent", USER_AGENT)
            .header("Origin", "https://app.traderepublic.com")
            .buildAsync(URI.create(TR_WS), new TrListener(pending, connected))
            .join();

        try {
            // Handshake
            send(ws, "connect 31 {\"locale\":\"de\",\"platformId\":\"webtrading\"," +
                "\"platformVersion\":\"chrome - 120.0.0.0\"," +
                "\"clientId\":\"app.traderepublic.com\",\"clientVersion\":\"5582\"}");
            connected.get(10, TimeUnit.SECONDS);

            // Cash balance
            String cashJson = sub(ws, pending, idCounter, "{\"type\":\"cash\"}");
            parseCash(cashJson, cashBalances);
            log.info("Trade Republic: {} Barpositionen", cashBalances.size());

            // Portfolio positions
            String portfolioJson = sub(ws, pending, idCounter,
                "{\"type\":\"compactPortfolio\",\"secAccNo\":\"" + secAccNo + "\"}");
            parsePortfolio(portfolioJson, positions);
            log.info("Trade Republic: {} Depot-Positionen", positions.size());

        } catch (ExecutionException | TimeoutException e) {
            throw new IllegalStateException("Fehler beim Datenabruf via WebSocket: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Verbindung unterbrochen.");
        } finally {
            ws.sendClose(WebSocket.NORMAL_CLOSURE, "done").join();
        }
    }

    private void send(WebSocket ws, String text) {
        ws.sendText(text, true).join();
    }

    private String sub(WebSocket ws, Map<Integer, CompletableFuture<String>> pending,
                       AtomicInteger idCounter, String payload)
            throws ExecutionException, TimeoutException, InterruptedException {
        int id = idCounter.getAndIncrement();
        CompletableFuture<String> future = new CompletableFuture<>();
        pending.put(id, future);
        ws.sendText("sub " + id + " " + payload, true).join();
        return future.get(15, TimeUnit.SECONDS);
    }

    private void parseCash(String json, Map<String, BigDecimal> out) {
        try {
            JsonNode arr = objectMapper.readTree(json);
            if (arr.isArray()) {
                for (JsonNode item : arr) {
                    String currency = item.get("currencyId").asText();
                    BigDecimal amount = new BigDecimal(item.get("amount").asText());
                    out.put(currency, amount);
                }
            }
        } catch (Exception e) {
            log.warn("Fehler beim Parsen der Barsalden: {}", e.getMessage());
        }
    }

    private void parsePortfolio(String json, Map<String, BigDecimal> out) {
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode posArr = root.get("positions");
            if (posArr != null && posArr.isArray()) {
                for (JsonNode pos : posArr) {
                    String isin = pos.get("instrumentId").asText();
                    String sizeStr = pos.has("netSize") ? pos.get("netSize").asText() : "0";
                    BigDecimal qty = new BigDecimal(sizeStr);
                    if (qty.compareTo(BigDecimal.ZERO) > 0) {
                        out.put(isin, qty);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Fehler beim Parsen der Depot-Positionen: {}", e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Database persistence
    // -------------------------------------------------------------------------

    private SyncResult persistData(String phoneNumber,
                                    Map<String, BigDecimal> cashBalances,
                                    Map<String, BigDecimal> positions) {
        TradeRepublicConfig config = configRepository.findByPhoneNumber(phoneNumber)
            .orElseGet(() -> {
                TradeRepublicConfig c = new TradeRepublicConfig();
                c.setPhoneNumber(phoneNumber);
                return c;
            });

        if (config.getBank() == null) {
            Bank bank = new Bank();
            bank.setName("Trade Republic");
            config.setBank(bankRepository.save(bank));
        }
        Bank bank = config.getBank();
        configRepository.save(config);

        LocalDate today = LocalDate.now();
        int balancesUpdated = 0;
        List<String> newAccounts = new ArrayList<>();

        for (Map.Entry<String, BigDecimal> entry : cashBalances.entrySet()) {
            String currency = entry.getKey();
            BigDecimal amount = entry.getValue();

            Account account = accountRepository.findByBankIdOrderByAccountNumberAsc(bank.getId())
                .stream()
                .filter(a -> currency.equalsIgnoreCase(a.getCurrency()))
                .findFirst()
                .orElseGet(() -> {
                    Account a = new Account();
                    a.setBank(bank);
                    a.setCurrency(currency);
                    a.setAccountNumber("Trade Republic " + currency);
                    a.setAssetAllocation(AssetAllocation.RISIKOFREI);
                    newAccounts.add(currency);
                    return accountRepository.save(a);
                });

            AccountBalance bal = balanceRepository.findByAccountAndDate(account, today)
                .orElseGet(() -> {
                    AccountBalance b = new AccountBalance();
                    b.setAccount(account);
                    b.setDate(today);
                    return b;
                });
            bal.setBalance(amount);
            balanceRepository.save(bal);
            balancesUpdated++;
        }

        Depot depot = depotRepository.findByBankIdAndName(bank.getId(), "Trade Republic")
            .orElseGet(() -> {
                Depot d = new Depot();
                d.setName("Trade Republic");
                d.setBank(bank);
                return depotRepository.save(d);
            });

        int positionsUpdated = 0;
        List<String> newAssets = new ArrayList<>();

        for (Map.Entry<String, BigDecimal> entry : positions.entrySet()) {
            String isin = entry.getKey();
            BigDecimal qty = entry.getValue();

            Asset asset = assetRepository.findFirstByIsinAndArchivedFalse(isin)
                .or(() -> assetRepository.findFirstByArchivedTrueAndIsin(isin))
                .orElseGet(() -> createAssetFromIsin(isin, newAssets));

            AssetQuantity q = quantityRepository.findByAssetAndDepotAndDate(asset, depot, today)
                .orElseGet(() -> {
                    AssetQuantity aq = new AssetQuantity();
                    aq.setAsset(asset);
                    aq.setDepot(depot);
                    aq.setDate(today);
                    return aq;
                });
            q.setQuantity(qty);
            quantityRepository.save(q);
            positionsUpdated++;
        }

        return new SyncResult(balancesUpdated, newAccounts, positionsUpdated, newAssets, bank.getId());
    }

    private Asset createAssetFromIsin(String isin, List<String> newAssets) {
        Asset a = new Asset();
        a.setIsin(isin);
        try {
            var results = assetSearchService.search(isin, "EUR");
            if (!results.isEmpty()) {
                var r = results.get(0);
                a.setName(r.getOrDefault("name", isin));
                String sym = r.get("symbol");
                if (sym != null && !sym.isBlank()) a.setSymbol(sym);
                a.setCurrency(r.getOrDefault("currency", "EUR"));
                try { a.setType(AssetType.valueOf(r.getOrDefault("type", "AKTIE"))); }
                catch (IllegalArgumentException e) { a.setType(AssetType.AKTIE); }
                try { a.setCategory(AssetCategory.valueOf(r.getOrDefault("category", "BOERSENGEHANDELT"))); }
                catch (IllegalArgumentException e) { a.setCategory(AssetCategory.BOERSENGEHANDELT); }
                try { a.setAssetAllocation(AssetAllocation.valueOf(r.getOrDefault("assetAllocation", "RISIKOBEHAFTET"))); }
                catch (IllegalArgumentException e) { a.setAssetAllocation(AssetAllocation.RISIKOBEHAFTET); }
                String dp = r.get("distributionPolicy");
                if (dp != null) {
                    try { a.setDistributionPolicy(DistributionPolicy.valueOf(dp)); }
                    catch (IllegalArgumentException ignored) {}
                }
                log.info("Asset via Yahoo Finance aufgelöst: {} → {}", isin, a.getName());
            } else {
                a.setName(isin);
                a.setAssetAllocation(AssetAllocation.RISIKOBEHAFTET);
                a.setCategory(AssetCategory.BOERSENGEHANDELT);
                a.setType(AssetType.AKTIE);
                a.setCurrency("EUR");
                log.warn("Kein Yahoo-Finance-Treffer für ISIN {}, verwende ISIN als Namen", isin);
            }
        } catch (Exception e) {
            a.setName(isin);
            a.setAssetAllocation(AssetAllocation.RISIKOBEHAFTET);
            a.setCategory(AssetCategory.BOERSENGEHANDELT);
            a.setType(AssetType.AKTIE);
            a.setCurrency("EUR");
            log.warn("Yahoo-Finance-Suche für {} fehlgeschlagen: {}", isin, e.getMessage());
        }
        newAssets.add(a.getName());
        return assetRepository.save(a);
    }

    // -------------------------------------------------------------------------
    // WebSocket listener
    // -------------------------------------------------------------------------

    private static class TrListener implements WebSocket.Listener {

        private final Map<Integer, CompletableFuture<String>> pending;
        private final CompletableFuture<Void> connected;
        private final StringBuilder buf = new StringBuilder();

        TrListener(Map<Integer, CompletableFuture<String>> pending, CompletableFuture<Void> connected) {
            this.pending   = pending;
            this.connected = connected;
        }

        @Override
        public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
            buf.append(data);
            if (last) {
                String msg = buf.toString();
                buf.setLength(0);
                dispatch(msg);
            }
            ws.request(1);
            return null;
        }

        private void dispatch(String msg) {
            if ("connected".equals(msg)) {
                connected.complete(null);
                return;
            }
            int sp = msg.indexOf(' ');
            if (sp < 0) return;
            try {
                int id = Integer.parseInt(msg.substring(0, sp));
                if (msg.length() <= sp + 2) return;
                char code = msg.charAt(sp + 1);
                String payload = msg.substring(sp + 2).strip();
                CompletableFuture<String> f = pending.remove(id);
                if (f == null) return;
                if (code == 'A') {
                    f.complete(payload);
                } else if (code == 'E') {
                    f.completeExceptionally(new IllegalStateException("Trade Republic Fehler: " + payload));
                }
            } catch (NumberFormatException ignored) {}
        }

        @Override
        public void onError(WebSocket ws, Throwable error) {
            connected.completeExceptionally(error);
            pending.values().forEach(f -> f.completeExceptionally(error));
        }
    }
}
