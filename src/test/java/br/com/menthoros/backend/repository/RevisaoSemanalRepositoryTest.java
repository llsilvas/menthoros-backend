package br.com.menthoros.backend.repository;

import br.com.menthoros.backend.AbstractIntegrationTest;
import br.com.menthoros.backend.entity.Assessoria;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.PlanoMetaDados;
import br.com.menthoros.backend.entity.PlanoSemanal;
import br.com.menthoros.backend.entity.RevisaoSemanal;
import br.com.menthoros.backend.enums.AtletaStatus;
import br.com.menthoros.backend.enums.DiaSemana;
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
                    .percentualRealizacao(new BigDecimal("42.50"))
                    .dadosSuficientes(true)
                    .geradaEm(Instant.now())
                    .build());
            flushClear();

            Optional<RevisaoSemanal> recarregada =
                    revisaoSemanalRepository.findByPlanoSemanalIdAndTenant(plano.getId(), tenantId);

            assertThat(recarregada).isPresent();
            assertThat(recarregada.get().getId()).isEqualTo(revisao.getId());
            assertThat(recarregada.get().getRecommendationType()).isEqualTo(RecommendationType.RECOVERY);
            assertThat(recarregada.get().getAdherenceStatus()).isEqualTo(NivelAderencia.BAIXA);
            assertThat(recarregada.get().getPercentualRealizacao()).isEqualByComparingTo("42.50");
            assertThat(recarregada.get().isDadosSuficientes()).isTrue();
            assertThat(recarregada.get().getPlanoSemanal().getId()).isEqualTo(plano.getId());
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
                .percentualRealizacao(new BigDecimal("75.00"))
                .dadosSuficientes(true)
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
        PlanoMetaDados meta = new PlanoMetaDados();
        meta.setAtleta(atleta);
        meta.setAssessoria(atleta.getAssessoria());
        meta.setDiaPreferidoLongo(DiaSemana.SABADO);
        meta = planoMetadadosRepository.save(meta);

        LocalDate semanaFim = LocalDate.now();
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
