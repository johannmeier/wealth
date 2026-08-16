package de.wsc.wealth.controller;

import de.wsc.wealth.service.FintsService;
import de.wsc.wealth.service.FintsService.DialogStartResult;
import de.wsc.wealth.service.FintsService.SyncResult;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/fints")
public class FintsController {

    private final FintsService fintsService;

    public FintsController(FintsService fintsService) {
        this.fintsService = fintsService;
    }

    @GetMapping
    public String page(Model model) {
        model.addAttribute("configs", fintsService.findAll());
        model.addAttribute("suggestedDefaults", FintsService.SUGGESTED_DEFAULTS);
        return "fints/settings";
    }

    @PostMapping("/config")
    public String saveConfig(@RequestParam(required = false) Long id,
                             @RequestParam String blz,
                             @RequestParam String fintsUrl,
                             @RequestParam(required = false) String tanVerfahren,
                             RedirectAttributes ra) {
        try {
            fintsService.saveConfig(id, blz, fintsUrl, tanVerfahren);
            ra.addFlashAttribute("configSaved", true);
        } catch (Exception e) {
            ra.addFlashAttribute("syncError", e.getMessage());
        }
        return "redirect:/fints";
    }

    @PostMapping("/{configId}/start")
    public String start(@PathVariable Long configId,
                        @RequestParam String userId,
                        @RequestParam String pin,
                        RedirectAttributes ra) {
        try {
            DialogStartResult result = fintsService.startDialog(configId, userId.strip(), pin.strip());
            if (result.challenge() != null) {
                ra.addFlashAttribute("challenge", result.challenge());
            } else {
                ra.addFlashAttribute("syncResult", result.result());
            }
        } catch (Exception e) {
            ra.addFlashAttribute("syncError", e.getMessage());
        }
        return "redirect:/fints";
    }

    @PostMapping("/submit-tan")
    public String submitTan(@RequestParam String processId,
                            @RequestParam String tan,
                            RedirectAttributes ra) {
        try {
            SyncResult result = fintsService.submitTan(processId, tan.strip());
            ra.addFlashAttribute("syncResult", result);
        } catch (Exception e) {
            ra.addFlashAttribute("syncError", e.getMessage());
        }
        return "redirect:/fints";
    }
}
