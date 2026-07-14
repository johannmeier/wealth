package de.wsc.wealth.controller;

import de.wsc.wealth.domain.CriteriaDefinition;
import de.wsc.wealth.dto.StatisticsGroup;
import de.wsc.wealth.service.CriteriaService;
import de.wsc.wealth.service.StatisticsService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Collections;
import java.util.List;

@Controller
@RequestMapping("/statistics")
public class StatisticsController {

    private final StatisticsService statisticsService;
    private final CriteriaService criteriaService;

    public StatisticsController(StatisticsService statisticsService, CriteriaService criteriaService) {
        this.statisticsService = statisticsService;
        this.criteriaService = criteriaService;
    }

    @GetMapping("/by-criteria")
    public String byCriteria(@RequestParam(required = false) Long definitionId, Model model) {
        List<CriteriaDefinition> definitions = criteriaService.findAll();
        Long selectedId = definitionId != null ? definitionId
            : definitions.stream().map(CriteriaDefinition::getId).findFirst().orElse(null);
        CriteriaDefinition selectedDefinition = definitions.stream()
            .filter(d -> d.getId().equals(selectedId)).findFirst().orElse(null);

        // Guards against a definitionId param for a criterion the license (no longer) grants:
        // selectedDefinition would already be null in that case since it comes from the same
        // license-filtered list.
        List<StatisticsGroup> groups = selectedDefinition != null
            ? statisticsService.getStatsByCriteria(selectedId)
            : Collections.emptyList();

        model.addAttribute("selectedDefinitionId", selectedId);
        model.addAttribute("selectedDefinition", selectedDefinition);
        model.addAttribute("groups", groups);
        model.addAttribute("totalWealth", statisticsService.getTotalWealth());
        return "statistics/by-criteria";
    }

    @GetMapping("/history")
    public String history(Model model) {
        model.addAttribute("history", statisticsService.getWealthHistory());
        return "statistics/history";
    }
}
