package de.wsc.wealth.service;

import de.wsc.wealth.domain.Asset;
import de.wsc.wealth.domain.Coin;
import de.wsc.wealth.domain.CoinQuantity;
import de.wsc.wealth.domain.Depot;
import de.wsc.wealth.repository.AssetRepository;
import de.wsc.wealth.repository.CoinQuantityRepository;
import de.wsc.wealth.repository.CoinRepository;
import de.wsc.wealth.repository.DepotRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@Transactional
public class CoinService {

    private static final BigDecimal GRAMS_PER_OZ = new BigDecimal("31.1034768");

    private final CoinRepository coinRepository;
    private final CoinQuantityRepository coinQuantityRepository;
    private final DepotRepository depotRepository;
    private final AssetRepository assetRepository;
    private final ExchangeRateService exchangeRateService;
    private final AssetService assetService;

    public CoinService(CoinRepository coinRepository,
                       CoinQuantityRepository coinQuantityRepository,
                       DepotRepository depotRepository,
                       AssetRepository assetRepository,
                       ExchangeRateService exchangeRateService,
                       AssetService assetService) {
        this.coinRepository = coinRepository;
        this.coinQuantityRepository = coinQuantityRepository;
        this.depotRepository = depotRepository;
        this.assetRepository = assetRepository;
        this.exchangeRateService = exchangeRateService;
        this.assetService = assetService;
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


    public Coin save(Long depotId, Long assetId, Coin coin) {
        Depot depot = depotRepository.findById(depotId).orElseThrow();
        coin.setDepot(depot);
        coin.setAsset(assetId != null ? assetRepository.findById(assetId).orElse(null) : null);
        Coin saved = coinRepository.save(coin);
        if (saved.getQuantity() != null) {
            Optional<CoinQuantity> latest = coinQuantityRepository.findFirstByCoinOrderByDateDesc(saved);
            boolean changed = latest.isEmpty() || !latest.get().getQuantity().equals(saved.getQuantity());
            if (changed) {
                LocalDate today = LocalDate.now();
                CoinQuantity cq = coinQuantityRepository.findByCoinAndDate(saved, today)
                    .orElseGet(() -> {
                        CoinQuantity q = new CoinQuantity();
                        q.setCoin(saved);
                        q.setDate(today);
                        return q;
                    });
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
        CoinQuantity cq = coinQuantityRepository.findByCoinAndDate(coin, date)
            .orElseGet(() -> {
                CoinQuantity q = new CoinQuantity();
                q.setCoin(coin);
                q.setDate(date);
                return q;
            });
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

    public BigDecimal valueEur(Coin coin) {
        if (coin.getMetal() == null || coin.getWeightGrams() == null || coin.getQuantity() == null) return null;
        Asset asset = coin.getAsset();
        if (asset == null) return null;
        BigDecimal price = assetService.getEffectivePrice(asset);
        if (price == null) return null;
        BigDecimal priceEur = exchangeRateService.toEur(price, asset.getCurrency());
        if (priceEur == null) return null;
        BigDecimal qty = BigDecimal.valueOf(coin.getQuantity());
        BigDecimal oz = coin.getWeightGrams().divide(GRAMS_PER_OZ, 10, RoundingMode.HALF_UP);
        return qty.multiply(oz).multiply(priceEur).setScale(2, RoundingMode.HALF_UP);
    }
}
