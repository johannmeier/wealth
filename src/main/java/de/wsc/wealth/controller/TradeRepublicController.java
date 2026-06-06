package de.wsc.wealth.controller;

import de.wsc.wealth.service.TradeRepublicService;
import de.wsc.wealth.service.TradeRepublicService.SyncResult;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/traderepublic")
public class TradeRepublicController {

    private final TradeRepublicService trService;

    public TradeRepublicController(TradeRepublicService trService) {
        this.trService = trService;
    }

    @GetMapping
    public String page(Model model) {
        trService.findConfig().ifPresent(c -> model.addAttribute("config", c));
        return "traderepublic/settings";
    }

    @PostMapping("/waf-token")
    public String saveWafToken(@RequestParam String phoneNumber,
                               @RequestParam String wafToken,
                               RedirectAttributes ra) {
        try {
            trService.saveWafToken(phoneNumber, wafToken);
            ra.addFlashAttribute("wafSaved", true);
        } catch (Exception e) {
            ra.addFlashAttribute("syncError", e.getMessage());
        }
        return "redirect:/traderepublic";
    }

    @PostMapping("/request-otp")
    public String requestOtp(@RequestParam String phoneNumber,
                             @RequestParam String pin,
                             RedirectAttributes ra) {
        try {
            String processId = trService.requestOtp(phoneNumber.strip(), pin.strip());
            ra.addFlashAttribute("processId", processId);
            ra.addFlashAttribute("otpPhone", phoneNumber.strip());
        } catch (Exception e) {
            ra.addFlashAttribute("syncError", e.getMessage());
            ra.addFlashAttribute("otpPhone", phoneNumber.strip());
        }
        return "redirect:/traderepublic";
    }

    @PostMapping("/sync")
    public String sync(@RequestParam String phoneNumber,
                       @RequestParam String processId,
                       @RequestParam String otp,
                       RedirectAttributes ra) {
        try {
            SyncResult result = trService.sync(phoneNumber, processId, otp);
            ra.addFlashAttribute("syncResult", result);
        } catch (Exception e) {
            ra.addFlashAttribute("syncError", e.getMessage());
        }
        return "redirect:/traderepublic";
    }
}
