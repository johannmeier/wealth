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
        String currentName = readDbName(configPath);

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
        return "settings/databases";
    }

    @PostMapping("/switch")
    public String switchDb(@RequestParam String dbName, Model model) {
        writeDbName(dbName);
        scheduleShutdown();
        model.addAttribute("newDb", dbName);
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

    private String readDbName(String configPath) {
        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(Path.of(configPath))) {
            props.load(in);
        } catch (IOException e) {
            return "?";
        }
        return props.getProperty("db.name", "?");
    }

    private void writeDbName(String dbName) {
        String configPath = System.getProperty("wealth.config.path");
        Properties props = new Properties();
        props.setProperty("db.name", dbName);
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
