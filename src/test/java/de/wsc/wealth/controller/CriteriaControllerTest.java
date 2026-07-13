package de.wsc.wealth.controller;

import de.wsc.wealth.domain.CriteriaDefinition;
import de.wsc.wealth.domain.CriteriaValueType;
import de.wsc.wealth.service.CriteriaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CriteriaControllerTest {

    @Mock private CriteriaService criteriaService;

    private CriteriaController controller;

    @BeforeEach
    void setUp() {
        controller = new CriteriaController(criteriaService);
    }

    @Test
    void list_returnsListViewWithDefinitions() {
        when(criteriaService.findAll()).thenReturn(List.of(definition("Kategorie")));

        Model model = new ExtendedModelMap();
        String view = controller.list(model);

        assertThat(view).isEqualTo("criteria/list");
        assertThat(model.getAttribute("definitions")).isNotNull();
    }

    @Test
    void newForm_returnsFormViewWithEmptyDefinition() {
        Model model = new ExtendedModelMap();
        String view = controller.newForm(model);

        assertThat(view).isEqualTo("criteria/form");
        assertThat(model.asMap()).containsKey("definition");
    }

    @Test
    void save_redirectsToCriteriaList() {
        CriteriaDefinition def = definition("Länder");
        String result = controller.save(def, new RedirectAttributesModelMap());

        assertThat(result).isEqualTo("redirect:/criteria");
        verify(criteriaService).save(def);
    }

    @Test
    void delete_whenAllowed_setsSuccessFlashAndRedirects() {
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();

        String result = controller.delete(1L, ra);

        assertThat(result).isEqualTo("redirect:/criteria");
        assertThat(ra.getFlashAttributes()).containsKey("success");
        verify(criteriaService).delete(1L);
    }

    @Test
    void delete_whenNotDeletable_setsErrorFlashAndRedirects() {
        doThrow(new IllegalStateException("System-Kriterien können nicht gelöscht werden."))
            .when(criteriaService).delete(1L);
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();

        String result = controller.delete(1L, ra);

        assertThat(result).isEqualTo("redirect:/criteria");
        assertThat(ra.getFlashAttributes()).containsKey("error");
    }

    @Test
    void options_populatesModelWithDefinitionAndOptions() {
        CriteriaDefinition def = definition("Kategorie");
        when(criteriaService.findById(1L)).thenReturn(Optional.of(def));
        when(criteriaService.getOptions(1L)).thenReturn(List.of());

        Model model = new ExtendedModelMap();
        String view = controller.options(1L, model);

        assertThat(view).isEqualTo("criteria/options");
        assertThat(model.getAttribute("definition")).isEqualTo(def);
    }

    @Test
    void saveOption_redirectsToOptionsPage() {
        String result = controller.saveOption(1L, null, "Deutschland", new RedirectAttributesModelMap());

        assertThat(result).isEqualTo("redirect:/criteria/1/options");
        verify(criteriaService).saveOption(1L, null, "Deutschland");
    }

    @Test
    void deleteOption_whenNotDeletable_setsErrorFlash() {
        doThrow(new IllegalStateException("System-Werte können nicht gelöscht werden."))
            .when(criteriaService).deleteOption(20L);
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();

        String result = controller.deleteOption(1L, 20L, ra);

        assertThat(result).isEqualTo("redirect:/criteria/1/options");
        assertThat(ra.getFlashAttributes()).containsKey("error");
    }

    private CriteriaDefinition definition(String name) {
        CriteriaDefinition d = new CriteriaDefinition();
        d.setName(name);
        d.setValueType(CriteriaValueType.FIXED_LIST);
        return d;
    }
}
