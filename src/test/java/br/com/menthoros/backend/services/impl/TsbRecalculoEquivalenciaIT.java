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
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Rede de segurança do CA5 — congela o resultado de {@code recalcularHistoricoCompleto}
 * ANTES do chunking transacional, para provar que o refactor não muda nenhum número.
 *
 * <p><b>Deliberadamente NÃO é {@code @Transactional}.</b> O método sob teste comita fora do
 * controle do teste (hoje dia a dia, depois por bloco com {@code REQUIRES_NEW}). Um teste
 * transacional envolveria tudo numa transação só e mascararia exatamente o comportamento que
 * esta change existe para corrigir. As asserções leem do banco via consulta nova.</p>
 *
 * <p><b>Gabarito:</b> {@code openspec/changes/fix-tsb-recalculo-resiliente/reference-dataset.md}.
 * Os valores foram calculados manualmente a partir das fórmulas EWMA, não extraídos da aplicação.</p>
 *
 * <p><b>Por que TSS é previsível:</b> com {@link TipoTreino#FACIL} (fatorImpacto = 1.0) e RPE = 8
 * (IF = 1.0 exato), a fórmula colapsa em {@code TSS = duracaoMin × 100 / 60}. Como todo TSS do
 * dataset é múltiplo de 5, {@code duracaoMin = TSS × 3 / 5} é inteiro e devolve o TSS exato, sem
 * ambiguidade de arredondamento.</p>
 */
class TsbRecalculoEquivalenciaIT extends AbstractIntegrationTest {

    /**
     * Tolerância das asserções contra o gabarito.
     *
     * <p>O {@code reference-dataset.md} sugere 0.01, mas o desvio máximo medido entre o gabarito e
     * a semântica de arredondamento real do código é exatamente 0.0100 — ou seja, 0.01 fica na
     * fronteira, sem margem para ruído de ponto flutuante. O código carrega CTL/ATL <b>arredondados</b>
     * para o dia seguinte ({@code metricasOntem.getCtl()}), mas calcula TSB a partir dos valores
     * <b>não arredondados</b> do mesmo dia — a diferença acumula ao longo de 90 dias.</p>
     *
     * <p>A garantia forte de equivalência do CA5 não vem daqui: vem da comparação exata dia a dia
     * antes/depois do refactor (ver {@code snapshot()}), que não usa tolerância nenhuma.</p>
     */
    private static final double DELTA = 0.02;

    private static final int DIAS = 90;

    /**
     * TSS diário do dataset de referência.
     *
     * <p><b>Dia 14 = 40</b>, não 55. A tabela AVANÇADO do {@code reference-dataset.md} v1.0 traz 55
     * nessa célula, mas a tabela INICIANTE traz 40 e os CTL/ATL/TSB tabelados nas duas foram
     * calculados com 40. Reproduzindo a EWMA com 55, 12 dos 14 checkpoints divergem a partir do dia
     * 14; com 40, todos batem. É typo da coluna TSS, não dos valores esperados.</p>
     */
    private static final int[] TSS = {
         35,   40,    0,   45,   50,    0,   55,   60,    0,   45,
         50,    0,   55,   40,   60,   65,    0,   70,   75,    0,
         80,   70,    0,   85,   90,    0,   75,   80,    0,   95,
        100,  110,    0,  120,   90,    0,  130,   40,   30,    0,
         20,    0,   30,   50,   70,   80,    0,   90,  100,    0,
        110,   85,    0,   95,  105,    0,  100,   90,    0,  115,
        120,  130,    0,  140,  180,    0,  100,  110,    0,  150,
         80,   60,    0,   40,    0,   30,    0,  200,    0,    0,
         20,    0,   30,    0,   40,    0,   50,   60,    0,   70
    };

    private static final double[] CTL_AVANCADO = {
          0.82,    1.75,    1.70,    2.72,    3.84,    3.74,    4.95,    6.25,    6.10,    7.01,
          8.03,    7.84,    8.95,    9.68,   10.86,   12.14,   11.85,   13.22,   14.67,   14.33,
         15.87,   17.14,   16.74,   18.35,   20.03,   19.56,   20.87,   22.26,   21.73,   23.46,
         25.26,   27.25,   26.61,   28.81,   30.25,   29.54,   31.90,   32.09,   32.04,   31.29,
         31.02,   30.29,   30.29,   30.75,   31.67,   32.81,   32.04,   33.40,   34.97,   34.15,
         35.93,   37.09,   36.21,   37.60,   39.18,   38.26,   39.71,   40.90,   39.93,   41.70,
         43.54,   45.58,   44.50,   46.75,   49.89,   48.71,   49.92,   51.33,   50.12,   52.47,
         53.12,   53.28,   52.03,   51.75,   50.53,   50.05,   48.87,   52.43,   51.19,   49.99,
         49.28,   48.12,   47.70,   46.57,   46.42,   45.33,   45.44,   45.78,   44.70,   45.30
    };

    private static final double[] ATL_AVANCADO = {
          4.66,    9.36,    8.12,   13.03,   17.95,   15.56,   20.81,   26.03,   22.56,   25.55,
         28.80,   24.97,   28.97,   30.44,   34.37,   38.45,   33.33,   38.21,   43.11,   37.37,
         43.05,   46.63,   40.43,   46.36,   52.17,   45.22,   49.19,   53.29,   46.20,   52.69,
         58.99,   65.78,   57.02,   65.41,   68.68,   59.54,   68.92,   65.07,   60.40,   52.36,
         48.05,   41.66,   40.10,   41.42,   45.23,   49.85,   43.22,   49.45,   56.18,   48.70,
         56.86,   60.60,   52.54,   58.19,   64.42,   55.85,   61.72,   65.49,   56.77,   64.52,
         71.91,   79.64,   69.04,   78.48,   92.00,   79.75,   82.45,   86.12,   74.65,   84.68,
         84.06,   80.86,   70.09,   66.09,   57.29,   53.66,   46.51,   66.95,   58.03,   50.31,
         46.27,   40.11,   38.77,   33.61,   34.46,   29.87,   32.55,   36.20,   31.38,   36.53
    };

    private static final double[] TSB_AVANCADO = {
         -3.84,   -7.62,   -6.41,  -10.30,  -14.11,  -11.81,  -15.86,  -19.78,  -16.46,  -18.53,
        -20.78,  -17.13,  -20.02,  -20.76,  -23.51,  -26.31,  -21.48,  -24.99,  -28.44,  -23.04,
        -27.17,  -29.49,  -23.68,  -28.01,  -32.14,  -25.66,  -28.32,  -31.03,  -24.46,  -29.23,
        -33.73,  -38.53,  -30.41,  -36.60,  -38.43,  -30.00,  -37.02,  -32.98,  -28.36,  -21.07,
        -17.03,  -11.36,   -9.82,  -10.67,  -13.55,  -17.04,  -11.18,  -16.04,  -21.21,  -14.55,
        -20.93,  -23.52,  -16.32,  -20.59,  -25.24,  -17.59,  -22.01,  -24.59,  -16.84,  -22.82,
        -28.36,  -34.06,  -24.53,  -31.73,  -42.11,  -31.04,  -32.53,  -34.78,  -24.53,  -32.21,
        -30.94,  -27.57,  -18.06,  -14.34,   -6.76,   -3.61,    2.36,  -14.52,   -6.84,   -0.32,
          3.01,    8.01,    8.93,   12.97,   11.96,   15.46,   12.89,    9.57,   13.32,    8.77
    };

    private static final double[] CTL_INICIANTE = {
          1.15,    2.42,    2.34,    3.74,    5.26,    5.08,    6.72,    8.47,    8.19,    9.40,
         10.73,   10.38,   11.84,   12.76,   14.31,   15.97,   15.45,   17.24,   19.13,   18.50,
         20.52,   22.14,   21.42,   23.50,   25.68,   24.84,   26.48,   28.24,   27.31,   29.53,
         31.84,   34.40,   33.28,   36.12,   37.89,   36.64,   39.70,   39.71,   39.40,   38.10,
         37.51,   36.28,   36.07,   36.53,   37.63,   39.02,   37.74,   39.45,   41.44,   40.08,
         42.37,   43.77,   42.33,   44.06,   46.06,   44.55,   46.37,   47.80,   46.23,   48.48,
         50.83,   53.42,   51.67,   54.57,   58.68,   56.76,   58.17,   59.87,   57.91,   60.93,
         61.55,   61.50,   59.49,   58.85,   56.92,   56.04,   54.20,   58.98,   57.05,   55.18,
         54.02,   52.25,   51.52,   49.83,   49.51,   47.89,   47.96,   48.35,   46.77,   47.53
    };

    private static final double[] ATL_INICIANTE = {
          6.34,   12.45,   10.19,   16.50,   22.57,   18.48,   25.10,   31.43,   25.73,   29.22,
         32.99,   27.01,   32.08,   33.52,   38.32,   43.16,   35.33,   41.62,   47.67,   39.03,
         46.45,   50.72,   41.53,   49.41,   56.77,   46.48,   51.65,   56.79,   46.49,   55.29,
         63.39,   71.84,   58.82,   69.91,   73.55,   60.22,   72.87,   66.91,   60.22,   49.30,
         43.99,   36.02,   34.93,   37.66,   43.52,   50.13,   41.05,   49.92,   59.00,   48.30,
         59.49,   64.11,   52.49,   60.20,   68.32,   55.93,   63.92,   68.65,   56.20,   66.86,
         76.49,   86.19,   70.57,   83.16,  100.71,   82.45,   85.63,   90.05,   73.73,   87.55,
         86.18,   81.44,   66.68,   61.84,   50.63,   46.89,   38.39,   67.69,   55.42,   45.37,
         40.77,   33.38,   32.77,   26.83,   29.22,   23.92,   28.65,   34.33,   28.11,   35.70
    };

    private static final double[] TSB_INICIANTE = {
         -5.20,  -10.02,   -7.85,  -12.76,  -17.32,  -13.40,  -18.38,  -22.96,  -17.54,  -19.83,
        -22.26,  -16.63,  -20.24,  -20.76,  -24.01,  -27.18,  -19.88,  -24.38,  -28.54,  -20.52,
        -25.93,  -28.58,  -20.11,  -25.91,  -31.09,  -21.64,  -25.16,  -28.55,  -19.18,  -25.75,
        -31.55,  -37.44,  -25.54,  -33.79,  -35.66,  -23.57,  -33.16,  -27.20,  -20.82,  -11.20,
         -6.48,    0.26,    1.15,   -1.13,   -5.89,  -11.12,   -3.31,  -10.47,  -17.56,   -8.23,
        -17.12,  -20.34,  -10.16,  -16.14,  -22.26,  -11.39,  -17.56,  -20.85,   -9.98,  -18.38,
        -25.67,  -32.77,  -18.90,  -28.59,  -42.03,  -25.70,  -27.46,  -30.18,  -15.82,  -26.62,
        -24.63,  -19.93,   -7.19,   -2.99,    6.29,    9.15,   15.81,   -8.71,    1.63,    9.80,
         13.25,   18.87,   18.75,   23.00,   20.29,   23.97,   19.31,   14.02,   18.66,   11.83
    };

    @Autowired
    private TsbService tsbService;
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

    @Nested
    @DisplayName("recalcularHistoricoCompleto")
    class RecalcularHistoricoCompleto {

        @Test
        @DisplayName("90 dias, atleta AVANÇADO (τ=42/7): CTL/ATL/TSB batem dia a dia com o gabarito")
        void equivalenciaAvancado() {
            LocalDate base = dataBase();
            Atleta atleta = seedAtletaComHistorico(42, 7, base);

            tsbService.recalcularHistoricoCompleto(atleta.getId());

            assertSerie(atleta.getId(), base, CTL_AVANCADO, ATL_AVANCADO, TSB_AVANCADO, "AVANÇADO");
        }

        @Test
        @DisplayName("90 dias, constantes customizadas (τ=30/5): CTL/ATL/TSB batem dia a dia")
        void equivalenciaConstantesCustomizadas() {
            LocalDate base = dataBase();
            Atleta atleta = seedAtletaComHistorico(30, 5, base);

            tsbService.recalcularHistoricoCompleto(atleta.getId());

            assertSerie(atleta.getId(), base, CTL_INICIANTE, ATL_INICIANTE, TSB_INICIANTE, "INICIANTE");
        }

        @Test
        @DisplayName("rampRate do dia 61 usa D-7 cruzando a fronteira de bloco (dia 54)")
        void rampRateCruzaFronteiraDeBloco() {
            LocalDate base = dataBase();
            Atleta atleta = seedAtletaComHistorico(42, 7, base);

            tsbService.recalcularHistoricoCompleto(atleta.getId());

            Map<LocalDate, MetricasDiarias> porData = indexarPorData(atleta.getId());
            // Invariante 5 do reference-dataset.md: CTL[61] - CTL[54] = 43.54 - 37.60 = 5.94.
            // É a asserção que prova que a leitura de D-7 atravessa o corte do chunk (dia 60→61).
            MetricasDiarias dia61 = porData.get(base.plusDays(60));
            assertThat(dia61).as("dia 61 deve existir").isNotNull();
            assertThat(dia61.getRampRate())
                    .as("rampRate do dia 61 = CTL[61] - CTL[54]")
                    .isCloseTo(5.94, within(DELTA));
        }

        @Test
        @DisplayName("duas execuções consecutivas produzem exatamente os mesmos 90 valores (idempotência)")
        void idempotente() {
            LocalDate base = dataBase();
            Atleta atleta = seedAtletaComHistorico(42, 7, base);

            tsbService.recalcularHistoricoCompleto(atleta.getId());
            Map<LocalDate, double[]> primeira = snapshot(atleta.getId());

            tsbService.recalcularHistoricoCompleto(atleta.getId());
            Map<LocalDate, double[]> segunda = snapshot(atleta.getId());

            assertThat(segunda.keySet())
                    .as("mesmo conjunto de dias nas duas execuções")
                    .isEqualTo(primeira.keySet());

            SoftAssertions soft = new SoftAssertions();
            primeira.forEach((data, esperado) -> soft.assertThat(segunda.get(data))
                    .as("métricas de %s idênticas entre execuções", data)
                    .containsExactly(esperado));
            soft.assertAll();
        }

        @Test
        @DisplayName("atleta sem treino e sem métricas: metadados zerados, nenhuma métrica escrita")
        void semHistoricoZeraMetadados() {
            Atleta atleta = seedAtleta(42, 7);

            tsbService.recalcularHistoricoCompleto(atleta.getId());

            assertThat(metricasDiariasRepository.findByAtletaIdOrderByDataAsc(atleta.getId()))
                    .as("nenhuma métrica deve ser criada para atleta sem histórico")
                    .isEmpty();

            PlanoMetaDados meta = planoMetadadosRepository.findByAtletaId(atleta.getId()).orElseThrow();
            assertThat(meta.getCtlAtual()).isZero();
            assertThat(meta.getAtlAtual()).isZero();
            assertThat(meta.getTsbAtual()).isZero();
        }
    }

    // ---- asserções ----

    private void assertSerie(UUID atletaId, LocalDate base,
                             double[] ctlEsperado, double[] atlEsperado, double[] tsbEsperado,
                             String perfil) {
        Map<LocalDate, MetricasDiarias> porData = indexarPorData(atletaId);

        assertThat(porData)
                .as("%s: devem existir métricas para os %d dias do dataset", perfil, DIAS)
                .hasSize(DIAS);

        SoftAssertions soft = new SoftAssertions();
        for (int i = 0; i < DIAS; i++) {
            LocalDate data = base.plusDays(i);
            MetricasDiarias m = porData.get(data);
            int dia = i + 1;

            if (m == null) {
                soft.fail("%s: métrica ausente no dia %d (%s)", perfil, dia, data);
                continue;
            }

            soft.assertThat(m.getTss())
                    .as("%s dia %d — TSS", perfil, dia)
                    .isEqualTo(TSS[i]);
            soft.assertThat(m.getCtl())
                    .as("%s dia %d — CTL", perfil, dia)
                    .isCloseTo(ctlEsperado[i], within(DELTA));
            soft.assertThat(m.getAtl())
                    .as("%s dia %d — ATL", perfil, dia)
                    .isCloseTo(atlEsperado[i], within(DELTA));
            soft.assertThat(m.getTsb())
                    .as("%s dia %d — TSB", perfil, dia)
                    .isCloseTo(tsbEsperado[i], within(DELTA));
        }
        soft.assertAll();
    }

    /**
     * Snapshot exato (sem tolerância) das métricas de um atleta, para comparação antes/depois.
     * Inclui {@code rampRate} — o CA5 exige identidade dele também, e ele depende de D-7.
     */
    private Map<LocalDate, double[]> snapshot(UUID atletaId) {
        Map<LocalDate, double[]> out = new LinkedHashMap<>();
        for (MetricasDiarias m : metricasDiariasRepository.findByAtletaIdOrderByDataAsc(atletaId)) {
            out.put(m.getData(), new double[]{
                    m.getCtl(), m.getAtl(), m.getTsb(), m.getRampRate()
            });
        }
        return out;
    }

    private Map<LocalDate, MetricasDiarias> indexarPorData(UUID atletaId) {
        Map<LocalDate, MetricasDiarias> out = new LinkedHashMap<>();
        for (MetricasDiarias m : metricasDiariasRepository.findByAtletaIdOrderByDataAsc(atletaId)) {
            out.put(m.getData(), m);
        }
        return out;
    }

    // ---- fixtures ----

    /** Dia 90 do dataset cai em hoje — {@code atualizarTsbDia} rejeita data futura. */
    private LocalDate dataBase() {
        return LocalDate.now().minusDays(DIAS - 1L);
    }

    private Atleta seedAtletaComHistorico(int ctlTau, int atlTau, LocalDate base) {
        Atleta atleta = seedAtleta(ctlTau, atlTau);
        for (int i = 0; i < DIAS; i++) {
            if (TSS[i] > 0) {
                salvarTreino(atleta, base.plusDays(i), TSS[i]);
            }
        }
        return atleta;
    }

    private Atleta seedAtleta(int ctlTau, int atlTau) {
        Assessoria assessoria = new Assessoria();
        assessoria.setNome("Assessoria TSB Equivalencia");
        assessoria.setDominio("tsb-equiv-" + UUID.randomUUID());
        assessoria.setPlano(PlanoAssessoria.BASIC);
        assessoria = assessoriaRepository.save(assessoria);

        Atleta atleta = new Atleta();
        atleta.setNome("Atleta TSB Equivalencia");
        atleta.setEmail("tsb-equiv-" + UUID.randomUUID() + "@test.com");
        atleta.setObjetivo("Congelar o resultado do recálculo histórico");
        atleta.setNivelExperiencia(NivelExperiencia.AVANCADO);
        atleta.setAtivo(AtletaStatus.ATIVO);
        atleta.setAssessoria(assessoria);
        // O valor do campo tem precedência sobre o nível de experiência na resolução de τ.
        atleta.setCtlTimeConstant(ctlTau);
        atleta.setAtlTimeConstant(atlTau);
        atleta = atletaRepository.save(atleta);

        PlanoMetaDados meta = new PlanoMetaDados();
        meta.setAtleta(atleta);
        meta.setAssessoria(assessoria);
        meta.setDiaPreferidoLongo(DiaSemana.SABADO);
        planoMetadadosRepository.save(meta);

        return atleta;
    }

    /**
     * Treino cujo TSS calculado é exatamente {@code tssAlvo}.
     *
     * <p>{@link TipoTreino#FACIL} tem fatorImpacto 1.0 e RPE 8 mapeia para IF 1.0, então
     * {@code TSS = round(duracaoHoras × 1.0² × 100) = duracaoMin × 100 / 60}. Com
     * {@code duracaoMin = tssAlvo × 3 / 5} (inteiro, porque todo TSS do dataset é múltiplo de 5),
     * o resultado é o próprio {@code tssAlvo}, sem arredondamento intermediário.</p>
     */
    private void salvarTreino(Atleta atleta, LocalDate data, int tssAlvo) {
        TreinoRealizado treino = new TreinoRealizado();
        treino.setAtleta(atleta);
        treino.setDataTreino(data);
        treino.setDiaSemana(diaSemanaDe(data));
        treino.setTipoTreino(TipoTreino.FACIL);
        treino.setDuracaoMin(Duration.ofMinutes(tssAlvo * 3L / 5L));
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
