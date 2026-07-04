package de.wsc.wealth.controller;

import de.wsc.wealth.service.IbkrService;
import de.wsc.wealth.service.IbkrService.SyncResult;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/ibkr")
public class IbkrController {

    private final IbkrService ibkrService;

    public IbkrController(IbkrService ibkrService) {
        this.ibkrService = ibkrService;
    }

    @GetMapping
    public String page(Model model) {
        ibkrService.findConfig().ifPresent(c -> model.addAttribute("config", c));
        return "ibkr/settings";
    }

    @PostMapping("/sync")
    public String sync(@RequestParam String token,
                       @RequestParam String queryId,
                       RedirectAttributes ra) {
        try {
            SyncResult result = ibkrService.sync(token.strip(), queryId.strip());
            ra.addFlashAttribute("syncResult", result);
        } catch (Exception e) {
            ra.addFlashAttribute("syncError", e.getMessage());
            ra.addFlashAttribute("syncQueryId", queryId);
        }
        return "redirect:/ibkr";
    }
}
