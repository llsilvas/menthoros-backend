package br.com.menthoros.backend.repository;

import br.com.menthoros.backend.AbstractIntegrationTest;
import br.com.menthoros.backend.entity.Assessoria;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.PlanoSemanal;
import br.com.menthoros.backend.entity.PlanoTreino;
import br.com.menthoros.backend.entity.Prova;
import br.com.menthoros.backend.enums.SeasonPlanStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integração real (Testcontainers + V116) do remapeamento de {@link PlanoTreino} para o plano de
 * temporada: cascata sem REMOVE, legado com {@code seasonStatus} nulo, isolamento por tenant e o
 * índice parcial de uma temporada ACTIVE por atleta.
 */
@Transactional
class PlanoTreinoRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    private PlanoTreinoRepository planoTreinoRepository;

    @PersistenceContext
    private EntityManager em;

    private SeasonPlanFixtures fx;
    private Atleta atleta;
    private Prova prova;

    @BeforeEach
    void setUp() {
        fx = new SeasonPlanFixtures(em);
        atleta = fx.atleta(fx.assessoria());
        prova = fx.prova(atleta);
    }

    @Nested
    @DisplayName("delete")
    class Delete {

        @Test
        @DisplayName("remover a temporada pelo JPA não apaga os planos semanais — ficam sem vínculo")
        void preservaPlanosSemanais() {
            PlanoTreino plano = fx.planoTreino(atleta, prova, SeasonPlanStatus.ACTIVE);
            PlanoSemanal semanal = fx.planoSemanalAprovado(plano);
            em.flush();
            em.clear();

            planoTreinoRepository.delete(planoTreinoRepository.findById(plano.getId()).orElseThrow());
            em.flush();
            em.clear();

            PlanoSemanal recarregado = em.find(PlanoSemanal.class, semanal.getId());
            assertThat(recarregado).isNotNull();
            assertThat(recarregado.getPlanoTreino()).isNull();
        }
    }

    @Nested
    @DisplayName("findByIdAndAssessoriaId")
    class FindByIdAndAssessoriaId {

        @Test
        @DisplayName("lê PlanoTreino legado com seasonStatus nulo")
        void legadoSemStatus() {
            PlanoTreino legado = fx.planoTreino(atleta, prova, null);
            em.flush();
            em.clear();

            PlanoTreino lido = planoTreinoRepository
                    .findByIdAndAssessoriaId(legado.getId(), atleta.getAssessoria().getId())
                    .orElseThrow();

            assertThat(lido.getSeasonStatus()).isNull();
        }

        @Test
        @DisplayName("temporada de outro tenant não aparece")
        void isolaTenant() {
            PlanoTreino plano = fx.planoTreino(atleta, prova, SeasonPlanStatus.ACTIVE);
            Assessoria outra = fx.assessoria();
            em.flush();

            assertThat(planoTreinoRepository.findByIdAndAssessoriaId(plano.getId(), outra.getId()))
                    .isEmpty();
        }
    }

    @Nested
    @DisplayName("existsReferencingRace")
    class ExistsReferencingRace {

        @Test
        @DisplayName("acha referência por prova_id")
        void porProvaId() {
            fx.planoTreino(atleta, prova, SeasonPlanStatus.ARCHIVED);
            em.flush();

            assertThat(planoTreinoRepository.existsReferencingRace(prova.getId(), tenantId())).isTrue();
        }

        @Test
        @DisplayName("acha referência só por prova_alvo_id (legado)")
        void porProvaAlvoId() {
            Prova outra = fx.prova(atleta);
            PlanoTreino legado = fx.planoTreino(atleta, outra, null);
            legado.setProvaAlvo(prova);
            em.flush();

            assertThat(planoTreinoRepository.existsReferencingRace(prova.getId(), tenantId())).isTrue();
        }

        @Test
        @DisplayName("prova sem PlanoTreino não tem referência")
        void semReferencia() {
            assertThat(planoTreinoRepository.existsReferencingRace(prova.getId(), tenantId())).isFalse();
        }

        @Test
        @DisplayName("não enxerga outro tenant")
        void outroTenant() {
            fx.planoTreino(atleta, prova, SeasonPlanStatus.ACTIVE);
            em.flush();

            assertThat(planoTreinoRepository.existsReferencingRace(prova.getId(), fx.assessoria().getId()))
                    .isFalse();
        }
    }

    @Nested
    @DisplayName("archiveOpenSeasons")
    class ArchiveOpenSeasons {

        @Test
        @DisplayName("arquiva ACTIVE e PROPOSED da prova; não toca ARCHIVED, COMPLETED nem legado")
        void arquivaSoAsAbertas() {
            PlanoTreino ativa = fx.planoTreino(atleta, prova, SeasonPlanStatus.ACTIVE);
            PlanoTreino proposta = fx.planoTreino(atleta, prova, SeasonPlanStatus.PROPOSED);
            PlanoTreino concluida = fx.planoTreino(atleta, prova, SeasonPlanStatus.COMPLETED);
            PlanoTreino legado = fx.planoTreino(atleta, prova, null);
            em.flush();

            int arquivadas = planoTreinoRepository.archiveOpenSeasons(prova.getId(), tenantId(), "prova cancelada");
            em.clear();

            assertThat(arquivadas).isEqualTo(2);
            assertThat(status(ativa)).isEqualTo(SeasonPlanStatus.ARCHIVED);
            assertThat(status(proposta)).isEqualTo(SeasonPlanStatus.ARCHIVED);
            assertThat(em.find(PlanoTreino.class, ativa.getId()).getVersionReason()).isEqualTo("prova cancelada");
            assertThat(status(concluida)).isEqualTo(SeasonPlanStatus.COMPLETED);
            assertThat(status(legado)).isNull();
        }

        @Test
        @DisplayName("repetir não altera nada")
        void idempotente() {
            fx.planoTreino(atleta, prova, SeasonPlanStatus.ACTIVE);
            em.flush();
            planoTreinoRepository.archiveOpenSeasons(prova.getId(), tenantId(), "prova cancelada");

            assertThat(planoTreinoRepository.archiveOpenSeasons(prova.getId(), tenantId(), "prova cancelada"))
                    .isZero();
        }

        @Test
        @DisplayName("não arquiva temporada de outro tenant")
        void outroTenant() {
            PlanoTreino ativa = fx.planoTreino(atleta, prova, SeasonPlanStatus.ACTIVE);
            em.flush();

            planoTreinoRepository.archiveOpenSeasons(prova.getId(), fx.assessoria().getId(), "prova cancelada");
            em.clear();

            assertThat(status(ativa)).isEqualTo(SeasonPlanStatus.ACTIVE);
        }

        private SeasonPlanStatus status(PlanoTreino plano) {
            return em.find(PlanoTreino.class, plano.getId()).getSeasonStatus();
        }
    }

    private java.util.UUID tenantId() {
        return atleta.getAssessoria().getId();
    }

    @Nested
    @DisplayName("índice uk_plano_treino_active_season")
    class IndiceAtivo {

        @Test
        @DisplayName("recusa a segunda temporada ACTIVE do mesmo atleta")
        void recusaSegundaAtiva() {
            fx.planoTreino(atleta, prova, SeasonPlanStatus.ACTIVE);
            em.flush();

            assertThatThrownBy(() -> {
                fx.planoTreino(atleta, fx.prova(atleta), SeasonPlanStatus.ACTIVE);
                planoTreinoRepository.flush();
            }).isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        @DisplayName("aceita vários PlanoTreino legados e propostas do mesmo atleta")
        void aceitaLegadosEPropostas() {
            fx.planoTreino(atleta, prova, null);
            fx.planoTreino(atleta, fx.prova(atleta), null);
            fx.planoTreino(atleta, fx.prova(atleta), SeasonPlanStatus.PROPOSED);
            fx.planoTreino(atleta, fx.prova(atleta), SeasonPlanStatus.ACTIVE);

            planoTreinoRepository.flush();

            assertThat(planoTreinoRepository.count()).isGreaterThanOrEqualTo(4);
        }
    }
}
