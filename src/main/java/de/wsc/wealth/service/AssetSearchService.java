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

    public Map<String, String> getQuoteDetails(String symbol) {
        try {
            String json = restClient.get()
                .uri("https://query1.finance.yahoo.com/v8/finance/chart/{symbol}?range=1d&interval=1d", symbol)
                .retrieve()
                .body(String.class);
            JsonNode meta = objectMapper.readTree(json).path("chart").path("result").get(0).path("meta");
            return Map.of("currency", meta.path("currency").asText(""));
        } catch (Exception e) {
            log.warn("Quote details fetch failed for '{}': {}", symbol, e.getMessage());
            return Map.of();
        }
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
                String exchange = q.path("exchange").asText("");
                String currency = q.path("currency").asText("");
                if (currency.isBlank()) currency = currencyForExchange(exchange);
                r.put("name", q.path("longname").asText(q.path("shortname").asText("")));
                r.put("symbol", q.path("symbol").asText(""));
                r.put("exchange", exchange);
                r.put("currency", currency);
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

    private String currencyForExchange(String exchange) {
        return switch (exchange) {
            case "GER", "FRA", "HAM", "HAN", "MUN", "STU", "DUS", "BER",
                 "PAR", "AMS", "MIL", "MCE", "ATH", "HEL", "LIS", "VIE" -> "EUR";
            case "LSE", "IOB"                                              -> "GBP";
            case "TOR"                                                     -> "CAD";
            case "ASX"                                                     -> "AUD";
            case "TSE", "TYO"                                              -> "JPY";
            case "HKG"                                                     -> "HKD";
            case "SHH", "SHZ"                                              -> "CNY";
            case "BSE", "NSI"                                              -> "INR";
            case "SAO"                                                     -> "BRL";
            default                                                        -> "USD";
        };
    }

    private String mapCategory(String quoteType) {
        return switch (quoteType) {
            case "ETF", "MUTUALFUND", "EQUITY" -> "BOERSENGEHANDELT";
            default -> "SONSTIGE";
        };
    }
}
