package br.com.menthoros.backend.repository;

import br.com.menthoros.backend.AbstractIntegrationTest;
import br.com.menthoros.backend.entity.Assessoria;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.SugestaoCoach;
import br.com.menthoros.backend.enums.AtletaStatus;
import br.com.menthoros.backend.enums.NivelExperiencia;
import br.com.menthoros.backend.enums.PlanoAssessoria;
import br.com.menthoros.backend.enums.StatusSugestao;
import br.com.menthoros.backend.enums.TipoSugestao;
import br.com.menthoros.backend.multitenancy.TenantContext;
import br.com.menthoros.backend.services.SugestaoCoachService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Prova, contra o schema real, que {@code findAtletaIdsByTenantIdAndStatus} (base do badge de
 * sugestão pendente, {@code add-pending-suggestion-badge}) exclui sugestões expiradas e nunca
 * vaza entre tenants.
 */
@Transactional
class SugestaoCoachRepositoryPendingAtletaIdsIT extends AbstractIntegrationTest {

    @Autowired
    private SugestaoCoachRepository sugestaoCoachRepository;
    @Autowired
    private AssessoriaRepository assessoriaRepository;
    @Autowired
    private AtletaRepository atletaRepository;
    @Autowired
    private SugestaoCoachService sugestaoCoachService;

    @PersistenceContext
    private EntityManager entityManager;

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Nested
    @DisplayName("findAtletaIdsByTenantIdAndStatus")
    class FindAtletaIdsByTenantIdAndStatus {

        @Test
        @DisplayName("retorna só atletas com PENDING não-expirada, ignora APPROVED/REJECTED/expirada")
        void filtraPorStatusENaoExpirada() {
            Assessoria assessoria = seedAssessoria();
            Instant agora = Instant.now();

            Atleta atletaPendentePendente = seedAtleta(assessoria, "Pendente Válida");
            seedSugestao(assessoria, atletaPendentePendente, StatusSugestao.PENDING, TipoSugestao.RECOVERY,
                    agora.plus(5, ChronoUnit.DAYS));

            Atleta atletaAprovado = seedAtleta(assessoria, "Aprovado");
            seedSugestao(assessoria, atletaAprovado, StatusSugestao.APPROVED, TipoSugestao.RECOVERY,
                    agora.plus(5, ChronoUnit.DAYS));

            Atleta atletaRejeitado = seedAtleta(assessoria, "Rejeitado");
            seedSugestao(assessoria, atletaRejeitado, StatusSugestao.REJECTED, TipoSugestao.RECOVERY,
                    agora.plus(5, ChronoUnit.DAYS));

            Atleta atletaExpirado = seedAtleta(assessoria, "Pendente Expirada");
            seedSugestao(assessoria, atletaExpirado, StatusSugestao.PENDING, TipoSugestao.PLAN_ADJUST,
                    agora.minus(1, ChronoUnit.DAYS));

            entityManager.flush();
            entityManager.clear();

            Set<UUID> resultado = sugestaoCoachRepository
                    .findAtletaIdsByTenantIdAndStatus(assessoria.getId(), StatusSugestao.PENDING, agora);

            assertThat(resultado)
                    .containsExactly(atletaPendentePendente.getId())
                    .doesNotContain(atletaAprovado.getId(), atletaRejeitado.getId(), atletaExpirado.getId());
        }

        @Test
        @DisplayName("sugestão PENDING sem expiresAt (null) conta como não-expirada")
        void semExpiresAtContaComoValida() {
            Assessoria assessoria = seedAssessoria();
            Atleta atleta = seedAtleta(assessoria, "Sem Expiração");
            seedSugestao(assessoria, atleta, StatusSugestao.PENDING, TipoSugestao.NEW_PLAN, null);

            entityManager.flush();
            entityManager.clear();

            Set<UUID> resultado = sugestaoCoachRepository
                    .findAtletaIdsByTenantIdAndStatus(assessoria.getId(), StatusSugestao.PENDING, Instant.now());

            assertThat(resultado).containsExactly(atleta.getId());
        }

        @Test
        @DisplayName("isolamento por tenant — sugestão de outro tenant nunca aparece")
        void isolamentoPorTenant() {
            Assessoria tenantA = seedAssessoria();
            Assessoria tenantB = seedAssessoria();
            Atleta atletaB = seedAtleta(tenantB, "Atleta do Tenant B");
            seedSugestao(tenantB, atletaB, StatusSugestao.PENDING, TipoSugestao.RECOVERY,
                    Instant.now().plus(5, ChronoUnit.DAYS));

            entityManager.flush();
            entityManager.clear();

            Set<UUID> resultado = sugestaoCoachRepository
                    .findAtletaIdsByTenantIdAndStatus(tenantA.getId(), StatusSugestao.PENDING, Instant.now());

            assertThat(resultado).isEmpty();
        }

        @Test
        @DisplayName("sem sugestão nenhuma retorna set vazio")
        void semSugestaoRetornaVazio() {
            Assessoria assessoria = seedAssessoria();

            Set<UUID> resultado = sugestaoCoachRepository
                    .findAtletaIdsByTenantIdAndStatus(assessoria.getId(), StatusSugestao.PENDING, Instant.now());

            assertThat(resultado).isEmpty();
        }
    }

    @Nested
    @DisplayName("findAllByAtletaIdAndTenantId prioriza PENDING não-expirada (D6, CA9)")
    class FindAllByAtletaIdAndTenantIdPrioridade {

        @Test
        @DisplayName("PENDING não-expirada mais antiga aparece mesmo com 3 decididas mais recentes")
        void pendenteMaisAntigaAparecePrimeiro() {
            Assessoria assessoria = seedAssessoria();
            Atleta atleta = seedAtleta(assessoria, "Pendencia Antiga");
            Instant agora = Instant.now();

            SugestaoCoach pendenteAntiga = seedSugestaoComCreatedAt(assessoria, atleta, StatusSugestao.PENDING,
                    TipoSugestao.RECOVERY, agora.plus(5, ChronoUnit.DAYS), agora.minus(4, ChronoUnit.DAYS));
            seedSugestaoComCreatedAt(assessoria, atleta, StatusSugestao.APPROVED, TipoSugestao.PLAN_ADJUST,
                    null, agora.minus(3, ChronoUnit.DAYS));
            seedSugestaoComCreatedAt(assessoria, atleta, StatusSugestao.REJECTED, TipoSugestao.NEW_PLAN,
                    null, agora.minus(2, ChronoUnit.DAYS));
            seedSugestaoComCreatedAt(assessoria, atleta, StatusSugestao.APPROVED, TipoSugestao.RECOVERY,
                    null, agora.minus(1, ChronoUnit.DAYS));

            entityManager.flush();
            entityManager.clear();

            var resultado = sugestaoCoachRepository.findAllByAtletaIdAndTenantId(atleta.getId(), assessoria.getId(), agora);

            assertThat(resultado).hasSize(3);
            assertThat(resultado).extracting(SugestaoCoach::getId)
                    .as("a pendência não-expirada tem que estar entre as 3, mesmo sendo a mais antiga")
                    .contains(pendenteAntiga.getId());
        }

        @Test
        @DisplayName("PENDING expirada não é priorizada — comportamento normal por data")
        void pendenteExpiradaNaoEPriorizada() {
            Assessoria assessoria = seedAssessoria();
            Atleta atleta = seedAtleta(assessoria, "Pendencia Expirada");
            Instant agora = Instant.now();

            SugestaoCoach pendenteExpirada = seedSugestaoComCreatedAt(assessoria, atleta, StatusSugestao.PENDING,
                    TipoSugestao.RECOVERY, agora.minus(1, ChronoUnit.DAYS), agora.minus(10, ChronoUnit.DAYS));
            seedSugestaoComCreatedAt(assessoria, atleta, StatusSugestao.APPROVED, TipoSugestao.PLAN_ADJUST,
                    null, agora.minus(3, ChronoUnit.DAYS));
            seedSugestaoComCreatedAt(assessoria, atleta, StatusSugestao.REJECTED, TipoSugestao.NEW_PLAN,
                    null, agora.minus(2, ChronoUnit.DAYS));
            seedSugestaoComCreatedAt(assessoria, atleta, StatusSugestao.APPROVED, TipoSugestao.RECOVERY,
                    null, agora.minus(1, ChronoUnit.DAYS).minusSeconds(1));

            entityManager.flush();
            entityManager.clear();

            var resultado = sugestaoCoachRepository.findAllByAtletaIdAndTenantId(atleta.getId(), assessoria.getId(), agora);

            assertThat(resultado).extracting(SugestaoCoach::getId).doesNotContain(pendenteExpirada.getId());
        }
    }

    @Nested
    @DisplayName("decisão zera o sinal (CA5)")
    class DecisaoZeraOSinal {

        @Test
        @DisplayName("aprovar a única sugestão PENDING do atleta faz o atleta sumir do set")
        void aprovarZeraOSinal() {
            Assessoria assessoria = seedAssessoria();
            Atleta atleta = seedAtleta(assessoria, "Decide Aqui");
            SugestaoCoach sugestao = seedSugestao(assessoria, atleta, StatusSugestao.PENDING, TipoSugestao.RECOVERY,
                    Instant.now().plus(5, ChronoUnit.DAYS));
            entityManager.flush();
            entityManager.clear();

            Set<UUID> antes = sugestaoCoachRepository
                    .findAtletaIdsByTenantIdAndStatus(assessoria.getId(), StatusSugestao.PENDING, Instant.now());
            assertThat(antes).containsExactly(atleta.getId());

            TenantContext.setTenantId(assessoria.getId());
            sugestaoCoachService.aprovar(sugestao.getId());
            TenantContext.clear();
            entityManager.flush();
            entityManager.clear();

            Set<UUID> depois = sugestaoCoachRepository
                    .findAtletaIdsByTenantIdAndStatus(assessoria.getId(), StatusSugestao.PENDING, Instant.now());
            assertThat(depois).isEmpty();
        }
    }

    // ── Helpers ─────────────────────────────────────────────────────────────────

    private Assessoria seedAssessoria() {
        Assessoria assessoria = new Assessoria();
        assessoria.setNome("Assessoria Pending Badge Test " + UUID.randomUUID());
        assessoria.setDominio("pending-badge-" + UUID.randomUUID());
        assessoria.setPlano(PlanoAssessoria.BASIC);
        return assessoriaRepository.save(assessoria);
    }

    private Atleta seedAtleta(Assessoria assessoria, String nome) {
        Atleta atleta = new Atleta();
        atleta.setNome(nome);
        atleta.setEmail(UUID.randomUUID() + "@test.com");
        atleta.setObjetivo("Correr 10km");
        atleta.setNivelExperiencia(NivelExperiencia.INTERMEDIARIO);
        atleta.setAtivo(AtletaStatus.ATIVO);
        atleta.setAssessoria(assessoria);
        return atletaRepository.save(atleta);
    }

    private SugestaoCoach seedSugestao(Assessoria assessoria, Atleta atleta, StatusSugestao status, TipoSugestao tipo,
                                        Instant expiresAt) {
        SugestaoCoach sugestao = SugestaoCoach.builder()
                .tenantId(assessoria.getId())
                .atleta(atleta)
                .tipo(tipo)
                .status(status)
                .confidence("HIGH")
                .summary("Sugestão de teste")
                .createdAt(Instant.now())
                .expiresAt(expiresAt)
                .build();
        return sugestaoCoachRepository.save(sugestao);
    }

    private SugestaoCoach seedSugestaoComCreatedAt(Assessoria assessoria, Atleta atleta, StatusSugestao status,
                                                     TipoSugestao tipo, Instant expiresAt, Instant createdAt) {
        SugestaoCoach sugestao = SugestaoCoach.builder()
                .tenantId(assessoria.getId())
                .atleta(atleta)
                .tipo(tipo)
                .status(status)
                .confidence("HIGH")
                .summary("Sugestão de teste")
                .createdAt(createdAt)
                .expiresAt(expiresAt)
                .build();
        return sugestaoCoachRepository.save(sugestao);
    }
}
