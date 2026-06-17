package br.com.menthoros.backend.controller;

import br.com.menthoros.backend.dto.output.CoachAtletaResumoDto;
import br.com.menthoros.backend.dto.output.CoachCalendarioDto;
import br.com.menthoros.backend.dto.output.CoachInsightsDto;
import br.com.menthoros.backend.security.JwtTenantFilter;
import br.com.menthoros.backend.security.StructuredLoggingFilter;
import br.com.menthoros.backend.services.CoachDashboardService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = CoachDashboardController.class, excludeFilters = @ComponentScan.Filter(
        type = FilterType.ASSIGNABLE_TYPE,
        classes = {JwtTenantFilter.class, StructuredLoggingFilter.class}))
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("CoachDashboardController")
class CoachDashboardControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private CoachDashboardService service;

    @Test
    @DisplayName("GET /coach/atletas → 200 com o roster")
    void roster() throws Exception {
        when(service.getRoster()).thenReturn(List.of(new CoachAtletaResumoDto(
                UUID.randomUUID(), "Ana Silva", 52.3, 44.0, 8.3, "BUILD", "warning",
                LocalDate.of(2026, 6, 15), new BigDecimal("32.5"))));

        mockMvc.perform(get("/api/v1/coach/atletas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nome").value("Ana Silva"))
                .andExpect(jsonPath("$[0].status").value("warning"));
    }

    @Test
    @DisplayName("GET /coach/calendario-semanal → 200 e repassa from")
    void calendario() throws Exception {
        when(service.getCalendarioSemanal(eq(LocalDate.of(2026, 6, 15))))
                .thenReturn(new CoachCalendarioDto(LocalDate.of(2026, 6, 15), LocalDate.of(2026, 6, 21), List.of()));

        mockMvc.perform(get("/api/v1/coach/calendario-semanal").param("from", "2026-06-15"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.semanaInicio").value("2026-06-15"));

        verify(service).getCalendarioSemanal(LocalDate.of(2026, 6, 15));
    }

    @Test
    @DisplayName("GET /coach/calendario-semanal sem from → service recebe null")
    void calendarioSemFrom() throws Exception {
        when(service.getCalendarioSemanal(null))
                .thenReturn(new CoachCalendarioDto(LocalDate.of(2026, 6, 15), LocalDate.of(2026, 6, 21), List.of()));

        mockMvc.perform(get("/api/v1/coach/calendario-semanal")).andExpect(status().isOk());

        verify(service).getCalendarioSemanal(null);
    }

    @Test
    @DisplayName("GET /coach/insights → 200 com KPIs")
    void insights() throws Exception {
        when(service.getInsights(any(), any())).thenReturn(new CoachInsightsDto(
                new CoachInsightsDto.Kpis(24, 18, 5, 1, 96), List.of(), List.of()));

        mockMvc.perform(get("/api/v1/coach/insights"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kpis.totalAtletas").value(24))
                .andExpect(jsonPath("$.kpis.emAtencao").value(5));
    }
}
