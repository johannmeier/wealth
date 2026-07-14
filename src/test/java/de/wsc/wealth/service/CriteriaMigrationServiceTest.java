package de.wsc.wealth.service;

import de.wsc.wealth.domain.*;
import de.wsc.wealth.repository.AssetCriteriaValueRepository;
import de.wsc.wealth.repository.AssetRepository;
import de.wsc.wealth.repository.CriteriaDefinitionRepository;
import de.wsc.wealth.repository.CriteriaOptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CriteriaMigrationServiceTest {

    @Mock private CriteriaDefinitionRepository definitionRepository;
    @Mock private CriteriaOptionRepository optionRepository;
    @Mock private AssetCriteriaValueRepository valueRepository;
    @Mock private AssetRepository assetRepository;
    @Mock private JdbcTemplate jdbcTemplate;

    private CriteriaMigrationService migrationService;

    @BeforeEach
    void setUp() {
        migrationService = new CriteriaMigrationService(
            definitionRepository, optionRepository, valueRepository, assetRepository, jdbcTemplate);
    }

    @Test
    void seedSystemCriteria_whenNotSeeded_createsAllSixDefinitions() {
        when(definitionRepository.findBySystemCode(any())).thenReturn(Optional.empty());
        when(definitionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(optionRepository.findByDefinitionAndSystemCode(any(), any())).thenReturn(Optional.empty());

        migrationService.seedSystemCriteria();

        verify(definitionRepository, times(6)).save(any());
        // 3 category + 8 type + 2 allocation + 2 distribution + 0 index + 5 wittmann options = 20
        verify(optionRepository, times(20)).save(any());
    }

    @Test
    void seedSystemCriteria_whenAlreadySeeded_doesNotDuplicate() {
        CriteriaDefinition existing = new CriteriaDefinition();
        existing.setSystemCode(SystemCriteria.CATEGORY);
        when(definitionRepository.findBySystemCode(any())).thenReturn(Optional.of(existing));
        CriteriaOption existingOption = new CriteriaOption();
        when(optionRepository.findByDefinitionAndSystemCode(any(), any())).thenReturn(Optional.of(existingOption));

        migrationService.seedSystemCriteria();

        verify(definitionRepository, never()).save(any());
        verify(optionRepository, never()).save(any());
    }

    @Test
    void backfillAssetCriteriaValues_whenLegacyColumnsAlreadyDropped_doesNothing() {
        when(jdbcTemplate.queryForObject(any(String.class), eq(Integer.class))).thenReturn(0);

        migrationService.backfillAssetCriteriaValues();

        verify(jdbcTemplate, never()).queryForList(any(String.class));
        verifyNoInteractions(valueRepository);
    }

    @Test
    void backfillAssetCriteriaValues_createsValuesFromLegacyColumns() {
        when(jdbcTemplate.queryForObject(any(String.class), eq(Integer.class))).thenReturn(1);

        CriteriaDefinition category = definition(SystemCriteria.CATEGORY);
        CriteriaDefinition type = definition(SystemCriteria.TYPE);
        CriteriaDefinition allocation = definition(SystemCriteria.ASSET_ALLOCATION);
        CriteriaDefinition distribution = definition(SystemCriteria.DISTRIBUTION_POLICY);
        CriteriaDefinition index = definition(SystemCriteria.INDEX_NAME);
        when(definitionRepository.findBySystemCode(SystemCriteria.CATEGORY)).thenReturn(Optional.of(category));
        when(definitionRepository.findBySystemCode(SystemCriteria.TYPE)).thenReturn(Optional.of(type));
        when(definitionRepository.findBySystemCode(SystemCriteria.ASSET_ALLOCATION)).thenReturn(Optional.of(allocation));
        when(definitionRepository.findBySystemCode(SystemCriteria.DISTRIBUTION_POLICY)).thenReturn(Optional.of(distribution));
        when(definitionRepository.findBySystemCode(SystemCriteria.INDEX_NAME)).thenReturn(Optional.of(index));

        Map<String, Object> row = new HashMap<>();
        row.put("ID", 1L);
        row.put("CATEGORY", "BOERSENGEHANDELT");
        row.put("TYPE", "ETF");
        row.put("ASSET_ALLOCATION", "RISIKOBEHAFTET");
        row.put("DISTRIBUTION_POLICY", null);
        row.put("INDEX_NAME", "MSCI World");
        when(jdbcTemplate.queryForList(any(String.class))).thenReturn(List.of(row));

        Asset asset = new Asset();
        asset.setId(1L);
        when(assetRepository.findById(1L)).thenReturn(Optional.of(asset));
        when(valueRepository.existsByAssetAndDefinition(any(), any())).thenReturn(false);
        CriteriaOption categoryOption = new CriteriaOption();
        when(optionRepository.findByDefinitionAndSystemCode(category, "BOERSENGEHANDELT")).thenReturn(Optional.of(categoryOption));
        CriteriaOption typeOption = new CriteriaOption();
        when(optionRepository.findByDefinitionAndSystemCode(type, "ETF")).thenReturn(Optional.of(typeOption));
        CriteriaOption allocationOption = new CriteriaOption();
        when(optionRepository.findByDefinitionAndSystemCode(allocation, "RISIKOBEHAFTET")).thenReturn(Optional.of(allocationOption));

        migrationService.backfillAssetCriteriaValues();

        // category + type + allocation + index (4 values); distribution is null -> skipped
        verify(valueRepository, times(4)).save(any());
    }

    @Test
    void backfillAssetCriteriaValues_skipsAssetWithExistingValue() {
        when(jdbcTemplate.queryForObject(any(String.class), eq(Integer.class))).thenReturn(1);

        CriteriaDefinition category = definition(SystemCriteria.CATEGORY);
        when(definitionRepository.findBySystemCode(any())).thenReturn(Optional.of(category));

        Map<String, Object> row = new HashMap<>();
        row.put("ID", 1L);
        row.put("CATEGORY", "BOERSENGEHANDELT");
        row.put("TYPE", null);
        row.put("ASSET_ALLOCATION", null);
        row.put("DISTRIBUTION_POLICY", null);
        row.put("INDEX_NAME", null);
        when(jdbcTemplate.queryForList(any(String.class))).thenReturn(List.of(row));

        Asset asset = new Asset();
        asset.setId(1L);
        when(assetRepository.findById(1L)).thenReturn(Optional.of(asset));
        when(valueRepository.existsByAssetAndDefinition(any(), any())).thenReturn(true);

        migrationService.backfillAssetCriteriaValues();

        verify(valueRepository, never()).save(any());
    }

    private CriteriaDefinition definition(String systemCode) {
        CriteriaDefinition definition = new CriteriaDefinition();
        definition.setSystemCode(systemCode);
        return definition;
    }
}
