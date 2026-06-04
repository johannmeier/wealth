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
    private static final String SEARCH_URL = "https://query2.finance.yahoo.com/v1/finance/search?q={q}&quotesCount=15&newsCount=0&enableFuzzyQuery=true";

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
            return Map.of("currency", meta.path("currency").asString(""));
        } catch (Exception e) {
            log.warn("Quote details fetch failed for '{}': {}", symbol, e.getMessage());
            return Map.of();
        }
    }

    public List<Map<String, String>> search(String query, String baseCurrency) {
        try {
            String json = restClient.get()
                .uri(SEARCH_URL, query)
                .retrieve()
                .body(String.class);

            JsonNode quotes = objectMapper.readTree(json).path("quotes");
            List<Map<String, String>> results = new ArrayList<>();

            for (JsonNode q : quotes) {
                String quoteType = q.path("quoteType").asString("");
                if (quoteType.isBlank() || quoteType.equals("OPTION") || quoteType.equals("FUTURE")) continue;

                Map<String, String> r = new HashMap<>();
                String exchange = q.path("exchange").asString("");
                String currency = q.path("currency").asString("");
                if (currency.isBlank()) currency = currencyForExchange(exchange);
                r.put("name", q.path("longname").asString(q.path("shortname").asString("")));
                r.put("symbol", q.path("symbol").asString(""));
                r.put("exchange", exchange);
                r.put("currency", currency);
                r.put("type", mapType(quoteType));
                r.put("category", mapCategory(quoteType));
                r.put("assetAllocation", mapAssetAllocation(r.get("name"), r.get("symbol"), quoteType, r.get("currency"), baseCurrency));
                String isin = q.path("isin").asString("");
                if (!isin.isBlank()) r.put("isin", isin);
                String dp = mapDistributionPolicy(r.get("name"), r.get("symbol"));
                if (dp != null) r.put("distributionPolicy", dp);
                results.add(r);
            }
            if (results.isEmpty()) {
                Map<String, String> direct = tryDirectSymbol(query, baseCurrency);
                if (!direct.isEmpty()) results.add(direct);
            }
            return results;
        } catch (Exception e) {
            log.warn("Asset search failed for '{}': {}", query, e.getMessage());
            // Fallback: try query as direct symbol
            Map<String, String> direct = tryDirectSymbol(query, baseCurrency);
            return direct.isEmpty() ? List.of() : List.of(direct);
        }
    }

    private Map<String, String> tryDirectSymbol(String symbol, String baseCurrency) {
        try {
            String json = restClient.get()
                .uri("https://query1.finance.yahoo.com/v8/finance/chart/{symbol}?range=1d&interval=1d", symbol)
                .retrieve().body(String.class);
            JsonNode result = objectMapper.readTree(json).path("chart").path("result");
            if (!result.isArray() || result.size() == 0) return Map.of();
            JsonNode meta = result.get(0).path("meta");
            String sym = meta.path("symbol").asString("");
            if (sym.isBlank()) return Map.of();
            String exchange = meta.path("exchangeName").asString("");
            String currency = meta.path("currency").asString("");
            Map<String, String> r = new HashMap<>();
            r.put("name", meta.path("shortName").asString(sym));
            r.put("symbol", sym);
            r.put("exchange", exchange);
            r.put("currency", currency.isBlank() ? currencyForExchange(exchange) : currency);
            r.put("type", "SONSTIGE");
            r.put("category", "BOERSENGEHANDELT");
            r.put("assetAllocation", mapAssetAllocation(r.get("name"), r.get("symbol"), "", r.get("currency"), baseCurrency));
            String dp = mapDistributionPolicy(r.get("name"), r.get("symbol"));
            if (dp != null) r.put("distributionPolicy", dp);
            return r;
        } catch (Exception e) {
            log.debug("Direct symbol lookup failed for '{}': {}", symbol, e.getMessage());
            return Map.of();
        }
    }

    private String mapType(String quoteType) {
        return switch (quoteType) {
            case "ETF"            -> "ETF";
            case "MUTUALFUND"     -> "AKTIENFONDS";
            case "EQUITY"         -> "AKTIE";
            case "CURRENCY"       -> "WAEHRUNG";
            case "CRYPTOCURRENCY" -> "KRYPTO";
            default               -> "SONSTIGE";
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

    private String mapAssetAllocation(String name, String symbol, String quoteType, String currency, String baseCurrency) {
        if ("EQUITY".equals(quoteType) || "CRYPTOCURRENCY".equals(quoteType)) return "RISIKOBEHAFTET";
        String combined = ((name != null ? name : "") + " " + (symbol != null ? symbol : "")).toUpperCase();
        boolean isBond = combined.contains("BOND") || combined.contains("ANLEIHE") || combined.contains("RENTEN")
                || combined.contains("FIXED INCOME") || combined.contains("TREASUR")
                || combined.contains("AGGREGATE") || combined.contains("SOVEREIGN") || combined.contains("GILT");
        if (isBond && baseCurrency.equals(currency)) return "RISIKOFREI";
        return "RISIKOBEHAFTET";
    }

    private String mapDistributionPolicy(String name, String symbol) {
        String combined = ((name != null ? name : "") + " " + (symbol != null ? symbol : "")).toUpperCase();
        if (combined.contains("ACC") || combined.contains("ACCUMUL") || combined.contains("THESAUR")) return "THESAURIEREND";
        if (combined.contains("DIST") || combined.contains("INCOME") || combined.contains("AUSSCH")) return "AUSSCHUETTEND";
        return null;
    }

    private String mapCategory(String quoteType) {
        return switch (quoteType) {
            case "ETF", "MUTUALFUND", "EQUITY", "CRYPTOCURRENCY" -> "BOERSENGEHANDELT";
            default -> "SONSTIGE";
        };
    }
}
