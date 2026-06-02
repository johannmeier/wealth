package de.wsc.wealth.service;

import de.wsc.wealth.domain.*;
import de.wsc.wealth.dto.WealthPosition;
import de.wsc.wealth.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StatisticsServiceTest {

    @Mock private AssetRepository assetRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private DepotRepository depotRepository;
    @Mock private AssetQuantityRepository quantityRepository;
    @Mock private AccountBalanceRepository balanceRepository;
    @Mock private ExchangeRateService exchangeRateService;
    @Mock private CoinRepository coinRepository;
    @Mock private CoinService coinService;

    private StatisticsService statisticsService;

    @BeforeEach
    void setUp() {
        statisticsService = new StatisticsService(assetRepository, accountRepository,
                depotRepository, quantityRepository, balanceRepository,
                exchangeRateService, coinRepository, coinService);
    }

    @Test
    void getTotalWealth_withNoData_returnsZero() {
        stubEmpty();
        assertThat(statisticsService.getTotalWealth()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void getAllPositions_withNoData_returnsEmptyList() {
        stubEmpty();
        assertThat(statisticsService.getAllPositions()).isEmpty();
    }

    @Test
    void getAllPositions_withAssetAndQuantity_returnsCorrectPosition() {
        Asset asset = asset(1L, "World ETF", new BigDecimal("100.00"), "EUR");
        Depot depot = depot(1L, "Main");
        AssetQuantity qty = quantity(asset, depot, new BigDecimal("5"));

        when(assetRepository.findAllByArchivedFalseOrderByNameAsc()).thenReturn(List.of(asset));
        when(depotRepository.findAllByOrderByNameAsc()).thenReturn(List.of(depot));
        when(accountRepository.findAllByOrderByBankAscAccountNumberAsc()).thenReturn(List.of());
        when(quantityRepository.findFirstByAssetAndDepotOrderByDateDesc(asset, depot))
                .thenReturn(Optional.of(qty));
        when(coinRepository.findAllByOrderByMetalAscNameAscMintYearAsc()).thenReturn(List.of());
        when(coinService.fetchSpotPricesUsd()).thenReturn(Map.of());
        when(exchangeRateService.toEur(new BigDecimal("100.00"), "EUR"))
                .thenReturn(new BigDecimal("100.00"));

        List<WealthPosition> positions = statisticsService.getAllPositions();

        assertThat(positions).hasSize(1);
        WealthPosition pos = positions.get(0);
        assertThat(pos.getName()).isEqualTo("World ETF");
        assertThat(pos.getValue()).isEqualByComparingTo(new BigDecimal("500.00"));
        assertThat(pos.getQuantity()).isEqualByComparingTo(new BigDecimal("5"));
        assertThat(pos.getCurrency()).isEqualTo("EUR");
        assertThat(pos.getPercentage()).isEqualByComparingTo(new BigDecimal("100.00"));
    }

    @Test
    void getAllPositions_withAccount_returnsAccountPosition() {
        Account account = account(1L, "My Bank", "EUR");
        AccountBalance balance = balance(account, new BigDecimal("1000.00"));

        when(assetRepository.findAllByArchivedFalseOrderByNameAsc()).thenReturn(List.of());
        when(depotRepository.findAllByOrderByNameAsc()).thenReturn(List.of());
        when(accountRepository.findAllByOrderByBankAscAccountNumberAsc()).thenReturn(List.of(account));
        when(balanceRepository.findFirstByAccountOrderByDateDesc(account)).thenReturn(Optional.of(balance));
        when(coinRepository.findAllByOrderByMetalAscNameAscMintYearAsc()).thenReturn(List.of());
        when(coinService.fetchSpotPricesUsd()).thenReturn(Map.of());

        List<WealthPosition> positions = statisticsService.getAllPositions();

        assertThat(positions).hasSize(1);
        WealthPosition pos = positions.get(0);
        assertThat(pos.getType()).isEqualTo("ACCOUNT");
        assertThat(pos.getValue()).isEqualByComparingTo(new BigDecimal("1000.00"));
    }

    @Test
    void getAllPositions_twoPositions_percentagesSumToHundred() {
        Asset asset = asset(1L, "ETF", new BigDecimal("100.00"), "EUR");
        Depot depot = depot(1L, "Main");
        AssetQuantity qty = quantity(asset, depot, new BigDecimal("5")); // 500 EUR

        Account account = account(2L, "Bank", "EUR");
        AccountBalance balance = balance(account, new BigDecimal("500.00")); // 500 EUR

        when(assetRepository.findAllByArchivedFalseOrderByNameAsc()).thenReturn(List.of(asset));
        when(depotRepository.findAllByOrderByNameAsc()).thenReturn(List.of(depot));
        when(accountRepository.findAllByOrderByBankAscAccountNumberAsc()).thenReturn(List.of(account));
        when(quantityRepository.findFirstByAssetAndDepotOrderByDateDesc(asset, depot))
                .thenReturn(Optional.of(qty));
        when(balanceRepository.findFirstByAccountOrderByDateDesc(account)).thenReturn(Optional.of(balance));
        when(coinRepository.findAllByOrderByMetalAscNameAscMintYearAsc()).thenReturn(List.of());
        when(coinService.fetchSpotPricesUsd()).thenReturn(Map.of());
        when(exchangeRateService.toEur(new BigDecimal("100.00"), "EUR"))
                .thenReturn(new BigDecimal("100.00"));

        List<WealthPosition> positions = statisticsService.getAllPositions();

        assertThat(positions).hasSize(2);
        BigDecimal totalPct = positions.stream()
                .map(WealthPosition::getPercentage)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(totalPct).isEqualByComparingTo(new BigDecimal("100.00"));
        positions.forEach(p -> assertThat(p.getPercentage()).isEqualByComparingTo(new BigDecimal("50.00")));
    }

    @Test
    void getAllPositions_assetWithNoQuantity_isExcluded() {
        Asset asset = asset(1L, "ETF", new BigDecimal("100.00"), "EUR");
        Depot depot = depot(1L, "Main");

        when(assetRepository.findAllByArchivedFalseOrderByNameAsc()).thenReturn(List.of(asset));
        when(depotRepository.findAllByOrderByNameAsc()).thenReturn(List.of(depot));
        when(accountRepository.findAllByOrderByBankAscAccountNumberAsc()).thenReturn(List.of());
        when(quantityRepository.findFirstByAssetAndDepotOrderByDateDesc(asset, depot))
                .thenReturn(Optional.empty());
        when(coinRepository.findAllByOrderByMetalAscNameAscMintYearAsc()).thenReturn(List.of());
        when(coinService.fetchSpotPricesUsd()).thenReturn(Map.of());
        when(exchangeRateService.toEur(new BigDecimal("100.00"), "EUR"))
                .thenReturn(new BigDecimal("100.00"));

        assertThat(statisticsService.getAllPositions()).isEmpty();
    }

    // --- helpers ---

    private void stubEmpty() {
        when(assetRepository.findAllByArchivedFalseOrderByNameAsc()).thenReturn(List.of());
        when(accountRepository.findAllByOrderByBankAscAccountNumberAsc()).thenReturn(List.of());
        when(depotRepository.findAllByOrderByNameAsc()).thenReturn(List.of());
        when(coinRepository.findAllByOrderByMetalAscNameAscMintYearAsc()).thenReturn(List.of());
        when(coinService.fetchSpotPricesUsd()).thenReturn(Map.of());
    }

    private Asset asset(Long id, String name, BigDecimal price, String currency) {
        Asset a = new Asset();
        a.setId(id);
        a.setName(name);
        a.setCurrentPrice(price);
        a.setCurrency(currency);
        return a;
    }

    private Depot depot(Long id, String name) {
        Depot d = new Depot();
        d.setId(id);
        d.setName(name);
        return d;
    }

    private AssetQuantity quantity(Asset asset, Depot depot, BigDecimal qty) {
        AssetQuantity q = new AssetQuantity();
        q.setAsset(asset);
        q.setDepot(depot);
        q.setQuantity(qty);
        return q;
    }

    private Account account(Long id, String bank, String currency) {
        Account a = new Account();
        a.setId(id);
        a.setBank(bank);
        a.setAccountNumber("000");
        a.setCurrency(currency);
        return a;
    }

    private AccountBalance balance(Account account, BigDecimal amount) {
        AccountBalance b = new AccountBalance();
        b.setAccount(account);
        b.setBalance(amount);
        return b;
    }
}
