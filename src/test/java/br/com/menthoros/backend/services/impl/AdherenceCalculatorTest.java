package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.dto.output.Aderencia4SemanasDto;
import br.com.menthoros.backend.dto.output.AderenciasSemanalDto;
import br.com.menthoros.backend.entity.TreinoPlanejado;
import br.com.menthoros.backend.entity.TreinoRealizado;
import br.com.menthoros.backend.enums.ReconciliationStatus;
import br.com.menthoros.backend.enums.StatusSincronizacao;
import br.com.menthoros.backend.enums.TipoTreino;
import br.com.menthoros.backend.repository.TreinoPlanejadoRepository;
import br.com.menthoros.backend.repository.TreinoRealizadoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Cobertura de cálculo puro do {@link AdherenceCalculator} — extraído em
 * extract-adherence-calculator (2026-10) de {@code AtletaProgressServiceImpl} (janela de 4
 * semanas / semanal) e {@code ProgressaoTreinoServiceImpl} (janela ISO fechada / regra antiga).
 * As asserções preservam exatamente o comportamento dos métodos originais (CA1).
 *
 * <p>A orquestração de {@code calcularHistorico} (fonte da data — atleta vs. servidor — e
 * filtragem de contagem) continua coberta em {@code ProgressaoTreinoServiceImplTest}, exercitando
 * este calculator real (CA2b) — não duplicado aqui.</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AdherenceCalculator")
class AdherenceCalculatorTest {

    // HOJE = 2026-06-17 (quarta) — mesma base usada pelos métodos de janela semanal/4-semanas.
    private static final LocalDate HOJE = LocalDate.of(2026, 6, 17);

    @Mock private TreinoPlanejadoRepository treinoPlanejadoRepository;
    @Mock private TreinoRealizadoRepository treinoRealizadoRepository;

    private AdherenceCalculator calculator;
    private UUID tenantId;
    private UUID atletaId;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        atletaId = UUID.randomUUID();
        Clock clock = Clock.fixed(Instant.parse("2026-06-17T12:00:00Z"), ZoneOffset.UTC);
        calculator = new AdherenceCalculator(treinoPlanejadoRepository, treinoRealizadoRepository, clock);
    }

    @Nested
    @DisplayName("getAderenciaSemanal")
    class GetAderenciaSemanal {

        // inicioSemanaAtual = 2026-06-15 (seg); semanas=8 → dataInicio = 2026-06-15 - 7 semanas = 2026-04-27
        private final LocalDate DATA_INICIO = LocalDate.of(2026, 4, 27);
        // fix-weekly-adherence-future-days-excluded: dataFim da consulta é o fim da semana ATUAL
        // (domingo), não HOJE — senão um planejado pro resto da semana nem entraria no total.
        private final LocalDate FIM_SEMANA_ATUAL = LocalDate.of(2026, 6, 21);

        @Test
        @DisplayName("nenhum treino planejado no período → lista vazia")
        void semTreinos() {
            when(treinoPlanejadoRepository.findComRealizadoByAtletaAndPeriodoAteData(atletaId, tenantId, DATA_INICIO, FIM_SEMANA_ATUAL))
                    .thenReturn(List.of());

            assertThat(calculator.getAderenciaSemanal(atletaId, tenantId, 8)).isEmpty();
        }

        @Test
        @DisplayName("treinos na mesma semana — calcula percentual corretamente")
        void calculaPercentualSemanal() {
            TreinoPlanejado tp1 = treinoPlanejadoComRealizado(LocalDate.of(2026, 6, 17), true);
            TreinoPlanejado tp2 = treinoPlanejadoComRealizado(LocalDate.of(2026, 6, 16), false);
            when(treinoPlanejadoRepository.findComRealizadoByAtletaAndPeriodoAteData(atletaId, tenantId, DATA_INICIO, FIM_SEMANA_ATUAL))
                    .thenReturn(List.of(tp1, tp2));

            var resultado = calculator.getAderenciaSemanal(atletaId, tenantId, 8);

            assertThat(resultado).hasSize(1);
            assertThat(resultado.get(0).semanaInicio()).isEqualTo(LocalDate.of(2026, 6, 15));
            assertThat(resultado.get(0).totalPlanejado()).isEqualTo(2);
            assertThat(resultado.get(0).totalRealizado()).isEqualTo(1);
            assertThat(resultado.get(0).percentual()).isEqualTo(50);
        }

        @Test
        @DisplayName("regressão — treino planejado pra depois de HOJE na semana atual entra no total (não fica 100% com dia pendente)")
        void treinoFuturoNaSemanaAtualEntraNoTotal() {
            TreinoPlanejado feito = treinoPlanejadoComRealizado(LocalDate.of(2026, 6, 17), true);
            TreinoPlanejado pendente = treinoPlanejadoComRealizado(LocalDate.of(2026, 6, 20), false);
            when(treinoPlanejadoRepository.findComRealizadoByAtletaAndPeriodoAteData(atletaId, tenantId, DATA_INICIO, FIM_SEMANA_ATUAL))
                    .thenReturn(List.of(feito, pendente));

            var resultado = calculator.getAderenciaSemanal(atletaId, tenantId, 8);

            assertThat(resultado).hasSize(1);
            assertThat(resultado.get(0).totalPlanejado()).isEqualTo(2);
            assertThat(resultado.get(0).totalRealizado()).isEqualTo(1);
            assertThat(resultado.get(0).percentual()).isEqualTo(50);
        }

        @Test
        @DisplayName("regressão — vínculo CANCELADO no Strava não conta como realizado")
        void vinculoCanceladoNaoContaComoRealizado() {
            TreinoPlanejado tp = new TreinoPlanejado();
            tp.setDataTreino(LocalDate.of(2026, 6, 17));
            TreinoRealizado trCancelado = new TreinoRealizado();
            trCancelado.setStatusSincronizacao(StatusSincronizacao.CANCELADO);
            tp.setTreinoRealizado(trCancelado);
            when(treinoPlanejadoRepository.findComRealizadoByAtletaAndPeriodoAteData(atletaId, tenantId, DATA_INICIO, FIM_SEMANA_ATUAL))
                    .thenReturn(List.of(tp));

            var resultado = calculator.getAderenciaSemanal(atletaId, tenantId, 8);

            assertThat(resultado).hasSize(1);
            assertThat(resultado.get(0).totalRealizado()).isZero();
            assertThat(resultado.get(0).percentual()).isZero();
        }

        @Test
        @DisplayName("treinos em semanas distintas — resultado ordenado por semanaInicio ASC")
        void multiplasSemanasOrdenadas() {
            TreinoPlanejado tpA1 = treinoPlanejadoComRealizado(LocalDate.of(2026, 6, 9), true);
            TreinoPlanejado tpA2 = treinoPlanejadoComRealizado(LocalDate.of(2026, 6, 10), true);
            TreinoPlanejado tpA3 = treinoPlanejadoComRealizado(LocalDate.of(2026, 6, 11), false);
            TreinoPlanejado tpB1 = treinoPlanejadoComRealizado(LocalDate.of(2026, 6, 16), true);
            TreinoPlanejado tpB2 = treinoPlanejadoComRealizado(LocalDate.of(2026, 6, 17), true);
            when(treinoPlanejadoRepository.findComRealizadoByAtletaAndPeriodoAteData(atletaId, tenantId, DATA_INICIO, FIM_SEMANA_ATUAL))
                    .thenReturn(List.of(tpA1, tpA2, tpA3, tpB1, tpB2));

            var resultado = calculator.getAderenciaSemanal(atletaId, tenantId, 8);

            assertThat(resultado).hasSize(2);
            assertThat(resultado.get(0).semanaInicio()).isEqualTo(LocalDate.of(2026, 6, 8));
            assertThat(resultado.get(0).totalPlanejado()).isEqualTo(3);
            assertThat(resultado.get(0).totalRealizado()).isEqualTo(2);
            assertThat(resultado.get(0).percentual()).isEqualTo(67);
            assertThat(resultado.get(1).semanaInicio()).isEqualTo(LocalDate.of(2026, 6, 15));
            assertThat(resultado.get(1).percentual()).isEqualTo(100);
        }

        @Test
        @DisplayName("todos os treinos sem realizado → percentual 0, lista retornada (tem planejados)")
        void semNenhumRealizado() {
            TreinoPlanejado tp = treinoPlanejadoComRealizado(LocalDate.of(2026, 6, 16), false);
            when(treinoPlanejadoRepository.findComRealizadoByAtletaAndPeriodoAteData(atletaId, tenantId, DATA_INICIO, FIM_SEMANA_ATUAL))
                    .thenReturn(List.of(tp));

            var resultado = calculator.getAderenciaSemanal(atletaId, tenantId, 8);

            assertThat(resultado).hasSize(1);
            assertThat(resultado.get(0).percentual()).isZero();
            assertThat(resultado.get(0).totalRealizado()).isZero();
        }
    }

    @Nested
    @DisplayName("getAderencia4Semanas")
    class GetAderencia4Semanas {

        private final LocalDate DATA_INICIO = LocalDate.of(2026, 5, 25);
        private final LocalDate FIM_SEMANA_ATUAL = LocalDate.of(2026, 6, 21);

        @Test
        @DisplayName("nenhum treino planejado na janela → zerado, não null")
        void semTreinos() {
            when(treinoPlanejadoRepository.findComRealizadoByAtletaAndPeriodoAteData(atletaId, tenantId, DATA_INICIO, FIM_SEMANA_ATUAL))
                    .thenReturn(List.of());

            var resultado = calculator.getAderencia4Semanas(atletaId, tenantId);

            assertThat(resultado.planejado()).isZero();
            assertThat(resultado.realizado()).isZero();
            assertThat(resultado.percentual()).isZero();
        }

        @Test
        @DisplayName("calcula percentual sobre a janela de 4 semanas (atual + 3 anteriores)")
        void calculaPercentual() {
            TreinoPlanejado feito1 = treinoPlanejadoComRealizado(LocalDate.of(2026, 6, 1), true);
            TreinoPlanejado feito2 = treinoPlanejadoComRealizado(LocalDate.of(2026, 6, 10), true);
            TreinoPlanejado pendente = treinoPlanejadoComRealizado(LocalDate.of(2026, 6, 16), false);
            when(treinoPlanejadoRepository.findComRealizadoByAtletaAndPeriodoAteData(atletaId, tenantId, DATA_INICIO, FIM_SEMANA_ATUAL))
                    .thenReturn(List.of(feito1, feito2, pendente));

            var resultado = calculator.getAderencia4Semanas(atletaId, tenantId);

            assertThat(resultado.planejado()).isEqualTo(3);
            assertThat(resultado.realizado()).isEqualTo(2);
            assertThat(resultado.percentual()).isEqualTo(67);
        }

        @Test
        @DisplayName("regressão — planejado pra depois de HOJE na semana atual entra no total (não fica 100% com dia pendente)")
        void incluiDiaFuturoDaSemanaAtual() {
            TreinoPlanejado feito = treinoPlanejadoComRealizado(HOJE, true);
            TreinoPlanejado pendenteDepoisDeHoje = treinoPlanejadoComRealizado(FIM_SEMANA_ATUAL, false);
            when(treinoPlanejadoRepository.findComRealizadoByAtletaAndPeriodoAteData(atletaId, tenantId, DATA_INICIO, FIM_SEMANA_ATUAL))
                    .thenReturn(List.of(feito, pendenteDepoisDeHoje));

            var resultado = calculator.getAderencia4Semanas(atletaId, tenantId);

            assertThat(resultado.planejado()).isEqualTo(2);
            assertThat(resultado.realizado()).isEqualTo(1);
            assertThat(resultado.percentual()).isEqualTo(50);
        }

        @Test
        @DisplayName("regressão — vínculo CANCELADO no Strava não conta como realizado")
        void vinculoCanceladoNaoContaComoRealizado() {
            TreinoPlanejado tp = new TreinoPlanejado();
            tp.setDataTreino(HOJE);
            TreinoRealizado trCancelado = new TreinoRealizado();
            trCancelado.setStatusSincronizacao(StatusSincronizacao.CANCELADO);
            tp.setTreinoRealizado(trCancelado);
            when(treinoPlanejadoRepository.findComRealizadoByAtletaAndPeriodoAteData(atletaId, tenantId, DATA_INICIO, FIM_SEMANA_ATUAL))
                    .thenReturn(List.of(tp));

            var resultado = calculator.getAderencia4Semanas(atletaId, tenantId);

            assertThat(resultado.realizado()).isZero();
            assertThat(resultado.percentual()).isZero();
        }
    }

    @Nested
    @DisplayName("calcularAderenciaJanelaFechada")
    class CalcularAderenciaJanelaFechada {

        // hoje = 2026-07-08 (quarta) → segundaAtual = 2026-07-06 → janela [2026-06-15, 2026-07-05]
        private static final LocalDate HOJE_JANELA = LocalDate.of(2026, 7, 8);
        private static final LocalDate INICIO_JANELA = LocalDate.of(2026, 6, 15);
        private static final LocalDate FIM_JANELA = LocalDate.of(2026, 7, 5);

        private void stubJanela(List<TreinoPlanejado> planejados, List<TreinoRealizado> avulsos) {
            when(treinoPlanejadoRepository.findComRealizadoByAtletaAndPeriodoAteData(
                    eq(atletaId), eq(tenantId), eq(INICIO_JANELA), eq(FIM_JANELA)))
                    .thenReturn(planejados);
            when(treinoRealizadoRepository.findByAtletaIdAndTenantIdAndDataTreinoBetween(
                    eq(atletaId), eq(tenantId), eq(INICIO_JANELA), eq(FIM_JANELA)))
                    .thenReturn(avulsos);
        }

        @Test
        @DisplayName("CA2 — DESCANSO não entra no denominador")
        void descansoForaDoDenominador() {
            TreinoPlanejado descanso = planejadoDescanso(INICIO_JANELA);
            TreinoPlanejado cumprido1 = vinculadoCumprido(planejadoDevido(INICIO_JANELA.plusDays(1)));
            TreinoPlanejado cumprido2 = vinculadoCumprido(planejadoDevido(INICIO_JANELA.plusDays(2)));
            stubJanela(List.of(descanso, cumprido1, cumprido2), List.of());

            var resultado = calculator.calcularAderenciaJanelaFechada(atletaId, tenantId, HOJE_JANELA);

            assertThat(resultado.cumpridos()).isEqualTo(2);
            assertThat(resultado.faltas()).isZero();
            assertThat(resultado.aderencia()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("CA3 — treino extra sem planejado na janela não infla a aderência")
        void extraNaoInfla() {
            List<TreinoPlanejado> planejados = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                planejados.add(vinculadoCumprido(planejadoDevido(INICIO_JANELA.plusDays(i))));
            }
            for (int i = 3; i < 6; i++) {
                planejados.add(planejadoDevido(INICIO_JANELA.plusDays(i)));
            }
            List<TreinoRealizado> extras = List.of(
                    avulso(INICIO_JANELA.plusDays(10), null, null),
                    avulso(INICIO_JANELA.plusDays(11), null, null),
                    avulso(INICIO_JANELA.plusDays(12), null, null),
                    avulso(INICIO_JANELA.plusDays(13), null, null)
            );
            stubJanela(planejados, extras);

            var resultado = calculator.calcularAderenciaJanelaFechada(atletaId, tenantId, HOJE_JANELA);

            assertThat(resultado.cumpridos()).isEqualTo(3);
            assertThat(resultado.faltas()).isEqualTo(3);
            assertThat(resultado.aderencia()).isEqualTo(0.5);
        }

        @Test
        @DisplayName("CA4 — pendência de reconciliação fica fora da conta (nem cumprido, nem falta)")
        void pendenciaForaDaConta() {
            List<TreinoPlanejado> planejados = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                planejados.add(vinculadoCumprido(planejadoDevido(INICIO_JANELA.plusDays(i))));
            }
            planejados.add(planejadoDevido(INICIO_JANELA.plusDays(3))); // pendente — avulso ambíguo no dia
            planejados.add(planejadoDevido(INICIO_JANELA.plusDays(4))); // falta
            planejados.add(planejadoDevido(INICIO_JANELA.plusDays(5))); // falta
            List<TreinoRealizado> avulsos = List.of(avulso(INICIO_JANELA.plusDays(3), null, null));
            stubJanela(planejados, avulsos);

            var resultado = calculator.calcularAderenciaJanelaFechada(atletaId, tenantId, HOJE_JANELA);

            assertThat(resultado.cumpridos()).isEqualTo(3);
            assertThat(resultado.faltas()).isEqualTo(2);
            assertThat(resultado.pendentes()).isEqualTo(1);
            assertThat(resultado.aderencia()).isEqualTo(0.6);
        }

        @Test
        @DisplayName("CA5 — pendências acima de 25% dos devidos tornam a aderência ausente")
        void pendenciaDemaisTornaAderenciaAusente() {
            List<TreinoPlanejado> planejados = new ArrayList<>();
            planejados.add(vinculadoCumprido(planejadoDevido(INICIO_JANELA)));
            planejados.add(vinculadoCumprido(planejadoDevido(INICIO_JANELA.plusDays(1))));
            planejados.add(planejadoDevido(INICIO_JANELA.plusDays(2))); // pendente
            planejados.add(planejadoDevido(INICIO_JANELA.plusDays(3))); // pendente
            List<TreinoRealizado> avulsos = List.of(
                    avulso(INICIO_JANELA.plusDays(2), null, null),
                    avulso(INICIO_JANELA.plusDays(3), null, null)
            );
            stubJanela(planejados, avulsos);

            var resultado = calculator.calcularAderenciaJanelaFechada(atletaId, tenantId, HOJE_JANELA);

            assertThat(resultado.pendentes()).isEqualTo(2);
            assertThat(resultado.aderencia()).isNull();
        }

        @Test
        @DisplayName("borda exata do teto de pendência: 25,0% (1 de 4 devidos) ainda decide — não é \"> 25%\"")
        void tetoDePendenciaNaBordaExataAindaDecide() {
            List<TreinoPlanejado> planejados = new ArrayList<>();
            planejados.add(vinculadoCumprido(planejadoDevido(INICIO_JANELA)));
            planejados.add(vinculadoCumprido(planejadoDevido(INICIO_JANELA.plusDays(1))));
            planejados.add(vinculadoCumprido(planejadoDevido(INICIO_JANELA.plusDays(2))));
            planejados.add(planejadoDevido(INICIO_JANELA.plusDays(3))); // pendente — 1 de 4 devidos = 25,0%
            List<TreinoRealizado> avulsos = List.of(avulso(INICIO_JANELA.plusDays(3), null, null));
            stubJanela(planejados, avulsos);

            var resultado = calculator.calcularAderenciaJanelaFechada(atletaId, tenantId, HOJE_JANELA);

            assertThat(resultado.pendentes()).isEqualTo(1);
            assertThat(resultado.aderencia()).isNotNull();
            assertThat(resultado.aderencia()).isEqualTo(1.0); // 3 cumpridos / (3 cumpridos + 0 faltas)
        }

        @Test
        @DisplayName("regressão — três avulsos no mesmo dia, só um triado por humano, continua pendente")
        void tresAvulsosNoMesmoDiaSoUmHumanoAindaPendente() {
            TreinoPlanejado semVinculo = planejadoDevido(INICIO_JANELA);
            List<TreinoRealizado> avulsos = List.of(
                    avulso(INICIO_JANELA, ReconciliationStatus.NAO_PLANEJADO, "coach-123"),
                    avulso(INICIO_JANELA, ReconciliationStatus.AMBIGUO, "SYSTEM"),
                    avulso(INICIO_JANELA, ReconciliationStatus.NAO_PLANEJADO, "SYSTEM"));
            stubJanela(List.of(semVinculo), avulsos);

            var resultado = calculator.calcularAderenciaJanelaFechada(atletaId, tenantId, HOJE_JANELA);

            assertThat(resultado.pendentes()).isEqualTo(1);
            assertThat(resultado.faltas()).isZero();
        }

        @Test
        @DisplayName("CA10 — vínculo cancelado não conta como cumprido")
        void vinculoCanceladoContaComoFalta() {
            TreinoPlanejado cancelado = vinculadoCancelado(planejadoDevido(INICIO_JANELA));
            stubJanela(List.of(cancelado), List.of());

            var resultado = calculator.calcularAderenciaJanelaFechada(atletaId, tenantId, HOJE_JANELA);

            assertThat(resultado.cumpridos()).isZero();
            assertThat(resultado.faltas()).isEqualTo(1);
        }

        @Test
        @DisplayName("CA11 — triagem humana de não-correspondência resolve a pendência como falta")
        void triagemHumanaContaComoFalta() {
            TreinoPlanejado semVinculo = planejadoDevido(INICIO_JANELA);
            List<TreinoRealizado> avulsos = List.of(
                    avulso(INICIO_JANELA, ReconciliationStatus.NAO_PLANEJADO, "coach-123"));
            stubJanela(List.of(semVinculo), avulsos);

            var resultado = calculator.calcularAderenciaJanelaFechada(atletaId, tenantId, HOJE_JANELA);

            assertThat(resultado.faltas()).isEqualTo(1);
            assertThat(resultado.pendentes()).isZero();
        }

        @Test
        @DisplayName("CA12 — NAO_PLANEJADO automático (reconciledBy=SYSTEM) continua pendente")
        void naoPlanejadoAutomaticoContinuaPendente() {
            TreinoPlanejado semVinculo = planejadoDevido(INICIO_JANELA);
            List<TreinoRealizado> avulsos = List.of(
                    avulso(INICIO_JANELA, ReconciliationStatus.NAO_PLANEJADO, "SYSTEM"));
            stubJanela(List.of(semVinculo), avulsos);

            var resultado = calculator.calcularAderenciaJanelaFechada(atletaId, tenantId, HOJE_JANELA);

            assertThat(resultado.pendentes()).isEqualTo(1);
            assertThat(resultado.faltas()).isZero();
        }

        @Test
        @DisplayName("regressão (Codex adversarial-review, 2026-10-01) — avulso humano não encobre outro ainda ambíguo no mesmo dia")
        void doisAvulsosNoMesmoDiaUmHumanoOutroAindaAmbiguoContinuaPendente() {
            TreinoPlanejado semVinculo = planejadoDevido(INICIO_JANELA);
            List<TreinoRealizado> avulsos = List.of(
                    avulso(INICIO_JANELA, ReconciliationStatus.NAO_PLANEJADO, "coach-123"),
                    avulso(INICIO_JANELA, ReconciliationStatus.AMBIGUO, "SYSTEM"));
            stubJanela(List.of(semVinculo), avulsos);

            var resultado = calculator.calcularAderenciaJanelaFechada(atletaId, tenantId, HOJE_JANELA);

            assertThat(resultado.pendentes()).isEqualTo(1);
            assertThat(resultado.faltas()).isZero();
        }

        @Test
        @DisplayName("regressão (Codex review, 2026-10-01) — avulso CANCELADO não vira candidato de pendência/falta")
        void avulsoCanceladoNaoContaComoCandidato() {
            TreinoPlanejado semVinculo = planejadoDevido(INICIO_JANELA);
            TreinoRealizado avulsoCancelado = avulso(INICIO_JANELA, ReconciliationStatus.NAO_PLANEJADO, "SYSTEM");
            avulsoCancelado.setStatusSincronizacao(StatusSincronizacao.CANCELADO);
            stubJanela(List.of(semVinculo), List.of(avulsoCancelado));

            var resultado = calculator.calcularAderenciaJanelaFechada(atletaId, tenantId, HOJE_JANELA);

            assertThat(resultado.faltas()).isEqualTo(1);
            assertThat(resultado.pendentes()).isZero();
        }
    }

    @Nested
    @DisplayName("calcularAderenciaRegraAntiga")
    class CalcularAderenciaRegraAntiga {

        private static final LocalDate HOJE_21D = LocalDate.of(2026, 7, 8);
        private static final LocalDate INICIO_21D = HOJE_21D.minusDays(21);

        @Test
        @DisplayName("D7 — ratio simples realizados/planejados, sem filtro de DESCANSO/pendência")
        void calculaRatioSimples() {
            when(treinoPlanejadoRepository.findComRealizadoByAtletaAndPeriodoAteData(atletaId, tenantId, INICIO_21D, HOJE_21D))
                    .thenReturn(List.of(planejadoDevido(INICIO_21D), planejadoDevido(INICIO_21D.plusDays(1)),
                            planejadoDevido(INICIO_21D.plusDays(2)), planejadoDevido(INICIO_21D.plusDays(3))));

            var resultado = calculator.calcularAderenciaRegraAntiga(atletaId, tenantId, INICIO_21D, HOJE_21D, 3);

            assertThat(resultado.cumpridos()).isEqualTo(3);
            assertThat(resultado.faltas()).isEqualTo(1);
            assertThat(resultado.pendentes()).isZero();
            assertThat(resultado.aderencia()).isEqualTo(0.75);
        }

        @Test
        @DisplayName("nenhum planejado na janela → aderência 0.0, não divisão por zero")
        void semPlanejados() {
            when(treinoPlanejadoRepository.findComRealizadoByAtletaAndPeriodoAteData(atletaId, tenantId, INICIO_21D, HOJE_21D))
                    .thenReturn(Collections.emptyList());

            var resultado = calculator.calcularAderenciaRegraAntiga(atletaId, tenantId, INICIO_21D, HOJE_21D, 0);

            assertThat(resultado.cumpridos()).isZero();
            assertThat(resultado.faltas()).isZero();
            assertThat(resultado.aderencia()).isEqualTo(0.0);
        }

        @Test
        @DisplayName("consulta o repositório com dataFim = hoje (parâmetro) — não vaza dias futuros")
        void consultaComDataFimIgualAHoje() {
            when(treinoPlanejadoRepository.findComRealizadoByAtletaAndPeriodoAteData(atletaId, tenantId, INICIO_21D, HOJE_21D))
                    .thenReturn(List.of(planejadoDevido(INICIO_21D)));

            var resultado = calculator.calcularAderenciaRegraAntiga(atletaId, tenantId, INICIO_21D, HOJE_21D, 0);

            assertThat(resultado.faltas()).isEqualTo(1);
        }
    }

    // ===== helpers =====

    private TreinoPlanejado treinoPlanejadoComRealizado(LocalDate data, boolean realizado) {
        TreinoPlanejado tp = new TreinoPlanejado();
        tp.setDataTreino(data);
        if (realizado) {
            tp.setTreinoRealizado(new TreinoRealizado());
        }
        return tp;
    }

    /** Planejado "devido" (não-DESCANSO) dentro da janela de aderência. */
    private TreinoPlanejado planejadoDevido(LocalDate data) {
        TreinoPlanejado p = new TreinoPlanejado();
        p.setDataTreino(data);
        p.setTipoTreino(TipoTreino.FACIL);
        return p;
    }

    private TreinoPlanejado planejadoDescanso(LocalDate data) {
        TreinoPlanejado p = new TreinoPlanejado();
        p.setDataTreino(data);
        p.setTipoTreino(TipoTreino.DESCANSO);
        return p;
    }

    /** Vincula um {@link TreinoRealizado} que conta na carga (cumprimento normal) ao planejado. */
    private TreinoPlanejado vinculadoCumprido(TreinoPlanejado planejado) {
        TreinoRealizado realizado = new TreinoRealizado();
        realizado.setDataTreino(planejado.getDataTreino());
        planejado.setTreinoRealizado(realizado);
        return planejado;
    }

    /** Vincula um {@link TreinoRealizado} cancelado no Strava — não conta como cumprido (CA10). */
    private TreinoPlanejado vinculadoCancelado(TreinoPlanejado planejado) {
        TreinoRealizado realizado = new TreinoRealizado();
        realizado.setDataTreino(planejado.getDataTreino());
        realizado.setStatusSincronizacao(StatusSincronizacao.CANCELADO);
        planejado.setTreinoRealizado(realizado);
        return planejado;
    }

    private TreinoRealizado avulso(LocalDate data, ReconciliationStatus status, String reconciledBy) {
        TreinoRealizado r = new TreinoRealizado();
        r.setDataTreino(data);
        r.setReconciliationStatus(status);
        r.setReconciledBy(reconciledBy);
        return r;
    }
}
