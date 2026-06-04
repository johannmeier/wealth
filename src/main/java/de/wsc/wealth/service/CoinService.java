package de.wsc.wealth.service;

import de.wsc.wealth.domain.Asset;
import de.wsc.wealth.domain.Coin;
import de.wsc.wealth.domain.CoinMetal;
import de.wsc.wealth.domain.CoinQuantity;
import de.wsc.wealth.domain.Depot;
import de.wsc.wealth.repository.AssetRepository;
import de.wsc.wealth.repository.CoinQuantityRepository;
import de.wsc.wealth.repository.CoinRepository;
import de.wsc.wealth.repository.DepotRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@Transactional
public class CoinService {

    private static final Logger log = LoggerFactory.getLogger(CoinService.class);
    private static final BigDecimal GRAMS_PER_OZ = new BigDecimal("31.1034768");

    private final CoinRepository coinRepository;
    private final CoinQuantityRepository coinQuantityRepository;
    private final DepotRepository depotRepository;
    private final AssetRepository assetRepository;
    private final PriceService priceService;
    private final ExchangeRateService exchangeRateService;

    public CoinService(CoinRepository coinRepository,
                       CoinQuantityRepository coinQuantityRepository,
                       DepotRepository depotRepository,
                       AssetRepository assetRepository,
                       PriceService priceService, ExchangeRateService exchangeRateService) {
        this.coinRepository = coinRepository;
        this.coinQuantityRepository = coinQuantityRepository;
        this.depotRepository = depotRepository;
        this.assetRepository = assetRepository;
        this.priceService = priceService;
        this.exchangeRateService = exchangeRateService;
    }

    @Transactional(readOnly = true)
    public List<Coin> findAll() { return coinRepository.findAllByOrderByMetalAscNameAscMintYearAsc(); }

    @Transactional(readOnly = true)
    public Optional<Coin> findById(Long id) { return coinRepository.findById(id); }

    @Transactional(readOnly = true)
    public List<String> findAllNames() { return coinRepository.findDistinctNames(); }

    @Transactional(readOnly = true)
    public Optional<Coin> findFirstByName(String name) { return coinRepository.findFirstByName(name); }

    @Transactional(readOnly = true)
    public List<Coin> findByDepotId(Long depotId) {
        Depot depot = depotRepository.findById(depotId).orElseThrow();
        return coinRepository.findByDepotOrderByMetalAscNameAscMintYearAsc(depot);
    }

    public BigDecimal totalValueEur(List<Coin> coins, Map<CoinMetal, BigDecimal> spotPricesUsd) {
        return coins.stream()
            .map(c -> valueEur(c, spotPricesUsd))
            .filter(v -> v != null)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public Coin save(Long depotId, Long assetId, Coin coin) {
        Depot depot = depotRepository.findById(depotId).orElseThrow();
        coin.setDepot(depot);
        coin.setAsset(assetId != null ? assetRepository.findById(assetId).orElse(null) : null);
        Coin saved = coinRepository.save(coin);
        if (saved.getQuantity() != null) {
            Optional<CoinQuantity> latest = coinQuantityRepository.findFirstByCoinOrderByDateDesc(saved);
            boolean changed = latest.isEmpty() || !latest.get().getQuantity().equals(saved.getQuantity());
            if (changed) {
                CoinQuantity cq = new CoinQuantity();
                cq.setCoin(saved);
                cq.setDate(LocalDate.now());
                cq.setQuantity(saved.getQuantity());
                coinQuantityRepository.save(cq);
            }
        }
        return saved;
    }

    @Transactional(readOnly = true)
    public List<CoinQuantity> findQuantities(Long coinId) {
        Coin coin = coinRepository.findById(coinId).orElseThrow();
        return coinQuantityRepository.findByCoinOrderByDateDesc(coin);
    }

    public void saveQuantity(Long coinId, LocalDate date, Integer quantity) {
        Coin coin = coinRepository.findById(coinId).orElseThrow();
        CoinQuantity cq = new CoinQuantity();
        cq.setCoin(coin);
        cq.setDate(date);
        cq.setQuantity(quantity);
        coinQuantityRepository.save(cq);
        coinQuantityRepository.findFirstByCoinOrderByDateDesc(coin).ifPresent(latest -> {
            coin.setQuantity(latest.getQuantity());
            coinRepository.save(coin);
        });
    }

    public void deleteQuantity(Long id) {
        coinQuantityRepository.findById(id).ifPresent(cq -> {
            coinQuantityRepository.deleteById(id);
            Optional<CoinQuantity> newLatest = coinQuantityRepository.findFirstByCoinOrderByDateDesc(cq.getCoin());
            Coin coin = cq.getCoin();
            coin.setQuantity(newLatest.map(CoinQuantity::getQuantity).orElse(0));
            coinRepository.save(coin);
        });
    }

    @Transactional(readOnly = true)
    public List<Asset> findAllAssets() { return assetRepository.findAllByArchivedFalseOrderByNameAsc(); }

    @Transactional(readOnly = true)
    public Map<String, Long> getMetalToAssetId() {
        return coinRepository.findAllByOrderByMetalAscNameAscMintYearAsc().stream()
            .filter(c -> c.getMetal() != null && c.getAsset() != null)
            .collect(java.util.stream.Collectors.toMap(
                c -> c.getMetal().name(),
                c -> c.getAsset().getId(),
                (a, b) -> a
            ));
    }

    public void delete(Long id) {
        coinRepository.findById(id).ifPresent(coin -> {
            coinQuantityRepository.findByCoinOrderByDateDesc(coin)
                .forEach(cq -> coinQuantityRepository.deleteById(cq.getId()));
            coinRepository.deleteById(id);
        });
    }

    @Transactional(readOnly = true)
    public List<Depot> findAllDepots() { return depotRepository.findAllByOrderByNameAsc(); }

    // Spot-Preise (USD/oz) für alle drei Metalle auf einmal holen
    public Map<CoinMetal, BigDecimal> fetchSpotPricesUsd() {
        Map<CoinMetal, BigDecimal> prices = new EnumMap<>(CoinMetal.class);
        for (CoinMetal metal : CoinMetal.values()) {
            try {
                prices.put(metal, priceService.fetchPrice(metal.getYahooSymbol()));
            } catch (Exception e) {
                log.warn("Failed to fetch spot price for {}: {}", metal.getYahooSymbol(), e.getMessage());
            }
        }
        return prices;
    }

    // Wert einer Münze in EUR:
    // - Mit verknüpftem Wertpapier: Anzahl × (Gewicht / 31,1035) × Kurs(EUR/oz)
    // - Ohne Wertpapier: Anzahl × (Gewicht / 31,1035) × Spotpreis(USD/oz) × USD→EUR
    public BigDecimal valueEur(Coin coin, Map<CoinMetal, BigDecimal> spotPricesUsd) {
        if (coin.getMetal() == null || coin.getWeightGrams() == null || coin.getQuantity() == null) return null;
        BigDecimal qty = BigDecimal.valueOf(coin.getQuantity());
        BigDecimal oz = coin.getWeightGrams().divide(GRAMS_PER_OZ, 10, RoundingMode.HALF_UP);
        Asset asset = coin.getAsset();
        if (asset != null && asset.getCurrentPrice() != null) {
            BigDecimal priceEur = exchangeRateService.toEur(asset.getCurrentPrice(), asset.getCurrency());
            if (priceEur != null) return qty.multiply(oz).multiply(priceEur).setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal spotUsd = spotPricesUsd.get(coin.getMetal());
        if (spotUsd == null) return null;
        BigDecimal valueUsd = qty.multiply(oz).multiply(spotUsd);
        return exchangeRateService.toEur(valueUsd, "USD");
    }
}
