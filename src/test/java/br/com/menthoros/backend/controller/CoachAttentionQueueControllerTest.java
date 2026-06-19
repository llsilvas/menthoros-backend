package br.com.menthoros.backend.controller;

import br.com.menthoros.backend.dto.output.CoachAttentionItemOutputDto;
import br.com.menthoros.backend.dto.output.CoachAttentionItemOutputDto.Evidencia;
import br.com.menthoros.backend.dto.output.RecommendationExplanation;
import br.com.menthoros.backend.enums.ExplanationConfidence;
import br.com.menthoros.backend.enums.MotivoAtencao;
import br.com.menthoros.backend.enums.Severidade;
import br.com.menthoros.backend.security.JwtTenantFilter;
import br.com.menthoros.backend.security.StructuredLoggingFilter;
import br.com.menthoros.backend.services.CoachAttentionQueueService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = CoachAttentionQueueController.class, excludeFilters = @ComponentScan.Filter(
        type = FilterType.ASSIGNABLE_TYPE,
        classes = {JwtTenantFilter.class, StructuredLoggingFilter.class}))
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("CoachAttentionQueueController")
class CoachAttentionQueueControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private CoachAttentionQueueService service;

    @Nested
    @DisplayName("getAttentionQueue")
    class GetAttentionQueue {

    @Test
    @DisplayName("GET /coach/attention-queue → 200 com a fila ordenada")
    void fila() throws Exception {
        RecommendationExplanation expFadiga = new RecommendationExplanation(
                "TSB em -40.0 situa-se na zona FADIGA_EXCESSIVA, indicando fadiga excessiva com risco de overtraining.",
                List.of("CoachAttentionSignalEvaluator.avaliarFadiga", "FaixaTsb.FADIGA_EXCESSIVA"),
                ExplanationConfidence.HIGH);
        RecommendationExplanation expSemPlano = new RecommendationExplanation(
                "Atleta sem plano ativo; impossível avaliar carga ou progressão.",
                List.of("CoachAttentionSignalEvaluator.avaliarSemPlano"),
                ExplanationConfidence.HIGH);
        when(service.getAttentionQueue()).thenReturn(List.of(
                new CoachAttentionItemOutputDto(
                        UUID.randomUUID(), "Ana Silva", Severidade.CRITICA, 350,
                        MotivoAtencao.FADIGA, MotivoAtencao.FADIGA.getSuggestedAction(),
                        Instant.parse("2026-06-18T12:00:00Z"),
                        List.of(new Evidencia("TSB", "-40.0 (Fadiga excessiva)")), expFadiga),
                new CoachAttentionItemOutputDto(
                        UUID.randomUUID(), "Bruno Costa", Severidade.ALTA, 235,
                        MotivoAtencao.SEM_PLANO, MotivoAtencao.SEM_PLANO.getSuggestedAction(),
                        Instant.parse("2026-06-18T12:00:00Z"),
                        List.of(new Evidencia("Plano", "sem plano ativo")), expSemPlano)));

        mockMvc.perform(get("/api/v1/coach/attention-queue"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].athleteName").value("Ana Silva"))
                .andExpect(jsonPath("$[0].severity").value("CRITICA"))
                .andExpect(jsonPath("$[0].primaryReason").value("FADIGA"))
                .andExpect(jsonPath("$[0].evidence[0].label").value("TSB"))
                .andExpect(jsonPath("$[0].explanation.confidence").value("HIGH"))
                .andExpect(jsonPath("$[0].explanation.rationale").isString())
                .andExpect(jsonPath("$[1].severity").value("ALTA"))
                .andExpect(jsonPath("$[1].primaryReason").value("SEM_PLANO"))
                .andExpect(jsonPath("$[1].explanation.confidence").value("HIGH"))
                .andExpect(jsonPath("$[0].explanation.sourceRules").isArray())
                .andExpect(jsonPath("$[1].explanation.sourceRules").isArray());
    }

    @Test
    @DisplayName("GET /coach/attention-queue vazia → 200 com lista vazia")
    void vazia() throws Exception {
        when(service.getAttentionQueue()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/coach/attention-queue"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }
    }
}
