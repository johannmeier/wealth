package de.wsc.wealth.service;

import de.wsc.wealth.repository.AssetRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class ExchangeRateService {

    private static final Logger log = LoggerFactory.getLogger(ExchangeRateService.class);

    private final AssetRepository assetRepository;
    private final PriceService priceService;
    private final Map<String, BigDecimal> rateCache = new ConcurrentHashMap<>();

    public ExchangeRateService(AssetRepository assetRepository, PriceService priceService) {
        this.assetRepository = assetRepository;
        this.priceService = priceService;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Scheduled(cron = "0 30 18 * * MON-FRI")
    public void refresh() {
        Set<String> currencies = assetRepository.findAll().stream()
            .map(a -> a.getCurrency())
            .filter(c -> c != null && !"EUR".equalsIgnoreCase(c))
            .collect(Collectors.toSet());

        for (String currency : currencies) {
            try {
                BigDecimal rate = priceService.fetchPrice(currency + "EUR=X");
                rateCache.put(currency.toUpperCase(), rate);
                log.debug("EUR rate for {}: {}", currency, rate);
            } catch (Exception e) {
                log.warn("Failed to fetch EUR rate for {}: {}", currency, e.getMessage());
            }
        }
    }

    public BigDecimal toEur(BigDecimal price, String currency) {
        if (price == null) return null;
        if (currency == null || "EUR".equalsIgnoreCase(currency)) return price;
        BigDecimal rate = rateCache.get(currency.toUpperCase());
        return rate != null ? price.multiply(rate).setScale(4, RoundingMode.HALF_UP) : null;
    }

    public Map<String, BigDecimal> getRates() {
        return Collections.unmodifiableMap(rateCache);
    }
}
