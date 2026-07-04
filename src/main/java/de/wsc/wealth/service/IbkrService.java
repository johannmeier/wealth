package de.wsc.wealth.service;

import de.wsc.wealth.domain.*;
import de.wsc.wealth.dto.ChangedPosition;
import de.wsc.wealth.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.*;

/**
 * Syncs account balances and portfolio positions from Interactive Brokers via the
 * Flex Web Service (SendRequest + GetStatement, two-step pull API based on a
 * pre-configured Flex Query token + query ID — see IBKR Client Portal &gt; Reports &gt; Flex Queries).
 */
@Service
@Transactional
public class IbkrService {

    private static final Logger log = LoggerFactory.getLogger(IbkrService.class);

    private static final String SEND_REQUEST_URL =
        "https://ndcdyn.interactivebrokers.com/AccountManagement/FlexWebService/SendRequest";

    private static final int MAX_POLL_ATTEMPTS = 10;
    private static final Duration POLL_INTERVAL = Duration.ofSeconds(3);
    private static final String STATEMENT_IN_PROGRESS_ERROR_CODE = "1019";

    private final IbkrConfigRepository configRepository;
    private final BankRepository bankRepository;
    private final AccountRepository accountRepository;
    private final AccountBalanceRepository balanceRepository;
    private final DepotRepository depotRepository;
    private final AssetRepository assetRepository;
    private final AssetQuantityRepository quantityRepository;
    private final AssetSearchService assetSearchService;

    public IbkrService(IbkrConfigRepository configRepository,
                       BankRepository bankRepository,
                       AccountRepository accountRepository,
                       AccountBalanceRepository balanceRepository,
                       DepotRepository depotRepository,
                       AssetRepository assetRepository,
                       AssetQuantityRepository quantityRepository,
                       AssetSearchService assetSearchService) {
        this.configRepository = configRepository;
        this.bankRepository = bankRepository;
        this.accountRepository = accountRepository;
        this.balanceRepository = balanceRepository;
        this.depotRepository = depotRepository;
        this.assetRepository = assetRepository;
        this.quantityRepository = quantityRepository;
        this.assetSearchService = assetSearchService;
    }

    @Transactional(readOnly = true)
    public Optional<IbkrConfig> findConfig() {
        return configRepository.findAll().stream().findFirst();
    }

    public record SyncResult(
        int balancesUpdated,
        List<String> newAccounts,
        List<ChangedPosition> changedPositions,
        List<String> newAssets,
        Long bankId
    ) {
        public int positionsUpdated() { return changedPositions.size(); }
    }

    private record SendRequestResult(String referenceCode, String statementUrl) {}
    private record OpenPosition(String isin, String symbol, BigDecimal quantity) {}

    public SyncResult sync(String token, String queryId) {
        HttpClient httpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

        SendRequestResult request = sendRequest(httpClient, token, queryId);
        String statementXml = fetchStatement(httpClient, token, request);

        Map<String, BigDecimal> cashBalances = new LinkedHashMap<>();
        List<OpenPosition> positions = new ArrayList<>();
        parseStatement(statementXml, cashBalances, positions);
        log.info("IBKR: {} Barsalden, {} Depot-Positionen", cashBalances.size(), positions.size());

        return persist(token, queryId, cashBalances, positions);
    }

    // -------------------------------------------------------------------------
    // Flex Web Service (SendRequest + GetStatement with polling)
    // -------------------------------------------------------------------------

    private SendRequestResult sendRequest(HttpClient client, String token, String queryId) {
        String url = SEND_REQUEST_URL + "?t=" + enc(token) + "&q=" + enc(queryId) + "&v=3";
        Document doc = parseXml(get(client, url));
        requireSuccess(doc);
        String referenceCode = text(doc, "ReferenceCode");
        String statementUrl = text(doc, "Url");
        if (referenceCode.isBlank() || statementUrl.isBlank()) {
            throw new IllegalStateException("Unerwartete Antwort von Interactive Brokers (SendRequest).");
        }
        return new SendRequestResult(referenceCode, statementUrl);
    }

    private String fetchStatement(HttpClient client, String token, SendRequestResult request) {
        String url = request.statementUrl() + "?q=" + enc(request.referenceCode()) + "&t=" + enc(token) + "&v=3";
        for (int attempt = 1; attempt <= MAX_POLL_ATTEMPTS; attempt++) {
            String xml = get(client, url);
            if (xml.contains("<FlexQueryResponse")) {
                return xml;
            }
            Document doc = parseXml(xml);
            String errorCode = text(doc, "ErrorCode");
            if (STATEMENT_IN_PROGRESS_ERROR_CODE.equals(errorCode)) {
                log.info("IBKR Flex-Statement wird noch generiert, Versuch {}/{}", attempt, MAX_POLL_ATTEMPTS);
                sleep();
                continue;
            }
            requireSuccess(doc);
            throw new IllegalStateException("Unerwartete Antwort von Interactive Brokers (GetStatement).");
        }
        throw new IllegalStateException(
            "IBKR Flex-Statement war nach mehreren Versuchen nicht bereit. Bitte später erneut versuchen.");
    }

    private void requireSuccess(Document doc) {
        String status = text(doc, "Status");
        if (status.isBlank() || "Success".equalsIgnoreCase(status)) {
            return;
        }
        String errMsg = text(doc, "ErrorMessage");
        throw new IllegalStateException(
            "IBKR Flex Query fehlgeschlagen: " + (errMsg.isBlank() ? status : errMsg));
    }

    private void sleep() {
        try {
            Thread.sleep(POLL_INTERVAL.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Verbindung unterbrochen.");
        }
    }

    private String get(HttpClient client, String url) {
        try {
            HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            return resp.body();
        } catch (IOException e) {
            throw new IllegalStateException("Verbindung zu Interactive Brokers fehlgeschlagen: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Verbindung unterbrochen.");
        }
    }

    private String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    // -------------------------------------------------------------------------
    // XML parsing
    // -------------------------------------------------------------------------

    private Document parseXml(String xml) {
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setFeature("http://xml.org/sax/features/external-general-entities", false);
            dbf.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            dbf.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            dbf.setExpandEntityReferences(false);
            DocumentBuilder db = dbf.newDocumentBuilder();
            return db.parse(new InputSource(new StringReader(xml)));
        } catch (Exception e) {
            throw new IllegalStateException("Fehler beim Lesen der IBKR-Antwort: " + e.getMessage());
        }
    }

    private String text(Document doc, String tag) {
        NodeList nodes = doc.getElementsByTagName(tag);
        return nodes.getLength() > 0 ? nodes.item(0).getTextContent().strip() : "";
    }

    private void parseStatement(String xml, Map<String, BigDecimal> cashBalances, List<OpenPosition> positions) {
        Document doc = parseXml(xml);

        NodeList cashNodes = doc.getElementsByTagName("CashReportCurrency");
        for (int i = 0; i < cashNodes.getLength(); i++) {
            Element el = (Element) cashNodes.item(i);
            String currency = el.getAttribute("currency");
            String endingCash = el.getAttribute("endingCash");
            if (currency.isBlank() || endingCash.isBlank() || "BASE_SUMMARY".equalsIgnoreCase(currency)) continue;
            cashBalances.put(currency.toUpperCase(), new BigDecimal(endingCash));
        }

        NodeList posNodes = doc.getElementsByTagName("OpenPosition");
        for (int i = 0; i < posNodes.getLength(); i++) {
            Element el = (Element) posNodes.item(i);
            String posStr = el.getAttribute("position");
            if (posStr.isBlank()) continue;
            BigDecimal quantity = new BigDecimal(posStr);
            if (quantity.compareTo(BigDecimal.ZERO) == 0) continue;

            String isin = el.getAttribute("securityID");
            String idType = el.getAttribute("securityIDType");
            String symbol = el.getAttribute("symbol");
            if (!"ISIN".equalsIgnoreCase(idType) || isin.isBlank()) {
                log.warn("IBKR-Position ohne ISIN übersprungen: {}", symbol);
                continue;
            }
            positions.add(new OpenPosition(isin, symbol, quantity));
        }
    }

    // -------------------------------------------------------------------------
    // Database persistence
    // -------------------------------------------------------------------------

    private SyncResult persist(String token, String queryId,
                                Map<String, BigDecimal> cashBalances,
                                List<OpenPosition> positions) {
        IbkrConfig config = configRepository.findByQueryId(queryId)
            .orElseGet(() -> {
                IbkrConfig c = new IbkrConfig();
                c.setQueryId(queryId);
                return c;
            });
        config.setToken(token);

        if (config.getBank() == null) {
            Bank bank = new Bank();
            bank.setName("Interactive Brokers");
            config.setBank(bankRepository.save(bank));
            log.info("Created Interactive Brokers bank");
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
                    a.setAccountNumber("IBKR " + currency);
                    a.setAssetAllocation(AssetAllocation.RISIKOFREI);
                    newAccounts.add(currency);
                    log.info("Created IBKR account for currency {}", currency);
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

        Depot depot = depotRepository.findByBankIdAndName(bank.getId(), "Interactive Brokers")
            .orElseGet(() -> {
                Depot d = new Depot();
                d.setName("Interactive Brokers");
                d.setBank(bank);
                return depotRepository.save(d);
            });

        List<ChangedPosition> changedPositions = new ArrayList<>();
        List<String> newAssets = new ArrayList<>();

        for (OpenPosition pos : positions) {
            Asset asset = assetRepository.findFirstByIsinAndArchivedFalse(pos.isin())
                .or(() -> assetRepository.findFirstByArchivedTrueAndIsin(pos.isin()))
                .orElseGet(() -> createAssetFromIsin(pos.isin(), newAssets));

            BigDecimal oldQty = quantityRepository
                .findFirstByAssetAndDepotOrderByDateDesc(asset, depot)
                .map(AssetQuantity::getQuantity)
                .orElse(null);

            if (oldQty == null || oldQty.compareTo(pos.quantity()) != 0) {
                AssetQuantity q = quantityRepository.findByAssetAndDepotAndDate(asset, depot, today)
                    .orElseGet(() -> {
                        AssetQuantity aq = new AssetQuantity();
                        aq.setAsset(asset);
                        aq.setDepot(depot);
                        aq.setDate(today);
                        return aq;
                    });
                q.setQuantity(pos.quantity());
                quantityRepository.save(q);
                changedPositions.add(new ChangedPosition(asset.getName(), pos.isin(), oldQty, pos.quantity()));
            }
        }

        return new SyncResult(balancesUpdated, newAccounts, changedPositions, newAssets, bank.getId());
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
}
