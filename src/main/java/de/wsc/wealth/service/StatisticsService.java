package de.wsc.wealth.service;

import de.wsc.wealth.domain.*;
import de.wsc.wealth.dto.MonthlyWealth;
import de.wsc.wealth.dto.StatisticsGroup;
import de.wsc.wealth.dto.WealthPosition;
import de.wsc.wealth.repository.*;
import java.util.TreeMap;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class StatisticsService {

    private final AssetRepository assetRepository;
    private final AccountRepository accountRepository;
    private final AssetQuantityRepository quantityRepository;
    private final AccountBalanceRepository balanceRepository;
    private final PriceHistoryRepository priceHistoryRepository;
    private final ExchangeRateService exchangeRateService;
    private final CoinRepository coinRepository;
    private final CoinQuantityRepository coinQuantityRepository;
    private final CoinService coinService;

    public StatisticsService(AssetRepository assetRepository,
                             AccountRepository accountRepository,
                             AssetQuantityRepository quantityRepository,
                             AccountBalanceRepository balanceRepository,
                             PriceHistoryRepository priceHistoryRepository,
                             ExchangeRateService exchangeRateService,
                             CoinRepository coinRepository,
                             CoinQuantityRepository coinQuantityRepository,
                             CoinService coinService) {
        this.assetRepository = assetRepository;
        this.accountRepository = accountRepository;
        this.quantityRepository = quantityRepository;
        this.balanceRepository = balanceRepository;
        this.priceHistoryRepository = priceHistoryRepository;
        this.exchangeRateService = exchangeRateService;
        this.coinRepository = coinRepository;
        this.coinQuantityRepository = coinQuantityRepository;
        this.coinService = coinService;
    }

    public List<WealthPosition> getAllPositions() {
        List<WealthPosition> positions = new ArrayList<>();

        // Bulk-load all quantities and balances to avoid N+1 queries
        Map<Long, Map<Long, AssetQuantity>> latestQtyByAssetDepot = new HashMap<>();
        for (AssetQuantity q : quantityRepository.findAllWithAssetAndDepot()) {
            if (q.getDate() == null || q.getQuantity() == null) continue;
            latestQtyByAssetDepot
                .computeIfAbsent(q.getAsset().getId(), k -> new HashMap<>())
                .merge(q.getDepot().getId(), q, (a, b) ->
                    b.getDate().isAfter(a.getDate()) ? b : a);
        }

        Map<Long, AccountBalance> latestBalByAccount = new HashMap<>();
        for (AccountBalance b : balanceRepository.findAllWithAccount()) {
            if (b.getDate() == null) continue;
            latestBalByAccount.merge(b.getAccount().getId(), b, (a, c) ->
                c.getDate().isAfter(a.getDate()) ? c : a);
        }

        for (Asset asset : assetRepository.findAllByArchivedFalseOrderByNameAsc()) {
            BigDecimal priceEur = exchangeRateService.toEur(asset.getCurrentPrice(), asset.getCurrency());
            BigDecimal totalQuantity = BigDecimal.ZERO;
            BigDecimal totalValue = BigDecimal.ZERO;
            List<String> assetDepots = new ArrayList<>();

            Map<Long, AssetQuantity> byDepot = latestQtyByAssetDepot.getOrDefault(asset.getId(), Map.of());
            for (AssetQuantity latest : byDepot.values()) {
                BigDecimal qty = latest.getQuantity();
                if (qty.compareTo(BigDecimal.ZERO) <= 0) continue;
                totalQuantity = totalQuantity.add(qty);
                totalValue = totalValue.add(computeValue(qty, priceEur));
                assetDepots.add(latest.getDepot().getName());
            }

            if (totalQuantity.compareTo(BigDecimal.ZERO) > 0) {
                WealthPosition p = new WealthPosition();
                p.setId(asset.getId());
                p.setName(asset.getName());
                p.setType("ASSET");
                p.setAssetType(asset.getType());
                p.setAssetAllocation(asset.getAssetAllocation());
                p.setIndexName(asset.getIndexName());
                p.setQuantity(totalQuantity);
                p.setPrice(priceEur);
                p.setCurrency("EUR");
                p.setValue(totalValue);
                p.setDepotName(String.join(", ", assetDepots));
                positions.add(p);
            }
        }

        for (Account account : accountRepository.findAllByOrderByBankAscAccountNumberAsc()) {
            AccountBalance latest = latestBalByAccount.get(account.getId());
            if (latest == null) continue;
            BigDecimal balEur = exchangeRateService.toEur(latest.getBalance(), account.getCurrency());
            if (balEur == null) continue;
            WealthPosition p = new WealthPosition();
            p.setId(account.getId());
            p.setName(account.getDisplayName());
            p.setType("ACCOUNT");
            p.setAssetAllocation(account.getAssetAllocation());
            p.setValue(balEur);
            p.setCurrency("EUR");
            positions.add(p);
        }

        Map<Long, BigDecimal> coinValueByAssetId = new HashMap<>();
        Map<Long, Set<String>> coinDepotsByAssetId = new HashMap<>();
        Map<Long, Asset> coinAssetObjects = new HashMap<>();
        for (Coin coin : coinRepository.findAllByOrderByMetalAscNameAscMintYearAsc()) {
            if (coin.getAsset() == null) continue;
            BigDecimal val = coinService.valueEur(coin);
            if (val == null) continue;
            Long aid = coin.getAsset().getId();
            coinValueByAssetId.merge(aid, val, BigDecimal::add);
            coinAssetObjects.put(aid, coin.getAsset());
            String depotName = coin.getDepot() != null ? coin.getDepot().getName() : null;
            if (depotName != null) coinDepotsByAssetId.computeIfAbsent(aid, k -> new LinkedHashSet<>()).add(depotName);
        }
        // Coin-Werte zu den verknüpften Wertpapier-Positionen addieren und Depot ergänzen
        Set<Long> mergedAssetIds = new HashSet<>();
        for (WealthPosition p : positions) {
            if (!"ASSET".equals(p.getType())) continue;
            BigDecimal extra = coinValueByAssetId.get(p.getId());
            if (extra == null) continue;
            mergedAssetIds.add(p.getId());
            p.setValue(p.getValue() != null ? p.getValue().add(extra) : extra);
            Set<String> coinDepots = coinDepotsByAssetId.get(p.getId());
            if (coinDepots != null) {
                String existing = p.getDepotName() != null && !p.getDepotName().isBlank() ? p.getDepotName() + ", " : "";
                p.setDepotName(existing + String.join(", ", coinDepots));
            }
        }
        // Coins, deren verknüpftes Wertpapier keine eigene Depotposition hat, als eigene Position einfügen
        for (Map.Entry<Long, BigDecimal> entry : coinValueByAssetId.entrySet()) {
            Long assetId = entry.getKey();
            if (mergedAssetIds.contains(assetId)) continue;
            Asset asset = coinAssetObjects.get(assetId);
            WealthPosition p = new WealthPosition();
            p.setId(assetId);
            p.setName(asset.getName());
            p.setType("COIN");
            p.setAssetType(asset.getType());
            p.setAssetAllocation(asset.getAssetAllocation());
            p.setIndexName(asset.getIndexName());
            p.setValue(entry.getValue());
            p.setCurrency("EUR");
            Set<String> coinDepots = coinDepotsByAssetId.get(assetId);
            if (coinDepots != null) p.setDepotName(String.join(", ", coinDepots));
            positions.add(p);
        }

        BigDecimal total = totalValue(positions);
        if (total.compareTo(BigDecimal.ZERO) > 0) {
            positions.forEach(p -> {
                if (p.getValue() != null) {
                    p.setPercentage(p.getValue().multiply(BigDecimal.valueOf(100))
                        .divide(total, 2, RoundingMode.HALF_UP));
                }
            });
        }
        positions.sort(Comparator.comparing(WealthPosition::getPercentage,
            Comparator.nullsLast(Comparator.reverseOrder())));
        return positions;
    }

    public BigDecimal getTotalWealth() {
        return totalValue(getAllPositions());
    }

    public List<MonthlyWealth> getWealthHistory() {
        List<AssetQuantity> allQuantities = quantityRepository.findAllWithAssetAndDepot();
        List<PriceHistory> allPrices = priceHistoryRepository.findAllWithAsset();
        List<AccountBalance> allBalances = balanceRepository.findAllWithAccount();
        List<Coin> allCoins = coinRepository.findAllByOrderByMetalAscNameAscMintYearAsc();
        List<CoinQuantity> allCoinQuantities = coinQuantityRepository.findAllWithCoin();

        // (assetId)_(depotId) -> date -> quantity
        Map<String, TreeMap<LocalDate, BigDecimal>> quantityMap = new HashMap<>();
        // assetId -> date -> PriceHistory
        Map<Long, TreeMap<LocalDate, PriceHistory>> priceMap = new HashMap<>();
        // accountId -> date -> balance
        Map<Long, TreeMap<LocalDate, BigDecimal>> balanceMap = new HashMap<>();
        // coinId -> date -> quantity
        Map<Long, TreeMap<LocalDate, Integer>> coinQtyMap = new HashMap<>();

        LocalDate minDate = null;
        for (AssetQuantity q : allQuantities) {
            String key = q.getAsset().getId() + "_" + q.getDepot().getId();
            quantityMap.computeIfAbsent(key, k -> new TreeMap<>()).put(q.getDate(), q.getQuantity());
            if (minDate == null || q.getDate().isBefore(minDate)) minDate = q.getDate();
        }
        for (PriceHistory p : allPrices) {
            priceMap.computeIfAbsent(p.getAsset().getId(), k -> new TreeMap<>()).put(p.getDate(), p);
        }
        for (AccountBalance b : allBalances) {
            balanceMap.computeIfAbsent(b.getAccount().getId(), k -> new TreeMap<>()).put(b.getDate(), b.getBalance());
            if (minDate == null || b.getDate().isBefore(minDate)) minDate = b.getDate();
        }
        for (CoinQuantity cq : allCoinQuantities) {
            coinQtyMap.computeIfAbsent(cq.getCoin().getId(), k -> new TreeMap<>()).put(cq.getDate(), cq.getQuantity());
            if (minDate == null || cq.getDate().isBefore(minDate)) minDate = cq.getDate();
        }

        if (minDate == null) return Collections.emptyList();

        Map<Long, Asset> assetById = assetRepository.findAll().stream()
            .collect(Collectors.toMap(Asset::getId, a -> a));
        Map<Long, Account> accountById = accountRepository.findAll().stream()
            .collect(Collectors.toMap(Account::getId, a -> a));

        LocalDate firstMonth = minDate.withDayOfMonth(1);
        LocalDate currentMonth = LocalDate.now().withDayOfMonth(1);

        List<MonthlyWealth> result = new ArrayList<>();
        for (LocalDate month = firstMonth; !month.isAfter(currentMonth); month = month.plusMonths(1)) {
            LocalDate valuationDate = month.equals(currentMonth)
                ? LocalDate.now()
                : month.withDayOfMonth(month.lengthOfMonth());

            BigDecimal assetsValue = BigDecimal.ZERO;
            BigDecimal accountsValue = BigDecimal.ZERO;
            BigDecimal coinsValue = BigDecimal.ZERO;

            for (Map.Entry<String, TreeMap<LocalDate, BigDecimal>> entry : quantityMap.entrySet()) {
                Map.Entry<LocalDate, BigDecimal> qtEntry = entry.getValue().floorEntry(valuationDate);
                if (qtEntry == null) continue;
                BigDecimal qty = qtEntry.getValue();
                if (qty == null || qty.compareTo(BigDecimal.ZERO) <= 0) continue;

                Long assetId = Long.parseLong(entry.getKey().split("_")[0]);
                Asset asset = assetById.get(assetId);
                if (asset == null) continue;

                BigDecimal priceEur = null;
                TreeMap<LocalDate, PriceHistory> prMap = priceMap.get(assetId);
                if (prMap != null) {
                    Map.Entry<LocalDate, PriceHistory> prEntry = prMap.floorEntry(valuationDate);
                    if (prEntry != null) {
                        PriceHistory ph = prEntry.getValue();
                        priceEur = exchangeRateService.toEur(ph.getPrice(), ph.getCurrency());
                    }
                }
                if (priceEur == null) {
                    priceEur = exchangeRateService.toEur(asset.getCurrentPrice(), asset.getCurrency());
                }
                if (priceEur == null) continue;

                assetsValue = assetsValue.add(qty.multiply(priceEur).setScale(2, RoundingMode.HALF_UP));
            }

            for (Map.Entry<Long, TreeMap<LocalDate, BigDecimal>> entry : balanceMap.entrySet()) {
                Map.Entry<LocalDate, BigDecimal> balEntry = entry.getValue().floorEntry(valuationDate);
                if (balEntry == null || balEntry.getValue() == null) continue;
                Account account = accountById.get(entry.getKey());
                if (account == null) continue;
                BigDecimal balEur = exchangeRateService.toEur(balEntry.getValue(), account.getCurrency());
                if (balEur != null) accountsValue = accountsValue.add(balEur);
            }

            for (Coin coin : allCoins) {
                if (coin.getAsset() == null || coin.getMetal() == null || coin.getWeightGrams() == null) continue;
                int qty;
                TreeMap<LocalDate, Integer> cqMap = coinQtyMap.get(coin.getId());
                if (cqMap != null && !cqMap.isEmpty()) {
                    Map.Entry<LocalDate, Integer> cqEntry = cqMap.floorEntry(valuationDate);
                    qty = cqEntry != null ? cqEntry.getValue() : 0;
                } else {
                    qty = coin.getQuantity() != null ? coin.getQuantity() : 0;
                }
                if (qty <= 0) continue;
                BigDecimal oz = coin.getWeightOz();
                if (oz == null) continue;

                BigDecimal priceEur = null;
                TreeMap<LocalDate, PriceHistory> prMap = priceMap.get(coin.getAsset().getId());
                if (prMap != null) {
                    Map.Entry<LocalDate, PriceHistory> prEntry = prMap.floorEntry(valuationDate);
                    if (prEntry != null) {
                        PriceHistory ph = prEntry.getValue();
                        priceEur = exchangeRateService.toEur(ph.getPrice(), ph.getCurrency());
                    }
                }
                if (priceEur == null) {
                    priceEur = exchangeRateService.toEur(coin.getAsset().getCurrentPrice(), coin.getAsset().getCurrency());
                }
                if (priceEur == null) continue;
                coinsValue = coinsValue.add(
                    BigDecimal.valueOf(qty).multiply(oz).multiply(priceEur).setScale(2, RoundingMode.HALF_UP));
            }

            result.add(new MonthlyWealth(month, assetsValue, accountsValue, coinsValue));
        }

        Collections.reverse(result);
        return result;
    }

    public List<StatisticsGroup> getStatsByIndex() {
        List<WealthPosition> all = getAllPositions();
        BigDecimal total = totalValue(all);

        Map<String, List<WealthPosition>> grouped = all.stream()
            .filter(p -> "ASSET".equals(p.getType()) || "COIN".equals(p.getType()))
            .collect(Collectors.groupingBy(
                p -> p.getIndexName() != null && !p.getIndexName().isBlank() ? p.getIndexName() : "KEIN_INDEX",
                LinkedHashMap::new, Collectors.toList()
            ));

        List<WealthPosition> accounts = all.stream().filter(p -> "ACCOUNT".equals(p.getType())).toList();
        if (!accounts.isEmpty()) grouped.put("Konten", accounts);

        return buildGroups(grouped, total);
    }

    public List<StatisticsGroup> getStatsByType() {
        List<WealthPosition> all = getAllPositions();
        BigDecimal total = totalValue(all);

        Map<String, List<WealthPosition>> grouped = all.stream()
            .collect(Collectors.groupingBy(
                p -> "ACCOUNT".equals(p.getType()) ? "KONTO"
                    : (p.getAssetType() != null ? p.getAssetType().name() : "SONSTIGE"),
                LinkedHashMap::new, Collectors.toList()
            ));

        return buildGroups(grouped, total);
    }

    public List<StatisticsGroup> getStatsByAllocation() {
        List<WealthPosition> all = getAllPositions();
        BigDecimal total = totalValue(all);

        Map<String, List<WealthPosition>> grouped = all.stream()
            .collect(Collectors.groupingBy(
                p -> p.getAssetAllocation() != null ? p.getAssetAllocation().name() : "NICHT_KLASSIFIZIERT",
                LinkedHashMap::new, Collectors.toList()
            ));

        return buildGroups(grouped, total);
    }

    private List<StatisticsGroup> buildGroups(Map<String, List<WealthPosition>> grouped, BigDecimal total) {
        return grouped.entrySet().stream().map(entry -> {
            List<WealthPosition> sorted = entry.getValue().stream()
                .sorted(Comparator.comparing(WealthPosition::getPercentage,
                    Comparator.nullsLast(Comparator.reverseOrder())))
                .collect(Collectors.toList());
            BigDecimal groupTotal = totalValue(sorted);
            BigDecimal pct = total.compareTo(BigDecimal.ZERO) > 0
                ? groupTotal.multiply(BigDecimal.valueOf(100)).divide(total, 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
            return new StatisticsGroup(entry.getKey(), sorted, groupTotal, pct);
        })
        .sorted(Comparator.comparing(StatisticsGroup::getPercentage,
            Comparator.nullsLast(Comparator.reverseOrder())))
        .collect(Collectors.toList());
    }

    private BigDecimal totalValue(List<WealthPosition> positions) {
        return positions.stream()
            .map(WealthPosition::getValue)
            .filter(Objects::nonNull)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal computeValue(BigDecimal quantity, BigDecimal price) {
        if (quantity == null || price == null) return BigDecimal.ZERO;
        return quantity.multiply(price).setScale(2, RoundingMode.HALF_UP);
    }
}
