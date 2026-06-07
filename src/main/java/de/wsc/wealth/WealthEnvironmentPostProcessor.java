package de.wsc.wealth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;
import java.util.Scanner;

public class WealthEnvironmentPostProcessor implements EnvironmentPostProcessor {

    private static final String CONFIG_FILE = "wealth-config.properties";
    private static final String DB_NAME_KEY = "db.name";
    private static final String DB_PATH_KEY = "db.path";

    private static Path appDataDir() {
        String os = System.getProperty("os.name", "").toLowerCase();
        Path base;
        if (os.contains("win")) {
            String appdata = System.getenv("APPDATA");
            base = appdata != null ? Path.of(appdata) : Path.of(System.getProperty("user.home"), "AppData", "Roaming");
        } else if (os.contains("mac")) {
            base = Path.of(System.getProperty("user.home"), "Library", "Application Support");
        } else {
            String xdg = System.getenv("XDG_CONFIG_HOME");
            base = xdg != null && !xdg.isBlank() ? Path.of(xdg) : Path.of(System.getProperty("user.home"), ".config");
        }
        return base.resolve("wealth");
    }

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Path dir = appDataDir();
        Path configPath = dir.resolve(CONFIG_FILE);
        String dbName;

        Properties config;
        if (Files.exists(configPath)) {
            config = readConfig(configPath);
        } else {
            dbName = promptForDbName();
            config = new Properties();
            config.setProperty(DB_NAME_KEY, dbName);
            saveConfig(configPath, config);
        }

        System.setProperty("wealth.config.path", configPath.toAbsolutePath().toString());

        String customPath = config.getProperty(DB_PATH_KEY);
        if (customPath != null && !customPath.isBlank()) {
            // User-specified absolute path
            Path dbFile = Path.of(customPath.strip());
            System.setProperty("wealth.db.path", dbFile.toAbsolutePath() + ".mv.db");
            String urlPath = dbFile.toString().replace("\\", "/");
            environment.getPropertySources().addFirst(
                new MapPropertySource("wealthConfig",
                    Map.of("spring.datasource.url", "jdbc:h2:file:" + urlPath + ";AUTO_SERVER=TRUE")));
        } else {
            dbName = config.getProperty(DB_NAME_KEY);
            if (dbName != null && !dbName.isBlank()) {
                Path dbFile = dir.resolve("wealth-db-" + dbName);
                System.setProperty("wealth.db.path", dbFile.toAbsolutePath() + ".mv.db");
                String urlPath = dbFile.toString().replace("\\", "/");
                environment.getPropertySources().addFirst(
                    new MapPropertySource("wealthConfig",
                        Map.of("spring.datasource.url", "jdbc:h2:file:" + urlPath + ";AUTO_SERVER=TRUE")));
            }
        }
    }

    private Properties readConfig(Path configPath) {
        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(configPath)) {
            props.load(in);
        } catch (IOException e) {
            throw new RuntimeException("Fehler beim Lesen von " + CONFIG_FILE, e);
        }
        return props;
    }

    private String promptForDbName() {
        String input = GraphicsEnvironment.isHeadless() ? promptConsole() : promptDialog();
        String sanitized = input.toLowerCase().replaceAll("[^a-z0-9]", "-")
                .replaceAll("-+", "-").replaceAll("(^-+|-+$)", "");
        return sanitized.isEmpty() ? "default" : sanitized;
    }

    private String promptConsole() {
        System.out.println();
        System.out.println("=== Wealth - Erste Einrichtung ===");
        System.out.print("Bitte geben Sie Ihren Namen für die Datenbank an (z.B. 'wolfgang'): ");
        System.out.flush();
        try {
            return new Scanner(System.in).nextLine().trim();
        } catch (Exception e) {
            return "default";
        }
    }

    private String promptDialog() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}

        String input = JOptionPane.showInputDialog(
                null,
                "Bitte geben Sie Ihren Namen für die Datenbank an:",
                "Wealth - Erste Einrichtung",
                JOptionPane.QUESTION_MESSAGE
        );
        return (input != null) ? input.trim() : "default";
    }

    private void saveConfig(Path configPath, Properties props) {
        try {
            Files.createDirectories(configPath.getParent());
        } catch (IOException e) {
            throw new RuntimeException("Verzeichnis konnte nicht erstellt werden: " + configPath.getParent(), e);
        }
        try (OutputStream out = Files.newOutputStream(configPath)) {
            props.store(out, "Wealth Konfiguration - nicht manuell bearbeiten");
        } catch (IOException e) {
            throw new RuntimeException("Fehler beim Schreiben von " + CONFIG_FILE, e);
        }
    }
}
