package de.wsc.wealth.controller;

import de.wsc.wealth.service.StatisticsService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class DashboardController {

    private final StatisticsService statisticsService;

    public DashboardController(StatisticsService statisticsService) {
        this.statisticsService = statisticsService;
    }

    @GetMapping("/")
    public String dashboard(Model model) {
        var positions = statisticsService.getAllPositions();
        model.addAttribute("positions", positions);
        model.addAttribute("totalWealth", statisticsService.getTotalWealth());
        return "index";
    }
}
