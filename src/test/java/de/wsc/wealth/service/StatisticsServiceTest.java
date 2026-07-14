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
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StatisticsServiceTest {

    @Mock private AssetRepository assetRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private AssetQuantityRepository quantityRepository;
    @Mock private AccountBalanceRepository balanceRepository;
    @Mock private PriceHistoryRepository priceHistoryRepository;
    @Mock private ExchangeRateService exchangeRateService;
    @Mock private CoinRepository coinRepository;
    @Mock private CoinQuantityRepository coinQuantityRepository;
    @Mock private CoinService coinService;
    @Mock private AssetService assetService;
    @Mock private AssetCriteriaService assetCriteriaService;

    private StatisticsService statisticsService;

    @BeforeEach
    void setUp() {
        statisticsService = new StatisticsService(assetRepository, accountRepository,
                quantityRepository, balanceRepository,
                priceHistoryRepository, exchangeRateService, coinRepository,
                coinQuantityRepository, coinService, assetService, assetCriteriaService);
        lenient().when(assetCriteriaService.getSnapshotsByAssetId()).thenReturn(Map.of());
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
        when(quantityRepository.findAllWithAssetAndDepot()).thenReturn(List.of(qty));
        when(accountRepository.findAllByOrderByBankNameAscAccountNumberAsc()).thenReturn(List.of());
        when(balanceRepository.findAllWithAccount()).thenReturn(List.of());
        when(coinRepository.findAllByOrderByMetalAscNameAscMintYearAsc()).thenReturn(List.of());
        when(assetService.getEffectivePricesByAssetId()).thenReturn(Map.of(1L, new BigDecimal("100.00")));
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
        when(quantityRepository.findAllWithAssetAndDepot()).thenReturn(List.of());
        when(accountRepository.findAllByOrderByBankNameAscAccountNumberAsc()).thenReturn(List.of(account));
        when(balanceRepository.findAllWithAccount()).thenReturn(List.of(balance));
        when(coinRepository.findAllByOrderByMetalAscNameAscMintYearAsc()).thenReturn(List.of());
        when(assetService.getEffectivePricesByAssetId()).thenReturn(Map.of());
        when(exchangeRateService.toEur(new BigDecimal("1000.00"), "EUR"))
                .thenReturn(new BigDecimal("1000.00"));

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
        when(quantityRepository.findAllWithAssetAndDepot()).thenReturn(List.of(qty));
        when(accountRepository.findAllByOrderByBankNameAscAccountNumberAsc()).thenReturn(List.of(account));
        when(balanceRepository.findAllWithAccount()).thenReturn(List.of(balance));
        when(coinRepository.findAllByOrderByMetalAscNameAscMintYearAsc()).thenReturn(List.of());
        when(assetService.getEffectivePricesByAssetId()).thenReturn(Map.of(1L, new BigDecimal("100.00")));
        when(exchangeRateService.toEur(new BigDecimal("100.00"), "EUR"))
                .thenReturn(new BigDecimal("100.00"));
        when(exchangeRateService.toEur(new BigDecimal("500.00"), "EUR"))
                .thenReturn(new BigDecimal("500.00"));

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

        when(assetRepository.findAllByArchivedFalseOrderByNameAsc()).thenReturn(List.of(asset));
        when(quantityRepository.findAllWithAssetAndDepot()).thenReturn(List.of());
        when(accountRepository.findAllByOrderByBankNameAscAccountNumberAsc()).thenReturn(List.of());
        when(balanceRepository.findAllWithAccount()).thenReturn(List.of());
        when(coinRepository.findAllByOrderByMetalAscNameAscMintYearAsc()).thenReturn(List.of());
        when(assetService.getEffectivePricesByAssetId()).thenReturn(Map.of(1L, new BigDecimal("100.00")));
        when(exchangeRateService.toEur(new BigDecimal("100.00"), "EUR"))
                .thenReturn(new BigDecimal("100.00"));

        assertThat(statisticsService.getAllPositions()).isEmpty();
    }

    @Test
    void getAllPositions_withUnlinkedCoin_includesPositionViaResolvedMetalAsset() {
        // A coin with no manually linked asset (e.g. physical bullion in a private safe) is still
        // priced via CoinService's metal-spot-asset fallback and must show up as its own position.
        Asset spotAsset = asset(2L, "Gold (physisch in Unzen)", null, "USD");
        Coin coin = new Coin();
        coin.setDepot(depot(1L, "Schließfach (Privat)"));

        when(assetRepository.findAllByArchivedFalseOrderByNameAsc()).thenReturn(List.of());
        when(quantityRepository.findAllWithAssetAndDepot()).thenReturn(List.of());
        when(accountRepository.findAllByOrderByBankNameAscAccountNumberAsc()).thenReturn(List.of());
        when(balanceRepository.findAllWithAccount()).thenReturn(List.of());
        when(coinRepository.findAllByOrderByMetalAscNameAscMintYearAsc()).thenReturn(List.of(coin));
        when(coinService.resolveAssetForPricing(coin)).thenReturn(spotAsset);
        when(coinService.valueEur(coin)).thenReturn(new BigDecimal("250.00"));
        when(assetService.getEffectivePricesByAssetId()).thenReturn(Map.of());

        List<WealthPosition> positions = statisticsService.getAllPositions();

        assertThat(positions).hasSize(1);
        WealthPosition pos = positions.get(0);
        assertThat(pos.getType()).isEqualTo("COIN");
        assertThat(pos.getName()).isEqualTo("Gold (physisch in Unzen)");
        assertThat(pos.getValue()).isEqualByComparingTo("250.00");
        assertThat(pos.getDepotName()).isEqualTo("Schließfach (Privat)");
    }

    @Test
    void getStatsByCriteria_groupsAssetsAndAccountsByOwnValue() {
        Asset asset1 = asset(1L, "ETF World", new BigDecimal("100.00"), "EUR");
        Asset asset2 = asset(2L, "ETF Emerging", new BigDecimal("50.00"), "EUR");
        Depot depot = depot(1L, "Main");
        AssetQuantity qty1 = quantity(asset1, depot, new BigDecimal("1"));
        AssetQuantity qty2 = quantity(asset2, depot, new BigDecimal("1"));
        Account account = account(3L, "Bank", "EUR");
        AccountBalance balance = balance(account, new BigDecimal("200.00"));

        when(assetRepository.findAllByArchivedFalseOrderByNameAsc()).thenReturn(List.of(asset1, asset2));
        when(quantityRepository.findAllWithAssetAndDepot()).thenReturn(List.of(qty1, qty2));
        when(accountRepository.findAllByOrderByBankNameAscAccountNumberAsc()).thenReturn(List.of(account));
        when(balanceRepository.findAllWithAccount()).thenReturn(List.of(balance));
        when(coinRepository.findAllByOrderByMetalAscNameAscMintYearAsc()).thenReturn(List.of());
        when(assetService.getEffectivePricesByAssetId()).thenReturn(
                Map.of(1L, new BigDecimal("100.00"), 2L, new BigDecimal("50.00")));
        when(exchangeRateService.toEur(new BigDecimal("100.00"), "EUR")).thenReturn(new BigDecimal("100.00"));
        when(exchangeRateService.toEur(new BigDecimal("50.00"), "EUR")).thenReturn(new BigDecimal("50.00"));
        when(exchangeRateService.toEur(new BigDecimal("200.00"), "EUR")).thenReturn(new BigDecimal("200.00"));
        when(assetCriteriaService.getValuesByAssetId(42L)).thenReturn(Map.of(1L, "Deutschland"));
        when(assetCriteriaService.getValuesByAccountId(42L)).thenReturn(Map.of(3L, "Frankreich"));

        List<de.wsc.wealth.dto.StatisticsGroup> groups = statisticsService.getStatsByCriteria(42L);

        assertThat(groups).extracting(de.wsc.wealth.dto.StatisticsGroup::getName)
                .containsExactlyInAnyOrder("Deutschland", "Frankreich", "KEIN_WERT");
    }

    // --- helpers ---

    private void stubEmpty() {
        when(assetRepository.findAllByArchivedFalseOrderByNameAsc()).thenReturn(List.of());
        when(quantityRepository.findAllWithAssetAndDepot()).thenReturn(List.of());
        when(accountRepository.findAllByOrderByBankNameAscAccountNumberAsc()).thenReturn(List.of());
        when(balanceRepository.findAllWithAccount()).thenReturn(List.of());
        when(coinRepository.findAllByOrderByMetalAscNameAscMintYearAsc()).thenReturn(List.of());
        when(assetService.getEffectivePricesByAssetId()).thenReturn(Map.of());
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
        q.setDate(LocalDate.of(2025, 1, 1));
        return q;
    }

    private Account account(Long id, String bankName, String currency) {
        Account a = new Account();
        a.setId(id);
        Bank b = new Bank(); b.setName(bankName);
        a.setBank(b);
        a.setAccountNumber("000");
        a.setCurrency(currency);
        return a;
    }

    private AccountBalance balance(Account account, BigDecimal amount) {
        AccountBalance b = new AccountBalance();
        b.setAccount(account);
        b.setBalance(amount);
        b.setDate(LocalDate.of(2025, 1, 1));
        return b;
    }
}
