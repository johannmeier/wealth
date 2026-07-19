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

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
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
    void seedSystemCriteria_whenNotSeeded_createsWittmannOnly() {
        when(definitionRepository.findBySystemCode(any())).thenReturn(Optional.empty());
        when(definitionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(optionRepository.findByDefinitionAndSystemCode(any(), any())).thenReturn(Optional.empty());

        migrationService.seedSystemCriteria();

        verify(definitionRepository, times(1))
            .save(argThat(d -> SystemCriteria.WITTMANN.equals(d.getSystemCode())));
        verify(optionRepository, times(5)).save(any());
    }

    @Test
    void seedSystemCriteria_whenAlreadySeeded_doesNotDuplicate() {
        CriteriaDefinition existing = new CriteriaDefinition();
        existing.setSystemCode(SystemCriteria.WITTMANN);
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
        verifyNoInteractions(valueRepository, definitionRepository);
    }

    @Test
    void backfillAssetCriteriaValues_createsUserCriteriaAndValuesFromLegacyColumns() {
        when(jdbcTemplate.queryForObject(any(String.class), eq(Integer.class))).thenReturn(1);
        when(definitionRepository.findAllByOrderBySortOrderAsc()).thenReturn(Collections.emptyList());
        when(definitionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(optionRepository.findByDefinitionOrderBySortOrderAsc(any())).thenReturn(Collections.emptyList());
        when(optionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

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

        migrationService.backfillAssetCriteriaValues();

        // the five legacy criteria are created as plain user criteria without a systemCode
        verify(definitionRepository, times(5)).save(argThat(d -> d.getSystemCode() == null));
        verify(optionRepository, never()).save(argThat(o -> o.getSystemCode() != null));
        // category + type + allocation + index (4 values); distribution is null -> skipped
        verify(valueRepository, times(4)).save(any());
    }

    @Test
    void backfillAssetCriteriaValues_skipsAssetWithExistingValue() {
        when(jdbcTemplate.queryForObject(any(String.class), eq(Integer.class))).thenReturn(1);
        when(definitionRepository.findAllByOrderBySortOrderAsc()).thenReturn(Collections.emptyList());
        when(definitionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(optionRepository.findByDefinitionOrderBySortOrderAsc(any())).thenReturn(Collections.emptyList());
        when(optionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

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

    @Test
    void clearLegacySystemCodes_clearsAllButWittmann() {
        CriteriaDefinition legacy = new CriteriaDefinition();
        legacy.setSystemCode("CATEGORY");
        CriteriaDefinition wittmann = new CriteriaDefinition();
        wittmann.setSystemCode(SystemCriteria.WITTMANN);
        CriteriaDefinition custom = new CriteriaDefinition();
        when(definitionRepository.findAllByOrderBySortOrderAsc()).thenReturn(List.of(legacy, wittmann, custom));
        CriteriaOption legacyOption = new CriteriaOption();
        legacyOption.setSystemCode("AKTIE");
        when(optionRepository.findByDefinitionOrderBySortOrderAsc(legacy)).thenReturn(List.of(legacyOption));

        migrationService.clearLegacySystemCodes();

        assertThat(legacy.getSystemCode()).isNull();
        assertThat(legacyOption.getSystemCode()).isNull();
        assertThat(wittmann.getSystemCode()).isEqualTo(SystemCriteria.WITTMANN);
        verify(definitionRepository).save(legacy);
        verify(optionRepository).save(legacyOption);
        verify(definitionRepository, never()).save(wittmann);
        verify(definitionRepository, never()).save(custom);
    }
}
