package de.wsc.wealth.service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class UpdateCheckService {

    private static final Logger log = LoggerFactory.getLogger(UpdateCheckService.class);
    private static final String LATEST_RELEASE_URL =
        "https://api.github.com/repos/johannmeier/wealth/releases/latest";

    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    private volatile String latestVersion;
    private volatile String releaseUrl;

    public UpdateCheckService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder()
            .defaultHeader("User-Agent", "Wealth-App")
            .defaultHeader("Accept", "application/vnd.github+json")
            .build();
    }

    @EventListener(ApplicationReadyEvent.class)
    @Scheduled(cron = "0 0 9 * * *")
    public void refresh() {
        try {
            String json = restClient.get().uri(LATEST_RELEASE_URL).retrieve().body(String.class);
            JsonNode node = objectMapper.readTree(json);
            String tag = node.path("tag_name").asString("");
            if (tag.isBlank()) return;
            latestVersion = tag.startsWith("v") ? tag.substring(1) : tag;
            releaseUrl = node.path("html_url").asString(null);
            log.debug("Latest release: {}", latestVersion);
        } catch (Exception e) {
            log.warn("Failed to check for updates: {}", e.getMessage());
        }
    }

    /**
     * The version baked into the JAR manifest by Spring Boot's repackage plugin. Null when run
     * unpackaged (e.g. {@code mvn spring-boot:run}), in which case update checks are skipped.
     */
    public String getCurrentVersion() {
        String version = getClass().getPackage().getImplementationVersion();
        return version != null ? version : "dev";
    }

    public String getLatestVersion() {
        return latestVersion;
    }

    public String getReleaseUrl() {
        return releaseUrl;
    }

    public boolean isUpdateAvailable() {
        String current = getCurrentVersion();
        if (latestVersion == null || "dev".equals(current) || current.contains("SNAPSHOT")) return false;
        return isNewer(latestVersion, current);
    }

    // Compares dotted numeric version strings (e.g. "1.0.12" vs "1.0.9") component by component.
    static boolean isNewer(String latest, String current) {
        String[] a = latest.split("\\.");
        String[] b = current.split("\\.");
        int len = Math.max(a.length, b.length);
        for (int i = 0; i < len; i++) {
            int av = i < a.length ? leadingInt(a[i]) : 0;
            int bv = i < b.length ? leadingInt(b[i]) : 0;
            if (av != bv) return av > bv;
        }
        return false;
    }

    private static int leadingInt(String s) {
        int end = 0;
        while (end < s.length() && Character.isDigit(s.charAt(end))) end++;
        if (end == 0) return 0;
        try {
            return Integer.parseInt(s.substring(0, end));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
