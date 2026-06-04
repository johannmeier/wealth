package de.wsc.wealth.controller;

import de.wsc.wealth.domain.*;
import de.wsc.wealth.service.AssetSearchService;
import de.wsc.wealth.service.AssetService;
import de.wsc.wealth.service.ExchangeRateService;
import de.wsc.wealth.service.PriceService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;


@Controller
@RequestMapping("/assets")
public class AssetController {

    private final AssetService assetService;
    private final PriceService priceService;
    private final ExchangeRateService exchangeRateService;
    private final AssetSearchService assetSearchService;

    public AssetController(AssetService assetService, PriceService priceService,
                           ExchangeRateService exchangeRateService, AssetSearchService assetSearchService) {
        this.assetService = assetService;
        this.priceService = priceService;
        this.exchangeRateService = exchangeRateService;
        this.assetSearchService = assetSearchService;
    }

    @GetMapping
    public String list(Model model, @org.springframework.web.bind.annotation.RequestParam(required = false) Long highlight) {
        List<Asset> assets = assetService.findAll();
        Map<Long, BigDecimal> eurPrices = new java.util.HashMap<>();
        for (Asset a : assets) {
            eurPrices.put(a.getId(), exchangeRateService.toEur(a.getCurrentPrice(), a.getCurrency()));
        }
        model.addAttribute("assets", assets);
        model.addAttribute("eurPrices", eurPrices);
        model.addAttribute("depotsByAsset", assetService.getDepotsByAssetId());
        model.addAttribute("archivedAssets", assetService.findAllArchived());
        model.addAttribute("deletableIds", assetService.getDeletableArchivedIds());
        if (highlight != null) model.addAttribute("highlightId", highlight);
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
        model.addAttribute("asset", assetService.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND)));
        addEnums(model);
        return "assets/form";
    }

    @PostMapping("/save")
    public String save(@ModelAttribute Asset asset, RedirectAttributes ra) {
        Asset saved = assetService.save(asset);
        ra.addFlashAttribute("success", "Wertpapier gespeichert.");
        ra.addFlashAttribute("highlightId", saved.getId());
        return "redirect:/assets";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes ra) {
        assetService.delete(id);
        ra.addFlashAttribute("success", "Wertpapier archiviert.");
        ra.addFlashAttribute("highlightId", id);
        return "redirect:/assets";
    }

    @PostMapping("/{id}/restore")
    public String restore(@PathVariable Long id, RedirectAttributes ra) {
        assetService.restore(id);
        ra.addFlashAttribute("success", "Wertpapier wiederhergestellt.");
        ra.addFlashAttribute("highlightId", id);
        return "redirect:/assets";
    }

    @PostMapping("/{id}/hard-delete")
    public String hardDelete(@PathVariable Long id, RedirectAttributes ra) {
        if (assetService.isDeletable(id)) {
            assetService.hardDelete(id);
            ra.addFlashAttribute("success", "Wertpapier gelöscht.");
        } else {
            ra.addFlashAttribute("error", "Wertpapier kann nicht gelöscht werden (Depotbestände oder Münzen vorhanden).");
        }
        return "redirect:/assets";
    }

    @GetMapping("/{id}/quantities")
    public String quantities(@PathVariable Long id, Model model) {
        model.addAttribute("asset", assetService.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND)));
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
        if (quantity.getDate() == null) {
            quantity.setDate(LocalDate.now());
        }
        quantity.setId(null);
        assetService.saveQuantity(id, depotId, quantity);
        ra.addFlashAttribute("success", "Bestand gespeichert.");
        return "redirect:/assets/" + id + "/quantities";
    }

    @GetMapping("/{id}/price-history")
    public String priceHistory(@PathVariable Long id, Model model) {
        model.addAttribute("asset", assetService.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND)));
        List<PriceHistory> history = assetService.getPriceHistory(id);
        Map<Long, BigDecimal> historyEurPrices = new java.util.HashMap<>();
        for (PriceHistory h : history) {
            historyEurPrices.put(h.getId(), exchangeRateService.toEur(h.getPrice(), h.getCurrency()));
        }
        model.addAttribute("priceHistory", history);
        model.addAttribute("historyEurPrices", historyEurPrices);
        return "assets/price-history";
    }

    @PostMapping("/{id}/refresh-price")
    public String refreshPrice(@PathVariable Long id, RedirectAttributes ra) {
        assetService.findById(id).ifPresent(s -> {
            if (s.isAutoPrice() && s.getSymbol() != null) {
                priceService.updatePrice(s);
            }
        });
        ra.addFlashAttribute("success", "Kurs aktualisiert.");
        ra.addFlashAttribute("highlightId", id);
        return "redirect:/assets";
    }

    @PostMapping("/refresh-all-prices")
    public String refreshAllPrices(RedirectAttributes ra) {
        List<de.wsc.wealth.domain.Asset> toUpdate = assetService.findAll().stream()
            .filter(s -> s.isAutoPrice() && s.getSymbol() != null)
            .toList();
        toUpdate.forEach(priceService::updatePrice);
        ra.addFlashAttribute("success", toUpdate.size() + " Kurse aktualisiert.");
        return "redirect:/assets";
    }

    @GetMapping("/search")
    @ResponseBody
    public List<Map<String, String>> search(@RequestParam String q,
            @CookieValue(name = "wealth-currency", defaultValue = "EUR") String baseCurrency) {
        return assetSearchService.search(q, baseCurrency);
    }

    @GetMapping("/quote-details")
    @ResponseBody
    public Map<String, String> quoteDetails(@RequestParam String symbol) {
        return assetSearchService.getQuoteDetails(symbol);
    }

    private void addEnums(Model model) {
        model.addAttribute("categories", AssetCategory.values());
        model.addAttribute("types", AssetType.values());
        model.addAttribute("allocations", AssetAllocation.values());
        model.addAttribute("distributionPolicies", de.wsc.wealth.domain.DistributionPolicy.values());
    }
}
