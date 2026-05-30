package de.wsc.wealth.controller;

import de.wsc.wealth.domain.*;
import de.wsc.wealth.service.PriceService;
import de.wsc.wealth.service.AssetService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;

@Controller
@RequestMapping("/assets")
public class AssetController {

    private final AssetService assetService;
    private final PriceService priceService;

    public AssetController(AssetService assetService, PriceService priceService) {
        this.assetService = assetService;
        this.priceService = priceService;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("assets", assetService.findAll());
        return "assets/list";
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        model.addAttribute("asset", new Asset());
        addEnums(model);
        return "assets/form";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        assetService.findById(id).ifPresent(s -> model.addAttribute("asset", s));
        addEnums(model);
        return "assets/form";
    }

    @PostMapping("/save")
    public String save(@ModelAttribute Asset asset, RedirectAttributes ra) {
        assetService.save(asset);
        ra.addFlashAttribute("success", "Wertpapier gespeichert.");
        return "redirect:/assets";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes ra) {
        assetService.delete(id);
        ra.addFlashAttribute("success", "Wertpapier gelöscht.");
        return "redirect:/assets";
    }

    @GetMapping("/{id}/quantities")
    public String quantities(@PathVariable Long id, Model model) {
        assetService.findById(id).ifPresent(s -> model.addAttribute("asset", s));
        model.addAttribute("quantities", assetService.getQuantities(id));
        model.addAttribute("depots", assetService.findAllDepots());
        model.addAttribute("newQuantity", new AssetQuantity());
        return "assets/quantities";
    }

    @PostMapping("/{id}/quantities/save")
    public String saveQuantity(@PathVariable Long id,
                               @RequestParam Long depotId,
                               @ModelAttribute AssetQuantity quantity,
                               RedirectAttributes ra) {
        if (quantity.getDate() == null) quantity.setDate(LocalDate.now());
        assetService.saveQuantity(id, depotId, quantity);
        ra.addFlashAttribute("success", "Bestand gespeichert.");
        return "redirect:/assets/" + id + "/quantities";
    }

    @PostMapping("/{id}/refresh-price")
    public String refreshPrice(@PathVariable Long id, RedirectAttributes ra) {
        assetService.findById(id).ifPresent(s -> {
            if (s.isAutoPrice() && s.getSymbol() != null) {
                priceService.updatePrice(s);
            }
        });
        ra.addFlashAttribute("success", "Kurs aktualisiert.");
        return "redirect:/assets";
    }

    private void addEnums(Model model) {
        model.addAttribute("categories", AssetCategory.values());
        model.addAttribute("types", AssetType.values());
        model.addAttribute("allocations", AssetAllocation.values());
    }
}
