package de.wsc.wealth.service;

import de.wsc.wealth.domain.Asset;
import de.wsc.wealth.domain.Coin;
import de.wsc.wealth.domain.CoinMetal;
import de.wsc.wealth.domain.Depot;
import de.wsc.wealth.domain.PriceHistory;
import de.wsc.wealth.repository.AssetQuantityRepository;
import de.wsc.wealth.repository.AssetRepository;
import de.wsc.wealth.repository.CoinRepository;
import de.wsc.wealth.repository.DepotRepository;
import de.wsc.wealth.repository.PriceHistoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssetServiceTest {

    @Mock private AssetRepository assetRepository;
    @Mock private AssetQuantityRepository quantityRepository;
    @Mock private DepotRepository depotRepository;
    @Mock private PriceHistoryRepository priceHistoryRepository;
    @Mock private CoinRepository coinRepository;

    private AssetService assetService;

    @BeforeEach
    void setUp() {
        assetService = new AssetService(assetRepository, quantityRepository, depotRepository,
                priceHistoryRepository, coinRepository);
    }

    @Test
    void getEffectivePrice_withCurrentPrice_returnsCurrentPrice() {
        Asset asset = asset(1L, new BigDecimal("100.00"));

        assertThat(assetService.getEffectivePrice(asset)).isEqualByComparingTo("100.00");
    }

    @Test
    void getEffectivePrice_withoutCurrentPrice_fallsBackToLatestHistory() {
        Asset asset = asset(1L, null);
        when(priceHistoryRepository.findFirstByAssetOrderByDateDesc(asset))
                .thenReturn(Optional.of(priceHistory(asset, new BigDecimal("93.50"))));

        assertThat(assetService.getEffectivePrice(asset)).isEqualByComparingTo("93.50");
    }

    @Test
    void getEffectivePrice_withoutCurrentPriceOrHistory_returnsNull() {
        Asset asset = asset(1L, null);
        when(priceHistoryRepository.findFirstByAssetOrderByDateDesc(asset))
                .thenReturn(Optional.empty());

        assertThat(assetService.getEffectivePrice(asset)).isNull();
    }

    @Test
    void getEffectivePricesByAssetId_currentPriceTakesPrecedenceOverHistory() {
        Asset withCurrent = asset(1L, new BigDecimal("100.00"));
        Asset withoutCurrent = asset(2L, null);

        when(priceHistoryRepository.findLatestPerAsset()).thenReturn(List.of(
                priceHistory(withCurrent, new BigDecimal("42.00")),
                priceHistory(withoutCurrent, new BigDecimal("77.00"))
        ));
        when(assetRepository.findAll()).thenReturn(List.of(withCurrent, withoutCurrent));

        Map<Long, BigDecimal> prices = assetService.getEffectivePricesByAssetId();

        assertThat(prices.get(1L)).isEqualByComparingTo("100.00");
        assertThat(prices.get(2L)).isEqualByComparingTo("77.00");
    }

    @Test
    void getDepotsByAssetId_includesCoinOnlyDepot_viaMetalSpotAsset() {
        // A coin without a manually linked asset (e.g. physical bullion in a private safe) should
        // still show up under its resolved metal-spot-price asset's depot list.
        Asset spotAsset = asset(3L, null);
        spotAsset.setSymbol("GC=F");
        when(assetRepository.findAllByArchivedFalseOrderByNameAsc()).thenReturn(List.of(spotAsset));
        when(quantityRepository.findByAssetOrderByDateDesc(spotAsset)).thenReturn(List.of());
        when(assetRepository.findFirstBySymbolAndArchivedFalse("GC=F")).thenReturn(Optional.of(spotAsset));

        Depot depot = new Depot();
        depot.setId(10L);
        depot.setName("Schließfach (Privat)");
        Coin coin = new Coin();
        coin.setMetal(CoinMetal.GOLD);
        coin.setDepot(depot);
        when(coinRepository.findAllWithDepot()).thenReturn(List.of(coin));

        Map<Long, Map<Long, String>> depotsByAsset = assetService.getDepotsByAssetId();

        assertThat(depotsByAsset.get(3L)).containsEntry(10L, "Schließfach (Privat)");
    }

    private Asset asset(Long id, BigDecimal currentPrice) {
        Asset a = new Asset();
        a.setId(id);
        a.setCurrentPrice(currentPrice);
        a.setCurrency("EUR");
        return a;
    }

    private PriceHistory priceHistory(Asset asset, BigDecimal price) {
        PriceHistory h = new PriceHistory();
        h.setAsset(asset);
        h.setDate(LocalDate.of(2025, 1, 1));
        h.setPrice(price);
        h.setCurrency(asset.getCurrency());
        return h;
    }
}
