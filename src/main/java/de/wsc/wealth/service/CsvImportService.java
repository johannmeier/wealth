package de.wsc.wealth.service;

import de.wsc.wealth.domain.*;
import de.wsc.wealth.dto.ChangedPosition;
import de.wsc.wealth.repository.AssetQuantityRepository;
import de.wsc.wealth.repository.AssetRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;

@Service
public class CsvImportService {

    private static final Logger log = LoggerFactory.getLogger(CsvImportService.class);

    public record ImportResult(List<ChangedPosition> changed, List<String> newAssets, List<String> skipped) {
        public int positionsUpdated() { return changed.size(); }
    }

    private final AssetRepository assetRepository;
    private final AssetQuantityRepository quantityRepository;
    private final AssetSearchService assetSearchService;

    public CsvImportService(AssetRepository assetRepository,
                            AssetQuantityRepository quantityRepository,
                            AssetSearchService assetSearchService) {
        this.assetRepository = assetRepository;
        this.quantityRepository = quantityRepository;
        this.assetSearchService = assetSearchService;
    }

    public ImportResult importDkb(InputStream in, Depot depot) throws IOException {
        // Comma-separated, UTF-8, quoted fields
        // Header: Datum der Erstellung,Depotnummer,Wertpapierbezeichnung,WKN,ISIN,Einstiegskurs,Bewertungskurs,Stückzahl,...
        Map<String, BigDecimal> positions = new LinkedHashMap<>();
        List<String> skipped = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String headerLine = reader.readLine();
            if (headerLine == null) return new ImportResult(Collections.emptyList(), Collections.emptyList(), Collections.emptyList());

            List<String> headers = parseCsvLine(headerLine, ',');
            int isinIdx = headers.indexOf("ISIN");
            int qtyIdx  = headers.indexOf("Stückzahl");
            if (isinIdx < 0 || qtyIdx < 0) {
                throw new IllegalArgumentException("DKB CSV: Spalten ISIN oder Stückzahl nicht gefunden.");
            }

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;
                List<String> fields = parseCsvLine(line, ',');
                if (fields.size() <= Math.max(isinIdx, qtyIdx)) continue;
                String isin = fields.get(isinIdx).trim();
                String qtyRaw = fields.get(qtyIdx).trim();
                if (isin.isBlank() || qtyRaw.isBlank()) continue;
                try {
                    positions.put(isin, parseGermanNumber(qtyRaw));
                } catch (NumberFormatException e) {
                    skipped.add(isin + " (ungültige Menge: " + qtyRaw + ")");
                }
            }
        }
        return persist(positions, depot, skipped);
    }

    public ImportResult importFdb(InputStream in, Depot depot) throws IOException {
        // Semicolon-separated, ISO-8859-1
        // Header: Wertpapier;ISIN;WKN;KAG;Produkt;Unterkontonummer;akt. Preis;Datum;Dev. Kurs;Stück;...
        Map<String, BigDecimal> positions = new LinkedHashMap<>();
        List<String> skipped = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(in, Charset.forName("ISO-8859-1")))) {
            String headerLine = reader.readLine();
            if (headerLine == null) return new ImportResult(Collections.emptyList(), Collections.emptyList(), Collections.emptyList());

            List<String> headers = parseCsvLine(headerLine, ';');
            int isinIdx = headers.indexOf("ISIN");
            // "Stück" may contain umlauts differently depending on encoding — match by prefix
            int qtyIdx = -1;
            for (int i = 0; i < headers.size(); i++) {
                if (headers.get(i).trim().toLowerCase().startsWith("st")) {
                    // match "Stück" / "Stueck" / "St?ck"
                    String h = headers.get(i).trim();
                    if (h.length() >= 2 && h.charAt(0) == 'S' && h.charAt(1) == 't') {
                        qtyIdx = i;
                        break;
                    }
                }
            }
            if (isinIdx < 0 || qtyIdx < 0) {
                throw new IllegalArgumentException("FondsDepotBank CSV: Spalten ISIN oder Stück nicht gefunden.");
            }

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;
                List<String> fields = parseCsvLine(line, ';');
                if (fields.size() <= Math.max(isinIdx, qtyIdx)) continue;
                String isin = fields.get(isinIdx).trim();
                String qtyRaw = fields.get(qtyIdx).trim();
                // FDB encodes quantity like "188,201 Stück" — strip trailing unit
                qtyRaw = qtyRaw.replaceAll("[^0-9.,].*", "").trim();
                if (isin.isBlank() || qtyRaw.isBlank()) continue;
                try {
                    positions.put(isin, parseGermanNumber(qtyRaw));
                } catch (NumberFormatException e) {
                    skipped.add(isin + " (ungültige Menge: " + qtyRaw + ")");
                }
            }
        }
        return persist(positions, depot, skipped);
    }

    private ImportResult persist(Map<String, BigDecimal> positions, Depot depot, List<String> skipped) {
        LocalDate today = LocalDate.now();
        List<ChangedPosition> changed = new ArrayList<>();
        List<String> newAssets = new ArrayList<>();

        for (Map.Entry<String, BigDecimal> entry : positions.entrySet()) {
            String isin = entry.getKey();
            BigDecimal newQty = entry.getValue();

            Asset asset = assetRepository.findFirstByIsinAndArchivedFalse(isin)
                .or(() -> assetRepository.findFirstByArchivedTrueAndIsin(isin))
                .orElseGet(() -> createAsset(isin, newAssets));

            BigDecimal oldQty = quantityRepository
                .findFirstByAssetAndDepotOrderByDateDesc(asset, depot)
                .map(AssetQuantity::getQuantity)
                .orElse(null);

            if (oldQty == null || oldQty.compareTo(newQty) != 0) {
                AssetQuantity q = quantityRepository.findByAssetAndDepotAndDate(asset, depot, today)
                    .orElseGet(() -> {
                        AssetQuantity aq = new AssetQuantity();
                        aq.setAsset(asset);
                        aq.setDepot(depot);
                        aq.setDate(today);
                        return aq;
                    });
                q.setQuantity(newQty);
                quantityRepository.save(q);
                changed.add(new ChangedPosition(asset.getName(), isin != null ? isin : asset.getSymbol(), oldQty, newQty));
            }
        }
        return new ImportResult(changed, newAssets, skipped);
    }

    private Asset createAsset(String isin, List<String> newAssets) {
        Asset a = new Asset();
        a.setIsin(isin);
        try {
            var results = assetSearchService.search(isin, "EUR");
            if (!results.isEmpty()) {
                var r = results.get(0);
                a.setName(r.getOrDefault("name", isin));
                String sym = r.get("symbol");
                if (sym != null && !sym.isBlank()) a.setSymbol(sym);
                a.setCurrency(r.getOrDefault("currency", "EUR"));
                try { a.setType(AssetType.valueOf(r.getOrDefault("type", "AKTIE"))); }
                catch (IllegalArgumentException e) { a.setType(AssetType.AKTIE); }
                try { a.setCategory(AssetCategory.valueOf(r.getOrDefault("category", "BOERSENGEHANDELT"))); }
                catch (IllegalArgumentException e) { a.setCategory(AssetCategory.BOERSENGEHANDELT); }
                try { a.setAssetAllocation(AssetAllocation.valueOf(r.getOrDefault("assetAllocation", "RISIKOBEHAFTET"))); }
                catch (IllegalArgumentException e) { a.setAssetAllocation(AssetAllocation.RISIKOBEHAFTET); }
                String dp = r.get("distributionPolicy");
                if (dp != null) {
                    try { a.setDistributionPolicy(DistributionPolicy.valueOf(dp)); }
                    catch (IllegalArgumentException ignored) {}
                }
            } else {
                a.setName(isin);
                a.setCurrency("EUR");
                a.setType(AssetType.AKTIE);
                a.setCategory(AssetCategory.BOERSENGEHANDELT);
                a.setAssetAllocation(AssetAllocation.RISIKOBEHAFTET);
            }
        } catch (Exception e) {
            log.warn("Yahoo Finance Suche fehlgeschlagen für ISIN {}: {}", isin, e.getMessage());
            a.setName(isin);
            a.setCurrency("EUR");
            a.setType(AssetType.AKTIE);
            a.setCategory(AssetCategory.BOERSENGEHANDELT);
            a.setAssetAllocation(AssetAllocation.RISIKOBEHAFTET);
        }
        assetRepository.save(a);
        newAssets.add(isin);
        log.info("Neues Wertpapier aus CSV angelegt: {}", isin);
        return a;
    }

    // Parses a single CSV line respecting double-quoted fields.
    private List<String> parseCsvLine(String line, char delimiter) {
        List<String> fields = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == delimiter && !inQuotes) {
                fields.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        fields.add(current.toString());
        return fields;
    }

    // German number format: dot = thousands separator, comma = decimal separator.
    private BigDecimal parseGermanNumber(String s) {
        String clean = s.trim()
            .replaceAll("[^0-9,.]", "")  // strip currency symbols, spaces, %
            .replace(".", "")             // remove thousands dots
            .replace(",", ".");           // decimal comma → dot
        if (clean.isEmpty()) throw new NumberFormatException("Leerer Wert");
        return new BigDecimal(clean);
    }
}
