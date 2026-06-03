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
import java.util.List;

@Service
public class PriceService {

    private static final Logger log = LoggerFactory.getLogger(PriceService.class);
    private static final String YAHOO_URL = "https://query1.finance.yahoo.com/v8/finance/chart/{symbol}?range=1d&interval=1d";

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
