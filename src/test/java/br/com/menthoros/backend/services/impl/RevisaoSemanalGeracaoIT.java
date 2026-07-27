package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.AbstractIntegrationTest;
import br.com.menthoros.backend.entity.Assessoria;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.PlanoMetaDados;
import br.com.menthoros.backend.entity.PlanoSemanal;
import br.com.menthoros.backend.entity.RevisaoSemanal;
import br.com.menthoros.backend.enums.AtletaStatus;
import br.com.menthoros.backend.enums.DiaSemana;
import br.com.menthoros.backend.enums.FocusSource;
import br.com.menthoros.backend.enums.NivelAderencia;
import br.com.menthoros.backend.enums.NivelExperiencia;
import br.com.menthoros.backend.enums.PlanoAssessoria;
import br.com.menthoros.backend.enums.PlanoReviewStatus;
import br.com.menthoros.backend.enums.PlanoStatus;
import br.com.menthoros.backend.enums.RecommendationType;
import br.com.menthoros.backend.services.helper.RevisaoSemanalCalculator;
import br.com.menthoros.backend.repository.AssessoriaRepository;
import br.com.menthoros.backend.repository.AtletaRepository;
import br.com.menthoros.backend.repository.PlanoMetadadosRepository;
import br.com.menthoros.backend.repository.PlanoSemanalRepository;
import br.com.menthoros.backend.repository.RevisaoSemanalRepository;
import br.com.menthoros.backend.services.RevisaoSemanalService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integração real (Testcontainers) do hook de encerramento
 * ({@link RevisaoSemanalService#gerarNoEncerramento}): geração no {@code CONCLUIDO} (CA1),
 * idempotência/insert-if-absent (CA6), gate de status e isolamento por tenant (CA7).
 */
@Transactional
class RevisaoSemanalGeracaoIT extends AbstractIntegrationTest {

    @Autowired
    private RevisaoSemanalService revisaoSemanalService;
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
    @DisplayName("gerarNoEncerramento")
    class GerarNoEncerramento {

        @Test
        @DisplayName("plano CONCLUIDO gera a revisão congelada (semana sem treinos → BAIXA/MAINTAIN)")
        void planoConcluidoGera() {
            PlanoSemanal plano = salvarPlano(PlanoStatus.CONCLUIDO, new BigDecimal("-5"));
            UUID tenantId = plano.getAssessoria().getId();

            revisaoSemanalService.gerarNoEncerramento(plano.getId(), tenantId);
            flushClear();

            Optional<RevisaoSemanal> r = revisaoSemanalRepository.findByPlanoSemanalIdAndTenant(plano.getId(), tenantId);
            assertThat(r).isPresent();
            assertThat(r.get().getAdherenceStatus()).isEqualTo(NivelAderencia.BAIXA);
            assertThat(r.get().getRecommendationType()).isEqualTo(RecommendationType.MAINTAIN);
            assertThat(r.get().isSufficientData()).isFalse();
            assertThat(r.get().getGeradaEm()).isNotNull();
        }

        @Test
        @DisplayName("revisão nasce com o foco template, coerente com o recommendationType (CA-LLM)")
        void nasceComFocoTemplate() {
            PlanoSemanal plano = salvarPlano(PlanoStatus.CONCLUIDO, new BigDecimal("-5"));
            UUID tenantId = plano.getAssessoria().getId();

            revisaoSemanalService.gerarNoEncerramento(plano.getId(), tenantId);
            flushClear();

            RevisaoSemanal r = revisaoSemanalRepository
                    .findByPlanoSemanalIdAndTenant(plano.getId(), tenantId).orElseThrow();
            assertThat(r.getFocusSource()).isEqualTo(FocusSource.TEMPLATE);
            assertThat(r.getNextWeekFocus())
                    .isEqualTo(RevisaoSemanalCalculator.nextWeekFocusTemplate(r.getRecommendationType()));
        }

        @Test
        @DisplayName("idempotente — reexecutar não duplica (insert-if-absent, preserva o congelamento)")
        void idempotente() {
            PlanoSemanal plano = salvarPlano(PlanoStatus.CONCLUIDO, new BigDecimal("-5"));
            UUID tenantId = plano.getAssessoria().getId();

            revisaoSemanalService.gerarNoEncerramento(plano.getId(), tenantId);
            revisaoSemanalService.gerarNoEncerramento(plano.getId(), tenantId);
            flushClear();

            assertThat(revisaoSemanalRepository.count()).isEqualTo(1);
        }

        @Test
        @DisplayName("plano não CONCLUIDO (perdidos-only / semana aberta) não gera revisão")
        void planoNaoConcluidoNaoGera() {
            PlanoSemanal plano = salvarPlano(PlanoStatus.EM_ANDAMENTO, new BigDecimal("-5"));
            UUID tenantId = plano.getAssessoria().getId();

            revisaoSemanalService.gerarNoEncerramento(plano.getId(), tenantId);
            flushClear();

            assertThat(revisaoSemanalRepository.findByPlanoSemanalIdAndTenant(plano.getId(), tenantId)).isEmpty();
        }

        @Test
        @DisplayName("tenant errado não encontra o plano e não gera (CA7)")
        void tenantErradoNaoGera() {
            PlanoSemanal plano = salvarPlano(PlanoStatus.CONCLUIDO, new BigDecimal("-5"));
            UUID tenantId = plano.getAssessoria().getId();

            revisaoSemanalService.gerarNoEncerramento(plano.getId(), UUID.randomUUID());
            flushClear();

            assertThat(revisaoSemanalRepository.findByPlanoSemanalIdAndTenant(plano.getId(), tenantId)).isEmpty();
        }
    }

    // ---- helpers ----

    private void flushClear() {
        entityManager.flush();
        entityManager.clear();
    }

    private PlanoSemanal salvarPlano(PlanoStatus status, BigDecimal tsbFim) {
        Assessoria assessoria = new Assessoria();
        assessoria.setNome("Assessoria Geracao Test");
        assessoria.setDominio("geracao-test-" + UUID.randomUUID());
        assessoria.setPlano(PlanoAssessoria.BASIC);
        assessoria = assessoriaRepository.save(assessoria);

        Atleta atleta = new Atleta();
        atleta.setNome("Atleta Geracao");
        atleta.setObjetivo("Correr 10km");
        atleta.setNivelExperiencia(NivelExperiencia.INICIANTE);
        atleta.setAtivo(AtletaStatus.ATIVO);
        atleta.setAssessoria(assessoria);
        atleta = atletaRepository.save(atleta);

        PlanoMetaDados meta = new PlanoMetaDados();
        meta.setAtleta(atleta);
        meta.setAssessoria(assessoria);
        meta.setDiaPreferidoLongo(DiaSemana.SABADO);
        meta = planoMetadadosRepository.save(meta);

        LocalDate semanaFim = LocalDate.now();
        PlanoSemanal plano = new PlanoSemanal();
        plano.setAtleta(atleta);
        plano.setAssessoria(assessoria);
        plano.setPlanoMetaDados(meta);
        plano.setSemanaInicio(semanaFim.minusDays(6));
        plano.setSemanaFim(semanaFim);
        plano.setVolumePlanejadoKm(BigDecimal.valueOf(40));
        plano.setTsbFim(tsbFim);
        plano.setStatus(status);
        plano.setReviewStatus(PlanoReviewStatus.AGUARDANDO_REVISAO);
        plano.setObjetivoSemanal("Semana base");
        return planoSemanalRepository.save(plano);
    }
}
