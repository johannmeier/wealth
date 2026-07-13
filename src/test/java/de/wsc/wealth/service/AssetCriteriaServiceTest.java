package de.wsc.wealth.service;

import de.wsc.wealth.domain.*;
import de.wsc.wealth.dto.AssetCriteriaSnapshot;
import de.wsc.wealth.repository.AssetCriteriaValueRepository;
import de.wsc.wealth.repository.CriteriaDefinitionRepository;
import de.wsc.wealth.repository.CriteriaOptionRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AssetCriteriaServiceTest {

    @Mock private CriteriaDefinitionRepository definitionRepository;
    @Mock private CriteriaOptionRepository optionRepository;
    @Mock private AssetCriteriaValueRepository valueRepository;

    private AssetCriteriaService assetCriteriaService;

    @BeforeEach
    void setUp() {
        assetCriteriaService = new AssetCriteriaService(definitionRepository, optionRepository, valueRepository);
    }

    @Test
    void isAutoPrice_withCategoryBoersengehandelt_returnsTrue() {
        Asset asset = asset(1L);
        when(valueRepository.findAllWithAssetAndDefinitionAndOption())
            .thenReturn(List.of(valueFor(asset, SystemCriteria.CATEGORY, "BOERSENGEHANDELT", "Börsengehandelt")));

        assertThat(assetCriteriaService.isAutoPrice(asset)).isTrue();
    }

    @Test
    void isAutoPrice_withCategoryEdelmetall_returnsTrue() {
        Asset asset = asset(1L);
        when(valueRepository.findAllWithAssetAndDefinitionAndOption())
            .thenReturn(List.of(valueFor(asset, SystemCriteria.CATEGORY, "EDELMETALL", "Edelmetall")));

        assertThat(assetCriteriaService.isAutoPrice(asset)).isTrue();
    }

    @Test
    void isAutoPrice_withTypeKrypto_returnsTrue() {
        Asset asset = asset(1L);
        when(valueRepository.findAllWithAssetAndDefinitionAndOption())
            .thenReturn(List.of(valueFor(asset, SystemCriteria.TYPE, "KRYPTO", "Krypto")));

        assertThat(assetCriteriaService.isAutoPrice(asset)).isTrue();
    }

    @Test
    void isAutoPrice_withNeitherCategoryNorType_returnsFalse() {
        Asset asset = asset(1L);
        when(valueRepository.findAllWithAssetAndDefinitionAndOption())
            .thenReturn(List.of(valueFor(asset, SystemCriteria.CATEGORY, "SONSTIGE", "Sonstige")));

        assertThat(assetCriteriaService.isAutoPrice(asset)).isFalse();
    }

    @Test
    void isAutoPrice_withNoValuesAtAll_returnsFalse() {
        Asset asset = asset(1L);
        when(valueRepository.findAllWithAssetAndDefinitionAndOption()).thenReturn(Collections.emptyList());

        assertThat(assetCriteriaService.isAutoPrice(asset)).isFalse();
    }

    @Test
    void getValuesByAssetId_returnsOptionValueForFixedListCriterion() {
        CriteriaDefinition definition = definition(null, CriteriaValueType.FIXED_LIST);
        definition.setId(30L);
        Asset asset = asset(1L);
        AssetCriteriaValue value = new AssetCriteriaValue();
        value.setAsset(asset);
        value.setDefinition(definition);
        value.setOption(option(definition, null, "Deutschland"));
        when(valueRepository.findAllWithAssetAndDefinitionAndOption()).thenReturn(List.of(value));

        Map<Long, String> result = assetCriteriaService.getValuesByAssetId(30L);

        assertThat(result).containsEntry(1L, "Deutschland");
    }

    @Test
    void getValuesByAssetId_returnsFreeTextForFreeTextCriterion() {
        CriteriaDefinition definition = definition(null, CriteriaValueType.FREE_TEXT);
        definition.setId(31L);
        Asset asset = asset(1L);
        AssetCriteriaValue value = new AssetCriteriaValue();
        value.setAsset(asset);
        value.setDefinition(definition);
        value.setFreeTextValue("MSCI World");
        when(valueRepository.findAllWithAssetAndDefinitionAndOption()).thenReturn(List.of(value));

        Map<Long, String> result = assetCriteriaService.getValuesByAssetId(31L);

        assertThat(result).containsEntry(1L, "MSCI World");
    }

    @Test
    void getValuesByAssetId_ignoresValuesForOtherDefinitions() {
        CriteriaDefinition definition = definition(null, CriteriaValueType.FREE_TEXT);
        definition.setId(31L);
        Asset asset = asset(1L);
        AssetCriteriaValue value = new AssetCriteriaValue();
        value.setAsset(asset);
        value.setDefinition(definition);
        value.setFreeTextValue("MSCI World");
        when(valueRepository.findAllWithAssetAndDefinitionAndOption()).thenReturn(List.of(value));

        Map<Long, String> result = assetCriteriaService.getValuesByAssetId(999L);

        assertThat(result).isEmpty();
    }

    @Test
    void getSnapshotsByAssetId_ignoresValuesForCustomCriteria() {
        Asset asset = asset(1L);
        CriteriaDefinition customDefinition = definition(null, CriteriaValueType.FREE_TEXT);
        AssetCriteriaValue customValue = new AssetCriteriaValue();
        customValue.setAsset(asset);
        customValue.setDefinition(customDefinition);
        customValue.setFreeTextValue("Deutschland");
        when(valueRepository.findAllWithAssetAndDefinitionAndOption()).thenReturn(List.of(customValue));

        Map<Long, AssetCriteriaSnapshot> snapshots = assetCriteriaService.getSnapshotsByAssetId();

        assertThat(snapshots).isEmpty();
    }

    @Test
    void assignSystemValueOrDefault_usesRawValueWhenValidOption() {
        Asset asset = asset(1L);
        CriteriaDefinition definition = definition(SystemCriteria.CATEGORY, CriteriaValueType.FIXED_LIST);
        CriteriaOption option = option(definition, "EDELMETALL", "Edelmetall");
        when(definitionRepository.findBySystemCode(SystemCriteria.CATEGORY)).thenReturn(Optional.of(definition));
        when(optionRepository.findByDefinitionAndSystemCode(definition, "EDELMETALL")).thenReturn(Optional.of(option));
        when(valueRepository.findByAssetAndDefinition(asset, definition)).thenReturn(Optional.empty());

        assetCriteriaService.assignSystemValueOrDefault(asset, SystemCriteria.CATEGORY, "EDELMETALL", "BOERSENGEHANDELT");

        ArgumentCaptor<AssetCriteriaValue> captor = ArgumentCaptor.forClass(AssetCriteriaValue.class);
        verify(valueRepository).save(captor.capture());
        assertThat(captor.getValue().getOption()).isSameAs(option);
    }

    @Test
    void assignSystemValueOrDefault_fallsBackToDefaultWhenRawInvalid() {
        Asset asset = asset(1L);
        CriteriaDefinition definition = definition(SystemCriteria.CATEGORY, CriteriaValueType.FIXED_LIST);
        CriteriaOption fallback = option(definition, "BOERSENGEHANDELT", "Börsengehandelt");
        when(definitionRepository.findBySystemCode(SystemCriteria.CATEGORY)).thenReturn(Optional.of(definition));
        when(optionRepository.findByDefinitionAndSystemCode(definition, "UNKNOWN")).thenReturn(Optional.empty());
        when(optionRepository.findByDefinitionAndSystemCode(definition, "BOERSENGEHANDELT")).thenReturn(Optional.of(fallback));
        when(valueRepository.findByAssetAndDefinition(asset, definition)).thenReturn(Optional.empty());

        assetCriteriaService.assignSystemValueOrDefault(asset, SystemCriteria.CATEGORY, "UNKNOWN", "BOERSENGEHANDELT");

        ArgumentCaptor<AssetCriteriaValue> captor = ArgumentCaptor.forClass(AssetCriteriaValue.class);
        verify(valueRepository).save(captor.capture());
        assertThat(captor.getValue().getOption()).isSameAs(fallback);
    }

    @Test
    void assignSystemValueOrDefault_withNeitherRawNorDefaultValid_leavesValueUnset() {
        Asset asset = asset(1L);
        CriteriaDefinition definition = definition(SystemCriteria.DISTRIBUTION_POLICY, CriteriaValueType.FIXED_LIST);
        when(definitionRepository.findBySystemCode(SystemCriteria.DISTRIBUTION_POLICY)).thenReturn(Optional.of(definition));

        assetCriteriaService.assignSystemValueOrDefault(asset, SystemCriteria.DISTRIBUTION_POLICY, null, null);

        verify(valueRepository, never()).save(any());
    }

    @Test
    void saveAssignments_fixedListWithSelectedOption_createsValue() {
        Asset asset = asset(1L);
        CriteriaDefinition definition = definition(SystemCriteria.CATEGORY, CriteriaValueType.FIXED_LIST);
        definition.setId(10L);
        CriteriaOption option = option(definition, "EDELMETALL", "Edelmetall");
        option.setId(20L);
        when(definitionRepository.findAllByOrderBySortOrderAsc()).thenReturn(List.of(definition));
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getParameter("crit_10")).thenReturn("20");
        when(optionRepository.findById(20L)).thenReturn(Optional.of(option));
        when(valueRepository.findByAssetAndDefinition(asset, definition)).thenReturn(Optional.empty());

        assetCriteriaService.saveAssignments(asset, request);

        ArgumentCaptor<AssetCriteriaValue> captor = ArgumentCaptor.forClass(AssetCriteriaValue.class);
        verify(valueRepository).save(captor.capture());
        assertThat(captor.getValue().getOption()).isSameAs(option);
        assertThat(captor.getValue().getFreeTextValue()).isNull();
    }

    @Test
    void saveAssignments_freeTextWithValue_createsValue() {
        Asset asset = asset(1L);
        CriteriaDefinition definition = definition(null, CriteriaValueType.FREE_TEXT);
        definition.setId(11L);
        when(definitionRepository.findAllByOrderBySortOrderAsc()).thenReturn(List.of(definition));
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getParameter("crit_11")).thenReturn("Deutschland");
        when(valueRepository.findByAssetAndDefinition(asset, definition)).thenReturn(Optional.empty());

        assetCriteriaService.saveAssignments(asset, request);

        ArgumentCaptor<AssetCriteriaValue> captor = ArgumentCaptor.forClass(AssetCriteriaValue.class);
        verify(valueRepository).save(captor.capture());
        assertThat(captor.getValue().getFreeTextValue()).isEqualTo("Deutschland");
        assertThat(captor.getValue().getOption()).isNull();
    }

    @Test
    void saveAssignments_blankValue_deletesExistingAssignment() {
        Asset asset = asset(1L);
        CriteriaDefinition definition = definition(null, CriteriaValueType.FREE_TEXT);
        definition.setId(11L);
        when(definitionRepository.findAllByOrderBySortOrderAsc()).thenReturn(List.of(definition));
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getParameter("crit_11")).thenReturn("");
        AssetCriteriaValue existing = new AssetCriteriaValue();
        when(valueRepository.findByAssetAndDefinition(asset, definition)).thenReturn(Optional.of(existing));

        assetCriteriaService.saveAssignments(asset, request);

        verify(valueRepository).delete(existing);
        verify(valueRepository, never()).save(any());
    }

    private Asset asset(Long id) {
        Asset asset = new Asset();
        asset.setId(id);
        return asset;
    }

    private CriteriaDefinition definition(String systemCode, CriteriaValueType valueType) {
        CriteriaDefinition definition = new CriteriaDefinition();
        definition.setSystemCode(systemCode);
        definition.setValueType(valueType);
        definition.setName(systemCode != null ? systemCode : "Custom");
        return definition;
    }

    private CriteriaOption option(CriteriaDefinition definition, String systemCode, String value) {
        CriteriaOption option = new CriteriaOption();
        option.setDefinition(definition);
        option.setSystemCode(systemCode);
        option.setValue(value);
        return option;
    }

    private AssetCriteriaValue valueFor(Asset asset, String definitionSystemCode, String optionSystemCode, String optionValue) {
        CriteriaDefinition definition = definition(definitionSystemCode, CriteriaValueType.FIXED_LIST);
        CriteriaOption option = option(definition, optionSystemCode, optionValue);
        AssetCriteriaValue value = new AssetCriteriaValue();
        value.setAsset(asset);
        value.setDefinition(definition);
        value.setOption(option);
        return value;
    }
}
