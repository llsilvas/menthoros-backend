package br.com.menthoros.backend.controller;

import br.com.menthoros.backend.config.core.JacksonConfig;
import br.com.menthoros.backend.domain.planner.TrainingPhase;
import br.com.menthoros.backend.dto.output.SeasonPlanResponseDto;
import br.com.menthoros.backend.enums.SeasonPlanStatus;
import br.com.menthoros.backend.enums.WeekValueSource;
import br.com.menthoros.backend.exception.DomainConflictException;
import br.com.menthoros.backend.exception.DomainNotFoundException;
import br.com.menthoros.backend.exception.DomainRuleViolationException;
import br.com.menthoros.backend.exception.SeasonPlanDisabledException;
import br.com.menthoros.backend.repository.TenantValidationRepository;
import br.com.menthoros.backend.repository.UsuarioRepository;
import br.com.menthoros.backend.services.AtletaProgressService;
import br.com.menthoros.backend.services.SeasonPlanAdjustmentService;
import br.com.menthoros.backend.services.SeasonPlanService;
import br.com.menthoros.backend.dto.input.SeasonPlanRecalculateInputDto;
import br.com.menthoros.backend.dto.output.SeasonPlanDiffDto;
import br.com.menthoros.backend.dto.input.SeasonWeekUpdateInputDto;
import org.springframework.http.MediaType;
import br.com.menthoros.backend.services.SeasonPlanService.Generation;
import br.com.menthoros.backend.services.UsuarioSyncService;
import br.com.menthoros.backend.testsupport.AuthWebMvcTestConfig;
import br.com.menthoros.backend.testsupport.JwtTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static br.com.menthoros.backend.testsupport.JwtTestSupport.TENANT_ID;
import static br.com.menthoros.backend.testsupport.JwtTestSupport.adminJwt;
import static br.com.menthoros.backend.testsupport.JwtTestSupport.atletaJwt;
import static br.com.menthoros.backend.testsupport.JwtTestSupport.tecnicoJwt;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Contrato HTTP do SeasonPlanController com a cadeia de segurança real (D1, D5, D12). */
@WebMvcTest(SeasonPlanController.class)
@Import({JacksonConfig.class, AuthWebMvcTestConfig.class})
class SeasonPlanControllerTest {

    private static final UUID ATLETA_ID = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final String BASE = "/api/v1/atletas/" + ATLETA_ID + "/season-plans";

    @Autowired private MockMvc mockMvc;

    @MockitoBean private SeasonPlanService seasonPlanService;
    @MockitoBean private SeasonPlanAdjustmentService adjustmentService;
    @MockitoBean private AtletaProgressService atletaProgressService;
    @MockitoBean private TenantValidationRepository tenantValidationRepository;
    @MockitoBean private JwtDecoder jwtDecoder;
    @MockitoBean private UsuarioSyncService usuarioSyncService;
    @MockitoBean private UsuarioRepository usuarioRepository;

    @BeforeEach
    void setUp() {
        JwtTestSupport.stubUsuarioAtivo(usuarioSyncService);
    }

    private void atletaDoTenant() {
        when(tenantValidationRepository.resourceBelongsToTenant(ATLETA_ID, TENANT_ID)).thenReturn(true);
    }

    @Nested
    @DisplayName("POST /season-plans")
    class Generate {

        @Test
        @DisplayName("201 com a temporada recém-gerada")
        void criada() throws Exception {
            atletaDoTenant();
            when(seasonPlanService.generate(ATLETA_ID)).thenReturn(new Generation(plano(), true));

            mockMvc.perform(post(BASE).with(tecnicoJwt()))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.seasonStatus").value("PROPOSED"))
                    .andExpect(jsonPath("$.weeks[0].weekIndex").value(1))
                    .andExpect(jsonPath("$.weeks[0].targetTss").value(285.0))
                    .andExpect(jsonPath("$.weeks[0].valueSource").value("ENGINE"))
                    .andExpect(jsonPath("$.milestones[0].weekIndex").value(6))
                    .andExpect(jsonPath("$.milestones[0].distance.value").value("21K"))
                    .andExpect(jsonPath("$.validations[0].rule").value("RAMP"))
                    .andExpect(jsonPath("$.validations[0].severity").value("WARN"))
                    .andExpect(jsonPath("$.validations[0].limit").value(10.0));
        }

        @Test
        @DisplayName("200 quando a proposta já existia")
        void jaExistia() throws Exception {
            atletaDoTenant();
            when(seasonPlanService.generate(ATLETA_ID)).thenReturn(new Generation(plano(), false));

            mockMvc.perform(post(BASE).with(adminJwt())).andExpect(status().isOk());
        }

        @Test
        @DisplayName("409 com temporada ativa")
        void conflito() throws Exception {
            atletaDoTenant();
            when(seasonPlanService.generate(ATLETA_ID)).thenThrow(new DomainConflictException("ativa"));

            mockMvc.perform(post(BASE).with(tecnicoJwt())).andExpect(status().isConflict());
        }

        @Test
        @DisplayName("422 sem prova-alvo válida")
        void regra() throws Exception {
            atletaDoTenant();
            when(seasonPlanService.generate(ATLETA_ID)).thenThrow(new DomainRuleViolationException("sem prova-alvo"));

            mockMvc.perform(post(BASE).with(tecnicoJwt())).andExpect(status().isUnprocessableEntity());
        }

        @Test
        @DisplayName("404 com code SEASON_PLAN_DISABLED quando a flag está desligada")
        void flagDesligada() throws Exception {
            atletaDoTenant();
            when(seasonPlanService.generate(ATLETA_ID)).thenThrow(new SeasonPlanDisabledException());

            mockMvc.perform(post(BASE).with(tecnicoJwt()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("SEASON_PLAN_DISABLED"));
        }

        @Test
        @DisplayName("403 para ATLETA, sem chegar ao serviço")
        void atletaProibido() throws Exception {
            mockMvc.perform(post(BASE).with(atletaJwt())).andExpect(status().isForbidden());

            verifyNoInteractions(seasonPlanService);
        }

        @Test
        @DisplayName("403 para atleta de outra assessoria, sem chegar ao serviço")
        void outraAssessoria() throws Exception {
            when(tenantValidationRepository.resourceBelongsToTenant(any(), any())).thenReturn(false);

            mockMvc.perform(post(BASE).with(tecnicoJwt())).andExpect(status().isForbidden());

            verifyNoInteractions(seasonPlanService);
        }
    }

    @Nested
    @DisplayName("GET /season-plans/current")
    class Current {

        @Test
        @DisplayName("200 com a temporada atual")
        void encontrada() throws Exception {
            atletaDoTenant();
            when(seasonPlanService.findCurrent(ATLETA_ID)).thenReturn(Optional.of(plano()));

            mockMvc.perform(get(BASE + "/current").with(tecnicoJwt()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.provaNome").value("Maratona alvo"));
        }

        @Test
        @DisplayName("204 sem temporada")
        void semTemporada() throws Exception {
            atletaDoTenant();
            when(seasonPlanService.findCurrent(ATLETA_ID)).thenReturn(Optional.empty());

            mockMvc.perform(get(BASE + "/current").with(tecnicoJwt())).andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("404 comum, sem code, para atleta inexistente")
        void naoEncontrado() throws Exception {
            atletaDoTenant();
            when(seasonPlanService.findCurrent(ATLETA_ID)).thenThrow(new DomainNotFoundException("atleta"));

            mockMvc.perform(get(BASE + "/current").with(tecnicoJwt()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").doesNotExist());
        }

        @Test
        @DisplayName("403 para ATLETA")
        void atletaProibido() throws Exception {
            mockMvc.perform(get(BASE + "/current").with(atletaJwt())).andExpect(status().isForbidden());

            verifyNoInteractions(seasonPlanService);
        }
    }

    @Nested
    @DisplayName("POST /season-plans/{id}/approve")
    class Approve {

        private final UUID planoId = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");
        private final String url = BASE + "/" + planoId + "/approve";

        @Test
        @DisplayName("200 com a temporada aprovada")
        void aprovada() throws Exception {
            atletaDoTenant();
            when(seasonPlanService.approve(ATLETA_ID, planoId, null)).thenReturn(planoAprovado());

            mockMvc.perform(post(url).with(tecnicoJwt()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.seasonStatus").value("ACTIVE"))
                    .andExpect(jsonPath("$.approvedAt").value("2026-11-20T13:00:00Z"));
        }

        @Test
        @DisplayName("404 para temporada de outro atleta")
        void naoEncontrada() throws Exception {
            atletaDoTenant();
            when(seasonPlanService.approve(ATLETA_ID, planoId, null)).thenThrow(new DomainNotFoundException("temporada"));

            mockMvc.perform(post(url).with(tecnicoJwt())).andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("404 com code SEASON_PLAN_DISABLED quando a flag está desligada")
        void flagDesligada() throws Exception {
            atletaDoTenant();
            when(seasonPlanService.approve(ATLETA_ID, planoId, null)).thenThrow(new SeasonPlanDisabledException());

            mockMvc.perform(post(url).with(adminJwt()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("SEASON_PLAN_DISABLED"));
        }

        @Test
        @DisplayName("409 com temporada arquivada")
        void conflito() throws Exception {
            atletaDoTenant();
            when(seasonPlanService.approve(ATLETA_ID, planoId, null)).thenThrow(new DomainConflictException("arquivada"));

            mockMvc.perform(post(url).with(tecnicoJwt())).andExpect(status().isConflict());
        }

        @Test
        @DisplayName("422 com a prova cancelada")
        void regra() throws Exception {
            atletaDoTenant();
            when(seasonPlanService.approve(ATLETA_ID, planoId, null)).thenThrow(new DomainRuleViolationException("cancelada"));

            mockMvc.perform(post(url).with(tecnicoJwt())).andExpect(status().isUnprocessableEntity());
        }

        @Test
        @DisplayName("403 para ATLETA, sem chegar ao serviço")
        void atletaProibido() throws Exception {
            mockMvc.perform(post(url).with(atletaJwt())).andExpect(status().isForbidden());

            verifyNoInteractions(seasonPlanService);
        }

        private SeasonPlanResponseDto planoAprovado() {
            SeasonPlanResponseDto p = plano();
            return new SeasonPlanResponseDto(planoId, SeasonPlanStatus.ACTIVE, Instant.parse("2026-11-20T13:00:00Z"),
                    p.provaId(), p.provaNome(), p.provaData(), p.shortPreparation(), p.totalWeeks(), p.initialCtl(),
                    p.peakVolumeKm(), p.weeks(), p.milestones(), p.validations(), 1, null, null, null,
                    null, null, null, null, false);
        }
    }

    @Nested
    @DisplayName("ajuste da temporada (add-season-plan-adjust-v2)")
    class Ajuste {

        private final UUID planoId = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");

        @Test
        @DisplayName("aprovar repassa o motivo do corpo ao serviço")
        void aprovarComMotivo() throws Exception {
            atletaDoTenant();
            when(seasonPlanService.approve(ATLETA_ID, planoId, "Atleta lesionou")).thenReturn(plano());

            mockMvc.perform(post(BASE + "/" + planoId + "/approve").with(tecnicoJwt())
                            .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Atleta lesionou\"}"))
                    .andExpect(status().isOk());

            verify(seasonPlanService).approve(ATLETA_ID, planoId, "Atleta lesionou");
        }

        @Test
        @DisplayName("POST /draft responde 201 ao criar e 200 com ajuste já aberto")
        void criarAjuste() throws Exception {
            atletaDoTenant();
            when(adjustmentService.createDraft(ATLETA_ID, planoId))
                    .thenReturn(new Generation(plano(), true), new Generation(plano(), false));

            mockMvc.perform(post(BASE + "/" + planoId + "/draft").with(tecnicoJwt())).andExpect(status().isCreated());
            mockMvc.perform(post(BASE + "/" + planoId + "/draft").with(tecnicoJwt())).andExpect(status().isOk());
        }

        @Test
        @DisplayName("POST /draft responde 409 quando a temporada não é a ativa")
        void criarAjusteConflito() throws Exception {
            atletaDoTenant();
            when(adjustmentService.createDraft(ATLETA_ID, planoId)).thenThrow(new DomainConflictException("não ativa"));

            mockMvc.perform(post(BASE + "/" + planoId + "/draft").with(tecnicoJwt())).andExpect(status().isConflict());
        }

        @Test
        @DisplayName("GET /{id} devolve a temporada")
        void porId() throws Exception {
            atletaDoTenant();
            when(adjustmentService.findById(ATLETA_ID, planoId)).thenReturn(plano());

            mockMvc.perform(get(BASE + "/" + planoId).with(tecnicoJwt()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.version").value(1))
                    .andExpect(jsonPath("$.weeks[0].locked").value(false));
        }

        @Test
        @DisplayName("PATCH da semana repassa os valores e devolve a temporada")
        void editarSemana() throws Exception {
            atletaDoTenant();
            when(adjustmentService.updateWeek(eq(ATLETA_ID), eq(planoId), eq(3), any())).thenReturn(plano());

            mockMvc.perform(patch(BASE + "/" + planoId + "/weeks/3").with(tecnicoJwt())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"targetVolumeKm\":45.0,\"keySessions\":\"6x1km\"}"))
                    .andExpect(status().isOk());

            verify(adjustmentService).updateWeek(ATLETA_ID, planoId, 3,
                    new SeasonWeekUpdateInputDto(null, null, new java.math.BigDecimal("45.0"), null, null, "6x1km"));
        }

        @Test
        @DisplayName("PATCH de semana travada responde 422")
        void editarSemanaTravada() throws Exception {
            atletaDoTenant();
            when(adjustmentService.updateWeek(eq(ATLETA_ID), eq(planoId), eq(1), any()))
                    .thenThrow(new DomainRuleViolationException("em andamento"));

            mockMvc.perform(patch(BASE + "/" + planoId + "/weeks/1").with(tecnicoJwt())
                            .contentType(MediaType.APPLICATION_JSON).content("{\"deload\":true}"))
                    .andExpect(status().isUnprocessableEntity());
        }

        @Test
        @DisplayName("DELETE descarta o ajuste com 204")
        void descartar() throws Exception {
            atletaDoTenant();

            mockMvc.perform(delete(BASE + "/" + planoId).with(tecnicoJwt())).andExpect(status().isNoContent());

            verify(adjustmentService).discard(ATLETA_ID, planoId);
        }

        @Test
        @DisplayName("403 para ATLETA nas rotas de ajuste, sem chegar ao serviço")
        void atletaProibido() throws Exception {
            mockMvc.perform(post(BASE + "/" + planoId + "/draft").with(atletaJwt())).andExpect(status().isForbidden());
            mockMvc.perform(patch(BASE + "/" + planoId + "/weeks/3").with(atletaJwt())
                    .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());
            mockMvc.perform(delete(BASE + "/" + planoId).with(atletaJwt())).andExpect(status().isForbidden());
            mockMvc.perform(get(BASE + "/" + planoId).with(atletaJwt())).andExpect(status().isForbidden());

            verifyNoInteractions(adjustmentService);
        }
    }

    @Nested
    @DisplayName("recalculate")
    class Recalculate {

        private final UUID planoId = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");
        private final String url = BASE + "/" + planoId + "/recalculate";
        private static final String CORPO =
                "{\"phaseWeeks\":{\"BASE\":3,\"BUILD\":4,\"PEAK\":2,\"TAPER\":2},\"startVolumeKm\":40.0}";

        @Test
        @DisplayName("POST repassa fases e volume e devolve o rascunho com 200")
        void recalcula() throws Exception {
            atletaDoTenant();
            when(adjustmentService.recalculate(eq(ATLETA_ID), eq(planoId), any())).thenReturn(plano());

            mockMvc.perform(post(url).with(tecnicoJwt()).contentType(MediaType.APPLICATION_JSON).content(CORPO))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.provaNome").value("Maratona alvo"));

            verify(adjustmentService).recalculate(ATLETA_ID, planoId, new SeasonPlanRecalculateInputDto(
                    Map.of(TrainingPhase.BASE, 3, TrainingPhase.BUILD, 4, TrainingPhase.PEAK, 2, TrainingPhase.TAPER, 2),
                    new BigDecimal("40.0")));
        }

        @Test
        @DisplayName("regra do recálculo violada responde 422 com a mensagem")
        void regraViolada() throws Exception {
            atletaDoTenant();
            when(adjustmentService.recalculate(eq(ATLETA_ID), eq(planoId), any()))
                    .thenThrow(new DomainRuleViolationException("A soma das fases dá 10 semanas (com a da prova); o ciclo tem 12"));

            mockMvc.perform(post(url).with(tecnicoJwt()).contentType(MediaType.APPLICATION_JSON).content(CORPO))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("o ciclo tem 12")));
        }

        @Test
        @DisplayName("temporada que não é ajuste em aberto responde 409")
        void naoRascunho() throws Exception {
            atletaDoTenant();
            when(adjustmentService.recalculate(eq(ATLETA_ID), eq(planoId), any()))
                    .thenThrow(new DomainConflictException("Só um ajuste em aberto"));

            mockMvc.perform(post(url).with(tecnicoJwt()).contentType(MediaType.APPLICATION_JSON).content(CORPO))
                    .andExpect(status().isConflict());
        }

        @Test
        @DisplayName("403 para ATLETA, sem chegar ao serviço")
        void atletaProibido() throws Exception {
            mockMvc.perform(post(url).with(atletaJwt()).contentType(MediaType.APPLICATION_JSON).content(CORPO))
                    .andExpect(status().isForbidden());

            verifyNoInteractions(adjustmentService);
        }
    }

    @Nested
    @DisplayName("diff")
    class Diff {

        private final UUID planoId = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");
        private final String url = BASE + "/" + planoId + "/diff";

        @Test
        @DisplayName("GET devolve a lista de alterações como array, com antes nulo explícito")
        void lista() throws Exception {
            atletaDoTenant();
            when(adjustmentService.diff(ATLETA_ID, planoId)).thenReturn(List.of(
                    new SeasonPlanDiffDto(5, "longRunKm", new BigDecimal("18"), new BigDecimal("20"), WeekValueSource.COACH),
                    new SeasonPlanDiffDto(6, "keySessions", null, "6x1km", WeekValueSource.COACH)));

            mockMvc.perform(get(url).with(tecnicoJwt()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].weekIndex").value(5))
                    .andExpect(jsonPath("$[0].field").value("longRunKm"))
                    .andExpect(jsonPath("$[0].before").value(18))
                    .andExpect(jsonPath("$[0].after").value(20))
                    .andExpect(jsonPath("$[0].source").value("COACH"))
                    .andExpect(jsonPath("$[1].before").value(org.hamcrest.Matchers.nullValue()))
                    .andExpect(jsonPath("$[1].after").value("6x1km"));
        }

        @Test
        @DisplayName("temporada que não é ajuste em aberto responde 409")
        void naoRascunho() throws Exception {
            atletaDoTenant();
            when(adjustmentService.diff(ATLETA_ID, planoId)).thenThrow(new DomainConflictException("Só um ajuste"));

            mockMvc.perform(get(url).with(tecnicoJwt())).andExpect(status().isConflict());
        }

        @Test
        @DisplayName("403 para ATLETA, sem chegar ao serviço")
        void atletaProibido() throws Exception {
            mockMvc.perform(get(url).with(atletaJwt())).andExpect(status().isForbidden());

            verifyNoInteractions(adjustmentService);
        }
    }

    private static SeasonPlanResponseDto plano() {
        return new SeasonPlanResponseDto(UUID.randomUUID(), SeasonPlanStatus.PROPOSED, null, UUID.randomUUID(),
                "Maratona alvo", LocalDate.of(2027, 4, 4), false, 20, 40.0, new BigDecimal("52.0"),
                List.of(new SeasonPlanResponseDto.Week(1, LocalDate.of(2026, 11, 16), TrainingPhase.BASE,
                        new BigDecimal("40.7"), 285.0, false, null, WeekValueSource.ENGINE, null, false, null)),
                List.of(new SeasonPlanResponseDto.Milestone(6, UUID.randomUUID(), "Meia de teste",
                        LocalDate.of(2026, 12, 27), br.com.menthoros.backend.enums.DistanciaProva.KM_21)),
                List.of(new SeasonPlanResponseDto.Validation(
                        br.com.menthoros.backend.domain.planner.SeasonPlanValidator.Rule.RAMP, "WARN", 2, 12.5, 10.0,
                        "Volume sobe 12,5% sobre a semana anterior (teto 10%)")), 1, null, null, null,
                null, null, null, null, false);
    }
}
