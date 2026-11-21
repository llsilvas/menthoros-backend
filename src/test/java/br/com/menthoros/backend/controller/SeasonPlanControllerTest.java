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
import br.com.menthoros.backend.services.SeasonPlanService;
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
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static br.com.menthoros.backend.testsupport.JwtTestSupport.TENANT_ID;
import static br.com.menthoros.backend.testsupport.JwtTestSupport.adminJwt;
import static br.com.menthoros.backend.testsupport.JwtTestSupport.atletaJwt;
import static br.com.menthoros.backend.testsupport.JwtTestSupport.tecnicoJwt;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

    private static SeasonPlanResponseDto plano() {
        return new SeasonPlanResponseDto(UUID.randomUUID(), SeasonPlanStatus.PROPOSED, UUID.randomUUID(),
                "Maratona alvo", LocalDate.of(2027, 4, 4), false, 20, 40.0, new BigDecimal("52.0"),
                List.of(new SeasonPlanResponseDto.Week(1, LocalDate.of(2026, 11, 16), TrainingPhase.BASE,
                        new BigDecimal("40.7"), 285.0, false, null, WeekValueSource.ENGINE)),
                List.of(new SeasonPlanResponseDto.Milestone(6, UUID.randomUUID(), "Meia de teste",
                        LocalDate.of(2026, 12, 27), br.com.menthoros.backend.enums.DistanciaProva.KM_21)),
                List.of(new SeasonPlanResponseDto.Validation(
                        br.com.menthoros.backend.domain.planner.SeasonPlanValidator.Rule.RAMP, "WARN", 2, 12.5, 10.0,
                        "Volume sobe 12,5% sobre a semana anterior (teto 10%)")));
    }
}
