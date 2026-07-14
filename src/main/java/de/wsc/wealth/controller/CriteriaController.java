package de.wsc.wealth.controller;

import de.wsc.wealth.domain.CriteriaDefinition;
import de.wsc.wealth.license.LicenseFeature;
import de.wsc.wealth.license.LicenseService;
import de.wsc.wealth.service.CriteriaService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/criteria")
public class CriteriaController {

    private final CriteriaService criteriaService;
    private final LicenseService licenseService;

    public CriteriaController(CriteriaService criteriaService, LicenseService licenseService) {
        this.criteriaService = criteriaService;
        this.licenseService = licenseService;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("definitions", criteriaService.findAll());
        model.addAttribute("licensedCustomCriteria", licenseService.isFeatureEnabled(LicenseFeature.CUSTOM_CRITERIA));
        return "criteria/list";
    }

    @GetMapping("/new")
    public String newForm(Model model, RedirectAttributes ra) {
        if (!licenseService.isFeatureEnabled(LicenseFeature.CUSTOM_CRITERIA)) {
            ra.addFlashAttribute("error", "Eigene Kriterien erfordern eine Lizenz.");
            return "redirect:/criteria";
        }
        CriteriaDefinition definition = new CriteriaDefinition();
        definition.setColorIndex(criteriaService.nextFreeColorIndex());
        model.addAttribute("definition", definition);
        addColorIndexes(model);
        return "criteria/form";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        model.addAttribute("definition", criteriaService.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND)));
        addColorIndexes(model);
        return "criteria/form";
    }

    @PostMapping("/save")
    public String save(@ModelAttribute CriteriaDefinition definition, RedirectAttributes ra) {
        try {
            criteriaService.save(definition);
            ra.addFlashAttribute("success", "Kriterium gespeichert.");
        } catch (IllegalStateException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/criteria";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes ra) {
        try {
            criteriaService.delete(id);
            ra.addFlashAttribute("success", "Kriterium gelöscht.");
        } catch (IllegalStateException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/criteria";
    }

    @GetMapping("/{id}/options")
    public String options(@PathVariable Long id, Model model) {
        model.addAttribute("definition", criteriaService.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND)));
        model.addAttribute("options", criteriaService.getOptions(id));
        return "criteria/options";
    }

    @PostMapping("/{id}/options/save")
    public String saveOption(@PathVariable Long id,
                             @RequestParam(required = false) Long optionId,
                             @RequestParam String value,
                             RedirectAttributes ra) {
        criteriaService.saveOption(id, optionId, value);
        ra.addFlashAttribute("success", "Wert gespeichert.");
        return "redirect:/criteria/" + id + "/options";
    }

    @PostMapping("/{id}/options/{optionId}/delete")
    public String deleteOption(@PathVariable Long id, @PathVariable Long optionId, RedirectAttributes ra) {
        try {
            criteriaService.deleteOption(optionId);
            ra.addFlashAttribute("success", "Wert gelöscht.");
        } catch (IllegalStateException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/criteria/" + id + "/options";
    }

    private void addColorIndexes(Model model) {
        model.addAttribute("colorIndexes", java.util.stream.IntStream.range(0, CriteriaService.COLOR_COUNT).boxed().toList());
    }
}
