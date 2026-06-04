package de.wsc.wealth.controller;

import de.wsc.wealth.domain.Account;
import de.wsc.wealth.domain.Bank;
import de.wsc.wealth.domain.Depot;
import de.wsc.wealth.service.AccountService;
import de.wsc.wealth.service.BankService;
import de.wsc.wealth.service.DepotService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/banks")
public class BankController {

    private final BankService bankService;
    private final AccountService accountService;
    private final DepotService depotService;

    public BankController(BankService bankService, AccountService accountService, DepotService depotService) {
        this.bankService = bankService;
        this.accountService = accountService;
        this.depotService = depotService;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("banks", bankService.findAll());
        return "banks/list";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model) {
        Bank bank = bankService.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        model.addAttribute("bank", bank);
        model.addAttribute("accounts", accountService.findByBankId(id));
        model.addAttribute("depots", depotService.findByBankId(id));
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
