package br.com.menthoros.backend.controller;

import br.com.menthoros.backend.config.core.JacksonConfig;
import br.com.menthoros.backend.domain.planner.TrainingPhase;
import br.com.menthoros.backend.dto.output.AthleteSeasonPlanDto;
import br.com.menthoros.backend.enums.DistanciaProva;
import br.com.menthoros.backend.exception.DomainNotFoundException;
import br.com.menthoros.backend.repository.TenantValidationRepository;
import br.com.menthoros.backend.repository.UsuarioRepository;
import br.com.menthoros.backend.services.AtletaProgressService;
import br.com.menthoros.backend.services.SeasonPlanService;
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

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static br.com.menthoros.backend.testsupport.JwtTestSupport.atletaJwt;
import static br.com.menthoros.backend.testsupport.JwtTestSupport.tecnicoJwt;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Contrato HTTP da leitura da temporada pelo atleta (add-season-plan-approve-athlete-view, A2). */
@WebMvcTest(AtletaSeasonPlanController.class)
@Import({JacksonConfig.class, AuthWebMvcTestConfig.class})
class AtletaSeasonPlanControllerTest {

    private static final String URL = "/api/v1/atletas/me/season-plan";

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

    @Nested
    @DisplayName("GET /atletas/me/season-plan")
    class Mine {

        @Test
        @DisplayName("200 com a temporada aprovada, sem validações nem dados do treinador")
        void aprovada() throws Exception {
            when(seasonPlanService.findForAuthenticatedAthlete()).thenReturn(Optional.of(new AthleteSeasonPlanDto(
                    "Maratona alvo", LocalDate.of(2027, 4, 4), DistanciaProva.KM_42, 135, TrainingPhase.BASE,
                    List.of(new AthleteSeasonPlanDto.Milestone("Meia", LocalDate.of(2026, 12, 27), DistanciaProva.KM_21)))));

            mockMvc.perform(get(URL).with(atletaJwt()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.raceName").value("Maratona alvo"))
                    .andExpect(jsonPath("$.daysToRace").value(135))
                    .andExpect(jsonPath("$.currentPhase").value("BASE"))
                    .andExpect(jsonPath("$.distance.value").value("42K"))
                    .andExpect(jsonPath("$.upcomingMilestones[0].name").value("Meia"))
                    .andExpect(jsonPath("$.validations").doesNotExist())
                    .andExpect(jsonPath("$.approvedBy").doesNotExist());
        }

        @Test
        @DisplayName("204 sem temporada aprovada visível")
        void semTemporada() throws Exception {
            when(seasonPlanService.findForAuthenticatedAthlete()).thenReturn(Optional.empty());

            mockMvc.perform(get(URL).with(atletaJwt())).andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("404 quando o usuário não tem atleta no tenant")
        void semAtleta() throws Exception {
            when(seasonPlanService.findForAuthenticatedAthlete()).thenThrow(new DomainNotFoundException("atleta"));

            mockMvc.perform(get(URL).with(atletaJwt())).andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("403 para TECNICO, sem chegar ao serviço")
        void tecnicoProibido() throws Exception {
            mockMvc.perform(get(URL).with(tecnicoJwt())).andExpect(status().isForbidden());

            verifyNoInteractions(seasonPlanService);
        }
    }
}
