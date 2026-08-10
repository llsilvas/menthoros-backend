package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.dto.DecisaoProgressao;
import br.com.menthoros.backend.dto.ProgressaoHistoricoResumo;
import br.com.menthoros.backend.entity.PlanoMetaDados;
import br.com.menthoros.backend.entity.TreinoPlanejado;
import br.com.menthoros.backend.entity.TreinoRealizado;
import br.com.menthoros.backend.enums.EstadoProgressao;
import br.com.menthoros.backend.enums.TipoTreino;
import br.com.menthoros.backend.multitenancy.TenantContext;
import br.com.menthoros.backend.repository.TreinoPlanejadoRepository;
import br.com.menthoros.backend.repository.TreinoRealizadoRepository;
import br.com.menthoros.backend.services.PlanoMetadadosService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProgressaoTreinoServiceImplTest {

    private static final LocalDate HOJE = LocalDate.of(2026, 7, 8);
    private static final LocalDate INICIO_7D = HOJE.minusDays(7);
    private static final LocalDate INICIO_21D = HOJE.minusDays(21);
    private static final LocalDate INICIO_42D = HOJE.minusDays(42);

    @Mock
    private TreinoRealizadoRepository treinoRealizadoRepository;
    @Mock
    private TreinoPlanejadoRepository treinoPlanejadoRepository;
    @Mock
    private PlanoMetadadosService planoMetadadosService;

    private ProgressaoTreinoServiceImpl service;

    private UUID atletaId;
    private UUID tenantId;

    @BeforeEach
    void setUp() {
        atletaId = UUID.randomUUID();
        tenantId = UUID.randomUUID();
        TenantContext.setTenantId(tenantId);

        Clock clock = Clock.fixed(HOJE.atStartOfDay(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault());
        service = new ProgressaoTreinoServiceImpl(treinoRealizadoRepository, treinoPlanejadoRepository, planoMetadadosService, clock);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
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
            when(treinoPlanejadoRepository.findComRealizadoByAtletaAndPeriodo(eq(atletaId), eq(tenantId), any()))
                    .thenReturn(List.of(planejado(), planejado(), planejado(), planejado()));
            when(planoMetadadosService.buscarPorAtletaId(atletaId))
                    .thenReturn(metaDados(-10.0, 50.0, 55.0));

            ProgressaoHistoricoResumo resultado = service.calcularHistorico(atletaId);

            assertThat(resultado.volumeKm7d()).isEqualTo(20.0);
            assertThat(resultado.volumeKm21d()).isEqualTo(52.0);
            assertThat(resultado.volumeKm42d()).isEqualTo(60.0);
            assertThat(resultado.longoesRealizados7d()).isEqualTo(1);
            assertThat(resultado.longoesRealizados21d()).isEqualTo(2);
            assertThat(resultado.treinosConcluidos21d()).isEqualTo(3);
            assertThat(resultado.treinosPlanejados21d()).isEqualTo(4);
            assertThat(resultado.tsbAtual()).isEqualTo(-10.0);
            assertThat(resultado.ctlAtual()).isEqualTo(50.0);
        }

        @Test
        @DisplayName("novo atleta sem treinos — campos zerados, sem exceção")
        void atletaSemTreinos() {
            when(treinoRealizadoRepository.findByAtletaIdAndTenantIdAndDataTreinoBetween(eq(atletaId), eq(tenantId), any(), any()))
                    .thenReturn(Collections.emptyList());
            when(treinoPlanejadoRepository.findComRealizadoByAtletaAndPeriodo(eq(atletaId), eq(tenantId), any()))
                    .thenReturn(Collections.emptyList());
            when(planoMetadadosService.buscarPorAtletaId(atletaId))
                    .thenReturn(metaDados(0.0, 0.0, 0.0));

            ProgressaoHistoricoResumo resultado = service.calcularHistorico(atletaId);

            assertThat(resultado.volumeKm7d()).isZero();
            assertThat(resultado.volumeKm21d()).isZero();
            assertThat(resultado.volumeKm42d()).isZero();
            assertThat(resultado.longoesRealizados7d()).isZero();
            assertThat(resultado.longoesRealizados21d()).isZero();
            assertThat(resultado.treinosConcluidos21d()).isZero();
            assertThat(resultado.treinosPlanejados21d()).isZero();
            assertThat(resultado.rpeMedioTreinosDuros()).isNull();
        }

        @Test
        @DisplayName("RPE ausente nos treinos duros — campo rpeMedioTreinosDuros fica nulo")
        void rpeAusenteNaoBloqueia() {
            TreinoRealizado intervalado = treino(HOJE.minusDays(5), TipoTreino.INTERVALADO, 10.0, null);
            TreinoRealizado tempoRun = treino(HOJE.minusDays(12), TipoTreino.TEMPO_RUN, 8.0, null);

            when(treinoRealizadoRepository.findByAtletaIdAndTenantIdAndDataTreinoBetween(eq(atletaId), eq(tenantId), any(), any()))
                    .thenReturn(List.of(intervalado, tempoRun));
            when(treinoPlanejadoRepository.findComRealizadoByAtletaAndPeriodo(eq(atletaId), eq(tenantId), any()))
                    .thenReturn(List.of(planejado(), planejado()));
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
            when(treinoPlanejadoRepository.findComRealizadoByAtletaAndPeriodo(eq(atletaId), eq(tenantId), any()))
                    .thenReturn(List.of(planejado(), planejado()));
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
            when(treinoPlanejadoRepository.findComRealizadoByAtletaAndPeriodo(eq(atletaId), eq(tenantId), any()))
                    .thenReturn(List.of(planejado(), planejado()));
            when(planoMetadadosService.buscarPorAtletaId(atletaId))
                    .thenReturn(metaDados(-10.0, 50.0, 55.0));

            ProgressaoHistoricoResumo resultado = service.calcularHistorico(atletaId);

            assertThat(resultado.longoesRealizados21d()).isEqualTo(1);
            assertThat(resultado.rpeMedioTreinosDuros()).isEqualTo(8.0);
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
                    5, 6, 30.0, 80.0, 200.0, 1, 2, 9.0, -10.0, 50.0, 55.0, 2
            );

            DecisaoProgressao decisao = service.calcularDecisao(resumo);

            assertThat(decisao.estado()).isEqualTo(EstadoProgressao.REDUZIR);
        }

        @Test
        @DisplayName("MANTER — fallback quando histórico insuficiente (< 3 treinos em 21 dias)")
        void fallbackHistoricoInsuficiente() {
            ProgressaoHistoricoResumo resumo = new ProgressaoHistoricoResumo(
                    2, 4, 10.0, 20.0, 50.0, 0, 1, null, -5.0, 40.0, 42.0, 1
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
    }

    // ── Helpers ─────────────────────────────────────────────────────────────────

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

    private PlanoMetaDados metaDados(double tsb, double ctl, double atl) {
        return PlanoMetaDados.builder()
                .tsbAtual(tsb)
                .ctlAtual(ctl)
                .atlAtual(atl)
                .semanasProgressaoContinua(2)
                .build();
    }

    /**
     * Cria um ProgressaoHistoricoResumo com valores controlados para testar calcularDecisao.
     *
     * @param concluidos21d treinos concluídos nos últimos 21 dias
     * @param planejados21d treinos planejados nos últimos 21 dias
     * @param longoes21d    longões realizados nos últimos 21 dias
     * @param rpe           RPE médio dos treinos duros (null para ausente)
     * @param tsb           TSB atual
     */
    private ProgressaoHistoricoResumo resumoCom(int concluidos21d, int planejados21d,
                                                int longoes21d, Double rpe, double tsb) {
        return new ProgressaoHistoricoResumo(
                concluidos21d, planejados21d,
                30.0, 80.0, 200.0,
                0, longoes21d,
                rpe,
                tsb, 50.0, 55.0,
                2
        );
    }
}
