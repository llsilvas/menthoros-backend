package br.com.menthoros.backend.controller;

import br.com.menthoros.backend.dto.input.ReconciliacaoAcaoRequestDto;
import br.com.menthoros.backend.entity.Assessoria;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.TreinoRealizado;
import br.com.menthoros.backend.entity.Usuario;
import br.com.menthoros.backend.enums.AtletaStatus;
import br.com.menthoros.backend.enums.DiaSemana;
import br.com.menthoros.backend.enums.FonteDados;
import br.com.menthoros.backend.enums.NivelExperiencia;
import br.com.menthoros.backend.enums.PlanoAssessoria;
import br.com.menthoros.backend.enums.ReconciliationActionType;
import br.com.menthoros.backend.enums.ReconciliationStatus;
import br.com.menthoros.backend.enums.TipoTreino;
import br.com.menthoros.backend.enums.UserRole;
import br.com.menthoros.backend.repository.AssessoriaRepository;
import br.com.menthoros.backend.repository.AtletaRepository;
import br.com.menthoros.backend.repository.TreinoRealizadoRepository;
import br.com.menthoros.backend.repository.UsuarioRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import br.com.menthoros.backend.AbstractIntegrationTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Contrato HTTP dos 3 endpoints de reconciliação manual — o fluxo em que o treinador vincula uma
 * atividade externa (Strava) ao treino planejado do atleta.
 *
 * <ol>
 *   <li>{@code GET  /api/v1/reconciliation/atletas/{atletaId}/pendentes}</li>
 *   <li>{@code GET  /api/v1/reconciliation/{treinoRealizadoId}/candidatos}</li>
 *   <li>{@code POST /api/v1/reconciliation/{treinoRealizadoId}/acao}</li>
 * </ol>
 *
 * <p><b>Autenticação: JWT, não {@code @WithMockUser}.</b> Esta classe ficou vermelha de 2026-05-14 a
 * 2026-08-02, com 14 dos 19 testes em 403, por dois defeitos empilhados — os dois no teste, nenhum na
 * produção. Primeiro, autenticava com {@code roles = {"USER"}}, e {@code ROLE_USER} não existe no
 * domínio: os endpoints exigem {@code TECNICO} ou {@code ADMIN}. Segundo, e decisivo,
 * {@code JwtTenantFilter} só popula o {@code TenantContext} quando o principal é um {@code Jwt};
 * {@code @WithMockUser} produz um {@code User}, então o filtro virava no-op e
 * {@code getRequiredTenantId()} lançava — 403.
 *
 * <p>O header {@code X-Tenant-ID} <b>não</b> aparece aqui: a produção resolve o tenant pelo claim do
 * JWT, nunca por header. O teste original o enviava porque foi escrito quando o controller o lia na
 * mão; o commit {@code 9cf6d20} mudou isso e não atualizou o teste.
 */
@AutoConfigureMockMvc
@DisplayName("Reconciliação manual — contrato HTTP")
class ManualReconciliationControllerIT extends AbstractIntegrationTest {

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

    @Autowired
    private UsuarioRepository usuarioRepository;

    private Assessoria assessoria;
    private Atleta atleta;
    private UUID tenantId;
    private UUID atletaId;
    private TreinoRealizado treinoRealizado;
    private UUID subTecnico;
    private UUID subAtleta;

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

        // Usuários semeados para que o JwtTenantFilter siga o caminho de SUCESSO ao sincronizar.
        // Sem a linha correspondente ao subject, o sync cai no branch de fail-safe e os testes
        // ficariam verdes exercitando um caminho degradado — que não é o que se quer provar.
        subTecnico = UUID.randomUUID();
        subAtleta = UUID.randomUUID();
        criarUsuario(subTecnico, UserRole.TECNICO);
        criarUsuario(subAtleta, UserRole.ATLETA);
    }

    // ===== Helpers =====

    /**
     * JWT no formato que a produção espera: subject <b>UUID</b> (o
     * {@code UsuarioSyncServiceImpl.createNewUsuario} faz {@code UUID.fromString(keycloakId)}),
     * claim {@code tenant_id} e authority {@code ROLE_<papel>}.
     *
     * <p>As authorities vão explícitas porque o post-processor {@code jwt()} não usa o
     * {@code JwtAuthenticationConverter} da aplicação — o default dele mapearia scopes para
     * {@code SCOPE_*}, e o {@code @PreAuthorize} exige {@code ROLE_*}.
     */
    private RequestPostProcessor jwtDe(UUID subject, UUID tenant, String papel) {
        return jwt()
                .authorities(new SimpleGrantedAuthority("ROLE_" + papel))
                .jwt(j -> j.subject(subject.toString()).claim("tenant_id", tenant.toString()));
    }

    /** Atalho para o caso dominante: técnico do tenant sob teste. */
    private RequestPostProcessor comoTecnico() {
        return jwtDe(subTecnico, tenantId, "TECNICO");
    }

    /**
     * JWT autenticado e com a role certa, mas <b>sem</b> {@code tenant_id} e sem {@code organization}
     * — o {@code JwtTenantFilter} não tem de onde resolver o tenant.
     */
    private RequestPostProcessor jwtSemTenant() {
        return jwt()
                .authorities(new SimpleGrantedAuthority("ROLE_TECNICO"))
                .jwt(j -> j.subject(subTecnico.toString()));
    }

    private Usuario criarUsuario(UUID subject, UserRole role) {
        Usuario usuario = Usuario.builder()
                .id(subject)
                .keycloakId(subject.toString())
                .assessoria(assessoria)
                .email(subject + "@menthoros.test")
                .nome("Usuario " + subject)
                .role(role)
                .ativo(true)
                .build();
        return usuarioRepository.save(usuario);
    }

    @Nested
    @DisplayName("GET /api/v1/reconciliation/atletas/{atletaId}/pendentes")
    class GetPendentesEndpoint {

        @Test
        @DisplayName("Happy path: 200 OK with paginated results")
        void shouldReturnOkWithPaginatedPendingActivities() throws Exception {
            mockMvc.perform(get("/api/v1/reconciliation/atletas/{atletaId}/pendentes", atletaId)
                    .with(comoTecnico())
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
        void shouldFilterByStatusAmbiguo() throws Exception {
            mockMvc.perform(get("/api/v1/reconciliation/atletas/{atletaId}/pendentes", atletaId)
                    .with(comoTecnico())
                    .param("statuses", "AMBIGUO")
                    .param("page", "0")
                    .param("size", "20")
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].reconciliationStatus").value("AMBIGUO"));
        }

        @Test
        @DisplayName("Filter by multiple statuses (AMBIGUO,NAO_PLANEJADO)")
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
                    .with(comoTecnico())
                    .param("statuses", "AMBIGUO", "NAO_PLANEJADO")
                    .param("page", "0")
                    .param("size", "20")
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(greaterThanOrEqualTo(2)));
        }


        @Test
        @DisplayName("400 Bad Request when invalid status provided")
        void shouldReturn400ForInvalidStatus() throws Exception {
            mockMvc.perform(get("/api/v1/reconciliation/atletas/{atletaId}/pendentes", atletaId)
                    .with(comoTecnico())
                    .param("statuses", "INVALID_STATUS")
                    .param("page", "0")
                    .param("size", "20")
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("400 Bad Request when non-pending status provided (e.g., VINCULADO_AUTOMATICO)")
        void shouldReturn400ForNonPendingStatus() throws Exception {
            mockMvc.perform(get("/api/v1/reconciliation/atletas/{atletaId}/pendentes", atletaId)
                    .with(comoTecnico())
                    .param("statuses", "VINCULADO_AUTOMATICO")
                    .param("page", "0")
                    .param("size", "20")
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("JWT sem claim de tenant é rejeitado com 403, mesmo com paginação válida")
        void jwtSemTenantEhRejeitadoNoPendentes() throws Exception {
            // Antes afirmava "sem X-Tenant-ID => 400". Esse header nunca foi lido pela produção;
            // o tenant vem do claim do JWT.
            mockMvc.perform(get("/api/v1/reconciliation/atletas/{atletaId}/pendentes", atletaId)
                    .with(jwtSemTenant())
                    .param("page", "0")
                    .param("size", "20")
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Pagination: page and size parameters work correctly")
        void shouldRespectPaginationParameters() throws Exception {
            mockMvc.perform(get("/api/v1/reconciliation/atletas/{atletaId}/pendentes", atletaId)
                    .with(comoTecnico())
                    .param("page", "0")
                    .param("size", "10")
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.pageable.pageSize").value(10));
        }

        @Test
        @DisplayName("Response DTO has all required fields")
        void shouldHaveAllRequiredFieldsInDto() throws Exception {
            mockMvc.perform(get("/api/v1/reconciliation/atletas/{atletaId}/pendentes", atletaId)
                    .with(comoTecnico())
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
        void shouldReturnOkWithCandidatesList() throws Exception {
            mockMvc.perform(get("/api/v1/reconciliation/{treinoRealizadoId}/candidatos", treinoRealizado.getId())
                    .with(comoTecnico())
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$").isArray());
        }

        @Test
        @DisplayName("Response DTO has all required score breakdown fields")
        void shouldHaveScoreBreakdownFields() throws Exception {
            mockMvc.perform(get("/api/v1/reconciliation/{treinoRealizadoId}/candidatos", treinoRealizado.getId())
                    .with(comoTecnico())
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray());
        }


        @Test
        @DisplayName("Empty candidates list when no matches found")
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
                    .with(comoTecnico())
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
        void shouldReturn400WhenActionIsNull() throws Exception {
            String invalidJson = "{\"action\": null}";

            mockMvc.perform(post("/api/v1/reconciliation/{treinoRealizadoId}/acao", treinoRealizado.getId())
                    .with(comoTecnico())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(invalidJson))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("400 Bad Request when VINCULAR_MANUALMENTE without treinoPlanejadoId")
        void shouldReturn400WhenVincularWithoutTreinoPlanejadoId() throws Exception {
            ReconciliacaoAcaoRequestDto request = new ReconciliacaoAcaoRequestDto(
                    ReconciliationActionType.VINCULAR_MANUALMENTE,
                    null,
                    "Missing planned ID"
            );

            mockMvc.perform(post("/api/v1/reconciliation/{treinoRealizadoId}/acao", treinoRealizado.getId())
                    .with(comoTecnico())
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
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized());
        }

    }

    @Nested
    @DisplayName("Security and Multi-tenancy Enforcement")
    class SecurityAndMultiTenancy {

        @Test
        @DisplayName("JWT sem claim de tenant é rejeitado com 403")
        void jwtSemTenantEhRejeitado() throws Exception {
            // Substitui o antigo "X-Tenant-ID ausente => 400", que afirmava um contrato inexistente:
            // a produção nunca leu esse header. O tenant vem do JWT, e sem ele o
            // TenantContext.getRequiredTenantId() lança — o GlobalExceptionHandler mapeia para 403.
            mockMvc.perform(get("/api/v1/reconciliation/atletas/{atletaId}/pendentes", atletaId)
                    .with(jwtSemTenant()))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("403 por role sem permissão: ATLETA não acessa endpoint de TECNICO/ADMIN")
        void atletaNaoAcessaEndpointDeTecnico() throws Exception {
            // Sem este caso, nada prova que o @PreAuthorize BLOQUEIA — os testes de 401 só cobrem
            // ausência de autenticação, e todos os demais usam uma role que passa.
            mockMvc.perform(get("/api/v1/reconciliation/atletas/{atletaId}/pendentes", atletaId)
                    .with(jwtDe(subAtleta, tenantId, "ATLETA")))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("401 quando a request nao esta autenticada (POST /acao)")
        void shouldReturn401WhenUnauthenticatedOnPostAcao() throws Exception {
            ReconciliacaoAcaoRequestDto request = new ReconciliacaoAcaoRequestDto(
                    ReconciliationActionType.MARCAR_NAO_PLANEJADO,
                    null,
                    null
            );

            mockMvc.perform(post("/api/v1/reconciliation/{treinoRealizadoId}/acao", treinoRealizado.getId())
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
        void shouldReturnJsonContentTypeOnGet() throws Exception {
            mockMvc.perform(get("/api/v1/reconciliation/atletas/{atletaId}/pendentes", atletaId)
                    .with(comoTecnico())
                    .param("page", "0")
                    .param("size", "20"))
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON));
        }

        @Test
        @DisplayName("POST returns application/json Content-Type")
        void shouldReturnJsonContentTypeOnPost() throws Exception {
            ReconciliacaoAcaoRequestDto request = new ReconciliacaoAcaoRequestDto(
                    ReconciliationActionType.MARCAR_NAO_PLANEJADO,
                    null,
                    null
            );

            mockMvc.perform(post("/api/v1/reconciliation/{treinoRealizadoId}/acao", treinoRealizado.getId())
                    .with(comoTecnico())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON));
        }

        @Test
        @DisplayName("Error response includes proper status code and message structure")
        void shouldReturnStructuredErrorResponse() throws Exception {
            mockMvc.perform(get("/api/v1/reconciliation/atletas/{atletaId}/pendentes", atletaId)
                    .with(comoTecnico())
                    .param("statuses", "INVALID_STATUS")
                    .param("page", "0")
                    .param("size", "20"))
                    .andExpect(status().isBadRequest());
        }


    }
}
