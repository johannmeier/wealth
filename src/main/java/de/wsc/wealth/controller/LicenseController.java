package de.wsc.wealth.controller;

import de.wsc.wealth.license.LicenseService;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

@Controller
@RequestMapping("/settings/license")
public class LicenseController {

    private final LicenseService licenseService;
    private final ApplicationContext applicationContext;

    public LicenseController(LicenseService licenseService, ApplicationContext applicationContext) {
        this.licenseService = licenseService;
        this.applicationContext = applicationContext;
    }

    @GetMapping
    public String show(Model model) {
        model.addAttribute("valid", licenseService.isValid());
        model.addAttribute("expired", licenseService.isExpired());
        model.addAttribute("features", licenseService.getFeatures());
        model.addAttribute("expiresOn", licenseService.getExpiresOn());
        return "settings/license";
    }

    @PostMapping("/save")
    public String save(@RequestParam String licenseKey, Model model, RedirectAttributes ra) {
        String trimmed = licenseKey.strip();
        if (!trimmed.isEmpty() && licenseService.parse(trimmed).isEmpty()) {
            ra.addFlashAttribute("error", "Ungültiger Lizenzschlüssel.");
            return "redirect:/settings/license";
        }

        String configPath = System.getProperty("wealth.config.path");
        Properties config = readConfig(configPath);
        if (trimmed.isEmpty()) {
            config.remove("license.key");
        } else {
            config.setProperty("license.key", trimmed);
        }
        writeConfig(configPath, config);
        scheduleShutdown();
        return "settings/license-shutdown";
    }

    private Properties readConfig(String configPath) {
        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(Path.of(configPath))) {
            props.load(in);
        } catch (IOException e) {
            return props;
        }
        return props;
    }

    private void writeConfig(String configPath, Properties props) {
        try (OutputStream out = Files.newOutputStream(Path.of(configPath))) {
            props.store(out, "Wealth Konfiguration - nicht manuell bearbeiten");
        } catch (IOException e) {
            throw new RuntimeException("Fehler beim Schreiben der Konfiguration", e);
        }
    }

    private void scheduleShutdown() {
        Thread t = new Thread(() -> {
            try { Thread.sleep(800); } catch (InterruptedException ignored) {}
            System.exit(SpringApplication.exit(applicationContext, () -> 0));
        });
        t.setDaemon(true);
        t.start();
    }
}
