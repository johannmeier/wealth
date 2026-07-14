package de.wsc.wealth.controller;

import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.*;
import java.nio.file.*;
import java.util.*;

@Controller
@RequestMapping("/settings/databases")
public class DatabaseController {

    private final ApplicationContext applicationContext;

    public DatabaseController(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @GetMapping
    public String list(Model model) {
        String configPath = System.getProperty("wealth.config.path");
        Path dir = Path.of(configPath).getParent();
        Properties config = readConfig(configPath);
        String customPath = config.getProperty("db.path");
        String currentName = config.getProperty("db.name", "?");

        List<String> databases = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "wealth-db-*.mv.db")) {
            for (Path p : stream) {
                String filename = p.getFileName().toString();
                String name = filename.substring("wealth-db-".length(), filename.length() - ".mv.db".length());
                databases.add(name);
            }
        } catch (IOException ignored) {}
        Collections.sort(databases);

        model.addAttribute("databases", databases);
        model.addAttribute("currentDb", currentName);
        model.addAttribute("customDbPath", customPath);
        model.addAttribute("currentDbFile", System.getProperty("wealth.db.path"));
        return "settings/databases";
    }

    @PostMapping("/switch")
    public String switchDb(@RequestParam String dbName, Model model) {
        writeDbName(dbName);
        scheduleShutdown();
        model.addAttribute("newDb", dbName);
        return "settings/shutdown";
    }

    @PostMapping("/set-path")
    public String setCustomPath(@RequestParam String dbPath, Model model, RedirectAttributes ra) {
        String trimmed = dbPath.strip();
        if (trimmed.isEmpty()) {
            // Clear custom path → fall back to db.name
            String configPath = System.getProperty("wealth.config.path");
            Properties config = readConfig(configPath);
            config.remove("db.path");
            writeConfig(config);
            scheduleShutdown();
            model.addAttribute("newDb", config.getProperty("db.name", "standard"));
            return "settings/shutdown";
        }
        // Strip .mv.db suffix if user accidentally included it
        if (trimmed.endsWith(".mv.db")) trimmed = trimmed.substring(0, trimmed.length() - 6);

        String configPath = System.getProperty("wealth.config.path");
        Properties config = readConfig(configPath);
        config.setProperty("db.path", trimmed);
        config.remove("db.name");
        writeConfig(config);
        scheduleShutdown();
        model.addAttribute("newDb", Path.of(trimmed).getFileName().toString());
        return "settings/shutdown";
    }

    @PostMapping("/create")
    public String createDb(@RequestParam String name, Model model, RedirectAttributes ra) {
        String sanitized = name.toLowerCase().replaceAll("[^a-z0-9]", "-")
                .replaceAll("-+", "-").replaceAll("(^-+|-+$)", "");
        if (sanitized.isEmpty()) {
            ra.addFlashAttribute("error", "Ungültiger Name.");
            return "redirect:/settings/databases";
        }
        String configPath = System.getProperty("wealth.config.path");
        Path dbFile = Path.of(configPath).getParent().resolve("wealth-db-" + sanitized + ".mv.db");
        if (Files.exists(dbFile)) {
            ra.addFlashAttribute("error", "Eine Datenbank mit dem Namen '" + sanitized + "' existiert bereits.");
            return "redirect:/settings/databases";
        }
        writeDbName(sanitized);
        scheduleShutdown();
        model.addAttribute("newDb", sanitized);
        return "settings/shutdown";
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

    private void writeConfig(Properties props) {
        String configPath = System.getProperty("wealth.config.path");
        try (OutputStream out = Files.newOutputStream(Path.of(configPath))) {
            props.store(out, "Wealth Konfiguration - nicht manuell bearbeiten");
        } catch (IOException e) {
            throw new RuntimeException("Fehler beim Schreiben der Konfiguration", e);
        }
    }

    private void writeDbName(String dbName) {
        String configPath = System.getProperty("wealth.config.path");
        Properties props = readConfig(configPath);
        props.setProperty("db.name", dbName);
        props.remove("db.path");
        writeConfig(props);
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
