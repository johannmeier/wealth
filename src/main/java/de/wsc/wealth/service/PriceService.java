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
    @Scheduled(cron = "0 0 18 * * *")
    @Transactional
    public void updatePrices() {
        List<Asset> assets = assetRepository.findByArchivedFalseAndSymbolIsNotNull().stream()
            .filter(s -> s.isAutoPrice() && !s.getSymbol().isBlank())
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

            PriceHistory entry = new PriceHistory();
            entry.setAsset(asset);
            entry.setDate(LocalDate.now());
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
        JsonNode root = objectMapper.readTree(json);
        JsonNode meta = root.path("chart").path("result").get(0).path("meta");
        return meta.path("regularMarketPrice").decimalValue();
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
        JsonNode closes = objectMapper.readTree(json)
            .path("chart").path("result").get(0)
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
    @Transactional
    public void backfillMissingMonthlyHistory() {
        LocalDate currentMonth = LocalDate.now().withDayOfMonth(1);
        for (Asset asset : assetRepository.findAll()) {
            if (!asset.isAutoPrice() || asset.getSymbol() == null || asset.getSymbol().isBlank()) continue;
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
        assetRepository.findAll().stream()
            .filter(s -> s.getCurrentPrice() != null)
            .forEach(s -> {
                PriceHistory h = new PriceHistory();
                h.setAsset(s);
                h.setDate(today);
                h.setPrice(s.getCurrentPrice());
                h.setCurrency(s.getCurrency());
                priceHistoryRepository.save(h);
            });
        log.info("Saved monthly price history for {}", today);
    }
}
