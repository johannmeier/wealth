package de.wsc.wealth.service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import de.wsc.wealth.domain.PriceHistory;
import de.wsc.wealth.domain.Asset;
import de.wsc.wealth.repository.PriceHistoryRepository;
import de.wsc.wealth.repository.AssetRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class PriceService {

    private static final Logger log = LoggerFactory.getLogger(PriceService.class);
    private static final String YAHOO_URL = "https://query1.finance.yahoo.com/v8/finance/chart/{symbol}?range=1d&interval=1d";
    private static final String YAHOO_HISTORY_URL = "https://query1.finance.yahoo.com/v8/finance/chart/{symbol}?period1={p1}&period2={p2}&interval=1d";

    private final AssetRepository assetRepository;
    private final PriceHistoryRepository priceHistoryRepository;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public PriceService(AssetRepository assetRepository,
                        PriceHistoryRepository priceHistoryRepository,
                        ObjectMapper objectMapper) {
        this.assetRepository = assetRepository;
        this.priceHistoryRepository = priceHistoryRepository;
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder()
            .defaultHeader("User-Agent", "Mozilla/5.0")
            .build();
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(1)
    @Scheduled(cron = "0 0 18 * * *")
    public void updatePrices() {
        List<Asset> candidates = assetRepository.findByArchivedFalseAndSymbolIsNotNull();
        List<Asset> assets = candidates.stream()
            .filter(Asset::isAutoPrice)
            .toList();
        log.info("Updating prices for {} assets", assets.size());
        assets.forEach(this::updatePrice);
    }

    @Transactional
    public void updatePrice(Asset asset) {
        try {
            BigDecimal price = fetchPrice(asset.getSymbol());
            asset.setCurrentPrice(price);
            asset.setLastPriceUpdate(LocalDateTime.now());
            assetRepository.save(asset);

            LocalDate today = LocalDate.now();
            PriceHistory entry = priceHistoryRepository.findByAssetAndDate(asset, today)
                .orElseGet(() -> {
                    PriceHistory h = new PriceHistory();
                    h.setAsset(asset);
                    h.setDate(today);
                    return h;
                });
            entry.setPrice(price);
            entry.setCurrency(asset.getCurrency());
            priceHistoryRepository.save(entry);

            log.debug("Updated price for {}: {}", asset.getName(), price);
        } catch (Exception e) {
            log.warn("Failed to update price for {}: {}", asset.getName(), e.getMessage());
        }
    }

    public BigDecimal fetchPrice(String symbol) {
        String json = restClient.get()
            .uri(YAHOO_URL, symbol)
            .retrieve()
            .body(String.class);
        JsonNode result = objectMapper.readTree(json).path("chart").path("result");
        if (!result.isArray() || result.isEmpty()) {
            throw new IllegalStateException("Kein Ergebnis von Yahoo Finance für Symbol: " + symbol);
        }
        JsonNode priceNode = result.get(0).path("meta").path("regularMarketPrice");
        if (!priceNode.isNumber()) {
            throw new IllegalStateException("Kein gültiger Kurs von Yahoo Finance für Symbol: " + symbol);
        }
        return priceNode.decimalValue();
    }

    // Returns the first available closing price on or after the given date (up to 7 days ahead,
    // to skip weekends and public holidays).
    public BigDecimal fetchHistoricalPrice(String symbol, LocalDate date) {
        long p1 = date.atStartOfDay(ZoneOffset.UTC).toEpochSecond();
        long p2 = date.plusDays(7).atStartOfDay(ZoneOffset.UTC).toEpochSecond();
        String json = restClient.get()
            .uri(YAHOO_HISTORY_URL, symbol, p1, p2)
            .retrieve()
            .body(String.class);
        JsonNode result = objectMapper.readTree(json).path("chart").path("result");
        if (!result.isArray() || result.isEmpty()) {
            return null;
        }
        JsonNode closes = result.get(0)
            .path("indicators").path("quote").get(0)
            .path("close");
        for (JsonNode c : closes) {
            if (!c.isNull() && c.decimalValue().compareTo(BigDecimal.ZERO) > 0) {
                return c.decimalValue();
            }
        }
        return null;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(2)
    @Transactional
    public void backfillMissingMonthlyHistory() {
        LocalDate currentMonth = LocalDate.now().withDayOfMonth(1);
        List<Asset> allAssets = assetRepository.findAll();
        for (Asset asset : allAssets) {
            if (!asset.isAutoPrice()) continue;
            List<PriceHistory> history = priceHistoryRepository.findByAssetOrderByDateAsc(asset);
            if (history.isEmpty()) continue;

            Set<YearMonth> existing = history.stream()
                .map(h -> YearMonth.from(h.getDate()))
                .collect(Collectors.toSet());

            LocalDate start = history.get(0).getDate().withDayOfMonth(1);
            for (LocalDate month = start; month.isBefore(currentMonth); month = month.plusMonths(1)) {
                if (existing.contains(YearMonth.from(month))) continue;
                try {
                    BigDecimal price = fetchHistoricalPrice(asset.getSymbol(), month);
                    if (price == null) continue;
                    PriceHistory h = new PriceHistory();
                    h.setAsset(asset);
                    h.setDate(month);
                    h.setPrice(price);
                    h.setCurrency(asset.getCurrency());
                    h.setMonthly(true);
                    priceHistoryRepository.save(h);
                    log.info("Backfilled price history for {} at {}", asset.getName(), month);
                } catch (Exception e) {
                    log.warn("Could not backfill price for {} at {}: {}", asset.getName(), month, e.getMessage());
                }
            }
        }
    }

    @Scheduled(cron = "0 0 8 1 * *")
    @Transactional
    public void saveMonthlyHistory() {
        LocalDate today = LocalDate.now();
        List<Asset> allAssets = assetRepository.findAll();
        for (Asset asset : allAssets) {
            if (asset.getCurrentPrice() == null) continue;
            if (priceHistoryRepository.existsByAssetAndDate(asset, today)) continue;
            BigDecimal price = asset.getCurrentPrice();
            if (asset.isAutoPrice()) {
                try {
                    BigDecimal fetched = fetchHistoricalPrice(asset.getSymbol(), today);
                    if (fetched != null) price = fetched;
                } catch (Exception e) {
                    log.warn("Could not fetch monthly price for {}, using currentPrice: {}", asset.getName(), e.getMessage());
                }
            }
            PriceHistory h = new PriceHistory();
            h.setAsset(asset);
            h.setDate(today);
            h.setPrice(price);
            h.setCurrency(asset.getCurrency());
            h.setMonthly(true);
            priceHistoryRepository.save(h);
        }
        log.info("Saved monthly price history for {}", today);
    }
}
