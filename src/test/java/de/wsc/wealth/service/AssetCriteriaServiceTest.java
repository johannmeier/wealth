package de.wsc.wealth.service;

import de.wsc.wealth.domain.*;
import de.wsc.wealth.dto.AssetCriteriaSnapshot;
import de.wsc.wealth.dto.CriteriaBadge;
import de.wsc.wealth.license.LicenseFeature;
import de.wsc.wealth.license.LicenseService;
import de.wsc.wealth.repository.AccountCriteriaValueRepository;
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
    @Mock private AccountCriteriaValueRepository accountValueRepository;
    @Mock private LicenseService licenseService;

    private AssetCriteriaService assetCriteriaService;

    @BeforeEach
    void setUp() {
        lenient().when(licenseService.isFeatureEnabled(LicenseFeature.CUSTOM_CRITERIA)).thenReturn(true);
        assetCriteriaService = new AssetCriteriaService(definitionRepository, optionRepository, valueRepository, accountValueRepository, licenseService);
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
    void getPropertyBadgesByAssetId_systemCriterionGetsMessageKey() {
        Asset asset = asset(1L);
        CriteriaDefinition definition = definition(SystemCriteria.CATEGORY, CriteriaValueType.FIXED_LIST);
        definition.setSortOrder(0);
        AssetCriteriaValue value = new AssetCriteriaValue();
        value.setAsset(asset);
        value.setDefinition(definition);
        value.setOption(option(definition, "EDELMETALL", "Edelmetall"));
        when(valueRepository.findAllWithAssetAndDefinitionAndOption()).thenReturn(List.of(value));

        List<CriteriaBadge> badges = assetCriteriaService.getPropertyBadgesByAssetId().get(1L);

        assertThat(badges).hasSize(1);
        assertThat(badges.get(0).getLabel()).isEqualTo("Edelmetall");
        assertThat(badges.get(0).getMessageKey()).isEqualTo("assetCategory.EDELMETALL");
        assertThat(badges.get(0).getTooltip()).isEqualTo(definition.getName());
    }

    @Test
    void getPropertyBadgesByAssetId_customCriterionHasNoMessageKey() {
        Asset asset = asset(1L);
        CriteriaDefinition definition = definition(null, CriteriaValueType.FREE_TEXT);
        definition.setSortOrder(0);
        AssetCriteriaValue value = new AssetCriteriaValue();
        value.setAsset(asset);
        value.setDefinition(definition);
        value.setFreeTextValue("Deutschland");
        when(valueRepository.findAllWithAssetAndDefinitionAndOption()).thenReturn(List.of(value));

        List<CriteriaBadge> badges = assetCriteriaService.getPropertyBadgesByAssetId().get(1L);

        assertThat(badges).hasSize(1);
        assertThat(badges.get(0).getLabel()).isEqualTo("Deutschland");
        assertThat(badges.get(0).getMessageKey()).isNull();
    }

    @Test
    void getPropertyBadgesByAssetId_whenCustomCriteriaNotLicensed_returnsEmptyWithoutQuerying() {
        // The badge display is gated as a whole, not just its custom-criteria part — even a
        // system-criterion value must disappear when unlicensed, so the method short-circuits
        // before ever reading AssetCriteriaValue rows.
        when(licenseService.isFeatureEnabled(LicenseFeature.CUSTOM_CRITERIA)).thenReturn(false);

        Map<Long, List<CriteriaBadge>> result = assetCriteriaService.getPropertyBadgesByAssetId();

        assertThat(result).isEmpty();
        verifyNoInteractions(valueRepository);
    }

    @Test
    void getPropertyBadgesByAccountId_whenCustomCriteriaNotLicensed_returnsEmpty() {
        when(licenseService.isFeatureEnabled(LicenseFeature.CUSTOM_CRITERIA)).thenReturn(false);

        Map<Long, List<CriteriaBadge>> result = assetCriteriaService.getPropertyBadgesByAccountId();

        assertThat(result).isEmpty();
        verifyNoInteractions(accountValueRepository);
    }

    @Test
    void findAllActive_whenCustomCriteriaNotLicensed_returnsEmpty() {
        // Must be fully empty, not just "system only": saveAssignments(Asset, request) iterates
        // this same list, so any definition it skips here is also skipped there — a definition
        // that appeared here but whose form field was never rendered would read as blank and
        // delete the asset's existing assignment for it, which the license gate must never do.
        when(licenseService.isFeatureEnabled(LicenseFeature.CUSTOM_CRITERIA)).thenReturn(false);

        List<CriteriaDefinition> result = assetCriteriaService.findAllActive();

        assertThat(result).isEmpty();
        verifyNoInteractions(definitionRepository);
    }

    @Test
    void saveAssignments_asset_whenCustomCriteriaNotLicensed_leavesExistingAssignmentsUntouched() {
        when(licenseService.isFeatureEnabled(LicenseFeature.CUSTOM_CRITERIA)).thenReturn(false);
        Asset asset = asset(1L);
        HttpServletRequest request = mock(HttpServletRequest.class);

        assetCriteriaService.saveAssignments(asset, request);

        verifyNoInteractions(definitionRepository, valueRepository, optionRepository);
    }

    @Test
    void getPropertyBadgesByAssetId_excludesIndexCriterion() {
        Asset asset = asset(1L);
        CriteriaDefinition definition = definition(SystemCriteria.INDEX_NAME, CriteriaValueType.FREE_TEXT);
        AssetCriteriaValue value = new AssetCriteriaValue();
        value.setAsset(asset);
        value.setDefinition(definition);
        value.setFreeTextValue("MSCI World");
        when(valueRepository.findAllWithAssetAndDefinitionAndOption()).thenReturn(List.of(value));

        Map<Long, List<CriteriaBadge>> result = assetCriteriaService.getPropertyBadgesByAssetId();

        assertThat(result).isEmpty();
    }

    @Test
    void getPropertyBadgesByAssetId_sortsByDefinitionSortOrder() {
        Asset asset = asset(1L);
        CriteriaDefinition first = definition(null, CriteriaValueType.FREE_TEXT);
        first.setSortOrder(0);
        first.setName("Länder");
        AssetCriteriaValue firstValue = new AssetCriteriaValue();
        firstValue.setAsset(asset);
        firstValue.setDefinition(first);
        firstValue.setFreeTextValue("Deutschland");

        CriteriaDefinition second = definition(null, CriteriaValueType.FREE_TEXT);
        second.setSortOrder(1);
        second.setName("Branche");
        AssetCriteriaValue secondValue = new AssetCriteriaValue();
        secondValue.setAsset(asset);
        secondValue.setDefinition(second);
        secondValue.setFreeTextValue("Tech");

        when(valueRepository.findAllWithAssetAndDefinitionAndOption())
            .thenReturn(List.of(secondValue, firstValue));

        List<CriteriaBadge> badges = assetCriteriaService.getPropertyBadgesByAssetId().get(1L);

        assertThat(badges).extracting(CriteriaBadge::getLabel).containsExactly("Deutschland", "Tech");
    }

    @Test
    void getPropertyBadgesByAccountId_returnsBadgeWithoutMessageKey() {
        Account account = account(1L);
        CriteriaDefinition definition = definition(null, CriteriaValueType.FREE_TEXT);
        definition.setSortOrder(0);
        definition.setName("Länder");
        AccountCriteriaValue value = new AccountCriteriaValue();
        value.setAccount(account);
        value.setDefinition(definition);
        value.setFreeTextValue("Frankreich");
        when(accountValueRepository.findAllWithAccountAndDefinitionAndOption()).thenReturn(List.of(value));

        List<CriteriaBadge> badges = assetCriteriaService.getPropertyBadgesByAccountId().get(1L);

        assertThat(badges).hasSize(1);
        assertThat(badges.get(0).getLabel()).isEqualTo("Frankreich");
        assertThat(badges.get(0).getMessageKey()).isNull();
        assertThat(badges.get(0).getTooltip()).isEqualTo("Länder");
    }

    @Test
    void getPropertyBadgesByAccountId_systemCriterionGetsMessageKey() {
        // Accounts can now carry system-criterion values too, so their badges must resolve
        // message keys the same way asset badges do.
        Account account = account(1L);
        CriteriaDefinition definition = definition(SystemCriteria.CATEGORY, CriteriaValueType.FIXED_LIST);
        AccountCriteriaValue value = new AccountCriteriaValue();
        value.setAccount(account);
        value.setDefinition(definition);
        value.setOption(option(definition, "EDELMETALL", "Edelmetall"));
        when(accountValueRepository.findAllWithAccountAndDefinitionAndOption()).thenReturn(List.of(value));

        List<CriteriaBadge> badges = assetCriteriaService.getPropertyBadgesByAccountId().get(1L);

        assertThat(badges).hasSize(1);
        assertThat(badges.get(0).getMessageKey()).isEqualTo("assetCategory.EDELMETALL");
    }

    @Test
    void getPropertyBadgesByAccountId_excludesIndexCriterion() {
        Account account = account(1L);
        CriteriaDefinition definition = definition(SystemCriteria.INDEX_NAME, CriteriaValueType.FREE_TEXT);
        AccountCriteriaValue value = new AccountCriteriaValue();
        value.setAccount(account);
        value.setDefinition(definition);
        value.setFreeTextValue("MSCI World");
        when(accountValueRepository.findAllWithAccountAndDefinitionAndOption()).thenReturn(List.of(value));

        Map<Long, List<CriteriaBadge>> result = assetCriteriaService.getPropertyBadgesByAccountId();

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

    @Test
    void getValuesByAccountId_returnsOptionValueForFixedListCriterion() {
        CriteriaDefinition definition = definition(null, CriteriaValueType.FIXED_LIST);
        definition.setId(30L);
        Account account = account(1L);
        AccountCriteriaValue value = new AccountCriteriaValue();
        value.setAccount(account);
        value.setDefinition(definition);
        value.setOption(option(definition, null, "Deutschland"));
        when(accountValueRepository.findAllWithAccountAndDefinitionAndOption()).thenReturn(List.of(value));

        Map<Long, String> result = assetCriteriaService.getValuesByAccountId(30L);

        assertThat(result).containsEntry(1L, "Deutschland");
    }

    @Test
    void saveAssignments_account_freeTextWithValue_createsValue() {
        Account account = account(1L);
        CriteriaDefinition definition = definition(null, CriteriaValueType.FREE_TEXT);
        definition.setId(11L);
        when(definitionRepository.findAllByOrderBySortOrderAsc()).thenReturn(List.of(definition));
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getParameter("crit_11")).thenReturn("Deutschland");
        when(accountValueRepository.findByAccountAndDefinition(account, definition)).thenReturn(Optional.empty());

        assetCriteriaService.saveAssignments(account, request);

        ArgumentCaptor<AccountCriteriaValue> captor = ArgumentCaptor.forClass(AccountCriteriaValue.class);
        verify(accountValueRepository).save(captor.capture());
        assertThat(captor.getValue().getFreeTextValue()).isEqualTo("Deutschland");
    }

    @Test
    void saveAssignments_account_processesSystemDefinitionsToo() {
        // Accounts can now be assigned any licensed criterion, system or custom — the old
        // "system criteria don't apply to accounts" restriction was dropped so licensed users
        // get full parity with the asset form.
        Account account = account(1L);
        CriteriaDefinition systemDef = definition(SystemCriteria.CATEGORY, CriteriaValueType.FIXED_LIST);
        systemDef.setId(10L);
        CriteriaOption option = option(systemDef, "EDELMETALL", "Edelmetall");
        option.setId(20L);
        when(definitionRepository.findAllByOrderBySortOrderAsc()).thenReturn(List.of(systemDef));
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getParameter("crit_10")).thenReturn("20");
        when(optionRepository.findById(20L)).thenReturn(Optional.of(option));
        when(accountValueRepository.findByAccountAndDefinition(account, systemDef)).thenReturn(Optional.empty());

        assetCriteriaService.saveAssignments(account, request);

        ArgumentCaptor<AccountCriteriaValue> captor = ArgumentCaptor.forClass(AccountCriteriaValue.class);
        verify(accountValueRepository).save(captor.capture());
        assertThat(captor.getValue().getOption()).isSameAs(option);
    }

    @Test
    void saveAssignments_account_whenCustomCriteriaNotLicensed_leavesExistingAssignmentsUntouched() {
        when(licenseService.isFeatureEnabled(LicenseFeature.CUSTOM_CRITERIA)).thenReturn(false);
        Account account = account(1L);
        HttpServletRequest request = mock(HttpServletRequest.class);

        assetCriteriaService.saveAssignments(account, request);

        verifyNoInteractions(definitionRepository, accountValueRepository, optionRepository);
    }

    private Account account(Long id) {
        Account account = new Account();
        account.setId(id);
        return account;
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
