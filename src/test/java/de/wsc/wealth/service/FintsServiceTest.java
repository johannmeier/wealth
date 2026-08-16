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
import org.mockito.ArgumentCaptor;
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
    void saveConfig_withOwnershipShare_appliesToAllAccountsAndDepotsOfBank() {
        Bank bank = new Bank();
        bank.setId(5L);
        bank.setName("Norisbank");

        FintsConfig existing = new FintsConfig();
        existing.setBank(bank);
        when(configRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(configRepository.save(any(FintsConfig.class))).thenAnswer(inv -> inv.getArgument(0));

        Account account = new Account();
        account.setId(10L);
        when(accountRepository.findByBankIdOrderByAccountNumberAsc(5L)).thenReturn(List.of(account));
        when(accountRepository.save(any(Account.class))).thenAnswer(inv -> inv.getArgument(0));

        Depot depot = new Depot();
        depot.setId(20L);
        when(depotRepository.findByBankIdOrderByNameAsc(5L)).thenReturn(List.of(depot));
        when(depotRepository.save(any(Depot.class))).thenAnswer(inv -> inv.getArgument(0));

        fintsService.saveConfig(1L, "12030000", "https://fints.example/fints", null, null, new BigDecimal("50"));

        assertThat(account.getOwnershipShare()).isEqualByComparingTo("50");
        assertThat(depot.getOwnershipShare()).isEqualByComparingTo("50");
    }

    @Test
    void saveConfig_withoutOwnershipShare_leavesExistingAccountsAndDepotsUntouched() {
        Bank bank = new Bank();
        bank.setId(5L);
        bank.setName("Norisbank");

        FintsConfig existing = new FintsConfig();
        existing.setBank(bank);
        when(configRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(configRepository.save(any(FintsConfig.class))).thenAnswer(inv -> inv.getArgument(0));

        fintsService.saveConfig(1L, "12030000", "https://fints.example/fints", null, null, null);

        verify(accountRepository, never()).findByBankIdOrderByAccountNumberAsc(any());
        verify(depotRepository, never()).findByBankIdOrderByNameAsc(any());
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
        existing.setIban("DE99999IBAN");
        existing.setCurrency("EUR");
        when(accountRepository.findByBankIdOrderByAccountNumberAsc(2L)).thenReturn(List.of(existing));
        when(balanceRepository.findByAccountAndDate(existing, LocalDate.now())).thenReturn(Optional.empty());
        when(balanceRepository.save(any(AccountBalance.class))).thenAnswer(inv -> inv.getArgument(0));

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
    void persistData_withJointlyOwnedAccount_storesOnlyOwnershipShareOfBalance() {
        FintsConfig config = configWithoutBank();
        Bank bank = new Bank();
        bank.setId(2L);
        bank.setName("Norisbank");
        config.setBank(bank);
        when(configRepository.save(any(FintsConfig.class))).thenAnswer(inv -> inv.getArgument(0));

        Account existing = new Account();
        existing.setId(10L);
        existing.setAccountNumber("DE99999");
        existing.setIban("DE99999IBAN");
        existing.setCurrency("EUR");
        existing.setOwnershipShare(new BigDecimal("50"));
        when(accountRepository.findByBankIdOrderByAccountNumberAsc(2L)).thenReturn(List.of(existing));
        when(balanceRepository.findByAccountAndDate(existing, LocalDate.now())).thenReturn(Optional.empty());
        when(balanceRepository.save(any(AccountBalance.class))).thenAnswer(inv -> inv.getArgument(0));

        RawFintsResult raw = new RawFintsResult(
            List.of(new RawBalance("DE99999", "DE99999IBAN", "EUR", new BigDecimal("500.00"))),
            Collections.emptyList());

        fintsService.persistData(config, raw);

        ArgumentCaptor<AccountBalance> captor = ArgumentCaptor.forClass(AccountBalance.class);
        verify(balanceRepository).save(captor.capture());
        assertThat(captor.getValue().getBalance()).isEqualByComparingTo("250.00");
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
        depot.setName("Consorsbank DEPOT1");
        when(depotRepository.findByBankIdOrderByNameAsc(3L)).thenReturn(List.of(depot));

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
        depot.setName("Norisbank DEPOT1");
        when(depotRepository.findByBankIdOrderByNameAsc(4L)).thenReturn(List.of(depot));

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

    @Test
    void persistData_withSpacedIban_matchesExistingAccountRegardlessOfWhitespace() {
        FintsConfig config = configWithoutBank();
        Bank bank = new Bank();
        bank.setId(7L);
        bank.setName("Norisbank");
        config.setBank(bank);
        when(configRepository.save(any(FintsConfig.class))).thenAnswer(inv -> inv.getArgument(0));

        Account existing = new Account();
        existing.setId(60L);
        existing.setIban("DE12 3456 7890 1234 5678 90");
        existing.setCurrency("EUR");
        when(accountRepository.findByBankIdOrderByAccountNumberAsc(7L)).thenReturn(List.of(existing));
        when(balanceRepository.findByAccountAndDate(existing, LocalDate.now())).thenReturn(Optional.empty());
        when(balanceRepository.save(any(AccountBalance.class))).thenAnswer(inv -> inv.getArgument(0));

        RawFintsResult raw = new RawFintsResult(
            List.of(new RawBalance(null, "DE12345678901234567890", "EUR", new BigDecimal("42.00"))),
            Collections.emptyList());

        SyncResult result = fintsService.persistData(config, raw);

        assertThat(result.newAccounts()).isEmpty();
        verify(accountRepository, never()).save(any(Account.class));
    }

    @Test
    void persistData_withMultipleDepots_createsSeparateDepotPerReportedNumber() {
        FintsConfig config = configWithoutBank();
        Bank bank = new Bank();
        bank.setId(5L);
        bank.setName("Consorsbank");
        config.setBank(bank);
        when(configRepository.save(any(FintsConfig.class))).thenAnswer(inv -> inv.getArgument(0));

        when(depotRepository.findByBankIdOrderByNameAsc(5L)).thenReturn(Collections.emptyList());
        when(depotRepository.save(any(Depot.class))).thenAnswer(inv -> inv.getArgument(0));

        Asset assetA = new Asset();
        assetA.setId(40L);
        assetA.setIsin("DE0000000001");
        assetA.setName("Fund A");
        Asset assetB = new Asset();
        assetB.setId(41L);
        assetB.setIsin("DE0000000002");
        assetB.setName("Fund B");
        when(assetRepository.findFirstByIsinAndArchivedFalse("DE0000000001")).thenReturn(Optional.of(assetA));
        when(assetRepository.findFirstByIsinAndArchivedFalse("DE0000000002")).thenReturn(Optional.of(assetB));
        when(quantityRepository.findFirstByAssetAndDepotOrderByDateDesc(any(), any())).thenReturn(Optional.empty());
        when(quantityRepository.findByAssetAndDepotAndDate(any(), any(), any())).thenReturn(Optional.empty());
        when(quantityRepository.save(any(AssetQuantity.class))).thenAnswer(inv -> inv.getArgument(0));

        RawFintsResult raw = new RawFintsResult(
            Collections.emptyList(),
            List.of(
                new RawPosition("111", "DE0000000001", "Fund A", new BigDecimal("3")),
                new RawPosition("222", "DE0000000002", "Fund B", new BigDecimal("7"))));

        fintsService.persistData(config, raw);

        verify(depotRepository).save(argThat(d -> "Consorsbank 111".equals(d.getName())));
        verify(depotRepository).save(argThat(d -> "Consorsbank 222".equals(d.getName())));
    }

    @Test
    void persistData_withSameAccountNumberDifferentIban_createsSeparateAccounts() {
        FintsConfig config = configWithoutBank();
        Bank bank = new Bank();
        bank.setId(6L);
        bank.setName("Consorsbank");
        config.setBank(bank);
        when(configRepository.save(any(FintsConfig.class))).thenAnswer(inv -> inv.getArgument(0));

        Account eurAccount = new Account();
        eurAccount.setId(50L);
        eurAccount.setAccountNumber("900123456");
        eurAccount.setIban("DE00EURIBAN1");
        eurAccount.setCurrency("EUR");
        when(accountRepository.findByBankIdOrderByAccountNumberAsc(6L)).thenReturn(List.of(eurAccount));
        when(accountRepository.save(any(Account.class))).thenAnswer(inv -> inv.getArgument(0));
        when(balanceRepository.findByAccountAndDate(any(Account.class), any(LocalDate.class)))
            .thenReturn(Optional.empty());
        when(balanceRepository.save(any(AccountBalance.class))).thenAnswer(inv -> inv.getArgument(0));

        // Same Kontonummer, but a distinct IBAN per currency sub-account — as real brokers report it.
        RawFintsResult raw = new RawFintsResult(
            List.of(
                new RawBalance("900123456", "DE00EURIBAN1", "EUR", new BigDecimal("100.00")),
                new RawBalance("900123456", "DE00USDIBAN1", "USD", new BigDecimal("50.00"))),
            Collections.emptyList());

        SyncResult result = fintsService.persistData(config, raw);

        assertThat(result.balancesUpdated()).isEqualTo(2);
        assertThat(result.newAccounts()).containsExactly("900123456");
        verify(accountRepository).save(argThat(a ->
            "900123456".equals(a.getAccountNumber()) && "USD".equals(a.getCurrency())));
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
