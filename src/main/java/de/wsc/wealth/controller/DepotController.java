package de.wsc.wealth.controller;

import de.wsc.wealth.domain.AssetQuantity;
import de.wsc.wealth.domain.Depot;
import de.wsc.wealth.service.DepotService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

@Controller
@RequestMapping("/depots")
public class DepotController {

    private final DepotService depotService;

    public DepotController(DepotService depotService) {
        this.depotService = depotService;
    }

    @InitBinder("quantity")
    public void initBinder(WebDataBinder binder) {
        binder.setDisallowedFields("id");
    }

    @GetMapping
    public String list(Model model) {
        Map<Long, BigDecimal> depotValues = depotService.getCurrentValueByDepotId();
        BigDecimal total = depotValues.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        model.addAttribute("depots", depotService.findAll());
        model.addAttribute("depotValues", depotValues);
        model.addAttribute("depotTotal", total);
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

    @GetMapping("/{id}/positions")
    public String positions(@PathVariable Long id, Model model) {
        depotService.findById(id).ifPresent(d -> model.addAttribute("depot", d));
        model.addAttribute("quantities", depotService.getQuantities(id));
        model.addAttribute("assets", depotService.findAllAssets());
        return "depots/positions";
    }

    @PostMapping("/{id}/positions/save")
    public String savePosition(@PathVariable Long id,
                               @RequestParam Long assetId,
                               @ModelAttribute AssetQuantity quantity,
                               RedirectAttributes ra) {
        if (quantity.getDate() == null) quantity.setDate(LocalDate.now());
        depotService.saveQuantity(id, assetId, quantity);
        ra.addFlashAttribute("success", "Bestand gespeichert.");
        return "redirect:/depots/" + id + "/positions";
    }

    @PostMapping("/{id}/positions/{quantityId}/delete")
    public String deletePosition(@PathVariable Long id, @PathVariable Long quantityId, RedirectAttributes ra) {
        depotService.deleteQuantity(quantityId);
        ra.addFlashAttribute("success", "Bestand gelöscht.");
        return "redirect:/depots/" + id + "/positions";
    }
}
