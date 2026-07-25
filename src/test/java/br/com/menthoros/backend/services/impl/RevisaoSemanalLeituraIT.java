package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.AbstractIntegrationTest;
import br.com.menthoros.backend.dto.output.RevisaoSemanalOutputDto;
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
import br.com.menthoros.backend.exception.DomainNotFoundException;
import br.com.menthoros.backend.multitenancy.TenantContext;
import br.com.menthoros.backend.repository.AssessoriaRepository;
import br.com.menthoros.backend.repository.AtletaRepository;
import br.com.menthoros.backend.repository.PlanoMetadadosRepository;
import br.com.menthoros.backend.repository.PlanoSemanalRepository;
import br.com.menthoros.backend.repository.RevisaoSemanalRepository;
import br.com.menthoros.backend.services.RevisaoSemanalService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integração real (Testcontainers) da leitura {@link RevisaoSemanalService#buscarUltima}:
 * weekOverWeekDelta computado (CA9), congelamento — devolve o persistido sem recomputar
 * (CA-Congelamento) — e 404 quando não há revisão.
 */
@Transactional
class RevisaoSemanalLeituraIT extends AbstractIntegrationTest {

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

    @AfterEach
    void limparTenant() {
        TenantContext.clear();
    }

    @Nested
    @DisplayName("buscarUltima")
    class BuscarUltima {

        @Test
        @DisplayName("weekOverWeekDelta computado vs. a revisão anterior (CA9)")
        void comDelta() {
            Atleta atleta = seedAtleta();
            salvarRevisao(atleta, LocalDate.now().minusDays(7), new BigDecimal("-10"),
                    RecommendationType.MAINTAIN, NivelAderencia.MEDIA, new BigDecimal("60.00"));
            salvarRevisao(atleta, LocalDate.now(), new BigDecimal("0"),
                    RecommendationType.PROGRESS, NivelAderencia.ALTA, new BigDecimal("90.00"));
            flushClear();
            TenantContext.setTenantId(atleta.getAssessoria().getId());

            RevisaoSemanalOutputDto dto = revisaoSemanalService.buscarUltima(atleta.getId());

            assertThat(dto.recommendationType()).isEqualTo(RecommendationType.PROGRESS);
            assertThat(dto.weekOverWeekDelta().primeiraSemana()).isFalse();
            assertThat(dto.weekOverWeekDelta().recommendationAnterior()).isEqualTo(RecommendationType.MAINTAIN);
            assertThat(dto.weekOverWeekDelta().deltaPercentualRealizacao()).isEqualByComparingTo("30.00");
            assertThat(dto.weekOverWeekDelta().deltaTsbFim()).isEqualByComparingTo("10");
        }

        @Test
        @DisplayName("PRIMEIRA_SEMANA quando não há revisão anterior")
        void primeiraSemana() {
            Atleta atleta = seedAtleta();
            salvarRevisao(atleta, LocalDate.now(), new BigDecimal("-5"),
                    RecommendationType.MAINTAIN, NivelAderencia.MEDIA, new BigDecimal("70.00"));
            flushClear();
            TenantContext.setTenantId(atleta.getAssessoria().getId());

            RevisaoSemanalOutputDto dto = revisaoSemanalService.buscarUltima(atleta.getId());

            assertThat(dto.weekOverWeekDelta().primeiraSemana()).isTrue();
            assertThat(dto.weekOverWeekDelta().deltaPercentualRealizacao()).isNull();
        }

        @Test
        @DisplayName("congelamento — devolve o recommendationType persistido mesmo contradizendo a regra")
        void congelamento() {
            Atleta atleta = seedAtleta();
            // PROGRESS com aderência BAIXA nunca seria produzido pela regra — prova que a leitura
            // devolve o valor persistido, sem recomputar (ADR-0006).
            salvarRevisao(atleta, LocalDate.now(), new BigDecimal("-30"),
                    RecommendationType.PROGRESS, NivelAderencia.BAIXA, new BigDecimal("10.00"));
            flushClear();
            TenantContext.setTenantId(atleta.getAssessoria().getId());

            RevisaoSemanalOutputDto dto = revisaoSemanalService.buscarUltima(atleta.getId());

            assertThat(dto.recommendationType()).isEqualTo(RecommendationType.PROGRESS);
            assertThat(dto.adherenceStatus()).isEqualTo(NivelAderencia.BAIXA);
        }

        @Test
        @DisplayName("sem revisão → DomainNotFoundException (mapeada para 404)")
        void semRevisao() {
            Atleta atleta = seedAtleta();
            TenantContext.setTenantId(atleta.getAssessoria().getId());

            assertThatThrownBy(() -> revisaoSemanalService.buscarUltima(atleta.getId()))
                    .isInstanceOf(DomainNotFoundException.class);
        }
    }

    // ---- helpers ----

    private void flushClear() {
        entityManager.flush();
        entityManager.clear();
    }

    private Atleta seedAtleta() {
        Assessoria assessoria = new Assessoria();
        assessoria.setNome("Assessoria Leitura Test");
        assessoria.setDominio("leitura-test-" + UUID.randomUUID());
        assessoria.setPlano(PlanoAssessoria.BASIC);
        assessoria = assessoriaRepository.save(assessoria);

        Atleta atleta = new Atleta();
        atleta.setNome("Atleta Leitura");
        atleta.setObjetivo("Correr 10km");
        atleta.setNivelExperiencia(NivelExperiencia.INICIANTE);
        atleta.setAtivo(AtletaStatus.ATIVO);
        atleta.setAssessoria(assessoria);
        return atletaRepository.save(atleta);
    }

    private void salvarRevisao(Atleta atleta, LocalDate semanaFim, BigDecimal tsbFim,
                               RecommendationType recommendationType, NivelAderencia status,
                               BigDecimal percentual) {
        PlanoMetaDados meta = new PlanoMetaDados();
        meta.setAtleta(atleta);
        meta.setAssessoria(atleta.getAssessoria());
        meta.setDiaPreferidoLongo(DiaSemana.SABADO);
        meta = planoMetadadosRepository.save(meta);

        PlanoSemanal plano = new PlanoSemanal();
        plano.setAtleta(atleta);
        plano.setAssessoria(atleta.getAssessoria());
        plano.setPlanoMetaDados(meta);
        plano.setSemanaInicio(semanaFim.minusDays(6));
        plano.setSemanaFim(semanaFim);
        plano.setVolumePlanejadoKm(BigDecimal.valueOf(40));
        plano.setTsbFim(tsbFim);
        plano.setStatus(PlanoStatus.CONCLUIDO);
        plano.setReviewStatus(PlanoReviewStatus.AGUARDANDO_REVISAO);
        plano.setObjetivoSemanal("Semana base");
        plano = planoSemanalRepository.save(plano);

        revisaoSemanalRepository.save(RevisaoSemanal.builder()
                .planoSemanal(plano)
                .recommendationType(recommendationType)
                .adherenceStatus(status)
                .percentualRealizacao(percentual)
                .dadosSuficientes(true)
                .geradaEm(Instant.now())
                .build());
    }
}
