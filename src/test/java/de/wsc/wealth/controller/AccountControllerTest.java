package de.wsc.wealth.controller;

import de.wsc.wealth.domain.Account;
import de.wsc.wealth.domain.AssetAllocation;
import de.wsc.wealth.domain.Bank;
import de.wsc.wealth.service.AccountService;
import de.wsc.wealth.service.AssetCriteriaService;
import de.wsc.wealth.service.BankService;
import de.wsc.wealth.service.ExchangeRateService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccountControllerTest {

    @Mock private AccountService accountService;
    @Mock private BankService bankService;
    @Mock private ExchangeRateService exchangeRateService;
    @Mock private AssetCriteriaService assetCriteriaService;

    private AccountController controller;

    @BeforeEach
    void setUp() {
        controller = new AccountController(accountService, bankService, exchangeRateService, assetCriteriaService);
    }

    @Test
    void list_returnsAccountsListView() {
        when(accountService.findAll()).thenReturn(List.of());
        when(accountService.getLatestBalancesByAccountId()).thenReturn(Map.of());

        Model model = new ExtendedModelMap();
        String view = controller.list(model);

        assertThat(view).isEqualTo("accounts/list");
    }

    @Test
    void list_populatesModelWithAccountsAndTotal() {
        Account account = account(1L, "My Bank", "EUR");
        when(accountService.findAll()).thenReturn(List.of(account));
        when(accountService.getLatestBalancesByAccountId())
                .thenReturn(Map.of(1L, new BigDecimal("2000.00")));
        when(exchangeRateService.toEur(new BigDecimal("2000.00"), "EUR"))
                .thenReturn(new BigDecimal("2000.00"));

        Model model = new ExtendedModelMap();
        controller.list(model);

        assertThat(model.asMap()).containsKeys("accounts", "latestBalances", "latestBalancesEur", "balanceTotal");
        assertThat(model.getAttribute("balanceTotal")).isEqualTo(new BigDecimal("2000.00"));
    }

    @Test
    void list_withNoAccounts_setsTotalToZero() {
        when(accountService.findAll()).thenReturn(List.of());
        when(accountService.getLatestBalancesByAccountId()).thenReturn(Map.of());

        Model model = new ExtendedModelMap();
        controller.list(model);

        assertThat(model.getAttribute("balanceTotal")).isEqualTo(BigDecimal.ZERO);
    }

    @Test
    void list_withMultipleAccounts_sumsTotalInEur() {
        Account a1 = account(1L, "Bank A", "EUR");
        Account a2 = account(2L, "Bank B", "EUR");
        when(accountService.findAll()).thenReturn(List.of(a1, a2));
        when(accountService.getLatestBalancesByAccountId()).thenReturn(
                Map.of(1L, new BigDecimal("1000.00"), 2L, new BigDecimal("500.00")));
        when(exchangeRateService.toEur(new BigDecimal("1000.00"), "EUR"))
                .thenReturn(new BigDecimal("1000.00"));
        when(exchangeRateService.toEur(new BigDecimal("500.00"), "EUR"))
                .thenReturn(new BigDecimal("500.00"));

        Model model = new ExtendedModelMap();
        controller.list(model);

        assertThat(model.getAttribute("balanceTotal")).isEqualTo(new BigDecimal("1500.00"));
    }

    @Test
    void newForm_returnsFormViewWithEmptyAccount() {
        Model model = new ExtendedModelMap();
        String view = controller.newForm(model);

        assertThat(view).isEqualTo("accounts/form");
        assertThat(model.asMap()).containsKey("account");
    }

    @Test
    void editForm_withExistingAccount_populatesModel() {
        Account account = account(1L, "My Bank", "EUR");
        when(accountService.findById(1L)).thenReturn(Optional.of(account));

        Model model = new ExtendedModelMap();
        controller.editForm(1L, model);

        assertThat(model.getAttribute("account")).isEqualTo(account);
    }

    @Test
    void save_redirectsToAccountsList() {
        Account account = account(1L, "My Bank", "EUR");
        when(accountService.save(any())).thenReturn(account);
        HttpServletRequest request = mock(HttpServletRequest.class);

        String result = controller.save(account, null, request, new RedirectAttributesModelMap());

        assertThat(result).isEqualTo("redirect:/accounts");
        verify(accountService).save(account);
        verify(assetCriteriaService).saveAssignments(account, request);
    }

    @Test
    void delete_redirectsToAccountsList() {
        String result = controller.delete(1L, new RedirectAttributesModelMap());

        assertThat(result).isEqualTo("redirect:/accounts");
        verify(accountService).delete(1L);
    }

    // helper
    private Account account(Long id, String bankName, String currency) {
        Account a = new Account();
        a.setId(id);
        Bank b = new Bank(); b.setName(bankName);
        a.setBank(b);
        a.setAccountNumber("000");
        a.setCurrency(currency);
        a.setAssetAllocation(AssetAllocation.RISIKOFREI);
        return a;
    }
}
