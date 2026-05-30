package de.wsc.wealth.controller;

import de.wsc.wealth.service.StatisticsService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/statistics")
public class StatisticsController {

    private final StatisticsService statisticsService;

    public StatisticsController(StatisticsService statisticsService) {
        this.statisticsService = statisticsService;
    }

    @GetMapping("/overview")
    public String overview(Model model) {
        model.addAttribute("positions", statisticsService.getAllPositions());
        model.addAttribute("totalWealth", statisticsService.getTotalWealth());
        return "statistics/overview";
    }

    @GetMapping("/by-index")
    public String byIndex(Model model) {
        model.addAttribute("groups", statisticsService.getStatsByIndex());
        model.addAttribute("totalWealth", statisticsService.getTotalWealth());
        return "statistics/by-index";
    }

    @GetMapping("/by-type")
    public String byType(Model model) {
        model.addAttribute("groups", statisticsService.getStatsByType());
        model.addAttribute("totalWealth", statisticsService.getTotalWealth());
        return "statistics/by-type";
    }

    @GetMapping("/by-allocation")
    public String byAllocation(Model model) {
        model.addAttribute("groups", statisticsService.getStatsByAllocation());
        model.addAttribute("totalWealth", statisticsService.getTotalWealth());
        return "statistics/by-allocation";
    }
}
