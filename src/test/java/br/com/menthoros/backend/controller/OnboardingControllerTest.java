package br.com.menthoros.backend.controller;

import br.com.menthoros.backend.domain.planner.AthleteBaseline;
import br.com.menthoros.backend.domain.planner.AthleteConstraints;
import br.com.menthoros.backend.domain.planner.OnboardingContext;
import br.com.menthoros.backend.domain.planner.PlanningPolicy;
import br.com.menthoros.backend.domain.planner.ReviewMode;
import br.com.menthoros.backend.domain.planner.TrainingPhase;
import br.com.menthoros.backend.dto.output.AtletaOnboardingOutputDto;
import br.com.menthoros.backend.dto.output.CalibracaoStatusOutputDto;
import br.com.menthoros.backend.dto.output.OnboardingConclusaoOutputDto;
import br.com.menthoros.backend.entity.PerfilOnboardingAtleta;
import br.com.menthoros.backend.entity.Prova;
import br.com.menthoros.backend.enums.DistanciaProva;
import br.com.menthoros.backend.enums.NivelExperiencia;
import br.com.menthoros.backend.enums.TipoProva;
import br.com.menthoros.backend.exception.AccessDeniedException;
import br.com.menthoros.backend.mapper.OnboardingMapper;
import br.com.menthoros.backend.multitenancy.TenantContext;
import br.com.menthoros.backend.security.JwtTenantFilter;
import br.com.menthoros.backend.security.StructuredLoggingFilter;
import br.com.menthoros.backend.services.onboarding.CalibrationStage;
import br.com.menthoros.backend.services.onboarding.CalibrationStatusResult;
import br.com.menthoros.backend.services.onboarding.ConfidenceTier;
import br.com.menthoros.backend.services.onboarding.OnboardingConclusionResult;
import br.com.menthoros.backend.services.onboarding.OnboardingDraftInput;
import br.com.menthoros.backend.services.onboarding.OnboardingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = OnboardingController.class, excludeFilters = @ComponentScan.Filter(
        type = FilterType.ASSIGNABLE_TYPE,
        classes = {JwtTenantFilter.class, StructuredLoggingFilter.class}))
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("OnboardingController")
class OnboardingControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private OnboardingService onboardingService;
    @MockitoBean private OnboardingMapper onboardingMapper;

    private final UUID atletaId = UUID.randomUUID();
    private final UUID tenantId = UUID.randomUUID();
    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        TenantContext.setTenantId(tenantId);
        setAtletaAuth();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Nested
    @DisplayName("POST /api/v1/atletas/{atletaId}/onboarding")
    class PostSalvarRascunho {

        @Test
        @DisplayName("200 com body válido")
        void retorna200QuandoDadosValidos() throws Exception {
            OnboardingDraftInput draftInput = draftInput();
            PerfilOnboardingAtleta perfil = new PerfilOnboardingAtleta();
            when(onboardingMapper.toDraftInput(any(), eq(false))).thenReturn(draftInput);
            when(onboardingService.salvarRascunho(eq(atletaId), eq(tenantId), any(), eq(false))).thenReturn(perfil);
            when(onboardingMapper.toOutputDto(perfil)).thenReturn(stubOutputDto());

            mockMvc.perform(post("/api/v1/atletas/{atletaId}/onboarding", atletaId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(bodyValido()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("RASCUNHO"));

            verify(onboardingService).salvarRascunho(eq(atletaId), eq(tenantId), any(), eq(false));
        }

        @Test
        @DisplayName("passa chamadorEhCoach=true quando o usuário tem role TECNICO")
        void passaFlagCoachQuandoRoleTecnico() throws Exception {
            setTecnicoAuth();
            when(onboardingMapper.toDraftInput(any(), eq(true))).thenReturn(draftInput());
            when(onboardingService.salvarRascunho(eq(atletaId), eq(tenantId), any(), eq(true)))
                    .thenReturn(new PerfilOnboardingAtleta());
            when(onboardingMapper.toOutputDto(any())).thenReturn(stubOutputDto());

            mockMvc.perform(post("/api/v1/atletas/{atletaId}/onboarding", atletaId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(bodyValido()))
                    .andExpect(status().isOk());

            verify(onboardingService).salvarRascunho(eq(atletaId), eq(tenantId), any(), eq(true));
        }

        @Test
        @DisplayName("403 quando atleta tenta editar onboarding de outro (AccessDeniedException do service)")
        void retorna403QuandoAccessDenied() throws Exception {
            when(onboardingMapper.toDraftInput(any(), anyBoolean())).thenReturn(draftInput());
            when(onboardingService.salvarRascunho(any(), any(), any(), anyBoolean()))
                    .thenThrow(new AccessDeniedException("Atleta so pode acessar o proprio onboarding"));

            mockMvc.perform(post("/api/v1/atletas/{atletaId}/onboarding", atletaId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(bodyValido()))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/atletas/{atletaId}/onboarding")
    class GetBuscarRascunho {

        @Test
        @DisplayName("200 com o rascunho quando existe")
        void retorna200QuandoExiste() throws Exception {
            PerfilOnboardingAtleta perfil = new PerfilOnboardingAtleta();
            when(onboardingService.buscarRascunho(atletaId, tenantId, false)).thenReturn(Optional.of(perfil));
            when(onboardingMapper.toOutputDto(perfil)).thenReturn(stubOutputDto());

            mockMvc.perform(get("/api/v1/atletas/{atletaId}/onboarding", atletaId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("RASCUNHO"));
        }

        @Test
        @DisplayName("204 quando o atleta ainda não iniciou o onboarding")
        void retorna204QuandoAusente() throws Exception {
            when(onboardingService.buscarRascunho(atletaId, tenantId, false)).thenReturn(Optional.empty());

            mockMvc.perform(get("/api/v1/atletas/{atletaId}/onboarding", atletaId))
                    .andExpect(status().isNoContent());
        }
    }

    @Nested
    @DisplayName("POST /api/v1/atletas/{atletaId}/onboarding/concluir")
    class PostConcluirOnboarding {

        @Test
        @DisplayName("200 com body válido")
        void retorna200QuandoDadosValidos() throws Exception {
            OnboardingConclusionResult resultado = conclusionResult();
            when(onboardingService.concluirOnboarding(
                    eq(atletaId), eq(tenantId), eq(false), any(), any(), any(), any(), any()))
                    .thenReturn(resultado);
            when(onboardingMapper.toConclusaoOutputDto(resultado)).thenReturn(conclusaoOutputDto());

            mockMvc.perform(post("/api/v1/atletas/{atletaId}/onboarding/concluir", atletaId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(bodyConclusaoValido()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("COMPLETO"));
        }

        @Test
        @DisplayName("400 quando dataProva está ausente")
        void retorna400QuandoCampoObrigatorioAusente() throws Exception {
            var bodySemDataProva = mapper.writeValueAsString(Map.of(
                    "tipoProva", "CORRIDA_RUA", "distancia", "KM_21"));

            mockMvc.perform(post("/api/v1/atletas/{atletaId}/onboarding/concluir", atletaId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(bodySemDataProva))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("409 quando Atleta foi editado depois do início do rascunho (DomainConflictException)")
        void retorna409QuandoConflito() throws Exception {
            when(onboardingService.concluirOnboarding(
                    eq(atletaId), eq(tenantId), eq(false), any(), any(), any(), any(), any()))
                    .thenThrow(new br.com.menthoros.backend.exception.DomainConflictException("conflito"));

            mockMvc.perform(post("/api/v1/atletas/{atletaId}/onboarding/concluir", atletaId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(bodyConclusaoValido()))
                    .andExpect(status().isConflict());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/atletas/{atletaId}/calibracao")
    class GetStatusCalibracao {

        @Test
        @DisplayName("200 quando o atleta está em calibração")
        void retorna200QuandoEmCalibracao() throws Exception {
            CalibrationStatusResult status = new CalibrationStatusResult(
                    TrainingPhase.CALIBRATION, CalibrationStage.CALIBRATION, 2, 40);
            when(onboardingService.obterStatusCalibracao(atletaId, tenantId, false)).thenReturn(Optional.of(status));
            when(onboardingMapper.toCalibracaoStatusOutputDto(status)).thenReturn(
                    new CalibracaoStatusOutputDto(TrainingPhase.CALIBRATION, CalibrationStage.CALIBRATION, 2, 40));

            mockMvc.perform(get("/api/v1/atletas/{atletaId}/calibracao", atletaId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.weekNumber").value(2));
        }

        @Test
        @DisplayName("204 quando o atleta não está em calibração")
        void retorna204QuandoAusente() throws Exception {
            when(onboardingService.obterStatusCalibracao(atletaId, tenantId, false)).thenReturn(Optional.empty());

            mockMvc.perform(get("/api/v1/atletas/{atletaId}/calibracao", atletaId))
                    .andExpect(status().isNoContent());
        }
    }

    private void setAtletaAuth() {
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken("atleta", null, List.of(new SimpleGrantedAuthority("ROLE_ATLETA"))));
    }

    private void setTecnicoAuth() {
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken("tecnico", null, List.of(new SimpleGrantedAuthority("ROLE_TECNICO"))));
    }

    private String bodyValido() throws Exception {
        return mapper.writeValueAsString(Map.of(
                "objetivo", "Correr uma maratona",
                "nivelExperiencia", "INTERMEDIARIO"
        ));
    }

    private String bodyConclusaoValido() throws Exception {
        return mapper.writeValueAsString(Map.of(
                "dataProva", LocalDate.now().plusMonths(3).toString(),
                "tipoProva", "CORRIDA_RUA",
                "distancia", "KM_21",
                "nomeProva", "Meia SP"
        ));
    }

    private OnboardingDraftInput draftInput() {
        return new OnboardingDraftInput(
                "Correr uma maratona", NivelExperiencia.INTERMEDIARIO, List.of(), null, null,
                null, null, null, null, null, null, null, null, false, null, null, null);
    }

    private AtletaOnboardingOutputDto stubOutputDto() {
        return new AtletaOnboardingOutputDto(
                UUID.randomUUID(), "RASCUNHO", "Correr uma maratona", NivelExperiencia.INTERMEDIARIO,
                List.of(), null, false, null, null, null, null, null, null, null, null, false,
                null, null, null, null, null);
    }

    private OnboardingConclusionResult conclusionResult() {
        Prova prova = Prova.builder().id(UUID.randomUUID()).provaAlvo(true).build();
        PerfilOnboardingAtleta perfil = new PerfilOnboardingAtleta();
        perfil.setStatus("COMPLETO");
        OnboardingContext context = new OnboardingContext(
                new AthleteBaseline(50.0, LocalDate.now()), 0.8,
                new PlanningPolicy(ReviewMode.EXCEPTION_ONLY, 1.0, true),
                new AthleteConstraints(List.of(), null, null, List.of()));
        return new OnboardingConclusionResult(perfil, prova, context, ConfidenceTier.A);
    }

    private OnboardingConclusaoOutputDto conclusaoOutputDto() {
        return new OnboardingConclusaoOutputDto("COMPLETO", null, 50.0, LocalDate.now(), 0.8, ConfidenceTier.A);
    }
}
