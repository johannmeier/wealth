package de.wsc.wealth.controller;

import de.wsc.wealth.domain.OrderInterval;
import de.wsc.wealth.domain.StandingOrder;
import de.wsc.wealth.service.DepotService;
import de.wsc.wealth.service.AssetService;
import de.wsc.wealth.service.StandingOrderService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/standing-orders")
public class StandingOrderController {

    private final StandingOrderService standingOrderService;
    private final AssetService assetService;
    private final DepotService depotService;

    public StandingOrderController(StandingOrderService standingOrderService,
                                   AssetService assetService,
                                   DepotService depotService) {
        this.standingOrderService = standingOrderService;
        this.assetService = assetService;
        this.depotService = depotService;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("orders", standingOrderService.findAll());
        return "standing-orders/list";
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        model.addAttribute("order", new StandingOrder());
        model.addAttribute("assets", assetService.findAll());
        model.addAttribute("depots", depotService.findAll());
        model.addAttribute("intervals", OrderInterval.values());
        return "standing-orders/form";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        standingOrderService.findById(id).ifPresent(o -> model.addAttribute("order", o));
        model.addAttribute("assets", assetService.findAll());
        model.addAttribute("depots", depotService.findAll());
        model.addAttribute("intervals", OrderInterval.values());
        return "standing-orders/form";
    }

    @PostMapping("/save")
    public String save(@ModelAttribute StandingOrder order,
                       @RequestParam Long assetId,
                       @RequestParam Long depotId,
                       RedirectAttributes ra) {
        standingOrderService.save(order, assetId, depotId);
        ra.addFlashAttribute("success", "Dauerauftrag gespeichert.");
        return "redirect:/standing-orders";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes ra) {
        standingOrderService.delete(id);
        ra.addFlashAttribute("success", "Dauerauftrag gelöscht.");
        return "redirect:/standing-orders";
    }
}
