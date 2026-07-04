package de.wsc.wealth.service;

import de.wsc.wealth.domain.Asset;
import de.wsc.wealth.domain.Coin;
import de.wsc.wealth.domain.CoinMetal;
import de.wsc.wealth.repository.AssetRepository;
import de.wsc.wealth.repository.CoinQuantityRepository;
import de.wsc.wealth.repository.CoinRepository;
import de.wsc.wealth.repository.DepotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CoinServiceTest {

    @Mock private CoinRepository coinRepository;
    @Mock private CoinQuantityRepository coinQuantityRepository;
    @Mock private DepotRepository depotRepository;
    @Mock private AssetRepository assetRepository;
    @Mock private ExchangeRateService exchangeRateService;
    @Mock private AssetService assetService;

    private CoinService coinService;

    private static final BigDecimal ONE_OZ_GRAMS = new BigDecimal("31.1034768");

    @BeforeEach
    void setUp() {
        coinService = new CoinService(coinRepository, coinQuantityRepository, depotRepository,
                assetRepository, exchangeRateService, assetService);
        // Mirrors AssetService's real fallback logic (currentPrice, else price history) closely
        // enough for these tests: just return whatever currentPrice the test set on the asset.
        lenient().when(assetService.getEffectivePrice(any(Asset.class)))
                .thenAnswer(inv -> ((Asset) inv.getArgument(0)).getCurrentPrice());
    }

    @Test
    void valueEur_withNullMetal_returnsNull() {
        assertThat(coinService.valueEur(coin(null, ONE_OZ_GRAMS, 1))).isNull();
    }

    @Test
    void valueEur_withNullWeight_returnsNull() {
        assertThat(coinService.valueEur(coin(CoinMetal.GOLD, null, 1))).isNull();
    }

    @Test
    void valueEur_withNullQuantity_returnsNull() {
        assertThat(coinService.valueEur(coin(CoinMetal.GOLD, ONE_OZ_GRAMS, null))).isNull();
    }

    @Test
    void valueEur_withNoLinkedAssetAndNoMetalSpotAsset_returnsNull() {
        when(assetRepository.findFirstBySymbolAndArchivedFalse("GC=F")).thenReturn(java.util.Optional.empty());
        assertThat(coinService.valueEur(coin(CoinMetal.GOLD, ONE_OZ_GRAMS, 1))).isNull();
    }

    @Test
    void valueEur_withoutLinkedAsset_fallsBackToMetalSpotAsset() {
        // Physical bullion with no manually linked asset (e.g. a private-safe coin) should still
        // be priced via the metal's spot-price asset (same one BullionVault sync creates/reuses).
        Asset spotAsset = new Asset();
        spotAsset.setSymbol("GC=F");
        spotAsset.setCurrentPrice(new BigDecimal("60.00"));
        spotAsset.setCurrency("EUR");
        when(assetRepository.findFirstBySymbolAndArchivedFalse("GC=F")).thenReturn(java.util.Optional.of(spotAsset));
        when(exchangeRateService.toEur(new BigDecimal("60.00"), "EUR"))
                .thenReturn(new BigDecimal("60.00"));

        Coin coin = coin(CoinMetal.GOLD, ONE_OZ_GRAMS, 2);

        assertThat(coinService.valueEur(coin)).isEqualByComparingTo(new BigDecimal("120.00"));
        assertThat(coinService.resolveAssetForPricing(coin)).isSameAs(spotAsset);
    }

    @Test
    void resolveAssetForPricing_prefersLinkedAssetOverMetalSpotAsset() {
        Asset linked = new Asset();
        Coin coin = coin(CoinMetal.GOLD, ONE_OZ_GRAMS, 1);
        coin.setAsset(linked);

        assertThat(coinService.resolveAssetForPricing(coin)).isSameAs(linked);
    }

    @Test
    void valueEur_withLinkedAssetInEur_usesAssetPrice() {
        // 2 coins × 1 oz × 60 EUR/oz = 120 EUR
        Asset asset = new Asset();
        asset.setCurrentPrice(new BigDecimal("60.00"));
        asset.setCurrency("EUR");

        Coin coin = coin(CoinMetal.GOLD, ONE_OZ_GRAMS, 2);
        coin.setAsset(asset);

        when(exchangeRateService.toEur(new BigDecimal("60.00"), "EUR"))
                .thenReturn(new BigDecimal("60.00"));

        assertThat(coinService.valueEur(coin)).isEqualByComparingTo(new BigDecimal("120.00"));
    }

    @Test
    void valueEur_withLinkedAssetInUsd_convertsToEur() {
        // 1 coin × 1 oz × 2000 USD/oz → toEur returns 1800 EUR
        Asset asset = new Asset();
        asset.setCurrentPrice(new BigDecimal("2000.00"));
        asset.setCurrency("USD");

        Coin coin = coin(CoinMetal.GOLD, ONE_OZ_GRAMS, 1);
        coin.setAsset(asset);

        when(exchangeRateService.toEur(new BigDecimal("2000.00"), "USD"))
                .thenReturn(new BigDecimal("1800.00"));

        assertThat(coinService.valueEur(coin)).isEqualByComparingTo(new BigDecimal("1800.00"));
    }

    @Test
    void valueEur_withMultipleCoins_scalesByQuantity() {
        // 3 coins × 1 oz × 60 EUR/oz = 180 EUR
        Asset asset = new Asset();
        asset.setCurrentPrice(new BigDecimal("60.00"));
        asset.setCurrency("EUR");

        Coin coin = coin(CoinMetal.GOLD, ONE_OZ_GRAMS, 3);
        coin.setAsset(asset);

        when(exchangeRateService.toEur(new BigDecimal("60.00"), "EUR"))
                .thenReturn(new BigDecimal("60.00"));

        assertThat(coinService.valueEur(coin)).isEqualByComparingTo(new BigDecimal("180.00"));
    }

    private Coin coin(CoinMetal metal, BigDecimal weightGrams, Integer quantity) {
        Coin c = new Coin();
        c.setName("Test Coin");
        c.setMetal(metal);
        c.setWeightGrams(weightGrams);
        c.setQuantity(quantity);
        return c;
    }
}
