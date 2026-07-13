package de.wsc.wealth.service;

import de.wsc.wealth.domain.*;
import de.wsc.wealth.repository.AssetCriteriaValueRepository;
import de.wsc.wealth.repository.AssetRepository;
import de.wsc.wealth.repository.CriteriaDefinitionRepository;
import de.wsc.wealth.repository.CriteriaOptionRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * One-time (idempotent, re-run-safe) migration from the legacy hardcoded Asset classification
 * columns (category/type/assetAllocation/distributionPolicy/indexName) to the generic
 * CriteriaDefinition/CriteriaOption/AssetCriteriaValue model. Runs on every startup; each step
 * checks for existing data before writing so re-runs are a fast no-op.
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
        seedDefinition(SystemCriteria.CATEGORY, "Kategorie", CriteriaValueType.FIXED_LIST, 0, List.of(
            new String[]{"BOERSENGEHANDELT", "Börsengehandelt"},
            new String[]{"EDELMETALL", "Edelmetall"},
            new String[]{"SONSTIGE", "Sonstige"}
        ));
        seedDefinition(SystemCriteria.TYPE, "Wertpapierart", CriteriaValueType.FIXED_LIST, 1, List.of(
            new String[]{"AKTIE", "Aktie"},
            new String[]{"AKTIENFONDS", "Aktienfonds"},
            new String[]{"ETF", "ETF"},
            new String[]{"ANLEIHE", "Anleihe"},
            new String[]{"WAEHRUNG", "Währung"},
            new String[]{"EDELMETALL", "Edelmetall"},
            new String[]{"KRYPTO", "Krypto"},
            new String[]{"SONSTIGE", "Sonstige"}
        ));
        seedDefinition(SystemCriteria.ASSET_ALLOCATION, "Allocation", CriteriaValueType.FIXED_LIST, 2, List.of(
            new String[]{"RISIKOBEHAFTET", "Risikobehaftet"},
            new String[]{"RISIKOFREI", "Risikofrei"}
        ));
        seedDefinition(SystemCriteria.DISTRIBUTION_POLICY, "Ausschüttung", CriteriaValueType.FIXED_LIST, 3, List.of(
            new String[]{"AUSSCHUETTEND", "Ausschüttend"},
            new String[]{"THESAURIEREND", "Thesaurierend"}
        ));
        seedDefinition(SystemCriteria.INDEX_NAME, "Index", CriteriaValueType.FREE_TEXT, 4, Collections.emptyList());
    }

    @Transactional
    public void backfillAssetCriteriaValues() {
        if (!legacyColumnsExist()) return;

        CriteriaDefinition category = definitionRepository.findBySystemCode(SystemCriteria.CATEGORY).orElseThrow();
        CriteriaDefinition type = definitionRepository.findBySystemCode(SystemCriteria.TYPE).orElseThrow();
        CriteriaDefinition allocation = definitionRepository.findBySystemCode(SystemCriteria.ASSET_ALLOCATION).orElseThrow();
        CriteriaDefinition distribution = definitionRepository.findBySystemCode(SystemCriteria.DISTRIBUTION_POLICY).orElseThrow();
        CriteriaDefinition index = definitionRepository.findBySystemCode(SystemCriteria.INDEX_NAME).orElseThrow();

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

    private void backfillOptionValue(Asset asset, CriteriaDefinition definition, String rawSystemCode) {
        if (rawSystemCode == null || rawSystemCode.isBlank()) return;
        if (valueRepository.existsByAssetAndDefinition(asset, definition)) return;
        optionRepository.findByDefinitionAndSystemCode(definition, rawSystemCode).ifPresent(option -> {
            AssetCriteriaValue value = new AssetCriteriaValue();
            value.setAsset(asset);
            value.setDefinition(definition);
            value.setOption(option);
            valueRepository.save(value);
        });
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
