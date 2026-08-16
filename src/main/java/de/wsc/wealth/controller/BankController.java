package de.wsc.wealth.controller;

import de.wsc.wealth.domain.Account;
import de.wsc.wealth.domain.Bank;
import de.wsc.wealth.domain.Depot;
import de.wsc.wealth.repository.BullionVaultConfigRepository;
import de.wsc.wealth.repository.FintsConfigRepository;
import de.wsc.wealth.repository.TradeRepublicConfigRepository;
import de.wsc.wealth.service.AccountService;
import de.wsc.wealth.service.BankService;
import de.wsc.wealth.service.DepotService;
import de.wsc.wealth.service.ExchangeRateService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/banks")
public class BankController {

    private final BankService bankService;
    private final AccountService accountService;
    private final DepotService depotService;
    private final ExchangeRateService exchangeRateService;
    private final BullionVaultConfigRepository bvConfigRepo;
    private final TradeRepublicConfigRepository trConfigRepo;
    private final FintsConfigRepository fintsConfigRepo;

    public BankController(BankService bankService, AccountService accountService,
                          DepotService depotService, ExchangeRateService exchangeRateService,
                          BullionVaultConfigRepository bvConfigRepo,
                          TradeRepublicConfigRepository trConfigRepo,
                          FintsConfigRepository fintsConfigRepo) {
        this.bankService = bankService;
        this.accountService = accountService;
        this.depotService = depotService;
        this.exchangeRateService = exchangeRateService;
        this.bvConfigRepo = bvConfigRepo;
        this.trConfigRepo = trConfigRepo;
        this.fintsConfigRepo = fintsConfigRepo;
    }

    @GetMapping
    public String list(Model model) {
        List<de.wsc.wealth.domain.Bank> banks = bankService.findAll();
        Map<Long, BigDecimal> latestBalances = accountService.getLatestBalancesByAccountId();
        Map<Long, BigDecimal> depotValues    = depotService.getCurrentValueByDepotId();
        Map<Long, java.time.LocalDate> accountLastChanged = accountService.getLatestBalanceDatesByAccountId();
        Map<Long, java.time.LocalDate> depotLastChanged   = depotService.getLastChangedDateByDepotId();

        Map<Long, List<de.wsc.wealth.domain.Account>> bankAccounts = new java.util.LinkedHashMap<>();
        Map<Long, List<de.wsc.wealth.domain.Depot>>   bankDepots   = new java.util.LinkedHashMap<>();
        Map<Long, BigDecimal> accountValuesEur = new java.util.HashMap<>();
        Map<Long, BigDecimal> bankTotals       = new java.util.LinkedHashMap<>();
        BigDecimal grandTotal = BigDecimal.ZERO;

        for (de.wsc.wealth.domain.Bank b : banks) {
            List<de.wsc.wealth.domain.Account> accs = accountService.findByBankId(b.getId());
            List<de.wsc.wealth.domain.Depot>   deps = depotService.findByBankId(b.getId());
            bankAccounts.put(b.getId(), accs);
            bankDepots.put(b.getId(), deps);

            BigDecimal total = BigDecimal.ZERO;
            for (de.wsc.wealth.domain.Account a : accs) {
                BigDecimal raw = latestBalances.get(a.getId());
                if (raw != null) {
                    BigDecimal eur = exchangeRateService.toEur(raw, a.getCurrency());
                    if (eur != null) { accountValuesEur.put(a.getId(), eur); total = total.add(eur); }
                }
            }
            for (de.wsc.wealth.domain.Depot d : deps) {
                BigDecimal val = depotValues.get(d.getId());
                if (val != null) total = total.add(val);
            }
            bankTotals.put(b.getId(), total);
            grandTotal = grandTotal.add(total);
        }

        Map<Long, String> syncUrls = new HashMap<>();
        bvConfigRepo.findAll().forEach(c -> { if (c.getBank() != null) syncUrls.put(c.getBank().getId(), "/bullionvault"); });
        trConfigRepo.findAll().forEach(c -> { if (c.getBank() != null) syncUrls.put(c.getBank().getId(), "/traderepublic"); });
        fintsConfigRepo.findAll().forEach(c -> { if (c.getBank() != null) syncUrls.put(c.getBank().getId(), "/fints"); });

        model.addAttribute("banks", banks);
        model.addAttribute("bankAccounts", bankAccounts);
        model.addAttribute("bankDepots", bankDepots);
        model.addAttribute("accountValuesEur", accountValuesEur);
        model.addAttribute("depotValues", depotValues);
        model.addAttribute("accountLastChanged", accountLastChanged);
        model.addAttribute("depotLastChanged", depotLastChanged);
        model.addAttribute("bankTotals", bankTotals);
        model.addAttribute("grandTotal", grandTotal);
        model.addAttribute("syncUrls", syncUrls);
        return "banks/list";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model) {
        Bank bank = bankService.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        List<Account> accounts = accountService.findByBankId(id);
        Map<Long, BigDecimal> latestBalances = accountService.getLatestBalancesByAccountId();
        BigDecimal accountTotal = BigDecimal.ZERO;
        java.util.Map<Long, BigDecimal> accountValuesEur = new java.util.HashMap<>();
        for (Account a : accounts) {
            BigDecimal raw = latestBalances.get(a.getId());
            if (raw != null) {
                BigDecimal eur = exchangeRateService.toEur(raw, a.getCurrency());
                if (eur != null) { accountValuesEur.put(a.getId(), eur); accountTotal = accountTotal.add(eur); }
            }
        }

        Map<Long, BigDecimal> depotValues = depotService.getCurrentValueByDepotId();
        List<de.wsc.wealth.domain.Depot> depots = depotService.findByBankId(id);
        BigDecimal depotTotal = BigDecimal.ZERO;
        for (de.wsc.wealth.domain.Depot d : depots) {
            BigDecimal val = depotValues.get(d.getId());
            if (val != null) depotTotal = depotTotal.add(val);
        }

        model.addAttribute("bank", bank);
        model.addAttribute("accounts", accounts);
        model.addAttribute("accountValuesEur", accountValuesEur);
        model.addAttribute("accountTotal", accountTotal);
        model.addAttribute("depots", depots);
        model.addAttribute("depotValues", depotValues);
        model.addAttribute("depotTotal", depotTotal);
        model.addAttribute("grandTotal", accountTotal.add(depotTotal));
        model.addAttribute("unassignedAccounts", accountService.findWithoutBank());
        model.addAttribute("unassignedDepots", depotService.findWithoutBank());
        return "banks/detail";
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        model.addAttribute("bank", new Bank());
        return "banks/form";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        model.addAttribute("bank", bankService.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND)));
        return "banks/form";
    }

    @PostMapping("/save")
    public String save(@ModelAttribute Bank bank, RedirectAttributes ra) {
        bankService.save(bank);
        ra.addFlashAttribute("success", "Bank gespeichert.");
        return "redirect:/banks";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes ra) {
        try {
            bankService.delete(id);
            ra.addFlashAttribute("success", "Bank gelöscht.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Bank kann nicht gelöscht werden – es sind noch Konten oder Depots zugeordnet.");
        }
        return "redirect:/banks";
    }

    @PostMapping("/{id}/accounts/assign")
    public String assignAccount(@PathVariable Long id, @RequestParam Long accountId, RedirectAttributes ra) {
        Bank bank = bankService.findById(id).orElseThrow();
        Account account = accountService.findById(accountId).orElseThrow();
        account.setBank(bank);
        accountService.save(account);
        return "redirect:/banks/" + id;
    }

    @PostMapping("/{id}/accounts/{accountId}/remove")
    public String removeAccount(@PathVariable Long id, @PathVariable Long accountId, RedirectAttributes ra) {
        Account account = accountService.findById(accountId).orElseThrow();
        account.setBank(null);
        accountService.save(account);
        return "redirect:/banks/" + id;
    }

    @PostMapping("/{id}/depots/assign")
    public String assignDepot(@PathVariable Long id, @RequestParam Long depotId, RedirectAttributes ra) {
        Bank bank = bankService.findById(id).orElseThrow();
        Depot depot = depotService.findById(depotId).orElseThrow();
        depot.setBank(bank);
        depotService.save(depot);
        return "redirect:/banks/" + id;
    }

    @PostMapping("/{id}/depots/{depotId}/remove")
    public String removeDepot(@PathVariable Long id, @PathVariable Long depotId, RedirectAttributes ra) {
        Depot depot = depotService.findById(depotId).orElseThrow();
        depot.setBank(null);
        depotService.save(depot);
        return "redirect:/banks/" + id;
    }
}
