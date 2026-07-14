package de.wsc.wealth.controller;

import de.wsc.wealth.dto.WealthPosition;
import de.wsc.wealth.service.AssetCriteriaService;
import de.wsc.wealth.service.AssetService;
import de.wsc.wealth.service.StatisticsService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

@Controller
public class DashboardController {

    private final StatisticsService statisticsService;
    private final AssetService assetService;
    private final AssetCriteriaService assetCriteriaService;

    public DashboardController(StatisticsService statisticsService, AssetService assetService,
                               AssetCriteriaService assetCriteriaService) {
        this.statisticsService = statisticsService;
        this.assetService = assetService;
        this.assetCriteriaService = assetCriteriaService;
    }

    @GetMapping("/")
    public String dashboard(Model model) {
        List<WealthPosition> positions = statisticsService.getAllPositions();
        BigDecimal total = positions.stream()
            .map(WealthPosition::getValue)
            .filter(Objects::nonNull)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        model.addAttribute("positions", positions);
        model.addAttribute("totalWealth", total);
        model.addAttribute("depotsByAsset", assetService.getDepotsByAssetId());
        model.addAttribute("assetPropertyBadges", assetCriteriaService.getPropertyBadgesByAssetId());
        model.addAttribute("accountPropertyBadges", assetCriteriaService.getPropertyBadgesByAccountId());
        return "index";
    }
}
