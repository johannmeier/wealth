package de.wsc.wealth.controller;

import de.wsc.wealth.domain.Account;
import de.wsc.wealth.domain.AccountBalance;
import de.wsc.wealth.domain.AssetAllocation;
import de.wsc.wealth.service.AccountService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;

@Controller
@RequestMapping("/accounts")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @InitBinder("balance")
    public void initBinder(WebDataBinder binder) {
        binder.setDisallowedFields("id");
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("accounts", accountService.findAll());
        return "accounts/list";
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        model.addAttribute("account", new Account());
        model.addAttribute("allocations", AssetAllocation.values());
        return "accounts/form";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        accountService.findById(id).ifPresent(a -> model.addAttribute("account", a));
        model.addAttribute("allocations", AssetAllocation.values());
        return "accounts/form";
    }

    @PostMapping("/save")
    public String save(@ModelAttribute Account account, RedirectAttributes ra) {
        accountService.save(account);
        ra.addFlashAttribute("success", "Konto gespeichert.");
        return "redirect:/accounts";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes ra) {
        accountService.delete(id);
        ra.addFlashAttribute("success", "Konto gelöscht.");
        return "redirect:/accounts";
    }

    @GetMapping("/{id}/balances")
    public String balances(@PathVariable Long id, Model model) {
        accountService.findById(id).ifPresent(a -> model.addAttribute("account", a));
        model.addAttribute("balances", accountService.getBalances(id));
        model.addAttribute("newBalance", new AccountBalance());
        return "accounts/balances";
    }

    @PostMapping("/{id}/balances/save")
    public String saveBalance(@PathVariable Long id,
                              @ModelAttribute AccountBalance balance,
                              RedirectAttributes ra) {
        if (balance.getDate() == null) balance.setDate(LocalDate.now());
        accountService.saveBalance(id, balance);
        ra.addFlashAttribute("success", "Saldo gespeichert.");
        return "redirect:/accounts/" + id + "/balances";
    }
}
