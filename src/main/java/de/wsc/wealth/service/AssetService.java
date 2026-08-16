package de.wsc.wealth.service;

import de.wsc.wealth.domain.*;
import de.wsc.wealth.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@Transactional
public class AssetService {

    private final AssetRepository assetRepository;
    private final AssetQuantityRepository quantityRepository;
    private final DepotRepository depotRepository;
    private final PriceHistoryRepository priceHistoryRepository;
    private final de.wsc.wealth.repository.CoinRepository coinRepository;

    public AssetService(AssetRepository assetRepository,
                        AssetQuantityRepository quantityRepository,
                        DepotRepository depotRepository,
                        PriceHistoryRepository priceHistoryRepository,
                        de.wsc.wealth.repository.CoinRepository coinRepository) {
        this.assetRepository = assetRepository;
        this.quantityRepository = quantityRepository;
        this.depotRepository = depotRepository;
        this.priceHistoryRepository = priceHistoryRepository;
        this.coinRepository = coinRepository;
    }

    @Transactional(readOnly = true)
    public List<Asset> findAll() { return assetRepository.findAllByArchivedFalseOrderByNameAsc(); }

    @Transactional(readOnly = true)
    public Optional<Asset> findById(Long id) { return assetRepository.findById(id); }

    public Asset save(Asset asset) {
        if (asset.getId() == null) {
            Optional<Asset> archived = findArchivedMatch(asset);
            if (archived.isPresent()) {
                Asset existing = archived.get();
                existing.setArchived(false);
                existing.setName(asset.getName());
                existing.setIsin(asset.getIsin());
                existing.setSymbol(asset.getSymbol());
                existing.setCurrency(asset.getCurrency());
                if (asset.getCurrentPrice() != null) existing.setCurrentPrice(asset.getCurrentPrice());
                return assetRepository.save(existing);
            }
        }

        BigDecimal newPrice = asset.getCurrentPrice();
        boolean recordPrice = newPrice != null;

        if (recordPrice && asset.getId() != null) {
            recordPrice = assetRepository.findById(asset.getId())
                .map(existing -> existing.getCurrentPrice() == null
                              || existing.getCurrentPrice().compareTo(newPrice) != 0)
                .orElse(true);
        }

        Asset saved = assetRepository.save(asset);

        if (recordPrice) {
            LocalDate today = LocalDate.now();
            PriceHistory entry = priceHistoryRepository.findByAssetAndDate(saved, today)
                .orElseGet(() -> {
                    PriceHistory h = new PriceHistory();
                    h.setAsset(saved);
                    h.setDate(today);
                    return h;
                });
            entry.setPrice(newPrice);
            entry.setCurrency(saved.getCurrency());
            priceHistoryRepository.save(entry);
        }

        return saved;
    }

    public void delete(Long id) {
        assetRepository.findById(id).ifPresent(asset -> {
            asset.setArchived(true);
            assetRepository.save(asset);
        });
    }

    public List<Asset> findAllArchived() { return assetRepository.findAllByArchivedTrueOrderByNameAsc(); }

    public void hardDelete(Long id) {
        assetRepository.findById(id).ifPresent(asset -> {
            if (quantityRepository.existsByAsset(asset) || coinRepository.existsByAsset(asset)) {
                throw new IllegalStateException("Asset hat noch verknüpfte Bestände oder Münzen und kann nicht gelöscht werden.");
            }
            priceHistoryRepository.deleteByAsset(asset);
            assetRepository.delete(asset);
        });
    }

    @Transactional(readOnly = true)
    public boolean isDeletable(Long id) {
        return assetRepository.findById(id).map(asset ->
            !quantityRepository.existsByAsset(asset) && !coinRepository.existsByAsset(asset)
        ).orElse(false);
    }

    @Transactional(readOnly = true)
    public java.util.Set<Long> getDeletableArchivedIds() {
        java.util.Set<Long> withQty = quantityRepository.findDistinctAssetIds();
        java.util.Set<Long> withCoins = coinRepository.findDistinctLinkedAssetIds();
        return findAllArchived().stream()
            .filter(a -> !withQty.contains(a.getId()) && !withCoins.contains(a.getId()))
            .map(Asset::getId)
            .collect(java.util.stream.Collectors.toSet());
    }

    @Transactional(readOnly = true)
    public java.util.Set<Long> getDeletableActiveIds() {
        java.util.Set<Long> withQty = quantityRepository.findDistinctAssetIds();
        java.util.Set<Long> withCoins = coinRepository.findDistinctLinkedAssetIds();
        return findAll().stream()
            .filter(a -> !withQty.contains(a.getId()) && !withCoins.contains(a.getId()))
            .map(Asset::getId)
            .collect(java.util.stream.Collectors.toSet());
    }

    // Falls der letzte Kursabruf fehlgeschlagen ist (currentPrice == null), wird der jüngste
    // Eintrag aus der Kurshistorie als Ersatzwert für Berechnungen herangezogen.
    @Transactional(readOnly = true)
    public BigDecimal getEffectivePrice(Asset asset) {
        if (asset.getCurrentPrice() != null) return asset.getCurrentPrice();
        return priceHistoryRepository.findFirstByAssetOrderByDateDesc(asset)
            .map(PriceHistory::getPrice)
            .orElse(null);
    }

    @Transactional(readOnly = true)
    public Map<Long, BigDecimal> getEffectivePricesByAssetId() {
        Map<Long, BigDecimal> result = new java.util.HashMap<>();
        for (PriceHistory ph : priceHistoryRepository.findLatestPerAsset()) {
            result.put(ph.getAsset().getId(), ph.getPrice());
        }
        for (Asset a : assetRepository.findAll()) {
            if (a.getCurrentPrice() != null) result.put(a.getId(), a.getCurrentPrice());
        }
        return result;
    }

    private Optional<Asset> findArchivedMatch(Asset asset) {
        boolean hasIsin   = asset.getIsin()   != null && !asset.getIsin().isBlank();
        boolean hasSymbol = asset.getSymbol() != null && !asset.getSymbol().isBlank();

        // ISIN + Symbol müssen beide übereinstimmen (gleiches Listing auf gleicher Börse)
        if (hasIsin && hasSymbol) {
            return assetRepository.findFirstByArchivedTrueAndIsin(asset.getIsin())
                .filter(a -> asset.getSymbol().equalsIgnoreCase(a.getSymbol()));
        }
        // Nur Symbol bekannt → danach suchen
        if (hasSymbol) {
            return assetRepository.findFirstByArchivedTrueAndSymbol(asset.getSymbol());
        }
        return Optional.empty();
    }

    public void restore(Long id) {
        assetRepository.findById(id).ifPresent(asset -> {
            asset.setArchived(false);
            assetRepository.save(asset);
        });
    }

    public AssetQuantity saveQuantity(Long assetId, Long depotId, AssetQuantity quantity) {
        Asset asset = assetRepository.findById(assetId).orElseThrow();
        Depot depot = depotRepository.findById(depotId).orElseThrow();
        AssetQuantity entry = quantityRepository
            .findByAssetAndDepotAndDate(asset, depot, quantity.getDate())
            .orElseGet(() -> {
                AssetQuantity q = new AssetQuantity();
                q.setAsset(asset);
                q.setDepot(depot);
                q.setDate(quantity.getDate());
                return q;
            });
        entry.setQuantity(quantity.getQuantity() != null
            ? quantity.getQuantity().multiply(depot.getOwnershipFactor()) : null);
        return quantityRepository.save(entry);
    }

    @Transactional(readOnly = true)
    public List<AssetQuantity> getQuantities(Long assetId) {
        Asset asset = assetRepository.findById(assetId).orElseThrow();
        return quantityRepository.findByAssetOrderByDateDesc(asset);
    }

    @Transactional(readOnly = true)
    public List<Depot> findAllDepots() { return depotRepository.findAllByOrderByNameAsc(); }

    @Transactional(readOnly = true)
    public List<PriceHistory> getPriceHistory(Long assetId) {
        Asset asset = assetRepository.findById(assetId).orElseThrow();
        return priceHistoryRepository.findByAssetOrderByDateAsc(asset);
    }

    @Transactional(readOnly = true)
    public Map<Long, Map<Long, String>> getDepotsByAssetId() {
        Map<Long, Map<Long, String>> result = new java.util.HashMap<>();
        for (Asset asset : assetRepository.findAllByArchivedFalseOrderByNameAsc()) {
            Map<Long, String> depots = quantityRepository.findByAssetOrderByDateDesc(asset).stream()
                .collect(java.util.stream.Collectors.toMap(
                    q -> q.getDepot().getId(),
                    q -> q.getDepot().getName(),
                    (a, b) -> a,
                    java.util.TreeMap::new
                ));
            if (!depots.isEmpty()) result.put(asset.getId(), depots);
        }

        // Coins also count as holding an asset in their depot — either the manually linked one,
        // or (for physical bullion without a link) the metal's spot-price asset. Without this,
        // coin-only depots (e.g. a private safe) would be missing from the depot list.
        for (Coin coin : coinRepository.findAllWithDepot()) {
            if (coin.getDepot() == null) continue;
            Asset resolved = coin.getAsset() != null ? coin.getAsset()
                : (coin.getMetal() != null
                    ? assetRepository.findFirstBySymbolAndArchivedFalse(coin.getMetal().getYahooSymbol()).orElse(null)
                    : null);
            if (resolved == null) continue;
            result.computeIfAbsent(resolved.getId(), k -> new java.util.TreeMap<>())
                  .put(coin.getDepot().getId(), coin.getDepot().getName());
        }
        return result;
    }
}
