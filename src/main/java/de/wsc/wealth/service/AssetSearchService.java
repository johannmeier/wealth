package de.wsc.wealth.service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class AssetSearchService {

    private static final Logger log = LoggerFactory.getLogger(AssetSearchService.class);
    private static final String SEARCH_URL = "https://query2.finance.yahoo.com/v1/finance/search?q={q}&quotesCount=15&newsCount=0&enableFuzzyQuery=false";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public AssetSearchService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder()
            .defaultHeader("User-Agent", "Mozilla/5.0")
            .build();
    }

    public List<Map<String, String>> search(String query) {
        try {
            String json = restClient.get()
                .uri(SEARCH_URL, query)
                .retrieve()
                .body(String.class);

            JsonNode quotes = objectMapper.readTree(json).path("quotes");
            List<Map<String, String>> results = new ArrayList<>();

            for (JsonNode q : quotes) {
                String quoteType = q.path("quoteType").asText("");
                if (quoteType.isBlank() || quoteType.equals("OPTION") || quoteType.equals("FUTURE")) continue;

                Map<String, String> r = new HashMap<>();
                r.put("name", q.path("longname").asText(q.path("shortname").asText("")));
                r.put("symbol", q.path("symbol").asText(""));
                r.put("exchange", q.path("exchange").asText(""));
                r.put("currency", q.path("currency").asText(""));
                r.put("type", mapType(quoteType));
                r.put("category", mapCategory(quoteType));
                results.add(r);
            }
            return results;
        } catch (Exception e) {
            log.warn("Asset search failed for '{}': {}", query, e.getMessage());
            return List.of();
        }
    }

    private String mapType(String quoteType) {
        return switch (quoteType) {
            case "ETF"        -> "ETF";
            case "MUTUALFUND" -> "AKTIENFONDS";
            case "EQUITY"     -> "AKTIE";
            case "CURRENCY"   -> "WAEHRUNG";
            default           -> "SONSTIGE";
        };
    }

    private String mapCategory(String quoteType) {
        return switch (quoteType) {
            case "ETF", "MUTUALFUND", "EQUITY" -> "BOERSENGEHANDELT";
            default -> "SONSTIGE";
        };
    }
}
