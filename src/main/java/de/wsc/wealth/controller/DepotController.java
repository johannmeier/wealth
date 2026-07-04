package de.wsc.wealth.controller;

import de.wsc.wealth.domain.Asset;
import de.wsc.wealth.domain.AssetQuantity;
import de.wsc.wealth.domain.Coin;
import de.wsc.wealth.domain.CoinMetal;
import de.wsc.wealth.domain.Depot;
import de.wsc.wealth.service.BankService;
import de.wsc.wealth.service.CoinService;
import de.wsc.wealth.service.DepotService;
import de.wsc.wealth.service.ExchangeRateService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Controller
@RequestMapping("/depots")
public class DepotController {

    private final DepotService depotService;
    private final BankService bankService;
    private final ExchangeRateService exchangeRateService;
    private final CoinService coinService;

    public DepotController(DepotService depotService, BankService bankService,
                           ExchangeRateService exchangeRateService, CoinService coinService) {
        this.depotService = depotService;
        this.bankService = bankService;
        this.exchangeRateService = exchangeRateService;
        this.coinService = coinService;
    }

    @InitBinder("quantity")
    public void initBinder(WebDataBinder binder) {
        binder.setDisallowedFields("id");
    }

    @GetMapping
    public String list(Model model) {
        List<Depot> depots = depotService.findAll();
        Map<Long, BigDecimal> depotValues = depotService.getCurrentValueByDepotId();
        BigDecimal total = depotValues.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);

        Set<Long> importDepotIds = new HashSet<>();
        for (Depot d : depots) {
            String name = (d.getName() + " " +
                           (d.getBank() != null ? d.getBank().getName() : "")).toLowerCase();
            if (name.contains("dkb") || name.contains("deutsche kreditbank") ||
                name.contains("fondsdepot") || name.contains("fonds depot") || name.contains("fdb")) {
                importDepotIds.add(d.getId());
            }
        }

        model.addAttribute("depots", depots);
        model.addAttribute("depotValues", depotValues);
        model.addAttribute("depotTotal", total);
        model.addAttribute("importDepotIds", importDepotIds);
        model.addAttribute("depotLastChanged", depotService.getLastChangedDateByDepotId());
        return "depots/list";
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        model.addAttribute("depot", new Depot());
        model.addAttribute("banks", bankService.findAll());
        return "depots/form";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        model.addAttribute("depot", depotService.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND)));
        model.addAttribute("banks", bankService.findAll());
        return "depots/form";
    }

    @PostMapping("/save")
    public String save(@ModelAttribute Depot depot,
                       @RequestParam(required = false) Long bankId,
                       RedirectAttributes ra) {
        depot.setBank(bankId != null ? bankService.findById(bankId).orElse(null) : null);
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
    public String positions(@PathVariable Long id,
                            @RequestParam(required = false) String returnUrl,
                            Model model) {
        model.addAttribute("returnUrl", returnUrl != null ? returnUrl : "/depots");
        model.addAttribute("depot", depotService.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND)));
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
        Map<CoinMetal, BigDecimal> coinOzByMetal = new java.util.EnumMap<>(CoinMetal.class);
        Map<CoinMetal, BigDecimal> coinValueByMetal = new java.util.EnumMap<>(CoinMetal.class);
        BigDecimal coinTotal = BigDecimal.ZERO;
        for (Coin c : coins) {
            if (c.getMetal() == null || c.getWeightGrams() == null || c.getQuantity() == null) continue;
            BigDecimal oz = c.getWeightOz().multiply(BigDecimal.valueOf(c.getQuantity()));
            coinOzByMetal.merge(c.getMetal(), oz, BigDecimal::add);
            BigDecimal val = coinService.valueEur(c);
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

    @GetMapping("/{depotId}/positions/{assetId}/quantities")
    public String assetQuantities(@PathVariable Long depotId, @PathVariable Long assetId, Model model) {
        model.addAttribute("depot", depotService.findById(depotId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND)));
        model.addAttribute("asset", depotService.findAssetById(assetId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND)));
        model.addAttribute("quantities", depotService.getQuantitiesForAsset(depotId, assetId));
        return "depots/quantities";
    }

    @PostMapping("/{depotId}/positions/{assetId}/quantities/save")
    public String saveAssetQuantity(@PathVariable Long depotId, @PathVariable Long assetId,
                                    @ModelAttribute AssetQuantity quantity, RedirectAttributes ra) {
        if (quantity.getDate() == null) quantity.setDate(LocalDate.now());
        depotService.saveQuantity(depotId, assetId, quantity);
        ra.addFlashAttribute("success", "Eintrag gespeichert.");
        return "redirect:/depots/" + depotId + "/positions/" + assetId + "/quantities";
    }

    @PostMapping("/{depotId}/positions/{assetId}/quantities/{quantityId}/delete")
    public String deleteAssetQuantity(@PathVariable Long depotId, @PathVariable Long assetId,
                                      @PathVariable Long quantityId, RedirectAttributes ra) {
        depotService.deleteQuantity(quantityId);
        ra.addFlashAttribute("success", "Eintrag gelöscht.");
        return "redirect:/depots/" + depotId + "/positions/" + assetId + "/quantities";
    }
}
