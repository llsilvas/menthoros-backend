package br.com.menthoros.backend.controller;

import br.com.menthoros.backend.dto.input.ReconciliacaoAcaoRequestDto;
import br.com.menthoros.backend.entity.Assessoria;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.TreinoRealizado;
import br.com.menthoros.backend.enums.AtletaStatus;
import br.com.menthoros.backend.enums.DiaSemana;
import br.com.menthoros.backend.enums.FonteDados;
import br.com.menthoros.backend.enums.NivelExperiencia;
import br.com.menthoros.backend.enums.PlanoAssessoria;
import br.com.menthoros.backend.enums.ReconciliationActionType;
import br.com.menthoros.backend.enums.ReconciliationStatus;
import br.com.menthoros.backend.enums.TipoTreino;
import br.com.menthoros.backend.repository.AssessoriaRepository;
import br.com.menthoros.backend.repository.AtletaRepository;
import br.com.menthoros.backend.repository.TreinoRealizadoRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for Task 5.1 Manual Reconciliation endpoints.
 *
 * Validates HTTP contract for 3 endpoints:
 * 1. GET /api/v1/reconciliation/atletas/{atletaId}/pendentes - list pending activities
 * 2. GET /api/v1/reconciliation/{treinoRealizadoId}/candidatos - list candidate matches
 * 3. POST /api/v1/reconciliation/{treinoRealizadoId}/acao - dispatch reconciliation action
 *
 * Uses @SpringBootTest with MockMvc (not standalone) and real Spring context.
 * Tests HTTP contract validation, security, error handling, and status codes.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("integration")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("Task 5.1 - Manual Reconciliation Endpoints")
class Task5p1ControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AssessoriaRepository assessoriaRepository;

    @Autowired
    private AtletaRepository atletaRepository;

    @Autowired
    private TreinoRealizadoRepository treinoRealizadoRepository;

    private Assessoria assessoria;
    private Atleta atleta;
    private UUID tenantId;
    private UUID atletaId;
    private TreinoRealizado treinoRealizado;

    @BeforeEach
    void setUp() {
        // Create tenant (assessoria)
        assessoria = new Assessoria();
        assessoria.setNome("Test Assessoria");
        assessoria.setDominio("test-domain-" + UUID.randomUUID());
        assessoria.setPlano(PlanoAssessoria.BASIC);
        assessoria = assessoriaRepository.save(assessoria);
        tenantId = assessoria.getId();

        // Create athlete
        atleta = new Atleta();
        atleta.setNome("Test Athlete");
        atleta.setObjetivo("Treinamento");
        atleta.setNivelExperiencia(NivelExperiencia.INTERMEDIARIO);
        atleta.setAtivo(AtletaStatus.ATIVO);
        atleta.setAssessoria(assessoria);
        atleta = atletaRepository.save(atleta);
        atletaId = atleta.getId();

        // Create realized activity (AMBIGUO status for pending review)
        treinoRealizado = new TreinoRealizado();
        treinoRealizado.setAtleta(atleta);
        treinoRealizado.setDataTreino(LocalDate.now());
        treinoRealizado.setDiaSemana(DiaSemana.SEGUNDA);
        treinoRealizado.setTipoTreino(TipoTreino.FACIL);
        treinoRealizado.setDuracaoMin(Duration.ofMinutes(55));
        treinoRealizado.setDistanciaKm(new BigDecimal("9.5"));
        treinoRealizado.setExternalId("strava-123456");
        treinoRealizado.setFonteDados(FonteDados.STRAVA);
        treinoRealizado.setReconciliationStatus(ReconciliationStatus.AMBIGUO);
        treinoRealizado.setReconciliationScore(new BigDecimal("0.75"));
        treinoRealizado.setTenantId(tenantId);
        treinoRealizado = treinoRealizadoRepository.save(treinoRealizado);
    }

    @Nested
    @DisplayName("GET /api/v1/reconciliation/atletas/{atletaId}/pendentes")
    class GetPendentesEndpoint {

        @Test
        @DisplayName("Happy path: 200 OK with paginated results")
        @WithMockUser(username = "coach@example.com", roles = {"USER"})
        void shouldReturnOkWithPaginatedPendingActivities() throws Exception {
            mockMvc.perform(get("/api/v1/reconciliation/atletas/{atletaId}/pendentes", atletaId)
                    .header("X-Tenant-ID", tenantId)
                    .param("page", "0")
                    .param("size", "20")
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.content").isArray())
                    .andExpect(jsonPath("$.content[0].id").exists())
                    .andExpect(jsonPath("$.content[0].atletaId").value(atletaId.toString()))
                    .andExpect(jsonPath("$.content[0].reconciliationStatus").value("AMBIGUO"))
                    .andExpect(jsonPath("$.pageable.pageNumber").value(0))
                    .andExpect(jsonPath("$.pageable.pageSize").value(20))
                    .andExpect(jsonPath("$.totalElements").value(greaterThanOrEqualTo(1)));
        }

        @Test
        @DisplayName("Filter by single status (AMBIGUO)")
        @WithMockUser(username = "coach@example.com", roles = {"USER"})
        void shouldFilterByStatusAmbiguo() throws Exception {
            mockMvc.perform(get("/api/v1/reconciliation/atletas/{atletaId}/pendentes", atletaId)
                    .header("X-Tenant-ID", tenantId)
                    .param("statuses", "AMBIGUO")
                    .param("page", "0")
                    .param("size", "20")
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].reconciliationStatus").value("AMBIGUO"));
        }

        @Test
        @DisplayName("Filter by multiple statuses (AMBIGUO,NAO_PLANEJADO)")
        @WithMockUser(username = "coach@example.com", roles = {"USER"})
        void shouldFilterByMultipleStatuses() throws Exception {
            // Create NAO_PLANEJADO activity
            TreinoRealizado orfao = new TreinoRealizado();
            orfao.setAtleta(atleta);
            orfao.setDataTreino(LocalDate.now().minusDays(1));
            orfao.setDiaSemana(DiaSemana.SEGUNDA);
            orfao.setTipoTreino(TipoTreino.REGENERATIVO);
            orfao.setDuracaoMin(Duration.ofMinutes(30));
            orfao.setDistanciaKm(new BigDecimal("5.0"));
            orfao.setExternalId("strava-999");
            orfao.setFonteDados(FonteDados.STRAVA);
            orfao.setReconciliationStatus(ReconciliationStatus.NAO_PLANEJADO);
            orfao.setTenantId(tenantId);
            treinoRealizadoRepository.save(orfao);

            mockMvc.perform(get("/api/v1/reconciliation/atletas/{atletaId}/pendentes", atletaId)
                    .header("X-Tenant-ID", tenantId)
                    .param("statuses", "AMBIGUO", "NAO_PLANEJADO")
                    .param("page", "0")
                    .param("size", "20")
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(greaterThanOrEqualTo(2)));
        }


        @Test
        @DisplayName("400 Bad Request when invalid status provided")
        @WithMockUser(username = "coach@example.com", roles = {"USER"})
        void shouldReturn400ForInvalidStatus() throws Exception {
            mockMvc.perform(get("/api/v1/reconciliation/atletas/{atletaId}/pendentes", atletaId)
                    .header("X-Tenant-ID", tenantId)
                    .param("statuses", "INVALID_STATUS")
                    .param("page", "0")
                    .param("size", "20")
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("400 Bad Request when non-pending status provided (e.g., VINCULADO_AUTOMATICO)")
        @WithMockUser(username = "coach@example.com", roles = {"USER"})
        void shouldReturn400ForNonPendingStatus() throws Exception {
            mockMvc.perform(get("/api/v1/reconciliation/atletas/{atletaId}/pendentes", atletaId)
                    .header("X-Tenant-ID", tenantId)
                    .param("statuses", "VINCULADO_AUTOMATICO")
                    .param("page", "0")
                    .param("size", "20")
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Missing X-Tenant-ID header returns 400 Bad Request")
        @WithMockUser(username = "coach@example.com", roles = {"USER"})
        void shouldHandleWhenTenantHeaderMissing() throws Exception {
            // Missing X-Tenant-ID header triggers MissingRequestHeaderException → GlobalExceptionHandler returns 400
            mockMvc.perform(get("/api/v1/reconciliation/atletas/{atletaId}/pendentes", atletaId)
                    .param("page", "0")
                    .param("size", "20")
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Pagination: page and size parameters work correctly")
        @WithMockUser(username = "coach@example.com", roles = {"USER"})
        void shouldRespectPaginationParameters() throws Exception {
            mockMvc.perform(get("/api/v1/reconciliation/atletas/{atletaId}/pendentes", atletaId)
                    .header("X-Tenant-ID", tenantId)
                    .param("page", "0")
                    .param("size", "10")
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.pageable.pageSize").value(10));
        }

        @Test
        @DisplayName("Response DTO has all required fields")
        @WithMockUser(username = "coach@example.com", roles = {"USER"})
        void shouldHaveAllRequiredFieldsInDto() throws Exception {
            mockMvc.perform(get("/api/v1/reconciliation/atletas/{atletaId}/pendentes", atletaId)
                    .header("X-Tenant-ID", tenantId)
                    .param("page", "0")
                    .param("size", "20")
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].id").exists())
                    .andExpect(jsonPath("$.content[0].externalId").exists())
                    .andExpect(jsonPath("$.content[0].atletaId").exists())
                    .andExpect(jsonPath("$.content[0].dataTreino").exists())
                    .andExpect(jsonPath("$.content[0].tipoTreino").exists())
                    .andExpect(jsonPath("$.content[0].reconciliationStatus").exists())
                    .andExpect(jsonPath("$.content[0].reconciliationScore").exists());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/reconciliation/{treinoRealizadoId}/candidatos")
    class GetCandidatosEndpoint {

        @Test
        @DisplayName("Happy path: 200 OK with candidate list")
        @WithMockUser(username = "coach@example.com", roles = {"USER"})
        void shouldReturnOkWithCandidatesList() throws Exception {
            mockMvc.perform(get("/api/v1/reconciliation/{treinoRealizadoId}/candidatos", treinoRealizado.getId())
                    .header("X-Tenant-ID", tenantId)
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$").isArray());
        }

        @Test
        @DisplayName("Response DTO has all required score breakdown fields")
        @WithMockUser(username = "coach@example.com", roles = {"USER"})
        void shouldHaveScoreBreakdownFields() throws Exception {
            mockMvc.perform(get("/api/v1/reconciliation/{treinoRealizadoId}/candidatos", treinoRealizado.getId())
                    .header("X-Tenant-ID", tenantId)
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray());
        }


        @Test
        @DisplayName("Empty candidates list when no matches found")
        @WithMockUser(username = "coach@example.com", roles = {"USER"})
        void shouldReturnEmptyListWhenNoMatches() throws Exception {
            // Create activity with no candidates (future date)
            TreinoRealizado noMatchActivity = new TreinoRealizado();
            noMatchActivity.setAtleta(atleta);
            noMatchActivity.setDataTreino(LocalDate.now().plusDays(30));
            noMatchActivity.setDiaSemana(DiaSemana.SEGUNDA);
            noMatchActivity.setTipoTreino(TipoTreino.TIRO);
            noMatchActivity.setDuracaoMin(Duration.ofMinutes(45));
            noMatchActivity.setDistanciaKm(new BigDecimal("2.0"));
            noMatchActivity.setExternalId("strava-no-match");
            noMatchActivity.setFonteDados(FonteDados.STRAVA);
            noMatchActivity.setReconciliationStatus(ReconciliationStatus.NAO_PLANEJADO);
            noMatchActivity.setTenantId(tenantId);
            noMatchActivity = treinoRealizadoRepository.save(noMatchActivity);

            mockMvc.perform(get("/api/v1/reconciliation/{treinoRealizadoId}/candidatos", noMatchActivity.getId())
                    .header("X-Tenant-ID", tenantId)
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$", hasSize(0)));
        }

    }

    @Nested
    @DisplayName("POST /api/v1/reconciliation/{treinoRealizadoId}/acao")
    class PostAcaoEndpoint {

        @Test
        @DisplayName("400 Bad Request when action is missing (null)")
        @WithMockUser(username = "coach@example.com", roles = {"USER"})
        void shouldReturn400WhenActionIsNull() throws Exception {
            String invalidJson = "{\"action\": null}";

            mockMvc.perform(post("/api/v1/reconciliation/{treinoRealizadoId}/acao", treinoRealizado.getId())
                    .header("X-Tenant-ID", tenantId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(invalidJson))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("400 Bad Request when VINCULAR_MANUALMENTE without treinoPlanejadoId")
        @WithMockUser(username = "coach@example.com", roles = {"USER"})
        void shouldReturn400WhenVincularWithoutTreinoPlanejadoId() throws Exception {
            ReconciliacaoAcaoRequestDto request = new ReconciliacaoAcaoRequestDto(
                    ReconciliationActionType.VINCULAR_MANUALMENTE,
                    null,
                    "Missing planned ID"
            );

            mockMvc.perform(post("/api/v1/reconciliation/{treinoRealizadoId}/acao", treinoRealizado.getId())
                    .header("X-Tenant-ID", tenantId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }




        @Test
        @DisplayName("Security: 401 Unauthorized when authentication missing")
        void shouldReturn401WhenNotAuthenticated() throws Exception {
            ReconciliacaoAcaoRequestDto request = new ReconciliacaoAcaoRequestDto(
                    ReconciliationActionType.MARCAR_NAO_PLANEJADO,
                    null,
                    null
            );

            mockMvc.perform(post("/api/v1/reconciliation/{treinoRealizadoId}/acao", treinoRealizado.getId())
                    .header("X-Tenant-ID", tenantId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized());
        }

    }

    @Nested
    @DisplayName("Security and Multi-tenancy Enforcement")
    class SecurityAndMultiTenancy {

        @Test
        @DisplayName("X-Tenant-ID header is required (missing header returns 400)")
        @WithMockUser(username = "coach@example.com", roles = {"USER"})
        void shouldEnforceTenantHeaderRequirement() throws Exception {
            // Without X-Tenant-ID header, MissingRequestHeaderException caught by GlobalExceptionHandler → 400 Bad Request
            mockMvc.perform(get("/api/v1/reconciliation/atletas/{atletaId}/pendentes", atletaId))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Authentication required: 401 without @WithMockUser on POST /acao")
        void shouldReturn401WhenUnauthenticatedOnPostAcao() throws Exception {
            ReconciliacaoAcaoRequestDto request = new ReconciliacaoAcaoRequestDto(
                    ReconciliationActionType.MARCAR_NAO_PLANEJADO,
                    null,
                    null
            );

            mockMvc.perform(post("/api/v1/reconciliation/{treinoRealizadoId}/acao", treinoRealizado.getId())
                    .header("X-Tenant-ID", tenantId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized());
        }

    }

    @Nested
    @DisplayName("HTTP Contract and Error Messages")
    class HttpContractValidation {

        @Test
        @DisplayName("GET returns application/json Content-Type")
        @WithMockUser(username = "coach@example.com", roles = {"USER"})
        void shouldReturnJsonContentTypeOnGet() throws Exception {
            mockMvc.perform(get("/api/v1/reconciliation/atletas/{atletaId}/pendentes", atletaId)
                    .header("X-Tenant-ID", tenantId)
                    .param("page", "0")
                    .param("size", "20"))
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON));
        }

        @Test
        @DisplayName("POST returns application/json Content-Type")
        @WithMockUser(username = "coach@example.com", roles = {"USER"})
        void shouldReturnJsonContentTypeOnPost() throws Exception {
            ReconciliacaoAcaoRequestDto request = new ReconciliacaoAcaoRequestDto(
                    ReconciliationActionType.MARCAR_NAO_PLANEJADO,
                    null,
                    null
            );

            mockMvc.perform(post("/api/v1/reconciliation/{treinoRealizadoId}/acao", treinoRealizado.getId())
                    .header("X-Tenant-ID", tenantId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON));
        }

        @Test
        @DisplayName("Error response includes proper status code and message structure")
        @WithMockUser(username = "coach@example.com", roles = {"USER"})
        void shouldReturnStructuredErrorResponse() throws Exception {
            mockMvc.perform(get("/api/v1/reconciliation/atletas/{atletaId}/pendentes", atletaId)
                    .header("X-Tenant-ID", tenantId)
                    .param("statuses", "INVALID_STATUS")
                    .param("page", "0")
                    .param("size", "20"))
                    .andExpect(status().isBadRequest());
        }


    }
}
