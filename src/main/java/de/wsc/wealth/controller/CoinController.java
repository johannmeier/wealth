package de.wsc.wealth.controller;

import de.wsc.wealth.domain.Coin;
import de.wsc.wealth.domain.CoinMetal;
import de.wsc.wealth.service.CoinService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.util.Collections;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/coins")
public class CoinController {

    private final CoinService coinService;

    public CoinController(CoinService coinService) {
        this.coinService = coinService;
    }

    @GetMapping
    public String list(Model model) {
        List<Coin> coins = coinService.findAll();
        Map<CoinMetal, BigDecimal> spotPrices = coinService.fetchSpotPricesUsd();
        Map<Long, BigDecimal> values = new HashMap<>();
        BigDecimal total = BigDecimal.ZERO;
        for (Coin c : coins) {
            BigDecimal val = coinService.valueEur(c, spotPrices);
            if (val != null) {
                values.put(c.getId(), val);
                total = total.add(val);
            }
        }
        model.addAttribute("coins", coins);
        model.addAttribute("values", values);
        model.addAttribute("total", total);
        model.addAttribute("spotPrices", spotPrices);
        return "coins/list";
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        model.addAttribute("coin", new Coin());
        model.addAttribute("metals", CoinMetal.values());
        model.addAttribute("assets", coinService.findAllAssets());
        model.addAttribute("depots", coinService.findAllDepots());
        model.addAttribute("existingNames", coinService.findAllNames());
        return "coins/form";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        coinService.findById(id).ifPresent(c -> model.addAttribute("coin", c));
        model.addAttribute("metals", CoinMetal.values());
        model.addAttribute("assets", coinService.findAllAssets());
        model.addAttribute("depots", coinService.findAllDepots());
        model.addAttribute("existingNames", coinService.findAllNames());
        return "coins/form";
    }

    @GetMapping("/api/by-name")
    @ResponseBody
    public Map<String, Object> getByName(@RequestParam String name) {
        return coinService.findFirstByName(name).map(c -> {
            Map<String, Object> result = new HashMap<>();
            result.put("metal", c.getMetal() != null ? c.getMetal().name() : null);
            result.put("weightGrams", c.getWeightGrams());
            result.put("assetId", c.getAsset() != null ? c.getAsset().getId() : null);
            return result;
        }).orElse(Collections.emptyMap());
    }

    @PostMapping("/save")
    public String save(@RequestParam Long depotId,
                       @RequestParam(required = false) Long assetId,
                       @ModelAttribute Coin coin,
                       RedirectAttributes ra) {
        coinService.save(depotId, assetId, coin);
        ra.addFlashAttribute("success", "Münze gespeichert.");
        return "redirect:/coins";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes ra) {
        coinService.delete(id);
        ra.addFlashAttribute("success", "Münze gelöscht.");
        return "redirect:/coins";
    }
}
