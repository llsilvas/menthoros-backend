package br.com.menthoros.backend.controller;

import br.com.menthoros.backend.dto.input.CoachDashboardQueryDto;
import br.com.menthoros.backend.dto.output.CoachAtletaResumoDto;
import br.com.menthoros.backend.dto.output.CoachCalendarioDto;
import br.com.menthoros.backend.dto.output.CoachDashboardOutputDto;
import br.com.menthoros.backend.dto.output.CoachDashboardRosterPageDto;
import br.com.menthoros.backend.dto.output.CoachDashboardSummaryDto;
import br.com.menthoros.backend.dto.output.CoachInsightsDto;
import br.com.menthoros.backend.dto.output.CoachAttentionItemOutputDto;
import br.com.menthoros.backend.dto.output.RecommendationExplanation;
import br.com.menthoros.backend.security.JwtTenantFilter;
import br.com.menthoros.backend.security.StructuredLoggingFilter;
import br.com.menthoros.backend.services.CoachDashboardService;
import br.com.menthoros.backend.enums.ExplanationConfidence;
import br.com.menthoros.backend.enums.MotivoAtencao;
import br.com.menthoros.backend.enums.Severidade;
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
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
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
                LocalDate.of(2026, 6, 15), new BigDecimal("32.5"), 80)));

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

    @Test
    @DisplayName("GET /coach/dashboard → 200 com resposta agregada")
    void dashboard() throws Exception {
        CoachAttentionItemOutputDto attention = new CoachAttentionItemOutputDto(
                UUID.randomUUID(), "Bruno S", Severidade.ALTA, 240, MotivoAtencao.FADIGA,
                MotivoAtencao.FADIGA.getSuggestedAction(), Instant.parse("2026-06-17T12:00:00Z"),
                List.of(), new RecommendationExplanation("Fadiga alta", List.of("rule-1"), ExplanationConfidence.HIGH));
        CoachDashboardOutputDto dashboard = new CoachDashboardOutputDto(
                Instant.parse("2026-06-17T12:00:00Z"),
                new CoachDashboardSummaryDto(new CoachInsightsDto.Kpis(24, 18, 5, 1, 96), 1, 1),
                new CoachDashboardRosterPageDto(
                        List.of(new CoachAtletaResumoDto(UUID.randomUUID(), "Bruno S", 52.3, 44.0, -12.4, "BUILD", "warning",
                                LocalDate.of(2026, 6, 15), new BigDecimal("32.5"), 80)),
                        0, 10, 1, 1),
                List.of(attention),
                new CoachCalendarioDto(LocalDate.of(2026, 6, 15), LocalDate.of(2026, 6, 21), List.of()),
                new CoachInsightsDto(new CoachInsightsDto.Kpis(24, 18, 5, 1, 96), List.of(), List.of()));
        when(service.getDashboard(any(CoachDashboardQueryDto.class))).thenReturn(dashboard);

        mockMvc.perform(get("/api/v1/coach/dashboard")
                        .param("q", "Bruno")
                        .param("status", "warning")
                        .param("sortBy", "priority")
                        .param("page", "0")
                        .param("size", "10")
                        .param("from", "2026-06-01")
                        .param("to", "2026-06-30")
                        .param("weekFrom", "2026-06-15"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.kpis.totalAtletas").value(24))
                .andExpect(jsonPath("$.roster.items[0].nome").value("Bruno S"))
                .andExpect(jsonPath("$.attentionQueue[0].athleteName").value("Bruno S"))
                .andExpect(jsonPath("$.calendar.semanaInicio").value("2026-06-15"));

        verify(service).getDashboard(argThat(query ->
                "Bruno".equals(query.q())
                        && "warning".equals(query.status())
                        && "priority".equals(query.sortBy())
                        && Integer.valueOf(0).equals(query.page())
                        && Integer.valueOf(10).equals(query.size())
                        && LocalDate.of(2026, 6, 1).equals(query.from())
                        && LocalDate.of(2026, 6, 30).equals(query.to())
                        && LocalDate.of(2026, 6, 15).equals(query.weekFrom())));
    }
}
