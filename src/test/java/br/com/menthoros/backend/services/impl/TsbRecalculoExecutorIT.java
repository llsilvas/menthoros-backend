package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.AbstractIntegrationTest;
import br.com.menthoros.backend.entity.Assessoria;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.MetricasDiarias;
import br.com.menthoros.backend.entity.PlanoMetaDados;
import br.com.menthoros.backend.entity.TreinoRealizado;
import br.com.menthoros.backend.enums.AtletaStatus;
import br.com.menthoros.backend.enums.DiaSemana;
import br.com.menthoros.backend.enums.NivelExperiencia;
import br.com.menthoros.backend.enums.PlanoAssessoria;
import br.com.menthoros.backend.enums.TipoTreino;
import br.com.menthoros.backend.repository.AssessoriaRepository;
import br.com.menthoros.backend.repository.AtletaRepository;
import br.com.menthoros.backend.repository.MetricasDiariasRepository;
import br.com.menthoros.backend.repository.PlanoMetadadosRepository;
import br.com.menthoros.backend.repository.TreinoRealizadoRepository;
import br.com.menthoros.backend.services.TsbService;
import br.com.menthoros.backend.services.helper.TsbRecalculoExecutor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Fronteira transacional do bloco: propagação real, contagem de blocos e a garantia de que
 * nenhum intervalo fica apagado sem reconstrução.
 *
 * <p>Não é {@code @Transactional} — os blocos comitam por conta própria, e é justamente isso que
 * está sob teste.</p>
 */
class TsbRecalculoExecutorIT extends AbstractIntegrationTest {

    @Autowired
    private TsbService tsbService;
    @Autowired
    private TransactionTemplate transactionTemplate;
    @Autowired
    private AssessoriaRepository assessoriaRepository;
    @Autowired
    private AtletaRepository atletaRepository;
    @Autowired
    private PlanoMetadadosRepository planoMetadadosRepository;
    @Autowired
    private TreinoRealizadoRepository treinoRealizadoRepository;
    @Autowired
    private MetricasDiariasRepository metricasDiariasRepository;

    @MockitoSpyBean
    private TsbRecalculoExecutor tsbRecalculoExecutor;

    @Nested
    @DisplayName("recalcularBloco")
    class RecalcularBloco {

        @Test
        @DisplayName("bloco comita mesmo quando a transação do chamador reverte (REQUIRES_NEW real)")
        void comitaApesarDoRollbackDoChamador() {
            Atleta atleta = seedAtleta();
            LocalDate dia = LocalDate.now().minusDays(3);

            // O chamador abre transação, delega o bloco e então reverte. Se REQUIRES_NEW não
            // estivesse valendo — método privado, auto-invocação, anotação decorativa — o bloco
            // participaria desta transação e sumiria junto no rollback.
            transactionTemplate.execute(status -> {
                tsbRecalculoExecutor.recalcularBloco(atleta.getId(), dia, dia,
                        (id, data) -> tsbService.atualizarTsbDia(id, data));
                status.setRollbackOnly();
                return null;
            });

            assertThat(metricasDiariasRepository.findByAtletaIdAndData(atleta.getId(), dia))
                    .as("o bloco tem de sobreviver ao rollback do chamador")
                    .isPresent();
        }

        @Test
        @DisplayName("falha no meio do bloco não deixa o intervalo apagado: o dado antigo permanece")
        void falhaNoBlocoPreservaDadoAntigo() {
            Atleta atleta = seedAtleta();
            LocalDate inicio = LocalDate.now().minusDays(5);
            LocalDate fim = LocalDate.now().minusDays(1);

            // Histórico "antigo" reconhecível, que o bloco vai tentar apagar e reconstruir.
            for (LocalDate d = inicio; !d.isAfter(fim); d = d.plusDays(1)) {
                metricasDiariasRepository.save(MetricasDiarias.builder()
                        .atleta(atleta)
                        .tenantId(atleta.getAssessoria().getId())
                        .data(d)
                        .tss(0)
                        .ctl(999.0).atl(999.0).tsb(0.0)
                        .volumeKm(BigDecimal.ZERO)
                        .treinosRealizados(0)
                        .build());
            }

            assertThatThrownBy(() -> tsbRecalculoExecutor.recalcularBloco(atleta.getId(), inicio, fim,
                    (id, data) -> {
                        if (data.equals(inicio.plusDays(2))) {
                            throw new IllegalStateException("falha injetada no meio do bloco");
                        }
                        tsbService.atualizarTsbDia(id, data);
                    }))
                    .isInstanceOf(IllegalStateException.class);

            List<MetricasDiarias> apos = metricasDiariasRepository
                    .findByAtletaIdAndDataBetweenOrderByDataAsc(atleta.getId(), inicio, fim);

            assertThat(apos)
                    .as("o intervalo não pode ficar vazio — delete e reconstrução vivem na mesma transação")
                    .hasSize(5);
            assertThat(apos).allSatisfy(m -> assertThat(m.getCtl())
                    .as("o dado antigo tem de continuar intacto em %s", m.getData())
                    .isEqualTo(999.0));
        }
    }

    @Nested
    @DisplayName("recalcularHistoricoCompleto")
    class RecalcularHistoricoCompleto {

        @Test
        @DisplayName("400 dias são processados em 14 blocos de no máximo 30 dias")
        void quatrocentosDiasEmQuatorzeBlocos() {
            Atleta atleta = seedAtleta();
            LocalDate inicio = LocalDate.now().minusDays(399);
            // O intervalo vem do primeiro e do último treino: dois treinos bastam para 400 dias.
            salvarTreino(atleta, inicio);
            salvarTreino(atleta, LocalDate.now());

            tsbService.recalcularHistoricoCompleto(atleta.getId());

            // ceil(400 / 30) = 14
            verify(tsbRecalculoExecutor, times(14))
                    .recalcularBloco(eq(atleta.getId()), any(), any(), any());

            assertThat(metricasDiariasRepository.findByAtletaIdOrderByDataAsc(atleta.getId()))
                    .as("os 400 dias devem estar materializados")
                    .hasSize(400);
        }

        @Test
        @DisplayName("intervalo vai além do último treino quando há métricas materializadas depois")
        void intervaloRespeitaMetricasAlemDoUltimoTreino() {
            Atleta atleta = seedAtleta();
            LocalDate ultimoTreino = LocalDate.now().minusDays(20);
            salvarTreino(atleta, ultimoTreino);

            // Dia de descanso materializado depois do último treino — o limite superior tem de vir
            // das métricas, não só dos treinos.
            LocalDate metricaTardia = LocalDate.now().minusDays(5);
            metricasDiariasRepository.save(MetricasDiarias.builder()
                    .atleta(atleta)
                    .tenantId(atleta.getAssessoria().getId())
                    .data(metricaTardia)
                    .tss(0)
                    .ctl(1.0).atl(1.0).tsb(0.0)
                    .volumeKm(BigDecimal.ZERO)
                    .treinosRealizados(0)
                    .build());

            tsbService.recalcularHistoricoCompleto(atleta.getId());

            assertThat(metricasDiariasRepository.findByAtletaIdAndData(atleta.getId(), metricaTardia))
                    .as("o intervalo tem de alcançar a métrica posterior ao último treino")
                    .isPresent();
        }
    }

    // ---- fixtures ----

    private Atleta seedAtleta() {
        Assessoria assessoria = new Assessoria();
        assessoria.setNome("Assessoria TSB Chunk");
        assessoria.setDominio("tsb-chunk-" + UUID.randomUUID());
        assessoria.setPlano(PlanoAssessoria.BASIC);
        assessoria = assessoriaRepository.save(assessoria);

        Atleta atleta = new Atleta();
        atleta.setNome("Atleta TSB Chunk");
        atleta.setEmail("tsb-chunk-" + UUID.randomUUID() + "@test.com");
        atleta.setObjetivo("Validar a fronteira transacional do bloco");
        atleta.setNivelExperiencia(NivelExperiencia.AVANCADO);
        atleta.setAtivo(AtletaStatus.ATIVO);
        atleta.setAssessoria(assessoria);
        atleta.setCtlTimeConstant(42);
        atleta.setAtlTimeConstant(7);
        atleta = atletaRepository.save(atleta);

        PlanoMetaDados meta = new PlanoMetaDados();
        meta.setAtleta(atleta);
        meta.setAssessoria(assessoria);
        meta.setDiaPreferidoLongo(DiaSemana.SABADO);
        planoMetadadosRepository.save(meta);

        return atleta;
    }

    private void salvarTreino(Atleta atleta, LocalDate data) {
        TreinoRealizado treino = new TreinoRealizado();
        treino.setAtleta(atleta);
        treino.setDataTreino(data);
        treino.setDiaSemana(diaSemanaDe(data));
        treino.setTipoTreino(TipoTreino.FACIL);
        treino.setDuracaoMin(Duration.ofMinutes(36));
        treino.setPercepcaoEsforco(8);
        treino.setDistanciaKm(BigDecimal.ZERO);
        treinoRealizadoRepository.save(treino);
    }

    private DiaSemana diaSemanaDe(LocalDate data) {
        DayOfWeek dow = data.getDayOfWeek();
        return switch (dow) {
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
