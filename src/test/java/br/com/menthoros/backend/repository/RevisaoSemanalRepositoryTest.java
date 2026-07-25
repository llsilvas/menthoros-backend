package br.com.menthoros.backend.repository;

import br.com.menthoros.backend.AbstractIntegrationTest;
import br.com.menthoros.backend.entity.Assessoria;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.PlanoMetaDados;
import br.com.menthoros.backend.entity.PlanoSemanal;
import br.com.menthoros.backend.entity.RevisaoSemanal;
import br.com.menthoros.backend.enums.AtletaStatus;
import br.com.menthoros.backend.enums.ConsumedReviewOutcome;
import br.com.menthoros.backend.enums.DiaSemana;
import br.com.menthoros.backend.enums.FocusSource;
import br.com.menthoros.backend.enums.NivelAderencia;
import br.com.menthoros.backend.enums.NivelExperiencia;
import br.com.menthoros.backend.enums.PlanoAssessoria;
import br.com.menthoros.backend.enums.PlanoReviewStatus;
import br.com.menthoros.backend.enums.PlanoStatus;
import br.com.menthoros.backend.enums.RecommendationType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integração real (Testcontainers) de {@link RevisaoSemanalRepository} contra o schema V71:
 * round-trip dos campos congelados/enums, isolamento por tenant (CA7) e unicidade 1:1 por
 * {@code plano_semanal_id} (uk_revisao_semanal_plano — base do upsert idempotente, CA6).
 */
@Transactional
class RevisaoSemanalRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    private AssessoriaRepository assessoriaRepository;
    @Autowired
    private AtletaRepository atletaRepository;
    @Autowired
    private PlanoMetadadosRepository planoMetadadosRepository;
    @Autowired
    private PlanoSemanalRepository planoSemanalRepository;
    @Autowired
    private RevisaoSemanalRepository revisaoSemanalRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Nested
    @DisplayName("findByPlanoSemanalIdAndTenant")
    class FindByPlanoSemanalIdAndTenant {

        @Test
        @DisplayName("round-trip dos campos congelados e enums contra o schema real")
        void roundTripCamposCongelados() {
            Atleta atleta = seedAtleta();
            UUID tenantId = atleta.getAssessoria().getId();
            PlanoSemanal plano = salvarPlano(atleta);
            RevisaoSemanal revisao = revisaoSemanalRepository.save(RevisaoSemanal.builder()
                    .planoSemanal(plano)
                    .recommendationType(RecommendationType.RECOVERY)
                    .adherenceStatus(NivelAderencia.BAIXA)
                    .completionRate(new BigDecimal("42.50"))
                    .sufficientData(true)
                    .geradaEm(Instant.now())
                    .build());
            flushClear();

            Optional<RevisaoSemanal> recarregada =
                    revisaoSemanalRepository.findByPlanoSemanalIdAndTenant(plano.getId(), tenantId);

            assertThat(recarregada).isPresent();
            assertThat(recarregada.get().getId()).isEqualTo(revisao.getId());
            assertThat(recarregada.get().getRecommendationType()).isEqualTo(RecommendationType.RECOVERY);
            assertThat(recarregada.get().getAdherenceStatus()).isEqualTo(NivelAderencia.BAIXA);
            assertThat(recarregada.get().getCompletionRate()).isEqualByComparingTo("42.50");
            assertThat(recarregada.get().isSufficientData()).isTrue();
            assertThat(recarregada.get().getPlanoSemanal().getId()).isEqualTo(plano.getId());
        }

        @Test
        @DisplayName("round-trip da narrativa e da origem do foco (V72)")
        void roundTripFocoEOrigem() {
            Atleta atleta = seedAtleta();
            UUID tenantId = atleta.getAssessoria().getId();
            PlanoSemanal plano = salvarPlano(atleta);
            revisaoSemanalRepository.save(novaRevisao(plano).toBuilder()
                    .nextWeekFocus("Consolidar volume aeróbico, sem intensidade.")
                    .focusSource(FocusSource.TEMPLATE)
                    .build());
            flushClear();

            Optional<RevisaoSemanal> recarregada =
                    revisaoSemanalRepository.findByPlanoSemanalIdAndTenant(plano.getId(), tenantId);

            assertThat(recarregada).isPresent();
            assertThat(recarregada.get().getNextWeekFocus())
                    .isEqualTo("Consolidar volume aeróbico, sem intensidade.");
            assertThat(recarregada.get().getFocusSource()).isEqualTo(FocusSource.TEMPLATE);
        }

        @Test
        @DisplayName("vazio quando o plano não tem revisão")
        void vazioQuandoSemRevisao() {
            assertThat(revisaoSemanalRepository.findByPlanoSemanalIdAndTenant(UUID.randomUUID(), UUID.randomUUID()))
                    .isEmpty();
        }

        @Test
        @DisplayName("isolamento por tenant — outro tenant não enxerga a revisão do plano (CA7)")
        void isolamentoCrossTenant() {
            Atleta atleta = seedAtleta();
            PlanoSemanal plano = salvarPlano(atleta);
            revisaoSemanalRepository.save(novaRevisao(plano));
            flushClear();

            UUID outroTenant = UUID.randomUUID();
            assertThat(revisaoSemanalRepository.findByPlanoSemanalIdAndTenant(plano.getId(), outroTenant))
                    .isEmpty();
        }
    }

    @Nested
    @DisplayName("uk_revisao_semanal_plano (1:1)")
    class Unicidade {

        @Test
        @DisplayName("impede duas revisões para o mesmo PlanoSemanal")
        void unicidadePorPlano() {
            PlanoSemanal plano = salvarPlano(seedAtleta());
            revisaoSemanalRepository.save(novaRevisao(plano));
            flushClear();

            RevisaoSemanal duplicada = novaRevisao(plano);

            assertThatThrownBy(() -> revisaoSemanalRepository.saveAndFlush(duplicada))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
    }

    @Nested
    @DisplayName("vínculo plano → revisão consumida (V72)")
    class RevisaoConsumida {

        @Test
        @DisplayName("plano guarda a revisão que consumiu e o desfecho")
        void planoGuardaVinculoEDesfecho() {
            Atleta atleta = seedAtleta();
            PlanoSemanal planoRevisado = salvarPlano(atleta, 1);
            RevisaoSemanal revisao = revisaoSemanalRepository.save(novaRevisao(planoRevisado));

            PlanoSemanal planoSeguinte = salvarPlano(atleta, 0);
            planoSeguinte.setConsumedReview(revisao);
            planoSeguinte.setConsumedReviewOutcome(ConsumedReviewOutcome.PENDING);
            planoSemanalRepository.save(planoSeguinte);
            flushClear();

            PlanoSemanal recarregado = planoSemanalRepository.findById(planoSeguinte.getId()).orElseThrow();
            assertThat(recarregado.getConsumedReview().getId()).isEqualTo(revisao.getId());
            assertThat(recarregado.getConsumedReviewOutcome()).isEqualTo(ConsumedReviewOutcome.PENDING);
        }

        @Test
        @DisplayName("plano que não consumiu revisão fica com vínculo nulo")
        void planoSemRevisaoConsumida() {
            PlanoSemanal plano = salvarPlano(seedAtleta());
            flushClear();

            PlanoSemanal recarregado = planoSemanalRepository.findById(plano.getId()).orElseThrow();
            assertThat(recarregado.getConsumedReview()).isNull();
        }

        @Test
        @DisplayName("dois planos consumindo a mesma revisão preservam desfechos independentes")
        void doisPlanosMesmaRevisao() {
            Atleta atleta = seedAtleta();
            RevisaoSemanal revisao = revisaoSemanalRepository.save(novaRevisao(salvarPlano(atleta, 2)));

            PlanoSemanal rejeitado = salvarPlano(atleta, 1);
            rejeitado.setConsumedReview(revisao);
            rejeitado.setConsumedReviewOutcome(ConsumedReviewOutcome.PLAN_REJECTED);
            planoSemanalRepository.save(rejeitado);

            PlanoSemanal aprovado = salvarPlano(atleta, 0);
            aprovado.setConsumedReview(revisao);
            aprovado.setConsumedReviewOutcome(ConsumedReviewOutcome.NO_ADJUSTMENT);
            planoSemanalRepository.save(aprovado);
            flushClear();

            assertThat(planoSemanalRepository.findById(rejeitado.getId()).orElseThrow()
                    .getConsumedReviewOutcome()).isEqualTo(ConsumedReviewOutcome.PLAN_REJECTED);
            assertThat(planoSemanalRepository.findById(aprovado.getId()).orElseThrow()
                    .getConsumedReviewOutcome()).isEqualTo(ConsumedReviewOutcome.NO_ADJUSTMENT);
        }
    }

    // ---- helpers ----

    private void flushClear() {
        entityManager.flush();
        entityManager.clear();
    }

    private RevisaoSemanal novaRevisao(PlanoSemanal plano) {
        return RevisaoSemanal.builder()
                .planoSemanal(plano)
                .recommendationType(RecommendationType.MAINTAIN)
                .adherenceStatus(NivelAderencia.MEDIA)
                .completionRate(new BigDecimal("75.00"))
                .sufficientData(true)
                .geradaEm(Instant.now())
                .build();
    }

    private Atleta seedAtleta() {
        Assessoria assessoria = new Assessoria();
        assessoria.setNome("Assessoria Revisao Test");
        assessoria.setDominio("revisao-test-" + UUID.randomUUID());
        assessoria.setPlano(PlanoAssessoria.BASIC);
        assessoria = assessoriaRepository.save(assessoria);

        Atleta atleta = new Atleta();
        atleta.setNome("Atleta Revisao");
        atleta.setObjetivo("Correr 10km");
        atleta.setNivelExperiencia(NivelExperiencia.INICIANTE);
        atleta.setAtivo(AtletaStatus.ATIVO);
        atleta.setAssessoria(assessoria);
        return atletaRepository.save(atleta);
    }

    private PlanoSemanal salvarPlano(Atleta atleta) {
        return salvarPlano(atleta, 0);
    }

    /**
     * {@code semanasAtras} desloca a janela: o índice único uk_plano_semanal_atleta_semana_ativo
     * impede dois planos ativos do mesmo atleta na mesma semana.
     */
    private PlanoSemanal salvarPlano(Atleta atleta, int semanasAtras) {
        PlanoMetaDados meta = new PlanoMetaDados();
        meta.setAtleta(atleta);
        meta.setAssessoria(atleta.getAssessoria());
        meta.setDiaPreferidoLongo(DiaSemana.SABADO);
        meta = planoMetadadosRepository.save(meta);

        LocalDate semanaFim = LocalDate.now().minusWeeks(semanasAtras);
        PlanoSemanal plano = new PlanoSemanal();
        plano.setAtleta(atleta);
        plano.setAssessoria(atleta.getAssessoria());
        plano.setPlanoMetaDados(meta);
        plano.setSemanaInicio(semanaFim.minusDays(6));
        plano.setSemanaFim(semanaFim);
        plano.setVolumePlanejadoKm(BigDecimal.valueOf(40));
        plano.setStatus(PlanoStatus.CONCLUIDO);
        plano.setReviewStatus(PlanoReviewStatus.AGUARDANDO_REVISAO);
        plano.setObjetivoSemanal("Semana base");
        return planoSemanalRepository.save(plano);
    }
}
