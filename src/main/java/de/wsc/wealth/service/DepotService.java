package de.wsc.wealth.service;

import de.wsc.wealth.domain.Asset;
import de.wsc.wealth.domain.AssetQuantity;
import de.wsc.wealth.domain.Depot;
import de.wsc.wealth.repository.AssetQuantityRepository;
import de.wsc.wealth.repository.AssetRepository;
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

    private final ExchangeRateService exchangeRateService;

    public DepotService(DepotRepository depotRepository,
                        AssetRepository assetRepository,
                        AssetQuantityRepository quantityRepository,
                        ExchangeRateService exchangeRateService) {
        this.depotRepository = depotRepository;
        this.assetRepository = assetRepository;
        this.quantityRepository = quantityRepository;
        this.exchangeRateService = exchangeRateService;
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
        quantity.setId(null);
        quantity.setDepot(depot);
        quantity.setAsset(asset);
        return quantityRepository.save(quantity);
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
    public List<Asset> findAllAssets() { return assetRepository.findAllByOrderByNameAsc(); }

    @Transactional(readOnly = true)
    public Map<Long, BigDecimal> getCurrentValueByDepotId() {
        Map<Long, BigDecimal> result = new LinkedHashMap<>();
        for (Depot depot : depotRepository.findAllByOrderByNameAsc()) {
            // group by asset, keep only the most recent entry per asset (list is already date-desc)
            BigDecimal depotValue = quantityRepository.findByDepotOrderByDateDesc(depot).stream()
                .collect(Collectors.toMap(
                    q -> q.getAsset().getId(),
                    q -> q,
                    (existing, replacement) -> existing
                ))
                .values().stream()
                .filter(q -> q.getQuantity() != null
                         && exchangeRateService.toEur(q.getAsset().getCurrentPrice(), q.getAsset().getCurrency()) != null)
                .map(q -> q.getQuantity()
                    .multiply(exchangeRateService.toEur(q.getAsset().getCurrentPrice(), q.getAsset().getCurrency()))
                    .setScale(2, RoundingMode.HALF_UP))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            result.put(depot.getId(), depotValue);
        }
        return result;
    }
}
