package br.com.menthoros.backend.controller;

import br.com.menthoros.backend.AbstractIntegrationTest;
import br.com.menthoros.backend.entity.Assessoria;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.PerfilOnboardingAtleta;
import br.com.menthoros.backend.entity.TreinoRealizado;
import br.com.menthoros.backend.entity.Usuario;
import br.com.menthoros.backend.enums.AtletaStatus;
import br.com.menthoros.backend.enums.DiaSemana;
import br.com.menthoros.backend.enums.FonteDados;
import br.com.menthoros.backend.enums.NivelExperiencia;
import br.com.menthoros.backend.enums.PlanoAssessoria;
import br.com.menthoros.backend.enums.TipoTreino;
import br.com.menthoros.backend.enums.UserRole;
import br.com.menthoros.backend.repository.AssessoriaRepository;
import br.com.menthoros.backend.repository.AtletaRepository;
import br.com.menthoros.backend.repository.PerfilOnboardingAtletaRepository;
import br.com.menthoros.backend.repository.TreinoRealizadoRepository;
import br.com.menthoros.backend.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Teste de integracao para DoD 9.0 (design.md Decisao 9, CA12, athlete-onboarding-baseline):
 * campos de lesao/dor/fadiga/sono/recuperacao sao visiveis ao proprio atleta dono do dado e a
 * qualquer TECNICO/ADMIN do MESMO tenant (coach-como-proxy, acesso tenant-wide por papel — nao ha
 * vinculo de coach individual no modelo). Um usuario autenticado para OUTRO tenant e rejeitado,
 * independente do papel.
 *
 * <p>Usa {@code @SpringBootTest} (nao {@code @WebMvcTest}) para exercitar o
 * {@code UsuarioSyncServiceImpl} real: ele auto-provisiona {@code Usuario} a partir do JWT
 * (subject = {@code keycloakId}, que precisa ser um UUID valido — {@code createNewUsuario} faz
 * {@code UUID.fromString(keycloakId)}). Para o cenario do ATLETA-dono, o {@code Usuario} e
 * pre-criado no {@code @BeforeEach} com o {@code Atleta} ja vinculado (em vez de depender do
 * auto-vinculo por email do sync), porque {@code resolverAtletaIdAtual()} resolve o atleta do
 * chamador via {@code Atleta.usuario}.
 */
@AutoConfigureMockMvc
@DisplayName("CA12 - Acesso a dado sensivel do onboarding (lesao / dor / fadiga / sono / recuperacao)")
class OnboardingSensitiveDataAccessIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AssessoriaRepository assessoriaRepository;

    @Autowired
    private AtletaRepository atletaRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private PerfilOnboardingAtletaRepository perfilOnboardingAtletaRepository;

    @Autowired
    private TreinoRealizadoRepository treinoRealizadoRepository;

    private UUID tenantAId;
    private UUID tenantBId;
    private UUID atletaAId;
    private UUID treinoRealizadoId;

    private UUID subAtletaA;
    private UUID subTecnicoA;
    private UUID subAdminA;
    private UUID subOutroTenant;

    @BeforeEach
    void setUp() {
        // ===== Tenant A (dono do dado) =====
        Assessoria assessoriaA = new Assessoria();
        assessoriaA.setNome("Assessoria A");
        assessoriaA.setDominio("assessoria-a-" + UUID.randomUUID());
        assessoriaA.setPlano(PlanoAssessoria.BASIC);
        assessoriaA = assessoriaRepository.save(assessoriaA);
        tenantAId = assessoriaA.getId();

        Atleta atletaA = new Atleta();
        atletaA.setNome("Atleta Dono");
        atletaA.setEmail("atleta-dono-" + UUID.randomUUID() + "@menthoros.test");
        atletaA.setObjetivo("Correr uma maratona");
        atletaA.setNivelExperiencia(NivelExperiencia.INTERMEDIARIO);
        atletaA.setAtivo(AtletaStatus.ATIVO);
        atletaA.setAssessoria(assessoriaA);
        atletaA = atletaRepository.save(atletaA);
        atletaAId = atletaA.getId();

        subAtletaA = UUID.randomUUID();
        subTecnicoA = UUID.randomUUID();
        subAdminA = UUID.randomUUID();

        Usuario usuarioAtletaA = criarUsuario(subAtletaA, assessoriaA, UserRole.ATLETA);
        atletaA.setUsuario(usuarioAtletaA);
        atletaRepository.save(atletaA);

        criarUsuario(subTecnicoA, assessoriaA, UserRole.TECNICO);
        criarUsuario(subAdminA, assessoriaA, UserRole.ADMIN);

        // ===== Tenant B (outro tenant, nao deve enxergar dado de A) =====
        Assessoria assessoriaB = new Assessoria();
        assessoriaB.setNome("Assessoria B");
        assessoriaB.setDominio("assessoria-b-" + UUID.randomUUID());
        assessoriaB.setPlano(PlanoAssessoria.BASIC);
        assessoriaB = assessoriaRepository.save(assessoriaB);
        tenantBId = assessoriaB.getId();

        subOutroTenant = UUID.randomUUID();
        criarUsuario(subOutroTenant, assessoriaB, UserRole.TECNICO);

        // ===== Dado sensivel: onboarding (lesao) =====
        PerfilOnboardingAtleta perfil = new PerfilOnboardingAtleta();
        Atleta atletaRef = new Atleta();
        atletaRef.setId(atletaAId);
        perfil.setAtleta(atletaRef);
        perfil.setTenantId(tenantAId);
        perfil.setStatus("RASCUNHO");
        perfil.setTemLesao(true);
        perfil.setDescricaoLesao("Lesao no joelho direito, dor ao correr acima de 10km");
        perfil.setDataUltimaLesao(LocalDate.now().minusMonths(2));
        perfil.setHistoricoLesoes("Entorse de tornozelo em 2023, tendinite patelar em 2024");
        perfilOnboardingAtletaRepository.save(perfil);

        // ===== Dado sensivel: treino realizado (dor/fadiga/sono/recuperacao) =====
        TreinoRealizado treino = new TreinoRealizado();
        treino.setAtleta(atletaA);
        treino.setTenantId(tenantAId);
        treino.setDataTreino(LocalDate.now());
        treino.setDiaSemana(toDiaSemana(LocalDate.now()));
        treino.setTipoTreino(TipoTreino.FACIL);
        treino.setDuracaoMin(Duration.ofMinutes(50));
        treino.setDistanciaKm(new BigDecimal("9.0"));
        treino.setFonteDados(FonteDados.MANUAL);
        treino.setNivelDor(3);
        treino.setNivelFadiga(4);
        treino.setQualidadeSonoNoiteAnterior(7);
        treino.setNivelRecuperacao(6);
        treino = treinoRealizadoRepository.save(treino);
        treinoRealizadoId = treino.getId();
    }

    @Nested
    @DisplayName("GET /api/v1/atletas/{atletaId}/onboarding - campos de lesao")
    class OnboardingLesao {

        @Test
        @DisplayName("Atleta dono le os proprios campos de lesao -> 200")
        void atletaDonoLeProprioOnboarding() throws Exception {
            mockMvc.perform(get("/api/v1/atletas/{atletaId}/onboarding", atletaAId)
                            .with(jwtDe(subAtletaA, tenantAId, "ATLETA")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.temLesao").value(true))
                    .andExpect(jsonPath("$.descricaoLesao").value("Lesao no joelho direito, dor ao correr acima de 10km"))
                    .andExpect(jsonPath("$.historicoLesoes").exists())
                    .andExpect(jsonPath("$.dataUltimaLesao").exists());
        }

        @Test
        @DisplayName("TECNICO do mesmo tenant le a lesao de qualquer atleta -> 200")
        void tecnicoMesmoTenantLeOnboarding() throws Exception {
            mockMvc.perform(get("/api/v1/atletas/{atletaId}/onboarding", atletaAId)
                            .with(jwtDe(subTecnicoA, tenantAId, "TECNICO")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.temLesao").value(true))
                    .andExpect(jsonPath("$.descricaoLesao").exists())
                    .andExpect(jsonPath("$.historicoLesoes").exists());
        }

        @Test
        @DisplayName("ADMIN do mesmo tenant le a lesao de qualquer atleta -> 200")
        void adminMesmoTenantLeOnboarding() throws Exception {
            mockMvc.perform(get("/api/v1/atletas/{atletaId}/onboarding", atletaAId)
                            .with(jwtDe(subAdminA, tenantAId, "ADMIN")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.temLesao").value(true))
                    .andExpect(jsonPath("$.descricaoLesao").exists())
                    .andExpect(jsonPath("$.historicoLesoes").exists());
        }

        @Test
        @DisplayName("Usuario de OUTRO tenant e rejeitado -> 403 (isolamento de tenant, @RequireTenant)")
        void outroTenantERejeitado() throws Exception {
            mockMvc.perform(get("/api/v1/atletas/{atletaId}/onboarding", atletaAId)
                            .with(jwtDe(subOutroTenant, tenantBId, "TECNICO")))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("GET .../me/treinos e GET /api/v1/treinos/realizados/{id} - dor/fadiga/sono/recuperacao")
    class TreinoRealizadoSensivel {

        @Test
        @DisplayName("Atleta dono le os proprios campos de dor/fadiga/sono/recuperacao -> 200 (GET /me/treinos)")
        void atletaDonoLeProprioTreino() throws Exception {
            mockMvc.perform(get("/api/v1/atletas/me/treinos")
                            .with(jwtDe(subAtletaA, tenantAId, "ATLETA")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].nivelDor").value(3))
                    .andExpect(jsonPath("$[0].nivelFadiga").value(4))
                    .andExpect(jsonPath("$[0].qualidadeSonoNoiteAnterior").value(7))
                    .andExpect(jsonPath("$[0].nivelRecuperacao").value(6));
        }

        @Test
        @DisplayName("TECNICO do mesmo tenant le dor/fadiga/sono/recuperacao -> 200 (GET /treinos/realizados/{id})")
        void tecnicoMesmoTenantLeTreino() throws Exception {
            mockMvc.perform(get("/api/v1/treinos/realizados/{id}", treinoRealizadoId)
                            .with(jwtDe(subTecnicoA, tenantAId, "TECNICO")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.nivelDor").value(3))
                    .andExpect(jsonPath("$.nivelFadiga").value(4))
                    .andExpect(jsonPath("$.qualidadeSonoNoiteAnterior").value(7))
                    .andExpect(jsonPath("$.nivelRecuperacao").value(6));
        }

        @Test
        @DisplayName("ADMIN do mesmo tenant le dor/fadiga/sono/recuperacao -> 200 (GET /treinos/realizados/{id})")
        void adminMesmoTenantLeTreino() throws Exception {
            mockMvc.perform(get("/api/v1/treinos/realizados/{id}", treinoRealizadoId)
                            .with(jwtDe(subAdminA, tenantAId, "ADMIN")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.nivelDor").value(3))
                    .andExpect(jsonPath("$.nivelFadiga").value(4))
                    .andExpect(jsonPath("$.qualidadeSonoNoiteAnterior").value(7))
                    .andExpect(jsonPath("$.nivelRecuperacao").value(6));
        }

        @Test
        @DisplayName("Usuario de OUTRO tenant e rejeitado -> 404 (isolamento por findByIdAndTenantId)")
        void outroTenantERejeitado() throws Exception {
            mockMvc.perform(get("/api/v1/treinos/realizados/{id}", treinoRealizadoId)
                            .with(jwtDe(subOutroTenant, tenantBId, "TECNICO")))
                    .andExpect(status().isNotFound());
        }
    }

    // ===== Helpers =====

    private Usuario criarUsuario(UUID subject, Assessoria assessoria, UserRole role) {
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

    /**
     * JWT com subject = UUID (exigido por {@code UsuarioSyncServiceImpl.createNewUsuario}, que faz
     * {@code UUID.fromString(keycloakId)} quando o usuario ainda nao existe localmente) + claim
     * {@code tenant_id} + authority {@code ROLE_<papel>}.
     */
    private RequestPostProcessor jwtDe(UUID subject, UUID tenantId, String papel) {
        return jwt()
                .authorities(new SimpleGrantedAuthority("ROLE_" + papel))
                .jwt(j -> j.subject(subject.toString()).claim("tenant_id", tenantId.toString()));
    }

    private DiaSemana toDiaSemana(LocalDate data) {
        return switch (data.getDayOfWeek()) {
            case MONDAY -> DiaSemana.SEGUNDA;
            case TUESDAY -> DiaSemana.TERCA;
            case WEDNESDAY -> DiaSemana.QUARTA;
            case THURSDAY -> DiaSemana.QUINTA;
            case FRIDAY -> DiaSemana.SEXTA;
            case SATURDAY -> DiaSemana.SABADO;
            case SUNDAY -> DiaSemana.DOMINGO;
        };
    }
}
