package de.wsc.wealth.service;

import de.wsc.wealth.domain.CriteriaDefinition;
import de.wsc.wealth.domain.CriteriaOption;
import de.wsc.wealth.domain.CriteriaValueType;
import de.wsc.wealth.domain.SystemCriteria;
import de.wsc.wealth.license.LicenseFeature;
import de.wsc.wealth.license.LicenseService;
import de.wsc.wealth.repository.AccountCriteriaValueRepository;
import de.wsc.wealth.repository.AssetCriteriaValueRepository;
import de.wsc.wealth.repository.CriteriaDefinitionRepository;
import de.wsc.wealth.repository.CriteriaOptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CriteriaServiceTest {

    @Mock private CriteriaDefinitionRepository definitionRepository;
    @Mock private CriteriaOptionRepository optionRepository;
    @Mock private AssetCriteriaValueRepository valueRepository;
    @Mock private AccountCriteriaValueRepository accountValueRepository;
    @Mock private LicenseService licenseService;

    private CriteriaService criteriaService;

    @BeforeEach
    void setUp() {
        lenient().when(licenseService.isFeatureEnabled(LicenseFeature.CUSTOM_CRITERIA)).thenReturn(true);
        lenient().when(licenseService.isCriterionUsable(any())).thenReturn(true);
        criteriaService = new CriteriaService(definitionRepository, optionRepository, valueRepository, accountValueRepository, licenseService);
    }

    @Test
    void save_newDefinition_assignsNextSortOrder() {
        CriteriaDefinition existing1 = definition(null, 0);
        CriteriaDefinition existing2 = definition(null, 1);
        when(definitionRepository.findAllByOrderBySortOrderAsc()).thenReturn(List.of(existing1, existing2));
        when(definitionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CriteriaDefinition form = new CriteriaDefinition();
        form.setName("Länder");
        form.setValueType(CriteriaValueType.FIXED_LIST);

        CriteriaDefinition saved = criteriaService.save(form);

        assertThat(saved.getSortOrder()).isEqualTo(2);
        assertThat(saved.getSystemCode()).isNull();
    }

    @Test
    void save_newDefinition_withoutExplicitColor_assignsNextFreeColor() {
        CriteriaDefinition existing = definition(null, 0);
        existing.setColorIndex(0);
        when(definitionRepository.findAllByOrderBySortOrderAsc()).thenReturn(List.of(existing));
        when(definitionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CriteriaDefinition form = new CriteriaDefinition();
        form.setName("Länder");
        form.setValueType(CriteriaValueType.FIXED_LIST);

        CriteriaDefinition saved = criteriaService.save(form);

        assertThat(saved.getColorIndex()).isEqualTo(1);
    }

    @Test
    void save_newDefinition_respectsExplicitColorChoice() {
        when(definitionRepository.findAllByOrderBySortOrderAsc()).thenReturn(List.of());
        when(definitionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CriteriaDefinition form = new CriteriaDefinition();
        form.setName("Länder");
        form.setValueType(CriteriaValueType.FIXED_LIST);
        form.setColorIndex(7);

        CriteriaDefinition saved = criteriaService.save(form);

        assertThat(saved.getColorIndex()).isEqualTo(7);
    }

    @Test
    void save_existingDefinition_updatesColorIndexWhenProvided() {
        CriteriaDefinition existing = definition("CATEGORY", 0);
        existing.setId(5L);
        existing.setColorIndex(2);
        when(definitionRepository.findById(5L)).thenReturn(Optional.of(existing));
        when(definitionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CriteriaDefinition form = new CriteriaDefinition();
        form.setId(5L);
        form.setName("Kategorie");
        form.setColorIndex(9);

        CriteriaDefinition saved = criteriaService.save(form);

        assertThat(saved.getColorIndex()).isEqualTo(9);
    }

    @Test
    void nextFreeColorIndex_noneUsed_returnsZero() {
        when(definitionRepository.findAllByOrderBySortOrderAsc()).thenReturn(List.of());

        assertThat(criteriaService.nextFreeColorIndex()).isEqualTo(0);
    }

    @Test
    void nextFreeColorIndex_someUsed_returnsLowestFree() {
        CriteriaDefinition d0 = definition(null, 0); d0.setColorIndex(0);
        CriteriaDefinition d1 = definition(null, 1); d1.setColorIndex(1);
        CriteriaDefinition d2 = definition(null, 2); d2.setColorIndex(2);
        when(definitionRepository.findAllByOrderBySortOrderAsc()).thenReturn(List.of(d0, d1, d2));

        assertThat(criteriaService.nextFreeColorIndex()).isEqualTo(3);
    }

    @Test
    void nextFreeColorIndex_allTenUsed_returnsLeastUsedIndex() {
        List<CriteriaDefinition> all = new java.util.ArrayList<>();
        for (int i = 0; i < 10; i++) {
            CriteriaDefinition d = definition(null, i);
            d.setColorIndex(i);
            all.add(d);
        }
        CriteriaDefinition duplicate = definition(null, 10);
        duplicate.setColorIndex(0);
        all.add(duplicate);
        when(definitionRepository.findAllByOrderBySortOrderAsc()).thenReturn(all);

        assertThat(criteriaService.nextFreeColorIndex()).isEqualTo(1);
    }

    @Test
    void assignMissingColorIndexes_onlyBackfillsNullColors() {
        CriteriaDefinition colored = definition(null, 0);
        colored.setId(1L);
        colored.setColorIndex(5);
        CriteriaDefinition uncolored = definition(null, 1);
        uncolored.setId(2L);
        when(definitionRepository.findAllByOrderBySortOrderAsc()).thenReturn(List.of(colored, uncolored));
        when(definitionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        criteriaService.assignMissingColorIndexes();

        verify(definitionRepository, never()).save(colored);
        assertThat(uncolored.getColorIndex()).isNotNull();
        verify(definitionRepository).save(uncolored);
    }

    @Test
    void save_newDefinition_whenCustomCriteriaNotLicensed_throws() {
        when(licenseService.isFeatureEnabled(LicenseFeature.CUSTOM_CRITERIA)).thenReturn(false);
        CriteriaDefinition form = new CriteriaDefinition();
        form.setName("Länder");
        form.setValueType(CriteriaValueType.FIXED_LIST);

        assertThatThrownBy(() -> criteriaService.save(form)).isInstanceOf(IllegalStateException.class);
        verify(definitionRepository, never()).save(any());
    }

    @Test
    void findAll_whenNoCriteriaLicensed_excludesEverything() {
        // Community edition: no criteria at all, not even system ones.
        when(licenseService.isCriterionUsable(any())).thenReturn(false);
        CriteriaDefinition systemDef = definition("CATEGORY", 0);
        CriteriaDefinition customDef = definition(null, 1);
        when(definitionRepository.findAllByOrderBySortOrderAsc()).thenReturn(List.of(systemDef, customDef));

        List<CriteriaDefinition> result = criteriaService.findAll();

        assertThat(result).isEmpty();
    }

    @Test
    void findAll_whenCustomCriteriaLicensed_includesAllDefinitions() {
        CriteriaDefinition systemDef = definition("CATEGORY", 0);
        CriteriaDefinition customDef = definition(null, 1);
        when(definitionRepository.findAllByOrderBySortOrderAsc()).thenReturn(List.of(systemDef, customDef));

        List<CriteriaDefinition> result = criteriaService.findAll();

        assertThat(result).containsExactly(systemDef, customDef);
    }

    @Test
    void findAll_whenOnlyWittmannLicensed_includesOnlyWittmann() {
        // isCriterionUsable() encapsulates the real Wittmann-vs-other-criteria rule (tested in
        // LicenseServiceTest); here we only need CriteriaService.findAll() to delegate to it
        // per-definition instead of applying one blanket switch.
        when(licenseService.isCriterionUsable(any())).thenAnswer(inv -> {
            CriteriaDefinition d = inv.getArgument(0);
            return SystemCriteria.WITTMANN.equals(d.getSystemCode());
        });
        CriteriaDefinition systemDef = definition("CATEGORY", 0);
        CriteriaDefinition wittmannDef = definition(SystemCriteria.WITTMANN, 1);
        CriteriaDefinition customDef = definition(null, 2);
        when(definitionRepository.findAllByOrderBySortOrderAsc()).thenReturn(List.of(systemDef, wittmannDef, customDef));

        List<CriteriaDefinition> result = criteriaService.findAll();

        assertThat(result).containsExactly(wittmannDef);
    }

    @Test
    void save_existingDefinition_onlyUpdatesName() {
        CriteriaDefinition existing = definition("CATEGORY", 0);
        existing.setId(5L);
        existing.setName("Kategorie");
        when(definitionRepository.findById(5L)).thenReturn(Optional.of(existing));
        when(definitionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CriteriaDefinition form = new CriteriaDefinition();
        form.setId(5L);
        form.setName("Neuer Name");
        form.setValueType(CriteriaValueType.FREE_TEXT); // must be ignored for existing definitions

        CriteriaDefinition saved = criteriaService.save(form);

        assertThat(saved.getName()).isEqualTo("Neuer Name");
        assertThat(saved.getValueType()).isEqualTo(CriteriaValueType.FIXED_LIST);
        assertThat(saved.getSystemCode()).isEqualTo("CATEGORY");
    }

    @Test
    void delete_systemDefinition_throwsAndDoesNotDelete() {
        CriteriaDefinition systemDef = definition("CATEGORY", 0);
        systemDef.setId(1L);
        when(definitionRepository.findById(1L)).thenReturn(Optional.of(systemDef));

        assertThatThrownBy(() -> criteriaService.delete(1L)).isInstanceOf(IllegalStateException.class);
        verify(definitionRepository, never()).delete(any());
    }

    @Test
    void delete_customDefinition_removesValuesOptionsAndDefinition() {
        CriteriaDefinition customDef = definition(null, 5);
        customDef.setId(2L);
        when(definitionRepository.findById(2L)).thenReturn(Optional.of(customDef));
        when(optionRepository.findByDefinitionOrderBySortOrderAsc(customDef)).thenReturn(List.of());

        criteriaService.delete(2L);

        verify(valueRepository).deleteByDefinition(customDef);
        verify(accountValueRepository).deleteByDefinition(customDef);
        verify(definitionRepository).delete(customDef);
    }

    @Test
    void deleteOption_systemOption_throwsAndDoesNotDelete() {
        CriteriaOption systemOption = option("BOERSENGEHANDELT");
        systemOption.setId(10L);
        when(optionRepository.findById(10L)).thenReturn(Optional.of(systemOption));

        assertThatThrownBy(() -> criteriaService.deleteOption(10L)).isInstanceOf(IllegalStateException.class);
        verify(optionRepository, never()).delete(any());
    }

    @Test
    void deleteOption_customOption_removesValuesAndOption() {
        CriteriaOption customOption = option(null);
        customOption.setId(11L);
        when(optionRepository.findById(11L)).thenReturn(Optional.of(customOption));

        criteriaService.deleteOption(11L);

        verify(valueRepository).deleteByOption(customOption);
        verify(accountValueRepository).deleteByOption(customOption);
        verify(optionRepository).delete(customOption);
    }

    @Test
    void saveOption_newOption_assignsNextSortOrder() {
        CriteriaDefinition def = definition(null, 0);
        def.setId(3L);
        when(definitionRepository.findById(3L)).thenReturn(Optional.of(def));
        CriteriaOption existingOption = option(null);
        existingOption.setSortOrder(0);
        when(optionRepository.findByDefinitionOrderBySortOrderAsc(def)).thenReturn(List.of(existingOption));
        when(optionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CriteriaOption saved = criteriaService.saveOption(3L, null, "Deutschland");

        assertThat(saved.getValue()).isEqualTo("Deutschland");
        assertThat(saved.getSortOrder()).isEqualTo(1);
        assertThat(saved.getSystemCode()).isNull();
    }

    @Test
    void saveOption_existingOption_onlyUpdatesValue() {
        CriteriaOption existing = option("BOERSENGEHANDELT");
        existing.setId(20L);
        when(optionRepository.findById(20L)).thenReturn(Optional.of(existing));
        when(optionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CriteriaOption saved = criteriaService.saveOption(3L, 20L, "Neuer Name");

        assertThat(saved.getValue()).isEqualTo("Neuer Name");
        assertThat(saved.getSystemCode()).isEqualTo("BOERSENGEHANDELT");
    }

    private CriteriaDefinition definition(String systemCode, int sortOrder) {
        CriteriaDefinition d = new CriteriaDefinition();
        d.setSystemCode(systemCode);
        d.setName("Kriterium");
        d.setValueType(CriteriaValueType.FIXED_LIST);
        d.setSortOrder(sortOrder);
        return d;
    }

    private CriteriaOption option(String systemCode) {
        CriteriaOption o = new CriteriaOption();
        o.setSystemCode(systemCode);
        o.setValue("Wert");
        return o;
    }
}
