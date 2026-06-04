package de.wsc.wealth.controller;

import de.wsc.wealth.dto.StatisticsGroup;
import de.wsc.wealth.service.StatisticsService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

@Controller
@RequestMapping("/statistics")
public class StatisticsController {

    private final StatisticsService statisticsService;

    public StatisticsController(StatisticsService statisticsService) {
        this.statisticsService = statisticsService;
    }

    @GetMapping("/overview")
    public String overview(Model model) {
        List<StatisticsGroup> groups = statisticsService.getStatsByAllocation();
        model.addAttribute("groups", groups);
        model.addAttribute("totalWealth", sumGroupTotals(groups));
        return "statistics/overview";
    }

    @GetMapping("/by-index")
    public String byIndex(Model model) {
        List<StatisticsGroup> groups = statisticsService.getStatsByIndex();
        model.addAttribute("groups", groups);
        model.addAttribute("totalWealth", sumGroupTotals(groups));
        return "statistics/by-index";
    }

    @GetMapping("/by-type")
    public String byType(Model model) {
        List<StatisticsGroup> groups = statisticsService.getStatsByType();
        model.addAttribute("groups", groups);
        model.addAttribute("totalWealth", sumGroupTotals(groups));
        return "statistics/by-type";
    }

    @GetMapping("/by-allocation")
    public String byAllocation(Model model) {
        List<StatisticsGroup> groups = statisticsService.getStatsByAllocation();
        model.addAttribute("groups", groups);
        model.addAttribute("totalWealth", sumGroupTotals(groups));
        return "statistics/by-allocation";
    }

    @GetMapping("/history")
    public String history(Model model) {
        model.addAttribute("history", statisticsService.getWealthHistory());
        return "statistics/history";
    }

    private BigDecimal sumGroupTotals(List<StatisticsGroup> groups) {
        return groups.stream()
            .map(StatisticsGroup::getTotalValue)
            .filter(Objects::nonNull)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
