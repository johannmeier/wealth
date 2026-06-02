package de.wsc.wealth.controller;

import de.wsc.wealth.service.AssetService;
import de.wsc.wealth.service.StatisticsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardControllerTest {

    @Mock
    private StatisticsService statisticsService;
    @Mock
    private AssetService assetService;

    private DashboardController controller;

    @BeforeEach
    void setUp() {
        controller = new DashboardController(statisticsService, assetService);
    }

    @Test
    void dashboard_returnsIndexView() {
        when(statisticsService.getAllPositions()).thenReturn(List.of());
        when(statisticsService.getTotalWealth()).thenReturn(BigDecimal.ZERO);

        Model model = new ExtendedModelMap();
        String view = controller.dashboard(model);

        assertThat(view).isEqualTo("index");
    }

    @Test
    void dashboard_populatesPositionsAndTotalWealth() {
        when(statisticsService.getAllPositions()).thenReturn(List.of());
        when(statisticsService.getTotalWealth()).thenReturn(new BigDecimal("12345.67"));

        Model model = new ExtendedModelMap();
        controller.dashboard(model);

        assertThat(model.asMap()).containsKey("positions");
        assertThat(model.asMap()).containsKey("totalWealth");
        assertThat(model.getAttribute("totalWealth")).isEqualTo(new BigDecimal("12345.67"));
    }
}
