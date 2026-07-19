package de.wsc.wealth.service;

import de.wsc.wealth.domain.*;
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
        lenient().when(licenseService.isCriterionUsable(any())).thenReturn(true);
        assetCriteriaService = new AssetCriteriaService(definitionRepository, optionRepository, valueRepository, accountValueRepository, licenseService);
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
    void getPropertyBadgesByAssetId_optionValue_usesOptionAsLabel() {
        Asset asset = asset(1L);
        CriteriaDefinition definition = definition(null, CriteriaValueType.FIXED_LIST);
        definition.setSortOrder(0);
        AssetCriteriaValue value = new AssetCriteriaValue();
        value.setAsset(asset);
        value.setDefinition(definition);
        value.setOption(option(definition, null, "Edelmetall"));
        when(valueRepository.findAllWithAssetAndDefinitionAndOption()).thenReturn(List.of(value));

        List<CriteriaBadge> badges = assetCriteriaService.getPropertyBadgesByAssetId().get(1L);

        assertThat(badges).hasSize(1);
        assertThat(badges.get(0).getLabel()).isEqualTo("Edelmetall");
        assertThat(badges.get(0).getTooltip()).isEqualTo(definition.getName());
    }

    @Test
    void getPropertyBadgesByAssetId_freeTextValue_usesTextAsLabel() {
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
    }

    @Test
    void getPropertyBadgesByAssetId_whenDefinitionNotUsable_excludesItsValues() {
        // Per-value gate: a value disappears once isCriterionUsable() says no for its
        // definition (e.g. no license at all, or a Wittmann-only license and this is a
        // custom criterion).
        when(licenseService.isCriterionUsable(any())).thenReturn(false);
        Asset asset = asset(1L);
        CriteriaDefinition definition = definition(null, CriteriaValueType.FIXED_LIST);
        AssetCriteriaValue value = new AssetCriteriaValue();
        value.setAsset(asset);
        value.setDefinition(definition);
        value.setOption(option(definition, null, "Edelmetall"));
        when(valueRepository.findAllWithAssetAndDefinitionAndOption()).thenReturn(List.of(value));

        Map<Long, List<CriteriaBadge>> result = assetCriteriaService.getPropertyBadgesByAssetId();

        assertThat(result).isEmpty();
    }

    @Test
    void getPropertyBadgesByAccountId_whenDefinitionNotUsable_excludesItsValues() {
        when(licenseService.isCriterionUsable(any())).thenReturn(false);
        Account account = account(1L);
        CriteriaDefinition definition = definition(null, CriteriaValueType.FREE_TEXT);
        AccountCriteriaValue value = new AccountCriteriaValue();
        value.setAccount(account);
        value.setDefinition(definition);
        value.setFreeTextValue("Frankreich");
        when(accountValueRepository.findAllWithAccountAndDefinitionAndOption()).thenReturn(List.of(value));

        Map<Long, List<CriteriaBadge>> result = assetCriteriaService.getPropertyBadgesByAccountId();

        assertThat(result).isEmpty();
    }

    @Test
    void findAllActive_whenNoDefinitionUsable_returnsEmpty() {
        // Must be fully empty, not just "system only": saveAssignments(Asset, request) iterates
        // this same list, so any definition it skips here is also skipped there — a definition
        // that appeared here but whose form field was never rendered would read as blank and
        // delete the asset's existing assignment for it, which the license gate must never do.
        when(licenseService.isCriterionUsable(any())).thenReturn(false);
        when(definitionRepository.findAllByOrderBySortOrderAsc())
            .thenReturn(List.of(definition(SystemCriteria.WITTMANN, CriteriaValueType.FIXED_LIST)));

        List<CriteriaDefinition> result = assetCriteriaService.findAllActive();

        assertThat(result).isEmpty();
    }

    @Test
    void findAllActive_withMixedUsability_returnsOnlyUsableDefinitions() {
        // End-to-end check that per-definition filtering (not "all or nothing") really reaches
        // findAllActive() — e.g. a Wittmann-only license includes Wittmann but excludes
        // custom criteria.
        CriteriaDefinition wittmannDef = definition(SystemCriteria.WITTMANN, CriteriaValueType.FIXED_LIST);
        CriteriaDefinition customDef = definition(null, CriteriaValueType.FIXED_LIST);
        when(licenseService.isCriterionUsable(wittmannDef)).thenReturn(true);
        when(licenseService.isCriterionUsable(customDef)).thenReturn(false);
        when(definitionRepository.findAllByOrderBySortOrderAsc()).thenReturn(List.of(customDef, wittmannDef));

        List<CriteriaDefinition> result = assetCriteriaService.findAllActive();

        assertThat(result).containsExactly(wittmannDef);
    }

    @Test
    void saveAssignments_asset_whenNoDefinitionUsable_leavesExistingAssignmentsUntouched() {
        when(licenseService.isCriterionUsable(any())).thenReturn(false);
        when(definitionRepository.findAllByOrderBySortOrderAsc())
            .thenReturn(List.of(definition(null, CriteriaValueType.FIXED_LIST)));
        Asset asset = asset(1L);
        HttpServletRequest request = mock(HttpServletRequest.class);

        assetCriteriaService.saveAssignments(asset, request);

        verifyNoInteractions(valueRepository, optionRepository);
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
    void getPropertyBadgesByAccountId_freeTextValue_usesTextAsLabel() {
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
        assertThat(badges.get(0).getTooltip()).isEqualTo("Länder");
    }

    @Test
    void getPropertyBadgesByAccountId_optionValue_usesOptionAsLabel() {
        Account account = account(1L);
        CriteriaDefinition definition = definition(null, CriteriaValueType.FIXED_LIST);
        AccountCriteriaValue value = new AccountCriteriaValue();
        value.setAccount(account);
        value.setDefinition(definition);
        value.setOption(option(definition, null, "Edelmetall"));
        when(accountValueRepository.findAllWithAccountAndDefinitionAndOption()).thenReturn(List.of(value));

        List<CriteriaBadge> badges = assetCriteriaService.getPropertyBadgesByAccountId().get(1L);

        assertThat(badges).hasSize(1);
        assertThat(badges.get(0).getLabel()).isEqualTo("Edelmetall");
    }

    @Test
    void saveAssignments_fixedListWithSelectedOption_createsValue() {
        Asset asset = asset(1L);
        CriteriaDefinition definition = definition(null, CriteriaValueType.FIXED_LIST);
        definition.setId(10L);
        CriteriaOption option = option(definition, null, "Edelmetall");
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
        // Accounts can be assigned any licensed criterion, system (Wittmann) or custom —
        // licensed users get full parity with the asset form.
        Account account = account(1L);
        CriteriaDefinition systemDef = definition(SystemCriteria.WITTMANN, CriteriaValueType.FIXED_LIST);
        systemDef.setId(10L);
        CriteriaOption option = option(systemDef, "EDELMETALLE", "Edelmetalle");
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
    void saveAssignments_account_whenNoDefinitionUsable_leavesExistingAssignmentsUntouched() {
        when(licenseService.isCriterionUsable(any())).thenReturn(false);
        when(definitionRepository.findAllByOrderBySortOrderAsc())
            .thenReturn(List.of(definition(null, CriteriaValueType.FIXED_LIST)));
        Account account = account(1L);
        HttpServletRequest request = mock(HttpServletRequest.class);

        assetCriteriaService.saveAssignments(account, request);

        verifyNoInteractions(accountValueRepository, optionRepository);
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
}
