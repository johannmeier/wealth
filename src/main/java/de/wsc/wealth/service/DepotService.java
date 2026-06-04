package de.wsc.wealth.service;

import de.wsc.wealth.domain.Asset;
import de.wsc.wealth.domain.AssetQuantity;
import de.wsc.wealth.domain.Depot;
import de.wsc.wealth.repository.AssetQuantityRepository;
import de.wsc.wealth.repository.AssetRepository;
import de.wsc.wealth.repository.CoinRepository;
import de.wsc.wealth.repository.DepotRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Transactional
public class DepotService {

    private final DepotRepository depotRepository;
    private final AssetRepository assetRepository;
    private final AssetQuantityRepository quantityRepository;
    private final CoinRepository coinRepository;
    private final ExchangeRateService exchangeRateService;
    private final CoinService coinService;

    public DepotService(DepotRepository depotRepository,
                        AssetRepository assetRepository,
                        AssetQuantityRepository quantityRepository,
                        CoinRepository coinRepository,
                        ExchangeRateService exchangeRateService,
                        CoinService coinService) {
        this.depotRepository = depotRepository;
        this.assetRepository = assetRepository;
        this.quantityRepository = quantityRepository;
        this.coinRepository = coinRepository;
        this.exchangeRateService = exchangeRateService;
        this.coinService = coinService;
    }

    @Transactional(readOnly = true)
    public List<Depot> findAll() { return depotRepository.findAllByOrderByNameAsc(); }

    @Transactional(readOnly = true)
    public Optional<Depot> findById(Long id) { return depotRepository.findById(id); }

    public Depot save(Depot depot) { return depotRepository.save(depot); }

    public void delete(Long id) { depotRepository.deleteById(id); }

    @Transactional(readOnly = true)
    public List<AssetQuantity> getQuantities(Long depotId) {
        Depot depot = depotRepository.findById(depotId).orElseThrow();
        return quantityRepository.findByDepotOrderByDateDesc(depot);
    }

    public AssetQuantity saveQuantity(Long depotId, Long assetId, AssetQuantity quantity) {
        Depot depot = depotRepository.findById(depotId).orElseThrow();
        Asset asset = assetRepository.findById(assetId).orElseThrow();
        AssetQuantity entry = quantityRepository
            .findByAssetAndDepotAndDate(asset, depot, quantity.getDate())
            .orElseGet(() -> {
                AssetQuantity q = new AssetQuantity();
                q.setDepot(depot);
                q.setAsset(asset);
                q.setDate(quantity.getDate());
                return q;
            });
        entry.setQuantity(quantity.getQuantity());
        return quantityRepository.save(entry);
    }

    public void updateQuantity(Long quantityId, Long assetId, LocalDate date, BigDecimal quantity) {
        AssetQuantity q = quantityRepository.findById(quantityId).orElseThrow();
        Asset asset = assetRepository.findById(assetId).orElseThrow();
        q.setAsset(asset);
        q.setDate(date);
        q.setQuantity(quantity);
        quantityRepository.save(q);
    }

    public void deleteQuantity(Long quantityId) {
        quantityRepository.deleteById(quantityId);
    }

    @Transactional(readOnly = true)
    public List<Asset> findAllAssets() { return assetRepository.findAllByArchivedFalseOrderByNameAsc(); }

    @Transactional(readOnly = true)
    public Map<Long, BigDecimal> getCurrentValueByDepotId() {
        // Bulk-load: latest quantity per depot+asset
        Map<Long, Map<Long, AssetQuantity>> latestQtyByDepotAsset = new java.util.HashMap<>();
        for (AssetQuantity q : quantityRepository.findAllWithAssetAndDepot()) {
            if (q.getQuantity() == null) continue;
            latestQtyByDepotAsset
                .computeIfAbsent(q.getDepot().getId(), k -> new java.util.HashMap<>())
                .merge(q.getAsset().getId(), q, (a, b) ->
                    b.getDate().isAfter(a.getDate()) ? b : a);
        }

        // Bulk-load: coin values per depot
        Map<Long, BigDecimal> coinValueByDepotId = new java.util.HashMap<>();
        for (de.wsc.wealth.domain.Coin c : coinRepository.findAllWithDepot()) {
            if (c.getDepot() == null) continue;
            BigDecimal val = coinService.valueEur(c);
            if (val != null) coinValueByDepotId.merge(c.getDepot().getId(), val, BigDecimal::add);
        }

        Map<Long, BigDecimal> result = new LinkedHashMap<>();
        for (Depot depot : depotRepository.findAllByOrderByNameAsc()) {
            BigDecimal assetValue = latestQtyByDepotAsset
                .getOrDefault(depot.getId(), java.util.Map.of()).values().stream()
                .filter(q -> q.getQuantity().compareTo(BigDecimal.ZERO) > 0)
                .map(q -> {
                    BigDecimal eur = exchangeRateService.toEur(
                        q.getAsset().getCurrentPrice(), q.getAsset().getCurrency());
                    return eur != null
                        ? q.getQuantity().multiply(eur).setScale(2, RoundingMode.HALF_UP)
                        : null;
                })
                .filter(v -> v != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal coinValue = coinValueByDepotId.getOrDefault(depot.getId(), BigDecimal.ZERO);
            result.put(depot.getId(), assetValue.add(coinValue));
        }
        return result;
    }
}
