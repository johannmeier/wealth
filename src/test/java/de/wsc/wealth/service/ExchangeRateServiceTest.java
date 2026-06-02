package de.wsc.wealth.service;

import de.wsc.wealth.repository.AssetRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExchangeRateServiceTest {

    @Mock
    private AssetRepository assetRepository;
    @Mock
    private PriceService priceService;

    private ExchangeRateService service;

    @BeforeEach
    void setUp() {
        service = new ExchangeRateService(assetRepository, priceService);
    }

    // --- toEur ---

    @Test
    void toEur_withEurCurrency_returnsUnchanged() {
        assertThat(service.toEur(new BigDecimal("100"), "EUR"))
                .isEqualByComparingTo(new BigDecimal("100"));
    }

    @Test
    void toEur_withNullCurrency_returnsUnchanged() {
        assertThat(service.toEur(new BigDecimal("100"), null))
                .isEqualByComparingTo(new BigDecimal("100"));
    }

    @Test
    void toEur_withNullPrice_returnsNull() {
        assertThat(service.toEur(null, "USD")).isNull();
    }

    @Test
    void toEur_withUnknownCurrency_returnsNull() {
        assertThat(service.toEur(new BigDecimal("100"), "XYZ")).isNull();
    }

    @Test
    void toEur_withCachedRate_convertsCorrectly() {
        // populate cache: 1 USD = 0.90 EUR
        when(priceService.fetchPrice("USDEUR=X")).thenReturn(new BigDecimal("0.90"));
        service.getEurToRate("USD");

        BigDecimal result = service.toEur(new BigDecimal("200"), "USD");

        assertThat(result).isEqualByComparingTo(new BigDecimal("180.0000"));
    }

    // --- getEurToRate ---

    @Test
    void getEurToRate_withEurCurrency_returnsOne() {
        assertThat(service.getEurToRate("EUR")).isEqualByComparingTo(BigDecimal.ONE);
    }

    @Test
    void getEurToRate_withNullCurrency_returnsOne() {
        assertThat(service.getEurToRate(null)).isEqualByComparingTo(BigDecimal.ONE);
    }

    @Test
    void getEurToRate_fetchesRateOnDemandAndCaches() {
        // 1 USD = 0.90 EUR  →  1 EUR = 1/0.90 ≈ 1.111111 USD
        when(priceService.fetchPrice("USDEUR=X")).thenReturn(new BigDecimal("0.90"));

        BigDecimal rate = service.getEurToRate("USD");

        assertThat(rate).isNotNull();
        assertThat(rate).isGreaterThan(BigDecimal.ONE);

        // second call must not trigger another fetch
        service.getEurToRate("USD");
        verify(priceService, times(1)).fetchPrice("USDEUR=X");
    }

    @Test
    void getEurToRate_whenFetchFails_returnsNull() {
        when(priceService.fetchPrice(anyString())).thenThrow(new RuntimeException("network error"));

        assertThat(service.getEurToRate("USD")).isNull();
    }

    // --- refresh ---

    @Test
    void refresh_fetchesRatesForNonEurAssetCurrencies() {
        var asset = new de.wsc.wealth.domain.Asset();
        asset.setCurrency("USD");
        when(assetRepository.findAll()).thenReturn(List.of(asset));
        when(priceService.fetchPrice("USDEUR=X")).thenReturn(new BigDecimal("0.90"));

        service.refresh();

        verify(priceService).fetchPrice("USDEUR=X");
    }
}
