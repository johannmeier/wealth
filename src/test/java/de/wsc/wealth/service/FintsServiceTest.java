package de.wsc.wealth.service;

import de.wsc.wealth.domain.*;
import de.wsc.wealth.repository.*;
import de.wsc.wealth.service.FintsService.RawBalance;
import de.wsc.wealth.service.FintsService.RawFintsResult;
import de.wsc.wealth.service.FintsService.RawPosition;
import de.wsc.wealth.service.FintsService.SyncResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FintsServiceTest {

    @Mock private FintsConfigRepository configRepository;
    @Mock private BankRepository bankRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private AccountBalanceRepository balanceRepository;
    @Mock private DepotRepository depotRepository;
    @Mock private AssetRepository assetRepository;
    @Mock private AssetQuantityRepository quantityRepository;
    @Mock private AssetSearchService assetSearchService;
    @Mock private PlatformTransactionManager transactionManager;

    private FintsService fintsService;

    @BeforeEach
    void setUp() {
        fintsService = new FintsService(configRepository, bankRepository, accountRepository,
            balanceRepository, depotRepository, assetRepository, quantityRepository, assetSearchService,
            transactionManager);
    }

    private FintsConfig configWithoutBank() {
        FintsConfig config = new FintsConfig();
        config.setBlz("12030000");
        config.setFintsUrl("https://fints.dkb.de/fints");
        return config;
    }

    @Test
    void persistData_withNoExistingBank_createsBankAccountAndBalance() {
        FintsConfig config = configWithoutBank();
        Bank savedBank = new Bank();
        savedBank.setId(1L);
        savedBank.setName("DKB");
        when(bankRepository.save(any(Bank.class))).thenReturn(savedBank);
        when(configRepository.save(any(FintsConfig.class))).thenAnswer(inv -> inv.getArgument(0));
        when(accountRepository.findByBankIdOrderByAccountNumberAsc(1L)).thenReturn(Collections.emptyList());
        when(accountRepository.save(any(Account.class))).thenAnswer(inv -> inv.getArgument(0));
        when(balanceRepository.findByAccountAndDate(any(Account.class), any(LocalDate.class)))
            .thenReturn(Optional.empty());
        when(balanceRepository.save(any(AccountBalance.class))).thenAnswer(inv -> inv.getArgument(0));
        when(depotRepository.findByBankIdAndName(eq(1L), any())).thenReturn(Optional.empty());
        when(depotRepository.save(any(Depot.class))).thenAnswer(inv -> inv.getArgument(0));

        RawFintsResult raw = new RawFintsResult(
            List.of(new RawBalance("DE00123", "DE00123IBAN", "EUR", new BigDecimal("1234.56"))),
            Collections.emptyList());

        SyncResult result = fintsService.persistData(config, raw);

        assertThat(result.balancesUpdated()).isEqualTo(1);
        assertThat(result.newAccounts()).containsExactly("DE00123");
        assertThat(result.bankId()).isEqualTo(1L);
        verify(accountRepository).save(argThat(a ->
            "DE00123".equals(a.getAccountNumber()) && "EUR".equals(a.getCurrency())
                && a.getAssetAllocation() == AssetAllocation.RISIKOFREI));
        verify(balanceRepository).save(argThat(b -> b.getBalance().compareTo(new BigDecimal("1234.56")) == 0));
    }

    @Test
    void persistData_withExistingAccount_updatesBalanceAndDoesNotCreateNewAccount() {
        FintsConfig config = configWithoutBank();
        Bank bank = new Bank();
        bank.setId(2L);
        bank.setName("Norisbank");
        config.setBank(bank);
        when(configRepository.save(any(FintsConfig.class))).thenAnswer(inv -> inv.getArgument(0));

        Account existing = new Account();
        existing.setId(10L);
        existing.setAccountNumber("DE99999");
        existing.setCurrency("EUR");
        when(accountRepository.findByBankIdOrderByAccountNumberAsc(2L)).thenReturn(List.of(existing));
        when(balanceRepository.findByAccountAndDate(existing, LocalDate.now())).thenReturn(Optional.empty());
        when(balanceRepository.save(any(AccountBalance.class))).thenAnswer(inv -> inv.getArgument(0));
        when(depotRepository.findByBankIdAndName(eq(2L), any())).thenReturn(Optional.empty());
        when(depotRepository.save(any(Depot.class))).thenAnswer(inv -> inv.getArgument(0));

        RawFintsResult raw = new RawFintsResult(
            List.of(new RawBalance("DE99999", "DE99999IBAN", "EUR", new BigDecimal("500.00"))),
            Collections.emptyList());

        SyncResult result = fintsService.persistData(config, raw);

        assertThat(result.balancesUpdated()).isEqualTo(1);
        assertThat(result.newAccounts()).isEmpty();
        verify(accountRepository, never()).save(any(Account.class));
        verify(bankRepository, never()).save(any(Bank.class));
    }

    @Test
    void persistData_withUnchangedPosition_doesNotWriteNewAssetQuantity() {
        FintsConfig config = configWithoutBank();
        Bank bank = new Bank();
        bank.setId(3L);
        bank.setName("Consorsbank");
        config.setBank(bank);
        when(configRepository.save(any(FintsConfig.class))).thenAnswer(inv -> inv.getArgument(0));

        Depot depot = new Depot();
        depot.setId(20L);
        depot.setName("Consorsbank");
        when(depotRepository.findByBankIdAndName(3L, "Consorsbank")).thenReturn(Optional.of(depot));

        Asset asset = new Asset();
        asset.setId(30L);
        asset.setIsin("DE0001234567");
        asset.setName("Test Bond");
        when(assetRepository.findFirstByIsinAndArchivedFalse("DE0001234567")).thenReturn(Optional.of(asset));
        when(quantityRepository.findFirstByAssetAndDepotOrderByDateDesc(asset, depot))
            .thenReturn(Optional.of(existingQuantity(asset, depot, new BigDecimal("10"))));

        RawFintsResult raw = new RawFintsResult(
            Collections.emptyList(),
            List.of(new RawPosition("DEPOT1", "DE0001234567", "Test Bond", new BigDecimal("10"))));

        SyncResult result = fintsService.persistData(config, raw);

        assertThat(result.changedPositions()).isEmpty();
        verify(quantityRepository, never()).save(any(AssetQuantity.class));
        verify(assetSearchService, never()).search(any(), any());
    }

    @Test
    void persistData_withChangedPosition_writesNewAssetQuantity() {
        FintsConfig config = configWithoutBank();
        Bank bank = new Bank();
        bank.setId(4L);
        bank.setName("Norisbank");
        config.setBank(bank);
        when(configRepository.save(any(FintsConfig.class))).thenAnswer(inv -> inv.getArgument(0));

        Depot depot = new Depot();
        depot.setId(21L);
        depot.setName("Norisbank");
        when(depotRepository.findByBankIdAndName(4L, "Norisbank")).thenReturn(Optional.of(depot));

        Asset asset = new Asset();
        asset.setId(31L);
        asset.setIsin("DE0007654321");
        asset.setName("Test Fund");
        when(assetRepository.findFirstByIsinAndArchivedFalse("DE0007654321")).thenReturn(Optional.of(asset));
        when(quantityRepository.findFirstByAssetAndDepotOrderByDateDesc(asset, depot))
            .thenReturn(Optional.of(existingQuantity(asset, depot, new BigDecimal("5"))));
        when(quantityRepository.findByAssetAndDepotAndDate(asset, depot, LocalDate.now()))
            .thenReturn(Optional.empty());
        when(quantityRepository.save(any(AssetQuantity.class))).thenAnswer(inv -> inv.getArgument(0));

        RawFintsResult raw = new RawFintsResult(
            Collections.emptyList(),
            List.of(new RawPosition("DEPOT1", "DE0007654321", "Test Fund", new BigDecimal("8"))));

        SyncResult result = fintsService.persistData(config, raw);

        assertThat(result.changedPositions()).hasSize(1);
        assertThat(result.changedPositions().get(0).oldQuantity()).isEqualByComparingTo("5");
        assertThat(result.changedPositions().get(0).newQuantity()).isEqualByComparingTo("8");
        verify(quantityRepository).save(any(AssetQuantity.class));
    }

    private AssetQuantity existingQuantity(Asset asset, Depot depot, BigDecimal qty) {
        AssetQuantity q = new AssetQuantity();
        q.setAsset(asset);
        q.setDepot(depot);
        q.setDate(LocalDate.now().minusDays(1));
        q.setQuantity(qty);
        return q;
    }
}
