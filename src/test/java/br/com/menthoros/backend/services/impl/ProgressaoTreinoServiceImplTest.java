package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.dto.DecisaoProgressao;
import br.com.menthoros.backend.dto.ProgressaoHistoricoResumo;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.PlanoMetaDados;
import br.com.menthoros.backend.entity.TreinoPlanejado;
import br.com.menthoros.backend.entity.TreinoRealizado;
import br.com.menthoros.backend.enums.EstadoProgressao;
import br.com.menthoros.backend.enums.ReconciliationStatus;
import br.com.menthoros.backend.enums.StatusSincronizacao;
import br.com.menthoros.backend.enums.TipoTreino;
import br.com.menthoros.backend.multitenancy.TenantContext;
import br.com.menthoros.backend.repository.AtletaRepository;
import br.com.menthoros.backend.repository.TreinoPlanejadoRepository;
import br.com.menthoros.backend.repository.TreinoRealizadoRepository;
import br.com.menthoros.backend.services.PlanoMetadadosService;
import br.com.menthoros.backend.services.helper.AtletaHojeResolver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProgressaoTreinoServiceImplTest {

    private static final ZoneId ZONA = ZoneId.of("America/Sao_Paulo");
    private static final LocalDate HOJE = LocalDate.of(2026, 7, 8);
    private static final LocalDate INICIO_7D = HOJE.minusDays(7);
    private static final LocalDate INICIO_21D = HOJE.minusDays(21);
    private static final LocalDate INICIO_42D = HOJE.minusDays(42);
    private static final LocalDate SEGUNDA_ATUAL = HOJE.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    private static final LocalDate INICIO_JANELA = SEGUNDA_ATUAL.minusDays(21);
    private static final LocalDate FIM_JANELA = SEGUNDA_ATUAL.minusDays(1);

    @Mock
    private TreinoRealizadoRepository treinoRealizadoRepository;
    @Mock
    private TreinoPlanejadoRepository treinoPlanejadoRepository;
    @Mock
    private AtletaRepository atletaRepository;
    @Mock
    private PlanoMetadadosService planoMetadadosService;

    private Clock clock;
    private ProgressaoTreinoServiceImpl service;

    private UUID atletaId;
    private UUID tenantId;

    @BeforeEach
    void setUp() {
        atletaId = UUID.randomUUID();
        tenantId = UUID.randomUUID();
        TenantContext.setTenantId(tenantId);

        clock = Clock.fixed(HOJE.atStartOfDay(ZONA).toInstant(), ZONA);
        service = new ProgressaoTreinoServiceImpl(
                treinoRealizadoRepository, treinoPlanejadoRepository, atletaRepository,
                new AtletaHojeResolver(clock), planoMetadadosService, clock);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private void stubAtletaPadrao() {
        when(atletaRepository.findByIdAndTenantId(atletaId, tenantId)).thenReturn(Optional.of(new Atleta()));
    }

    @Nested
    @DisplayName("calcularHistorico")
    class CalcularHistorico {

        @Test
        @DisplayName("atleta com histórico completo de 42 dias — volumes e contagens corretos")
        void historicoCompleto() {
            TreinoRealizado longo7d = treino(HOJE.minusDays(3), TipoTreino.LONGO, 20.0, null);
            TreinoRealizado intervalado21d = treino(HOJE.minusDays(10), TipoTreino.INTERVALADO, 10.0, 8);
            TreinoRealizado longo21d = treino(HOJE.minusDays(14), TipoTreino.LONGO, 22.0, null);
            TreinoRealizado facil42d = treino(HOJE.minusDays(35), TipoTreino.FACIL, 8.0, null);

            when(treinoRealizadoRepository.findByAtletaIdAndTenantIdAndDataTreinoBetween(eq(atletaId), eq(tenantId), any(), any()))
                    .thenReturn(List.of(longo7d, intervalado21d, longo21d, facil42d));
            when(treinoPlanejadoRepository.findComRealizadoByAtletaAndPeriodoAteData(eq(atletaId), eq(tenantId), any(), any()))
                    .thenReturn(List.of(planejado(), planejado(), planejado(), planejado()));
            stubAtletaPadrao();
            when(planoMetadadosService.buscarPorAtletaId(atletaId))
                    .thenReturn(metaDados(-10.0, 50.0, 55.0));

            ProgressaoHistoricoResumo resultado = service.calcularHistorico(atletaId);

            assertThat(resultado.volumeKm7d()).isEqualTo(20.0);
            assertThat(resultado.volumeKm21d()).isEqualTo(52.0);
            assertThat(resultado.volumeKm42d()).isEqualTo(60.0);
            assertThat(resultado.longoesRealizados7d()).isEqualTo(1);
            assertThat(resultado.longoesRealizados21d()).isEqualTo(2);
            assertThat(resultado.treinosRealizados21d()).isEqualTo(3);
            // CA8: flag desligada (default do campo @Value em teste puro) reproduz a regra antiga —
            // aderência = realizados/planejados, sem filtro de DESCANSO/pendência.
            assertThat(resultado.treinosCumpridos()).isEqualTo(3);
            assertThat(resultado.treinosFaltas()).isEqualTo(1);
            assertThat(resultado.aderencia()).isEqualTo(0.75);
            assertThat(resultado.tsbAtual()).isEqualTo(-10.0);
            assertThat(resultado.ctlAtual()).isEqualTo(50.0);
        }

        @Test
        @DisplayName("regra antiga (flag desligada) consulta o repositório com dataFim = hoje — não vaza dias futuros da janela")
        void planejadosLimitadosAHoje() {
            when(treinoRealizadoRepository.findByAtletaIdAndTenantIdAndDataTreinoBetween(eq(atletaId), eq(tenantId), any(), any()))
                    .thenReturn(Collections.emptyList());
            when(treinoPlanejadoRepository.findComRealizadoByAtletaAndPeriodoAteData(eq(atletaId), eq(tenantId), eq(INICIO_21D), eq(HOJE)))
                    .thenReturn(List.of(planejado()));
            stubAtletaPadrao();
            when(planoMetadadosService.buscarPorAtletaId(atletaId))
                    .thenReturn(metaDados(0.0, 0.0, 0.0));

            ProgressaoHistoricoResumo resultado = service.calcularHistorico(atletaId);

            assertThat(resultado.treinosFaltas()).isEqualTo(1);
            verify(treinoPlanejadoRepository).findComRealizadoByAtletaAndPeriodoAteData(atletaId, tenantId, INICIO_21D, HOJE);
        }

        @Test
        @DisplayName("regressão (Codex, 2026-10-01) — janelas legadas (42/21/7d) usam o relógio do servidor, não o fuso do atleta")
        void janelasLegadasNaoUsamFusoDoAtleta() {
            // Atleta num fuso bem distante do relógio do teste (America/Sao_Paulo): se calcularHistorico
            // confundisse "hoje" legado com o hoje no fuso do atleta, as datas destas duas assinaturas
            // divergiriam de HOJE/INICIO_21D/INICIO_42D (fora de escopo desta change — CA8/D7).
            Atleta atletaFusoDistante = Atleta.builder().timezone("Asia/Tokyo").build();
            when(atletaRepository.findByIdAndTenantId(atletaId, tenantId)).thenReturn(Optional.of(atletaFusoDistante));
            when(treinoRealizadoRepository.findByAtletaIdAndTenantIdAndDataTreinoBetween(eq(atletaId), eq(tenantId), eq(INICIO_42D), eq(HOJE)))
                    .thenReturn(Collections.emptyList());
            when(treinoPlanejadoRepository.findComRealizadoByAtletaAndPeriodoAteData(eq(atletaId), eq(tenantId), eq(INICIO_21D), eq(HOJE)))
                    .thenReturn(Collections.emptyList());
            when(planoMetadadosService.buscarPorAtletaId(atletaId)).thenReturn(metaDados(0.0, 0.0, 0.0));

            service.calcularHistorico(atletaId);

            verify(treinoRealizadoRepository).findByAtletaIdAndTenantIdAndDataTreinoBetween(atletaId, tenantId, INICIO_42D, HOJE);
            verify(treinoPlanejadoRepository).findComRealizadoByAtletaAndPeriodoAteData(atletaId, tenantId, INICIO_21D, HOJE);
        }

        @Test
        @DisplayName("novo atleta sem treinos — campos zerados, sem exceção")
        void atletaSemTreinos() {
            when(treinoRealizadoRepository.findByAtletaIdAndTenantIdAndDataTreinoBetween(eq(atletaId), eq(tenantId), any(), any()))
                    .thenReturn(Collections.emptyList());
            when(treinoPlanejadoRepository.findComRealizadoByAtletaAndPeriodoAteData(eq(atletaId), eq(tenantId), any(), any()))
                    .thenReturn(Collections.emptyList());
            stubAtletaPadrao();
            when(planoMetadadosService.buscarPorAtletaId(atletaId))
                    .thenReturn(metaDados(0.0, 0.0, 0.0));

            ProgressaoHistoricoResumo resultado = service.calcularHistorico(atletaId);

            assertThat(resultado.volumeKm7d()).isZero();
            assertThat(resultado.volumeKm21d()).isZero();
            assertThat(resultado.volumeKm42d()).isZero();
            assertThat(resultado.longoesRealizados7d()).isZero();
            assertThat(resultado.longoesRealizados21d()).isZero();
            assertThat(resultado.treinosRealizados21d()).isZero();
            assertThat(resultado.treinosCumpridos()).isZero();
            assertThat(resultado.treinosFaltas()).isZero();
            assertThat(resultado.aderencia()).isEqualTo(0.0);
            assertThat(resultado.rpeMedioTreinosDuros()).isNull();
        }

        @Test
        @DisplayName("RPE ausente nos treinos duros — campo rpeMedioTreinosDuros fica nulo")
        void rpeAusenteNaoBloqueia() {
            TreinoRealizado intervalado = treino(HOJE.minusDays(5), TipoTreino.INTERVALADO, 10.0, null);
            TreinoRealizado tempoRun = treino(HOJE.minusDays(12), TipoTreino.TEMPO_RUN, 8.0, null);

            when(treinoRealizadoRepository.findByAtletaIdAndTenantIdAndDataTreinoBetween(eq(atletaId), eq(tenantId), any(), any()))
                    .thenReturn(List.of(intervalado, tempoRun));
            when(treinoPlanejadoRepository.findComRealizadoByAtletaAndPeriodoAteData(eq(atletaId), eq(tenantId), any(), any()))
                    .thenReturn(List.of(planejado(), planejado()));
            stubAtletaPadrao();
            when(planoMetadadosService.buscarPorAtletaId(atletaId))
                    .thenReturn(metaDados(0.0, 0.0, 0.0));

            ProgressaoHistoricoResumo resultado = service.calcularHistorico(atletaId);

            assertThat(resultado.rpeMedioTreinosDuros()).isNull();
        }

        @Test
        @DisplayName("longão vinculado a planejado LONGO conta como longão mesmo com tipo realizado TEMPO_RUN")
        void longaoComTipoInferidoErradoContaPeloPlanejado() {
            // A sincronização do Strava infere o tipo por duração/FC: um longão de menos de 90min
            // com FC acima do limiar vira TEMPO_RUN. O vínculo com o planejado é a fonte da verdade.
            TreinoRealizado longoMalClassificado =
                    vinculado(treino(HOJE.minusDays(4), TipoTreino.TEMPO_RUN, 18.0, 8), TipoTreino.LONGO);
            TreinoRealizado longoOk = treino(HOJE.minusDays(11), TipoTreino.LONGO, 20.0, null);

            when(treinoRealizadoRepository.findByAtletaIdAndTenantIdAndDataTreinoBetween(eq(atletaId), eq(tenantId), any(), any()))
                    .thenReturn(List.of(longoMalClassificado, longoOk));
            when(treinoPlanejadoRepository.findComRealizadoByAtletaAndPeriodoAteData(eq(atletaId), eq(tenantId), any(), any()))
                    .thenReturn(List.of(planejado(), planejado()));
            stubAtletaPadrao();
            when(planoMetadadosService.buscarPorAtletaId(atletaId))
                    .thenReturn(metaDados(-10.0, 50.0, 55.0));

            ProgressaoHistoricoResumo resultado = service.calcularHistorico(atletaId);

            assertThat(resultado.longoesRealizados7d()).isEqualTo(1);
            assertThat(resultado.longoesRealizados21d()).isEqualTo(2);
            // o RPE 8 do longão não pode contaminar a média de treinos duros
            assertThat(resultado.rpeMedioTreinosDuros()).isNull();
        }

        @Test
        @DisplayName("treino não planejado mantém o tipo inferido na contagem")
        void treinoSemVinculoUsaTipoRealizado() {
            TreinoRealizado avulso = treino(HOJE.minusDays(4), TipoTreino.TEMPO_RUN, 12.0, 8);
            TreinoRealizado longo = treino(HOJE.minusDays(11), TipoTreino.LONGO, 20.0, null);

            when(treinoRealizadoRepository.findByAtletaIdAndTenantIdAndDataTreinoBetween(eq(atletaId), eq(tenantId), any(), any()))
                    .thenReturn(List.of(avulso, longo));
            when(treinoPlanejadoRepository.findComRealizadoByAtletaAndPeriodoAteData(eq(atletaId), eq(tenantId), any(), any()))
                    .thenReturn(List.of(planejado(), planejado()));
            stubAtletaPadrao();
            when(planoMetadadosService.buscarPorAtletaId(atletaId))
                    .thenReturn(metaDados(-10.0, 50.0, 55.0));

            ProgressaoHistoricoResumo resultado = service.calcularHistorico(atletaId);

            assertThat(resultado.longoesRealizados21d()).isEqualTo(1);
            assertThat(resultado.rpeMedioTreinosDuros()).isEqualTo(8.0);
        }

        @Test
        @DisplayName("D8: treino CANCELADO não conta na carga; status null conta normalmente")
        void treinoCanceladoNaoContaNaCarga() {
            TreinoRealizado longoValido = treino(HOJE.minusDays(3), TipoTreino.LONGO, 20.0, null);
            TreinoRealizado longoCancelado = treino(HOJE.minusDays(5), TipoTreino.LONGO, 25.0, null);
            longoCancelado.setStatusSincronizacao(StatusSincronizacao.CANCELADO);
            TreinoRealizado semStatus = treino(HOJE.minusDays(6), TipoTreino.FACIL, 8.0, null);
            semStatus.setStatusSincronizacao(null);

            when(treinoRealizadoRepository.findByAtletaIdAndTenantIdAndDataTreinoBetween(eq(atletaId), eq(tenantId), any(), any()))
                    .thenReturn(List.of(longoValido, longoCancelado, semStatus));
            when(treinoPlanejadoRepository.findComRealizadoByAtletaAndPeriodoAteData(eq(atletaId), eq(tenantId), any(), any()))
                    .thenReturn(List.of(planejado(), planejado(), planejado()));
            stubAtletaPadrao();
            when(planoMetadadosService.buscarPorAtletaId(atletaId))
                    .thenReturn(metaDados(-10.0, 50.0, 55.0));

            ProgressaoHistoricoResumo resultado = service.calcularHistorico(atletaId);

            assertThat(resultado.treinosRealizados21d()).isEqualTo(2);
            assertThat(resultado.longoesRealizados21d()).isEqualTo(1);
            assertThat(resultado.volumeKm21d()).isEqualTo(28.0);
        }
    }

    @Nested
    @DisplayName("calcularHistorico — aderência por devidos (D1/D2/D6/D7, flag ligada)")
    class AderenciaJanelaFechada {

        @BeforeEach
        void ligarFlag() {
            ReflectionTestUtils.setField(service, "aderenciaDevidosEnabled", true);
            stubAtletaPadrao();
            when(planoMetadadosService.buscarPorAtletaId(atletaId)).thenReturn(metaDados(0.0, 0.0, 0.0));
            // a janela de carga (42/21/7d corridos) não importa a estes testes — só a aderência.
            lenient().when(treinoRealizadoRepository.findByAtletaIdAndTenantIdAndDataTreinoBetween(
                            eq(atletaId), eq(tenantId), any(), any()))
                    .thenReturn(Collections.emptyList());
        }

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

            ProgressaoHistoricoResumo resultado = service.calcularHistorico(atletaId);

            assertThat(resultado.treinosCumpridos()).isEqualTo(2);
            assertThat(resultado.treinosFaltas()).isZero();
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
                // sem vínculo e sem avulso no mesmo dia → falta
                planejados.add(planejadoDevido(INICIO_JANELA.plusDays(i)));
            }
            List<TreinoRealizado> extras = List.of(
                    avulso(INICIO_JANELA.plusDays(10), null, null),
                    avulso(INICIO_JANELA.plusDays(11), null, null),
                    avulso(INICIO_JANELA.plusDays(12), null, null),
                    avulso(INICIO_JANELA.plusDays(13), null, null)
            );
            stubJanela(planejados, extras);

            ProgressaoHistoricoResumo resultado = service.calcularHistorico(atletaId);

            assertThat(resultado.treinosCumpridos()).isEqualTo(3);
            assertThat(resultado.treinosFaltas()).isEqualTo(3);
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

            ProgressaoHistoricoResumo resultado = service.calcularHistorico(atletaId);

            assertThat(resultado.treinosCumpridos()).isEqualTo(3);
            assertThat(resultado.treinosFaltas()).isEqualTo(2);
            assertThat(resultado.treinosPendentes()).isEqualTo(1);
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

            ProgressaoHistoricoResumo resultado = service.calcularHistorico(atletaId);

            assertThat(resultado.treinosPendentes()).isEqualTo(2);
            assertThat(resultado.aderencia()).isNull();
        }

        @Test
        @DisplayName("CA7 — histórico mínimo continua contando todos os realizados, vinculados ou não")
        void historicoMinimoIndependenteDoVinculo() {
            TreinoRealizado r1 = treino(HOJE.minusDays(2), TipoTreino.FACIL, 8.0, null);
            TreinoRealizado r2 = treino(HOJE.minusDays(5), TipoTreino.FACIL, 8.0, null);
            TreinoRealizado r3 = treino(HOJE.minusDays(9), TipoTreino.FACIL, 8.0, null);
            when(treinoRealizadoRepository.findByAtletaIdAndTenantIdAndDataTreinoBetween(
                            eq(atletaId), eq(tenantId), eq(INICIO_42D), eq(HOJE)))
                    .thenReturn(List.of(r1, r2, r3));
            stubJanela(List.of(), List.of());

            ProgressaoHistoricoResumo resultado = service.calcularHistorico(atletaId);

            assertThat(resultado.treinosRealizados21d()).isEqualTo(3);
            assertThat(service.calcularDecisao(resultado).motivo()).doesNotContain("insuficiente");
        }

        @Test
        @DisplayName("CA10 — vínculo cancelado não conta como cumprido")
        void vinculoCanceladoContaComoFalta() {
            TreinoPlanejado cancelado = vinculadoCancelado(planejadoDevido(INICIO_JANELA));
            stubJanela(List.of(cancelado), List.of());

            ProgressaoHistoricoResumo resultado = service.calcularHistorico(atletaId);

            assertThat(resultado.treinosCumpridos()).isZero();
            assertThat(resultado.treinosFaltas()).isEqualTo(1);
        }

        @Test
        @DisplayName("CA11 — triagem humana de não-correspondência resolve a pendência como falta")
        void triagemHumanaContaComoFalta() {
            TreinoPlanejado semVinculo = planejadoDevido(INICIO_JANELA);
            List<TreinoRealizado> avulsos = List.of(
                    avulso(INICIO_JANELA, ReconciliationStatus.NAO_PLANEJADO, "coach-123"));
            stubJanela(List.of(semVinculo), avulsos);

            ProgressaoHistoricoResumo resultado = service.calcularHistorico(atletaId);

            assertThat(resultado.treinosFaltas()).isEqualTo(1);
            assertThat(resultado.treinosPendentes()).isZero();
        }

        @Test
        @DisplayName("CA12 — NAO_PLANEJADO automático (reconciledBy=SYSTEM) continua pendente")
        void naoPlanejadoAutomaticoContinuaPendente() {
            TreinoPlanejado semVinculo = planejadoDevido(INICIO_JANELA);
            List<TreinoRealizado> avulsos = List.of(
                    avulso(INICIO_JANELA, ReconciliationStatus.NAO_PLANEJADO, "SYSTEM"));
            stubJanela(List.of(semVinculo), avulsos);

            ProgressaoHistoricoResumo resultado = service.calcularHistorico(atletaId);

            assertThat(resultado.treinosPendentes()).isEqualTo(1);
            assertThat(resultado.treinosFaltas()).isZero();
        }

        @Test
        @DisplayName("regressão (Codex adversarial-review, 2026-10-01) — avulso humano não encobre outro ainda ambíguo no mesmo dia")
        void doisAvulsosNoMesmoDiaUmHumanoOutroAindaAmbiguoContinuaPendente() {
            TreinoPlanejado semVinculo = planejadoDevido(INICIO_JANELA);
            List<TreinoRealizado> avulsos = List.of(
                    avulso(INICIO_JANELA, ReconciliationStatus.NAO_PLANEJADO, "coach-123"),
                    avulso(INICIO_JANELA, ReconciliationStatus.AMBIGUO, "SYSTEM"));
            stubJanela(List.of(semVinculo), avulsos);

            ProgressaoHistoricoResumo resultado = service.calcularHistorico(atletaId);

            // Um avulso já triado como "não corresponde" não resolve a ambiguidade do dia enquanto
            // o outro candidato ainda não passou por triagem humana — a pendência precisa continuar
            // fora da conta (nem falta, nem cumprido), e não CA11 (allMatch, não anyMatch).
            assertThat(resultado.treinosPendentes()).isEqualTo(1);
            assertThat(resultado.treinosFaltas()).isZero();
        }

        @Test
        @DisplayName("regressão (Codex review, 2026-10-01) — avulso CANCELADO não vira candidato de pendência/falta")
        void avulsoCanceladoNaoContaComoCandidato() {
            TreinoPlanejado semVinculo = planejadoDevido(INICIO_JANELA);
            TreinoRealizado avulsoCancelado = avulso(INICIO_JANELA, ReconciliationStatus.NAO_PLANEJADO, "SYSTEM");
            avulsoCancelado.setStatusSincronizacao(StatusSincronizacao.CANCELADO);
            stubJanela(List.of(semVinculo), List.of(avulsoCancelado));

            ProgressaoHistoricoResumo resultado = service.calcularHistorico(atletaId);

            // O avulso foi apagado no Strava (markAsCanceled nunca remove a linha) — não pode
            // encobrir a falta como se houvesse um candidato de verdade no dia.
            assertThat(resultado.treinosFaltas()).isEqualTo(1);
            assertThat(resultado.treinosPendentes()).isZero();
        }

        @ParameterizedTest(name = "hoje = segunda da semana atual + {0} dia(s)")
        @ValueSource(ints = {0, 1, 2, 3, 4, 5, 6})
        @DisplayName("CA1 — semana em curso fica fora da janela, qualquer dia em que o histórico for calculado")
        void semanaEmCursoForaDaJanela(int offsetDias) {
            LocalDate segundaAtualDoCaso = LocalDate.of(2026, 7, 6);
            LocalDate hojeDoCaso = segundaAtualDoCaso.plusDays(offsetDias);
            Clock clockDoCaso = Clock.fixed(hojeDoCaso.atStartOfDay(ZONA).toInstant(), ZONA);
            ProgressaoTreinoServiceImpl servicoDoCaso = new ProgressaoTreinoServiceImpl(
                    treinoRealizadoRepository, treinoPlanejadoRepository, atletaRepository,
                    new AtletaHojeResolver(clockDoCaso), planoMetadadosService, clockDoCaso);
            ReflectionTestUtils.setField(servicoDoCaso, "aderenciaDevidosEnabled", true);

            LocalDate inicioJanelaCaso = segundaAtualDoCaso.minusDays(21);
            LocalDate fimJanelaCaso = segundaAtualDoCaso.minusDays(1);
            List<TreinoPlanejado> tresSemanasCompletas = new ArrayList<>();
            for (LocalDate data = inicioJanelaCaso; !data.isAfter(fimJanelaCaso); data = data.plusDays(1)) {
                tresSemanasCompletas.add(vinculadoCumprido(planejadoDevido(data)));
            }
            when(treinoPlanejadoRepository.findComRealizadoByAtletaAndPeriodoAteData(
                            eq(atletaId), eq(tenantId), eq(inicioJanelaCaso), eq(fimJanelaCaso)))
                    .thenReturn(tresSemanasCompletas);

            ProgressaoHistoricoResumo resultado = servicoDoCaso.calcularHistorico(atletaId);

            // A semana atual (1 de 4 treinos feitos) fica inteiramente fora da janela — não é
            // consultada, e por isso nem precisa ser estubada: só as 3 semanas fechadas decidem.
            assertThat(resultado.aderencia()).isEqualTo(1.0);
        }
    }

    @Nested
    @DisplayName("calcularDecisao")
    class CalcularDecisao {

        @Test
        @DisplayName("PROGREDIR — aderência >= 80%, 2+ longões, RPE <= 7.5, TSB > -15")
        void progredir() {
            ProgressaoHistoricoResumo resumo = resumoCom(
                    5, 6, 2, 7.0, -10.0
            );

            DecisaoProgressao decisao = service.calcularDecisao(resumo);

            assertThat(decisao.estado()).isEqualTo(EstadoProgressao.PROGREDIR);
            assertThat(decisao.ajusteVolumePercentual()).isGreaterThan(0.0);
            assertThat(decisao.permitirProgressaoIntensidade()).isTrue();
        }

        @Test
        @DisplayName("PROGREDIR_LEVE — aderência >= 70%, TSB entre -15 e -22")
        void progredirLeve() {
            ProgressaoHistoricoResumo resumo = resumoCom(
                    4, 5, 1, 7.0, -18.0
            );

            DecisaoProgressao decisao = service.calcularDecisao(resumo);

            assertThat(decisao.estado()).isEqualTo(EstadoProgressao.PROGREDIR_LEVE);
            assertThat(decisao.ajusteVolumePercentual()).isGreaterThan(0.0);
            assertThat(decisao.ajusteVolumePercentual()).isLessThan(0.06);
        }

        @Test
        @DisplayName("MANTER — aderência entre 60-70%")
        void manter() {
            ProgressaoHistoricoResumo resumo = resumoCom(
                    4, 6, 1, 7.0, -10.0
            );

            DecisaoProgressao decisao = service.calcularDecisao(resumo);

            assertThat(decisao.estado()).isEqualTo(EstadoProgressao.MANTER);
            assertThat(decisao.ajusteVolumePercentual()).isZero();
        }

        @Test
        @DisplayName("REDUZIR — TSB < -22")
        void reduzirPorTsb() {
            ProgressaoHistoricoResumo resumo = resumoCom(
                    5, 6, 2, 7.0, -25.0
            );

            DecisaoProgressao decisao = service.calcularDecisao(resumo);

            assertThat(decisao.estado()).isEqualTo(EstadoProgressao.REDUZIR);
            assertThat(decisao.ajusteVolumePercentual()).isNegative();
        }

        @Test
        @DisplayName("REDUZIR — aderência < 60%")
        void reduzirPorAderencia() {
            ProgressaoHistoricoResumo resumo = resumoCom(
                    3, 6, 1, 7.0, -10.0
            );

            DecisaoProgressao decisao = service.calcularDecisao(resumo);

            assertThat(decisao.estado()).isEqualTo(EstadoProgressao.REDUZIR);
        }

        @Test
        @DisplayName("REDUZIR — RPE médio > 8.5 nos treinos duros")
        void reduzirPorRpe() {
            ProgressaoHistoricoResumo resumo = new ProgressaoHistoricoResumo(
                    5, 30.0, 80.0, 200.0, 1, 2, 9.0, -10.0, 50.0, 55.0, 2,
                    5, 1, 0, 5.0 / 6.0
            );

            DecisaoProgressao decisao = service.calcularDecisao(resumo);

            assertThat(decisao.estado()).isEqualTo(EstadoProgressao.REDUZIR);
        }

        @Test
        @DisplayName("MANTER — fallback quando histórico insuficiente (< 3 treinos em 21 dias)")
        void fallbackHistoricoInsuficiente() {
            ProgressaoHistoricoResumo resumo = new ProgressaoHistoricoResumo(
                    2, 10.0, 20.0, 50.0, 0, 1, null, -5.0, 40.0, 42.0, 1,
                    2, 2, 0, 0.5
            );

            DecisaoProgressao decisao = service.calcularDecisao(resumo);

            assertThat(decisao.estado()).isEqualTo(EstadoProgressao.MANTER);
            assertThat(decisao.motivo()).contains("insuficiente");
        }

        @Test
        @DisplayName("semana boa isolada não vence histórico ruim de 42 dias — permanece MANTER")
        void semanaBoanaoVenceHistoricoRuim() {
            // 5 treinos concluídos de 6 planejados nos últimos 21d (aderência 83%)
            // mas TSB está em -18 (fadiga moderada) e apenas 1 longão — não atinge PROGREDIR
            ProgressaoHistoricoResumo resumo = resumoCom(
                    5, 6, 1, 7.0, -18.0
            );

            DecisaoProgressao decisao = service.calcularDecisao(resumo);

            assertThat(decisao.estado()).isNotEqualTo(EstadoProgressao.PROGREDIR);
        }

        @Test
        @DisplayName("CA6 — aderência ausente não libera PROGREDIR/PROGREDIR_LEVE; REDUZIR só por fadiga comprovada")
        void aderenciaAusenteNaoProgride() {
            assertThat(service.calcularDecisao(resumoComAderencia(null, 3, 7.0, -10.0)).estado())
                    .isEqualTo(EstadoProgressao.MANTER);

            DecisaoProgressao decisaoTsb = service.calcularDecisao(resumoComAderencia(null, 3, 7.0, -25.0));
            assertThat(decisaoTsb.estado()).isEqualTo(EstadoProgressao.REDUZIR);
            assertThat(decisaoTsb.motivo()).contains("aderência ausente");

            DecisaoProgressao decisaoRpe = service.calcularDecisao(resumoComAderencia(null, 3, 9.0, -10.0));
            assertThat(decisaoRpe.estado()).isEqualTo(EstadoProgressao.REDUZIR);
            assertThat(decisaoRpe.motivo()).contains("aderência ausente");
        }

        @ParameterizedTest(name = "aderência {0} → {1}")
        @CsvSource({
                "0.59, REDUZIR",
                "0.60, MANTER",
                "0.69, MANTER",
                "0.70, PROGREDIR_LEVE",
                "0.79, PROGREDIR_LEVE",
                "0.80, PROGREDIR"
        })
        @DisplayName("bordas 59/60/69/70/79/80% com aderência presente")
        void bordasDeAderencia(double aderencia, EstadoProgressao esperado) {
            ProgressaoHistoricoResumo resumo = resumoComAderencia(aderencia, 2, null, -10.0);

            assertThat(service.calcularDecisao(resumo).estado()).isEqualTo(esperado);
        }
    }

    /**
     * Regressão do bug em que o volume travava: o tipo do realizado vindo de integração é inferido
     * por heurística de duração/FC, e um longão prescrito voltava do sync como TEMPO_RUN. O
     * {@code contarLongoes} não enxergava esses longões, {@code podeProgredir} nunca atingia o
     * mínimo de 2, e o plano gerado ficava preso em MANTER/PROGREDIR_LEVE — o atleta cumpria a
     * prescrição e mesmo assim não recebia carga.
     *
     * <p>Estes testes atravessam {@code calcularHistorico} → {@code calcularDecisao} de propósito:
     * a contagem isolada já é coberta em {@link CalcularHistorico}, mas o que o coach percebe é a
     * decisão. Sem o {@code getTipoTreinoEfetivo()} todos eles falham na decisão, não na contagem.
     */
    @Nested
    @DisplayName("regressão — longão mal classificado pelo sync")
    class LongaoMalClassificado {

        @Test
        @DisplayName("dois longões prescritos e cumpridos liberam PROGREDIR mesmo voltando do sync como TEMPO_RUN")
        void progredirComLongoesVinculados() {
            // Cenário do bug: o coach prescreveu 2 longos, o atleta cumpriu, mas o Strava inferiu
            // TEMPO_RUN nos dois. Antes do fix: longões=0 → PROGREDIR_LEVE (metade do incremento).
            List<TreinoRealizado> treinos = List.of(
                    vinculado(treino(HOJE.minusDays(4), TipoTreino.TEMPO_RUN, 18.0, null), TipoTreino.LONGO),
                    vinculado(treino(HOJE.minusDays(11), TipoTreino.TEMPO_RUN, 19.0, null), TipoTreino.LONGO),
                    treino(HOJE.minusDays(2), TipoTreino.FACIL, 8.0, null),
                    treino(HOJE.minusDays(6), TipoTreino.FACIL, 8.0, null),
                    treino(HOJE.minusDays(9), TipoTreino.REGENERATIVO, 6.0, null)
            );
            stubHistorico(treinos, 6, metaDados(-10.0, 50.0, 55.0));

            DecisaoProgressao decisao = service.calcularDecisao(service.calcularHistorico(atletaId));

            assertThat(decisao.estado()).isEqualTo(EstadoProgressao.PROGREDIR);
            assertThat(decisao.ajusteVolumePercentual()).isEqualTo(0.06);
            assertThat(decisao.permitirProgressaoIntensidade()).isTrue();
        }

        @Test
        @DisplayName("os mesmos treinos sem vínculo com o planejado não liberam PROGREDIR — é o estado do bug")
        void semVinculoAtletaFicaSemProgressao() {
            // Contraste do teste acima: treino idêntico, só que sem reconciliação com o planejado.
            // Aqui não há prescrição para consultar, o tipo inferido é tudo que existe e a decisão
            // fica na metade do incremento — exatamente o que o atleta viveu antes do fix.
            List<TreinoRealizado> treinos = List.of(
                    treino(HOJE.minusDays(4), TipoTreino.TEMPO_RUN, 18.0, null),
                    treino(HOJE.minusDays(11), TipoTreino.TEMPO_RUN, 19.0, null),
                    treino(HOJE.minusDays(2), TipoTreino.FACIL, 8.0, null),
                    treino(HOJE.minusDays(6), TipoTreino.FACIL, 8.0, null),
                    treino(HOJE.minusDays(9), TipoTreino.REGENERATIVO, 6.0, null)
            );
            stubHistorico(treinos, 6, metaDados(-10.0, 50.0, 55.0));

            ProgressaoHistoricoResumo resumo = service.calcularHistorico(atletaId);
            DecisaoProgressao decisao = service.calcularDecisao(resumo);

            assertThat(resumo.longoesRealizados21d()).isZero();
            assertThat(decisao.estado()).isEqualTo(EstadoProgressao.PROGREDIR_LEVE);
            assertThat(decisao.permitirProgressaoIntensidade()).isFalse();
        }

        @Test
        @DisplayName("RPE alto de longão mal classificado não entra na média de treinos duros nem dispara REDUZIR")
        void rpeDoLongaoNaoDisparaReducao() {
            // O mesmo treino tinha efeito duplo: sumia da contagem de longões e ainda inflava o RPE
            // médio de treinos duros (TEMPO_RUN está em TREINOS_DUROS), podendo puxar para REDUZIR.
            List<TreinoRealizado> treinos = List.of(
                    vinculado(treino(HOJE.minusDays(4), TipoTreino.TEMPO_RUN, 18.0, 9), TipoTreino.LONGO),
                    vinculado(treino(HOJE.minusDays(11), TipoTreino.TEMPO_RUN, 19.0, 9), TipoTreino.LONGO),
                    treino(HOJE.minusDays(2), TipoTreino.FACIL, 8.0, null),
                    treino(HOJE.minusDays(6), TipoTreino.FACIL, 8.0, null),
                    treino(HOJE.minusDays(9), TipoTreino.REGENERATIVO, 6.0, null)
            );
            stubHistorico(treinos, 6, metaDados(-10.0, 50.0, 55.0));

            ProgressaoHistoricoResumo resumo = service.calcularHistorico(atletaId);

            assertThat(resumo.rpeMedioTreinosDuros()).isNull();
            assertThat(service.calcularDecisao(resumo).estado()).isEqualTo(EstadoProgressao.PROGREDIR);
        }

        @Test
        @DisplayName("um longão vinculado a menos mantém a decisão abaixo de PROGREDIR (limite de 2)")
        void umLongaoNaoBastaParaProgredir() {
            // Fronteira de LONGAS_MINIMAS_PROGREDIR: o fix não pode inflar a contagem — com 1 longão
            // a decisão continua sendo a moderada.
            List<TreinoRealizado> treinos = List.of(
                    vinculado(treino(HOJE.minusDays(4), TipoTreino.TEMPO_RUN, 18.0, null), TipoTreino.LONGO),
                    treino(HOJE.minusDays(11), TipoTreino.TEMPO_RUN, 12.0, null),
                    treino(HOJE.minusDays(2), TipoTreino.FACIL, 8.0, null),
                    treino(HOJE.minusDays(6), TipoTreino.FACIL, 8.0, null),
                    treino(HOJE.minusDays(9), TipoTreino.REGENERATIVO, 6.0, null)
            );
            stubHistorico(treinos, 6, metaDados(-10.0, 50.0, 55.0));

            ProgressaoHistoricoResumo resumo = service.calcularHistorico(atletaId);

            assertThat(resumo.longoesRealizados21d()).isEqualTo(1);
            assertThat(service.calcularDecisao(resumo).estado()).isEqualTo(EstadoProgressao.PROGREDIR_LEVE);
        }

        @Test
        @DisplayName("vínculo com planejado sem tipo definido não apaga o tipo do realizado")
        void planejadoSemTipoMantemTipoRealizado() {
            // TreinoPlanejado com tipoTreino nulo existe em plano importado/rascunho: o fallback tem
            // de ser o tipo executado, não null — senão o treino sai de toda contagem.
            TreinoRealizado longoSemPrescricaoDeTipo =
                    vinculado(treino(HOJE.minusDays(4), TipoTreino.LONGO, 20.0, null), null);
            List<TreinoRealizado> treinos = List.of(
                    longoSemPrescricaoDeTipo,
                    treino(HOJE.minusDays(11), TipoTreino.LONGO, 20.0, null),
                    treino(HOJE.minusDays(2), TipoTreino.FACIL, 8.0, null)
            );
            stubHistorico(treinos, 4, metaDados(-10.0, 50.0, 55.0));

            ProgressaoHistoricoResumo resumo = service.calcularHistorico(atletaId);

            assertThat(resumo.longoesRealizados21d()).isEqualTo(2);
        }

        @Test
        @DisplayName("treino fora da janela de 21 dias não conta como longão, mesmo vinculado")
        void longaoVinculadoForaDaJanelaNaoConta() {
            List<TreinoRealizado> treinos = List.of(
                    vinculado(treino(HOJE.minusDays(4), TipoTreino.TEMPO_RUN, 18.0, null), TipoTreino.LONGO),
                    vinculado(treino(HOJE.minusDays(30), TipoTreino.TEMPO_RUN, 19.0, null), TipoTreino.LONGO),
                    treino(HOJE.minusDays(2), TipoTreino.FACIL, 8.0, null),
                    treino(HOJE.minusDays(6), TipoTreino.FACIL, 8.0, null)
            );
            stubHistorico(treinos, 4, metaDados(-10.0, 50.0, 55.0));

            ProgressaoHistoricoResumo resumo = service.calcularHistorico(atletaId);

            assertThat(resumo.longoesRealizados21d()).isEqualTo(1);
            assertThat(resumo.longoesRealizados7d()).isEqualTo(1);
        }
    }

    // ── Helpers ─────────────────────────────────────────────────────────────────

    private void stubHistorico(List<TreinoRealizado> treinos, int planejados21d, PlanoMetaDados metaDados) {
        when(treinoRealizadoRepository.findByAtletaIdAndTenantIdAndDataTreinoBetween(eq(atletaId), eq(tenantId), any(), any()))
                .thenReturn(treinos);
        when(treinoPlanejadoRepository.findComRealizadoByAtletaAndPeriodoAteData(eq(atletaId), eq(tenantId), any(), any()))
                .thenReturn(Collections.nCopies(planejados21d, planejado()));
        stubAtletaPadrao();
        when(planoMetadadosService.buscarPorAtletaId(atletaId)).thenReturn(metaDados);
    }

    private TreinoRealizado treino(LocalDate data, TipoTreino tipo, double distanciaKm, Integer rpe) {
        TreinoRealizado tr = new TreinoRealizado();
        tr.setDataTreino(data);
        tr.setTipoTreino(tipo);
        tr.setDistanciaKm(BigDecimal.valueOf(distanciaKm));
        tr.setPercepcaoEsforco(rpe);
        return tr;
    }

    private TreinoPlanejado planejado() {
        return new TreinoPlanejado();
    }

    private TreinoRealizado vinculado(TreinoRealizado realizado, TipoTreino tipoPlanejado) {
        TreinoPlanejado planejado = new TreinoPlanejado();
        planejado.setTipoTreino(tipoPlanejado);
        realizado.setTreinoPlanejado(planejado);
        return realizado;
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

    private PlanoMetaDados metaDados(double tsb, double ctl, double atl) {
        return PlanoMetaDados.builder()
                .tsbAtual(tsb)
                .ctlAtual(ctl)
                .atlAtual(atl)
                .semanasProgressaoContinua(2)
                .build();
    }

    /**
     * Cria um ProgressaoHistoricoResumo com valores controlados para testar calcularDecisao, com a
     * aderência derivada de concluidos/planejados (semântica da regra antiga — suficiente para os
     * testes que não cobrem CA6/bordas diretamente).
     *
     * @param concluidos21d treinos concluídos nos últimos 21 dias
     * @param planejados21d treinos planejados nos últimos 21 dias
     * @param longoes21d    longões realizados nos últimos 21 dias
     * @param rpe           RPE médio dos treinos duros (null para ausente)
     * @param tsb           TSB atual
     */
    private ProgressaoHistoricoResumo resumoCom(int concluidos21d, int planejados21d,
                                                int longoes21d, Double rpe, double tsb) {
        double aderencia = planejados21d == 0 ? 0.0 : (double) concluidos21d / planejados21d;
        int cumpridos = Math.min(concluidos21d, planejados21d);
        int faltas = Math.max(0, planejados21d - concluidos21d);
        return new ProgressaoHistoricoResumo(
                concluidos21d,
                30.0, 80.0, 200.0,
                0, longoes21d,
                rpe,
                tsb, 50.0, 55.0,
                2,
                cumpridos, faltas, 0, aderencia
        );
    }

    /** Cria um resumo com aderência controlada diretamente — para CA6 e os testes de borda (D3). */
    private ProgressaoHistoricoResumo resumoComAderencia(Double aderencia, int longoes21d, Double rpe, double tsb) {
        return new ProgressaoHistoricoResumo(
                5,
                30.0, 80.0, 200.0,
                0, longoes21d,
                rpe,
                tsb, 50.0, 55.0,
                2,
                aderencia == null ? 0 : 3,
                aderencia == null ? 0 : 1,
                0,
                aderencia
        );
    }
}
