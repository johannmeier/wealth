package de.wsc.wealth.controller;

import de.wsc.wealth.domain.Bank;
import de.wsc.wealth.service.BankService;
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

    public BankController(BankService bankService) {
        this.bankService = bankService;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("banks", bankService.findAll());
        return "banks/list";
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
}
