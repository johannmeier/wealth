package de.wsc.wealth.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class InfoController {

    @GetMapping("/info")
    public String info(Model model) {
        String version = InfoController.class.getPackage().getImplementationVersion();
        model.addAttribute("version", version != null ? version : "dev");
        model.addAttribute("configPath", System.getProperty("wealth.config.path", "-"));
        model.addAttribute("dbPath", System.getProperty("wealth.db.path", "-"));
        return "info";
    }
}
