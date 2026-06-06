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
import java.net.CookieManager;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;

@Service
@Transactional
public class BullionVaultService {

    private static final Logger log = LoggerFactory.getLogger(BullionVaultService.class);

    private static final String BV_LOGIN   = "https://www.bullionvault.com/secure/j_security_check";
    private static final String BV_BALANCE = "https://www.bullionvault.com/secure/api/v2/view_balance_xml.do?simple=true";

    private static final BigDecimal GRAMS_PER_OZ = new BigDecimal("31.1034768");
    private static final BigDecimal GRAMS_PER_KG = new BigDecimal("1000");

    private final BullionVaultConfigRepository configRepository;
    private final BankRepository bankRepository;
    private final AccountRepository accountRepository;
    private final AccountBalanceRepository balanceRepository;
    private final DepotRepository depotRepository;
    private final AssetRepository assetRepository;
    private final AssetQuantityRepository quantityRepository;

    public BullionVaultService(BullionVaultConfigRepository configRepository,
                               BankRepository bankRepository,
                               AccountRepository accountRepository,
                               AccountBalanceRepository balanceRepository,
                               DepotRepository depotRepository,
                               AssetRepository assetRepository,
                               AssetQuantityRepository quantityRepository) {
        this.configRepository = configRepository;
        this.bankRepository = bankRepository;
        this.accountRepository = accountRepository;
        this.balanceRepository = balanceRepository;
        this.depotRepository = depotRepository;
        this.assetRepository = assetRepository;
        this.quantityRepository = quantityRepository;
    }

    @Transactional(readOnly = true)
    public Optional<BullionVaultConfig> findConfig() {
        return configRepository.findAll().stream().findFirst();
    }

    public record SyncResult(
        int balancesUpdated,
        List<String> newAccounts,
        List<String> metalsUpdated,
        List<String> newAssets,
        List<String> newDepots,
        Long bankId,
        List<ChangedPosition> changedPositions
    ) {}

    private record VaultPosition(String metal, String locationCode, String locationName, BigDecimal oz) {}

    public SyncResult sync(String username, String password) {
        // 1. Login with session-cookie auth (password only used here, never stored)
        HttpClient httpClient = createSession(username, password);

        // 2. Fetch balance XML — single endpoint returns currencies AND metal positions
        String xml = fetchBalanceXml(httpClient);

        // 3. Parse XML: currencies + per-location vault positions
        Map<String, BigDecimal>  currencies = new LinkedHashMap<>();
        List<VaultPosition>      positions  = new ArrayList<>();
        parseBalance(xml, currencies, positions);
        log.debug("BullionVault currencies: {}", currencies);
        log.debug("BullionVault positions: {}", positions);

        // 4. Load or create config
        BullionVaultConfig config = configRepository.findByUsername(username)
            .orElseGet(() -> {
                BullionVaultConfig c = new BullionVaultConfig();
                c.setUsername(username);
                return c;
            });

        // 5. Ensure BullionVault bank exists
        if (config.getBank() == null) {
            Bank bank = new Bank();
            bank.setName("BullionVault");
            config.setBank(bankRepository.save(bank));
            log.info("Created BullionVault bank");
        }
        Bank bank = config.getBank();

        // 6. Sync cash balances → accounts
        LocalDate today = LocalDate.now();
        int balancesUpdated = 0;
        List<String> newAccounts = new ArrayList<>();

        for (Map.Entry<String, BigDecimal> entry : currencies.entrySet()) {
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
                    a.setAccountNumber("BullionVault " + currency);
                    a.setAssetAllocation(AssetAllocation.RISIKOFREI);
                    newAccounts.add(currency);
                    log.info("Created BullionVault account for currency {}", currency);
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

        // 7. Sync vault positions → one depot per location, one AssetQuantity per metal+location
        List<String> metalsUpdated    = new ArrayList<>();
        List<String> newAssets        = new ArrayList<>();
        List<String> newDepots        = new ArrayList<>();
        List<ChangedPosition> changed = new ArrayList<>();

        for (VaultPosition pos : positions) {
            Asset asset = resolveAsset(config, pos.metal(), newAssets);
            if (asset == null) {
                log.warn("Unknown BullionVault metal: {}", pos.metal());
                continue;
            }

            String depotName = "BullionVault " + pos.locationName();
            Depot depot = depotRepository.findByBankIdAndName(bank.getId(), depotName)
                .orElseGet(() -> {
                    Depot d = new Depot();
                    d.setName(depotName);
                    d.setBank(bank);
                    newDepots.add(depotName);
                    log.info("Created depot {}", depotName);
                    return depotRepository.save(d);
                });

            BigDecimal oldQty = quantityRepository
                .findFirstByAssetAndDepotOrderByDateDesc(asset, depot)
                .map(AssetQuantity::getQuantity)
                .orElse(null);

            if (oldQty == null || oldQty.compareTo(pos.oz()) != 0) {
                AssetQuantity qty = quantityRepository
                    .findByAssetAndDepotAndDate(asset, depot, today)
                    .orElseGet(() -> {
                        AssetQuantity q = new AssetQuantity();
                        q.setAsset(asset);
                        q.setDepot(depot);
                        q.setDate(today);
                        return q;
                    });
                qty.setQuantity(pos.oz());
                quantityRepository.save(qty);
                changed.add(new ChangedPosition(asset.getName(), pos.metal() + " – " + pos.locationName(), oldQty, pos.oz()));
            }
            if (!metalsUpdated.contains(pos.metal())) metalsUpdated.add(pos.metal());
        }

        configRepository.save(config);
        return new SyncResult(balancesUpdated, newAccounts, metalsUpdated, newAssets, newDepots, bank.getId(), changed);
    }

    // --- HTTP helpers ---

    private HttpClient createSession(String username, String password) {
        CookieManager cookieManager = new CookieManager();
        HttpClient httpClient = HttpClient.newBuilder()
            .cookieHandler(cookieManager)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
        try {
            String body = "j_username=" + URLEncoder.encode(username, StandardCharsets.UTF_8)
                        + "&j_password=" + URLEncoder.encode(password, StandardCharsets.UTF_8);
            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BV_LOGIN))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
            httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new IllegalStateException("Verbindung zu BullionVault fehlgeschlagen: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Verbindung zu BullionVault unterbrochen.");
        }
        return httpClient;
    }

    private String fetchBalanceXml(HttpClient httpClient) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BV_BALANCE))
                .GET()
                .build();
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            String body = resp.body();
            if (body == null || !body.contains("clientBalance")) {
                throw new IllegalStateException("Authentifizierung fehlgeschlagen – Benutzername oder Passwort falsch.");
            }
            return body;
        } catch (IllegalStateException e) {
            throw e;
        } catch (IOException e) {
            throw new IllegalStateException("Verbindung zu BullionVault fehlgeschlagen: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Verbindung zu BullionVault unterbrochen.");
        }
    }

    // --- XML parsing ---

    private void parseBalance(String xml, Map<String, BigDecimal> currencies, List<VaultPosition> vaultPositions) {
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setFeature("http://xml.org/sax/features/external-general-entities", false);
            dbf.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            dbf.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            dbf.setExpandEntityReferences(false);

            DocumentBuilder db = dbf.newDocumentBuilder();
            Document doc = db.parse(new InputSource(new StringReader(xml)));

            NodeList nodes = doc.getElementsByTagName("clientPosition");
            log.info("BullionVault: {} clientPosition element(s) found in XML", nodes.getLength());
            for (int i = 0; i < nodes.getLength(); i++) {
                Element el       = (Element) nodes.item(i);
                String secId     = el.getAttribute("securityId");
                String narrative = el.getAttribute("classNarrative");
                String availStr  = el.getAttribute("available");
                log.info("  clientPosition securityId={} classNarrative={} available={}", secId, narrative, availStr);
                if (availStr.isBlank()) continue;
                BigDecimal available = new BigDecimal(availStr);

                if ("CURRENCY".equals(narrative)) {
                    currencies.put(secId.toUpperCase(), available);
                } else if (secId.length() >= 5) {
                    // classNarrative IS the metal name: SILVER, GOLD, PLATINUM, PALLADIUM
                    // secId format: <3-char prefix><location-code>  e.g. AGXZU, AGXLN, AUXZU
                    // API returns weight in kg → convert to troy oz
                    String locationCode = secId.substring(3);
                    BigDecimal oz = available.multiply(GRAMS_PER_KG)
                        .divide(GRAMS_PER_OZ, 10, java.math.RoundingMode.HALF_UP);
                    vaultPositions.add(new VaultPosition(narrative.toUpperCase(), locationCode, locationName(locationCode), oz));
                }
            }
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Fehler beim Lesen der BullionVault-Antwort: " + e.getMessage());
        }
    }

    private String locationName(String code) {
        return switch (code) {
            case "ZU" -> "Zürich";
            case "LN" -> "London";
            case "NY" -> "New York";
            case "TR" -> "Toronto";
            case "SG" -> "Singapur";
            default   -> code;
        };
    }

    // --- Asset resolution ---

    private Asset resolveAsset(BullionVaultConfig config, String metal, List<String> newAssets) {
        return switch (metal) {
            case "GOLD" -> {
                if (config.getGoldAsset() == null)
                    config.setGoldAsset(findOrCreateMetalAsset("Gold (physisch in Unzen)", "GC=F", "USD", newAssets));
                yield config.getGoldAsset();
            }
            case "SILVER" -> {
                if (config.getSilverAsset() == null)
                    config.setSilverAsset(findOrCreateMetalAsset("Silber (physisch in Unzen)", "SI=F", "USD", newAssets));
                yield config.getSilverAsset();
            }
            case "PLATINUM" -> {
                if (config.getPlatinumAsset() == null)
                    config.setPlatinumAsset(findOrCreateMetalAsset("Platin (physisch in Unzen)", "PL=F", "USD", newAssets));
                yield config.getPlatinumAsset();
            }
            default -> null;
        };
    }

    private Asset findOrCreateMetalAsset(String name, String symbol, String currency, List<String> newAssets) {
        return assetRepository.findFirstByNameAndArchivedFalse(name).orElseGet(() -> {
            Asset a = new Asset();
            a.setName(name);
            a.setSymbol(symbol);
            a.setCurrency(currency);
            a.setCategory(AssetCategory.EDELMETALL);
            a.setAssetAllocation(AssetAllocation.RISIKOBEHAFTET);
            newAssets.add(name);
            log.info("Created asset {} ({})", name, symbol);
            return assetRepository.save(a);
        });
    }
}
