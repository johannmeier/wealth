package de.wsc.wealth.service;

import de.wsc.wealth.domain.*;
import de.wsc.wealth.repository.AssetCriteriaValueRepository;
import de.wsc.wealth.repository.AssetRepository;
import de.wsc.wealth.repository.CriteriaDefinitionRepository;
import de.wsc.wealth.repository.CriteriaOptionRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * One-time (idempotent, re-run-safe) migrations for the criteria model, run on every startup:
 * seeds the Wittmann system criterion, migrates the legacy hardcoded Asset classification
 * columns (category/type/assetAllocation/distributionPolicy/indexName) into ordinary
 * user-managed criteria, and clears the systemCode of the formerly built-in criteria
 * (Kategorie, Wertpapierart, …) so they become fully user-managed (renamable, deletable).
 * Only Wittmann keeps its systemCode — it is license-gated and protected.
 */
@Service
public class CriteriaMigrationService {

    private final CriteriaDefinitionRepository definitionRepository;
    private final CriteriaOptionRepository optionRepository;
    private final AssetCriteriaValueRepository valueRepository;
    private final AssetRepository assetRepository;
    private final JdbcTemplate jdbcTemplate;

    public CriteriaMigrationService(CriteriaDefinitionRepository definitionRepository,
                                    CriteriaOptionRepository optionRepository,
                                    AssetCriteriaValueRepository valueRepository,
                                    AssetRepository assetRepository,
                                    JdbcTemplate jdbcTemplate) {
        this.definitionRepository = definitionRepository;
        this.optionRepository = optionRepository;
        this.valueRepository = valueRepository;
        this.assetRepository = assetRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public void seedSystemCriteria() {
        seedDefinition(SystemCriteria.WITTMANN, "Wittmann", CriteriaValueType.FIXED_LIST, 5, List.of(
            new String[]{"LIQUIDITAET_DEVISEN", "Liquidität/Devisen"},
            new String[]{"EDELMETALLE", "Edelmetalle"},
            new String[]{"UNTERNEHMEN", "Unternehmen"},
            new String[]{"IMMOBILIEN", "Immobilien"},
            new String[]{"SPEZIALANLAGEN", "Spezialanlagen"}
        ));
    }

    /**
     * Migrates databases that still carry the pre-criteria Asset columns: creates the five
     * classification criteria as ordinary user criteria (no systemCode) and copies each
     * asset's column values over. The legacy columns hold enum codes (e.g. AKTIE), which are
     * mapped to the human-readable option values created here.
     */
    @Transactional
    public void backfillAssetCriteriaValues() {
        if (!legacyColumnsExist()) return;

        Map<String, CriteriaOption> category = ensureUserDefinition("Kategorie", CriteriaValueType.FIXED_LIST, 0, List.of(
            new String[]{"BOERSENGEHANDELT", "Börsengehandelt"},
            new String[]{"EDELMETALL", "Edelmetall"},
            new String[]{"SONSTIGE", "Sonstige"}
        ));
        Map<String, CriteriaOption> type = ensureUserDefinition("Wertpapierart", CriteriaValueType.FIXED_LIST, 1, List.of(
            new String[]{"AKTIE", "Aktie"},
            new String[]{"AKTIENFONDS", "Aktienfonds"},
            new String[]{"ETF", "ETF"},
            new String[]{"ANLEIHE", "Anleihe"},
            new String[]{"WAEHRUNG", "Währung"},
            new String[]{"EDELMETALL", "Edelmetall"},
            new String[]{"KRYPTO", "Krypto"},
            new String[]{"SONSTIGE", "Sonstige"}
        ));
        Map<String, CriteriaOption> allocation = ensureUserDefinition("Allocation", CriteriaValueType.FIXED_LIST, 2, List.of(
            new String[]{"RISIKOBEHAFTET", "Risikobehaftet"},
            new String[]{"RISIKOFREI", "Risikofrei"}
        ));
        Map<String, CriteriaOption> distribution = ensureUserDefinition("Ausschüttung", CriteriaValueType.FIXED_LIST, 3, List.of(
            new String[]{"AUSSCHUETTEND", "Ausschüttend"},
            new String[]{"THESAURIEREND", "Thesaurierend"}
        ));
        CriteriaDefinition index = findOrCreateDefinition("Index", CriteriaValueType.FREE_TEXT, 4);

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            "SELECT ID, CATEGORY, TYPE, ASSET_ALLOCATION, DISTRIBUTION_POLICY, INDEX_NAME FROM ASSET");

        for (Map<String, Object> row : rows) {
            Long assetId = ((Number) row.get("ID")).longValue();
            Asset asset = assetRepository.findById(assetId).orElse(null);
            if (asset == null) continue;
            backfillOptionValue(asset, category, (String) row.get("CATEGORY"));
            backfillOptionValue(asset, type, (String) row.get("TYPE"));
            backfillOptionValue(asset, allocation, (String) row.get("ASSET_ALLOCATION"));
            backfillOptionValue(asset, distribution, (String) row.get("DISTRIBUTION_POLICY"));
            backfillFreeText(asset, index, (String) row.get("INDEX_NAME"));
        }
    }

    @Transactional
    public void dropLegacyAssetColumns() {
        jdbcTemplate.execute("ALTER TABLE ASSET DROP COLUMN IF EXISTS CATEGORY");
        jdbcTemplate.execute("ALTER TABLE ASSET DROP COLUMN IF EXISTS TYPE");
        jdbcTemplate.execute("ALTER TABLE ASSET DROP COLUMN IF EXISTS ASSET_ALLOCATION");
        jdbcTemplate.execute("ALTER TABLE ASSET DROP COLUMN IF EXISTS DISTRIBUTION_POLICY");
        jdbcTemplate.execute("ALTER TABLE ASSET DROP COLUMN IF EXISTS INDEX_NAME");
    }

    /**
     * Databases migrated by earlier versions carry systemCodes on the five formerly built-in
     * criteria and their options. Clearing them turns those criteria into ordinary user
     * criteria; only Wittmann stays protected.
     */
    @Transactional
    public void clearLegacySystemCodes() {
        for (CriteriaDefinition definition : definitionRepository.findAllByOrderBySortOrderAsc()) {
            if (definition.getSystemCode() == null || SystemCriteria.WITTMANN.equals(definition.getSystemCode())) {
                continue;
            }
            definition.setSystemCode(null);
            definitionRepository.save(definition);
            for (CriteriaOption option : optionRepository.findByDefinitionOrderBySortOrderAsc(definition)) {
                if (option.getSystemCode() != null) {
                    option.setSystemCode(null);
                    optionRepository.save(option);
                }
            }
        }
    }

    private void seedDefinition(String systemCode, String name, CriteriaValueType valueType,
                                int sortOrder, List<String[]> options) {
        CriteriaDefinition definition = definitionRepository.findBySystemCode(systemCode).orElseGet(() -> {
            CriteriaDefinition d = new CriteriaDefinition();
            d.setSystemCode(systemCode);
            d.setName(name);
            d.setValueType(valueType);
            d.setSortOrder(sortOrder);
            return definitionRepository.save(d);
        });

        int sort = 0;
        for (String[] option : options) {
            String optionCode = option[0];
            String label = option[1];
            if (optionRepository.findByDefinitionAndSystemCode(definition, optionCode).isEmpty()) {
                CriteriaOption o = new CriteriaOption();
                o.setDefinition(definition);
                o.setSystemCode(optionCode);
                o.setValue(label);
                o.setSortOrder(sort);
                optionRepository.save(o);
            }
            sort++;
        }
    }

    /**
     * Creates the definition and its options as plain user criteria if missing (matching by
     * name / option value so a crashed and re-run migration stays idempotent) and returns the
     * options keyed by their legacy enum code for the backfill.
     */
    private Map<String, CriteriaOption> ensureUserDefinition(String name, CriteriaValueType valueType,
                                                             int sortOrder, List<String[]> options) {
        CriteriaDefinition definition = findOrCreateDefinition(name, valueType, sortOrder);
        List<CriteriaOption> existing = optionRepository.findByDefinitionOrderBySortOrderAsc(definition);

        Map<String, CriteriaOption> byLegacyCode = new LinkedHashMap<>();
        int sort = 0;
        for (String[] option : options) {
            String legacyCode = option[0];
            String label = option[1];
            CriteriaOption resolved = existing.stream()
                .filter(o -> label.equals(o.getValue()))
                .findFirst()
                .orElseGet(() -> {
                    CriteriaOption o = new CriteriaOption();
                    o.setDefinition(definition);
                    o.setValue(label);
                    return o;
                });
            resolved.setSortOrder(sort++);
            byLegacyCode.put(legacyCode, optionRepository.save(resolved));
        }
        return byLegacyCode;
    }

    private CriteriaDefinition findOrCreateDefinition(String name, CriteriaValueType valueType, int sortOrder) {
        Optional<CriteriaDefinition> existing = definitionRepository.findAllByOrderBySortOrderAsc().stream()
            .filter(d -> name.equals(d.getName()))
            .findFirst();
        return existing.orElseGet(() -> {
            CriteriaDefinition d = new CriteriaDefinition();
            d.setName(name);
            d.setValueType(valueType);
            d.setSortOrder(sortOrder);
            return definitionRepository.save(d);
        });
    }

    private void backfillOptionValue(Asset asset, Map<String, CriteriaOption> optionsByLegacyCode, String rawCode) {
        if (rawCode == null || rawCode.isBlank()) return;
        CriteriaOption option = optionsByLegacyCode.get(rawCode);
        if (option == null) return;
        if (valueRepository.existsByAssetAndDefinition(asset, option.getDefinition())) return;
        AssetCriteriaValue value = new AssetCriteriaValue();
        value.setAsset(asset);
        value.setDefinition(option.getDefinition());
        value.setOption(option);
        valueRepository.save(value);
    }

    private void backfillFreeText(Asset asset, CriteriaDefinition definition, String text) {
        if (text == null || text.isBlank()) return;
        if (valueRepository.existsByAssetAndDefinition(asset, definition)) return;
        AssetCriteriaValue value = new AssetCriteriaValue();
        value.setAsset(asset);
        value.setDefinition(definition);
        value.setFreeTextValue(text);
        valueRepository.save(value);
    }

    private boolean legacyColumnsExist() {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'ASSET' AND COLUMN_NAME = 'CATEGORY'",
            Integer.class);
        return count != null && count > 0;
    }
}
