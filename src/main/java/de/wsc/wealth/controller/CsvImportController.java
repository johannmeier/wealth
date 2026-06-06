package de.wsc.wealth.controller;

import de.wsc.wealth.domain.Depot;
import de.wsc.wealth.service.CsvImportService;
import de.wsc.wealth.service.CsvImportService.ImportResult;
import de.wsc.wealth.service.DepotService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/depots/{id}/import")
public class CsvImportController {

    private final DepotService depotService;
    private final CsvImportService csvImportService;

    public CsvImportController(DepotService depotService, CsvImportService csvImportService) {
        this.depotService = depotService;
        this.csvImportService = csvImportService;
    }

    @GetMapping
    public String page(@PathVariable Long id, Model model) {
        Depot depot = depotService.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        model.addAttribute("depot", depot);
        model.addAttribute("suggestedFormat", detectFormat(depot));
        return "depots/import";
    }

    private String detectFormat(Depot depot) {
        String name = (depot.getName() + " " +
                       (depot.getBank() != null ? depot.getBank().getName() : "")).toLowerCase();
        if (name.contains("dkb") || name.contains("deutsche kreditbank")) return "dkb";
        if (name.contains("fondsdepot") || name.contains("fonds depot") || name.contains("fdb")) return "fdb";
        return null;
    }

    @PostMapping
    public String doImport(@PathVariable Long id,
                           @RequestParam String format,
                           @RequestParam MultipartFile file,
                           RedirectAttributes ra) {
        Depot depot = depotService.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        try {
            ImportResult result = switch (format) {
                case "dkb" -> csvImportService.importDkb(file.getInputStream(), depot);
                case "fdb" -> csvImportService.importFdb(file.getInputStream(), depot);
                default    -> throw new IllegalArgumentException("Unbekanntes Format: " + format);
            };
            ra.addFlashAttribute("importResult", result);
        } catch (Exception e) {
            ra.addFlashAttribute("importError", e.getMessage());
        }
        return "redirect:/depots/" + id + "/import";
    }
}
