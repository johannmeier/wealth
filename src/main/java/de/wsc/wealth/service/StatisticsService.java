package de.wsc.wealth.service;

import de.wsc.wealth.domain.*;
import de.wsc.wealth.dto.StatisticsGroup;
import de.wsc.wealth.dto.WealthPosition;
import de.wsc.wealth.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class StatisticsService {

    private final AssetRepository assetRepository;
    private final AccountRepository accountRepository;
    private final DepotRepository depotRepository;
    private final AssetQuantityRepository quantityRepository;
    private final AccountBalanceRepository balanceRepository;
    private final ExchangeRateService exchangeRateService;

    public StatisticsService(AssetRepository assetRepository,
                             AccountRepository accountRepository,
                             DepotRepository depotRepository,
                             AssetQuantityRepository quantityRepository,
                             AccountBalanceRepository balanceRepository,
                             ExchangeRateService exchangeRateService) {
        this.assetRepository = assetRepository;
        this.accountRepository = accountRepository;
        this.depotRepository = depotRepository;
        this.quantityRepository = quantityRepository;
        this.balanceRepository = balanceRepository;
        this.exchangeRateService = exchangeRateService;
    }

    public List<WealthPosition> getAllPositions() {
        List<WealthPosition> positions = new ArrayList<>();
        List<Depot> depots = depotRepository.findAllByOrderByNameAsc();

        for (Asset asset : assetRepository.findAllByOrderByNameAsc()) {
            BigDecimal priceEur = exchangeRateService.toEur(asset.getCurrentPrice(), asset.getCurrency());
            BigDecimal totalQuantity = BigDecimal.ZERO;
            BigDecimal totalValue = BigDecimal.ZERO;
            List<String> assetDepots = new ArrayList<>();

            for (Depot depot : depots) {
                var latest = quantityRepository.findFirstByAssetAndDepotOrderByDateDesc(asset, depot);
                if (latest.isPresent() && latest.get().getQuantity() != null) {
                    BigDecimal qty = latest.get().getQuantity();
                    totalQuantity = totalQuantity.add(qty);
                    totalValue = totalValue.add(computeValue(qty, priceEur));
                    assetDepots.add(depot.getName());
                }
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
            balanceRepository.findFirstByAccountOrderByDateDesc(account).ifPresent(ab -> {
                WealthPosition p = new WealthPosition();
                p.setId(account.getId());
                p.setName(account.getDisplayName());
                p.setType("ACCOUNT");
                p.setAssetAllocation(account.getAssetAllocation());
                p.setValue(ab.getBalance());
                p.setCurrency(account.getCurrency());
                positions.add(p);
            });
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
        return positions;
    }

    public BigDecimal getTotalWealth() {
        return totalValue(getAllPositions());
    }

    public List<StatisticsGroup> getStatsByIndex() {
        List<WealthPosition> all = getAllPositions();
        BigDecimal total = totalValue(all);

        Map<String, List<WealthPosition>> grouped = all.stream()
            .filter(p -> "ASSET".equals(p.getType()))
            .collect(Collectors.groupingBy(
                p -> p.getIndexName() != null && !p.getIndexName().isBlank() ? p.getIndexName() : "Kein Index",
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
                p -> "ACCOUNT".equals(p.getType()) ? "Konto"
                    : (p.getAssetType() != null ? p.getAssetType().getLabel() : "Sonstige"),
                LinkedHashMap::new, Collectors.toList()
            ));

        return buildGroups(grouped, total);
    }

    public List<StatisticsGroup> getStatsByAllocation() {
        List<WealthPosition> all = getAllPositions();
        BigDecimal total = totalValue(all);

        Map<String, List<WealthPosition>> grouped = all.stream()
            .collect(Collectors.groupingBy(
                p -> p.getAssetAllocation() != null ? p.getAssetAllocation().getLabel() : "Nicht klassifiziert",
                LinkedHashMap::new, Collectors.toList()
            ));

        return buildGroups(grouped, total);
    }

    private List<StatisticsGroup> buildGroups(Map<String, List<WealthPosition>> grouped, BigDecimal total) {
        return grouped.entrySet().stream().map(entry -> {
            BigDecimal groupTotal = totalValue(entry.getValue());
            BigDecimal pct = total.compareTo(BigDecimal.ZERO) > 0
                ? groupTotal.multiply(BigDecimal.valueOf(100)).divide(total, 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
            return new StatisticsGroup(entry.getKey(), entry.getValue(), groupTotal, pct);
        }).collect(Collectors.toList());
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
