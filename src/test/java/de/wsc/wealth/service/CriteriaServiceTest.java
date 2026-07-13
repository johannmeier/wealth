package de.wsc.wealth.service;

import de.wsc.wealth.domain.CriteriaDefinition;
import de.wsc.wealth.domain.CriteriaOption;
import de.wsc.wealth.domain.CriteriaValueType;
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

    private CriteriaService criteriaService;

    @BeforeEach
    void setUp() {
        criteriaService = new CriteriaService(definitionRepository, optionRepository, valueRepository);
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
