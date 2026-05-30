package de.wsc.wealth.controller;

import de.wsc.wealth.domain.Depot;
import de.wsc.wealth.service.DepotService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/depots")
public class DepotController {

    private final DepotService depotService;

    public DepotController(DepotService depotService) {
        this.depotService = depotService;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("depots", depotService.findAll());
        return "depots/list";
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        model.addAttribute("depot", new Depot());
        return "depots/form";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        depotService.findById(id).ifPresent(d -> model.addAttribute("depot", d));
        return "depots/form";
    }

    @PostMapping("/save")
    public String save(@ModelAttribute Depot depot, RedirectAttributes ra) {
        depotService.save(depot);
        ra.addFlashAttribute("success", "Depot gespeichert.");
        return "redirect:/depots";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes ra) {
        depotService.delete(id);
        ra.addFlashAttribute("success", "Depot gelöscht.");
        return "redirect:/depots";
    }
}
