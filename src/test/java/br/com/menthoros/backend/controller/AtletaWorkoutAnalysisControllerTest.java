package br.com.menthoros.backend.controller;

import br.com.menthoros.backend.dto.output.AthleteWorkoutAnalysisOutputDto;
import br.com.menthoros.backend.enums.AnaliseStatus;
import br.com.menthoros.backend.exception.DomainNotFoundException;
import br.com.menthoros.backend.security.JwtTenantFilter;
import br.com.menthoros.backend.security.StructuredLoggingFilter;
import br.com.menthoros.backend.services.AtletaProgressService;
import br.com.menthoros.backend.services.AtletaTreinoFeedbackService;
import br.com.menthoros.backend.services.AtletaTreinoHojeService;
import br.com.menthoros.backend.services.AtletaWorkoutAnalysisService;
import br.com.menthoros.backend.services.TreinoService;
import org.junit.jupiter.api.BeforeEach;
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
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Contrato HTTP do GET /me/realizados/{id}/analise (analise-ia-treino-atleta, task 2.2). */
@WebMvcTest(controllers = AtletaTreinoController.class, excludeFilters = @ComponentScan.Filter(
        type = FilterType.ASSIGNABLE_TYPE,
        classes = {JwtTenantFilter.class, StructuredLoggingFilter.class}))
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("AtletaTreinoController — análise do atleta")
class AtletaWorkoutAnalysisControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private TreinoService treinoService;
    @MockitoBean private AtletaProgressService atletaProgressService;
    @MockitoBean private AtletaTreinoHojeService treinoHojeService;
    @MockitoBean private AtletaTreinoFeedbackService treinoFeedbackService;
    @MockitoBean private AtletaWorkoutAnalysisService atletaWorkoutAnalysisService;

    private final UUID atletaId = UUID.randomUUID();
    private final UUID treinoId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        when(atletaProgressService.resolverAtletaIdAtual()).thenReturn(atletaId);
    }

    @Test
    @DisplayName("200 com o corpo do atleta — e sem nenhum campo do coach")
    void ok200() throws Exception {
        when(atletaWorkoutAnalysisService.buscarAnalise(atletaId, treinoId)).thenReturn(Optional.of(
                new AthleteWorkoutAnalysisOutputDto(AnaliseStatus.COMPLETED, null,
                        "Você segurou o ritmo.", "Saiu como planejado.", "Pesou mais que o esperado.",
                        "Capriche no sono.",
                        new AthleteWorkoutAnalysisOutputDto.Executado(58L, new BigDecimal("11.2"), 7),
                        new AthleteWorkoutAnalysisOutputDto.Planejado(61L, new BigDecimal("11.0"), 6))));

        mockMvc.perform(get("/api/v1/atletas/me/realizados/{id}/analise", treinoId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.comoFoi").value("Saiu como planejado."))
                .andExpect(jsonPath("$.executado.duracaoMin").value(58))
                .andExpect(jsonPath("$.planejado.rpeEsperado").value(6))
                .andExpect(jsonPath("$.technicalInterpretation").doesNotExist())
                .andExpect(jsonPath("$.executionScore").doesNotExist())
                .andExpect(jsonPath("$.primaryCause").doesNotExist());
    }

    @Test
    @DisplayName("204 quando não há nada a mostrar")
    void noContent204() throws Exception {
        when(atletaWorkoutAnalysisService.buscarAnalise(atletaId, treinoId)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/atletas/me/realizados/{id}/analise", treinoId))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("404 quando o realizado não é do atleta")
    void notFound404() throws Exception {
        when(atletaWorkoutAnalysisService.buscarAnalise(atletaId, treinoId))
                .thenThrow(new DomainNotFoundException("Treino realizado não encontrado"));

        mockMvc.perform(get("/api/v1/atletas/me/realizados/{id}/analise", treinoId))
                .andExpect(status().isNotFound());
    }
}
