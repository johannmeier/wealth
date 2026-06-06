package de.wsc.wealth.controller;

import de.wsc.wealth.service.BullionVaultService;
import de.wsc.wealth.service.BullionVaultService.SyncResult;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/bullionvault")
public class BullionVaultController {

    private final BullionVaultService bullionVaultService;

    public BullionVaultController(BullionVaultService bullionVaultService) {
        this.bullionVaultService = bullionVaultService;
    }

    @GetMapping
    public String page(Model model) {
        bullionVaultService.findConfig().ifPresent(c -> model.addAttribute("config", c));
        return "bullionvault/settings";
    }

    @PostMapping("/sync")
    public String sync(@RequestParam String username,
                       @RequestParam String password,
                       RedirectAttributes ra) {
        try {
            SyncResult result = bullionVaultService.sync(username.strip(), password);
            ra.addFlashAttribute("syncResult", result);
        } catch (Exception e) {
            ra.addFlashAttribute("syncError", e.getMessage());
            ra.addFlashAttribute("syncUsername", username);
        }
        return "redirect:/bullionvault";
    }
}
