package de.wsc.wealth.controller;

import de.wsc.wealth.domain.AssetQuantity;
import de.wsc.wealth.domain.Coin;
import de.wsc.wealth.domain.CoinMetal;
import de.wsc.wealth.domain.Depot;
import de.wsc.wealth.service.CoinService;
import de.wsc.wealth.service.DepotService;
import de.wsc.wealth.service.ExchangeRateService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/depots")
public class DepotController {

    private final DepotService depotService;
    private final ExchangeRateService exchangeRateService;
    private final CoinService coinService;

    public DepotController(DepotService depotService, ExchangeRateService exchangeRateService,
                           CoinService coinService) {
        this.depotService = depotService;
        this.exchangeRateService = exchangeRateService;
        this.coinService = coinService;
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
        List<AssetQuantity> quantities = depotService.getQuantities(id);
        model.addAttribute("quantities", quantities);
        model.addAttribute("assets", depotService.findAllAssets());

        Map<Long, BigDecimal> positionValues = new HashMap<>();
        BigDecimal total = BigDecimal.ZERO;
        for (AssetQuantity q : quantities) {
            BigDecimal eurPrice = exchangeRateService.toEur(
                q.getAsset().getCurrentPrice(), q.getAsset().getCurrency());
            if (q.getQuantity() != null && eurPrice != null) {
                BigDecimal val = q.getQuantity().multiply(eurPrice).setScale(2, RoundingMode.HALF_UP);
                positionValues.put(q.getId(), val);
                total = total.add(val);
            }
        }
        model.addAttribute("positionValues", positionValues);
        model.addAttribute("positionTotal", total);

        List<Coin> coins = coinService.findByDepotId(id);
        Map<CoinMetal, BigDecimal> spotPrices = coinService.fetchSpotPricesUsd();
        Map<CoinMetal, BigDecimal> coinOzByMetal = new java.util.EnumMap<>(CoinMetal.class);
        Map<CoinMetal, BigDecimal> coinValueByMetal = new java.util.EnumMap<>(CoinMetal.class);
        BigDecimal coinTotal = BigDecimal.ZERO;
        for (Coin c : coins) {
            if (c.getMetal() == null || c.getWeightGrams() == null || c.getQuantity() == null) continue;
            BigDecimal oz = c.getWeightOz().multiply(c.getQuantity());
            coinOzByMetal.merge(c.getMetal(), oz, BigDecimal::add);
            BigDecimal val = coinService.valueEur(c, spotPrices);
            if (val != null) {
                coinValueByMetal.merge(c.getMetal(), val, BigDecimal::add);
                coinTotal = coinTotal.add(val);
            }
        }
        model.addAttribute("coinOzByMetal", coinOzByMetal);
        model.addAttribute("coinValueByMetal", coinValueByMetal);
        model.addAttribute("coinTotal", coinTotal);
        return "depots/positions";
    }

    @PostMapping("/{id}/positions/save")
    public String savePosition(@PathVariable Long id,
                               @RequestParam Long assetId,
                               @RequestParam(required = false) Long quantityId,
                               @ModelAttribute AssetQuantity quantity,
                               RedirectAttributes ra) {
        if (quantity.getDate() == null) quantity.setDate(LocalDate.now());
        if (quantityId != null) {
            depotService.updateQuantity(quantityId, assetId, quantity.getDate(), quantity.getQuantity());
            ra.addFlashAttribute("success", "Bestand aktualisiert.");
        } else {
            depotService.saveQuantity(id, assetId, quantity);
            ra.addFlashAttribute("success", "Bestand gespeichert.");
        }
        return "redirect:/depots/" + id + "/positions";
    }

    @PostMapping("/{id}/positions/{quantityId}/delete")
    public String deletePosition(@PathVariable Long id, @PathVariable Long quantityId, RedirectAttributes ra) {
        depotService.deleteQuantity(quantityId);
        ra.addFlashAttribute("success", "Bestand gelöscht.");
        return "redirect:/depots/" + id + "/positions";
    }
}
