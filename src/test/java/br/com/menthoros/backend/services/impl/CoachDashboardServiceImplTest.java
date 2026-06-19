package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.dto.output.CoachAtletaResumoDto;
import br.com.menthoros.backend.dto.output.CoachAttentionItemOutputDto;
import br.com.menthoros.backend.dto.output.CoachCalendarioDto;
import br.com.menthoros.backend.dto.output.CoachInsightsDto;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.enums.MotivoAtencao;
import br.com.menthoros.backend.enums.Severidade;
import br.com.menthoros.backend.services.CoachAttentionQueueService;
import br.com.menthoros.backend.entity.MetricasDiarias;
import br.com.menthoros.backend.entity.TreinoPlanejado;
import br.com.menthoros.backend.entity.TreinoRealizado;
import br.com.menthoros.backend.enums.AtletaStatus;
import br.com.menthoros.backend.enums.TipoTreino;
import br.com.menthoros.backend.exception.DomainRuleViolationException;
import br.com.menthoros.backend.multitenancy.TenantContext;
import br.com.menthoros.backend.repository.AtletaRepository;
import br.com.menthoros.backend.repository.MetricasDiariasRepository;
import br.com.menthoros.backend.repository.PlanoMetadadosRepository;
import br.com.menthoros.backend.repository.TreinoPlanejadoRepository;
import br.com.menthoros.backend.repository.TreinoRealizadoRepository;
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
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CoachDashboardServiceImpl")
class CoachDashboardServiceImplTest {

    private static final LocalDate HOJE = LocalDate.of(2026, 6, 17);          // quarta
    private static final LocalDate INICIO_SEMANA = LocalDate.of(2026, 6, 15); // segunda
    private static final LocalDate FIM_SEMANA = LocalDate.of(2026, 6, 21);    // domingo

    @Mock private AtletaRepository atletaRepository;
    @Mock private MetricasDiariasRepository metricasDiariasRepository;
    @Mock private PlanoMetadadosRepository planoMetadadosRepository;
    @Mock private TreinoRealizadoRepository treinoRealizadoRepository;
    @Mock private TreinoPlanejadoRepository treinoPlanejadoRepository;
    @Mock private CoachAttentionQueueService coachAttentionQueueService;

    private CoachDashboardServiceImpl service;
    private UUID tenantId;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        TenantContext.setTenantId(tenantId);
        Clock clock = Clock.fixed(Instant.parse("2026-06-17T12:00:00Z"), ZoneOffset.UTC);
        service = new CoachDashboardServiceImpl(
                atletaRepository, metricasDiariasRepository, planoMetadadosRepository,
                treinoRealizadoRepository, treinoPlanejadoRepository, coachAttentionQueueService, clock);
        // Default: sem itens de atenção (cada teste de calendário que precisar sobrescreve)
        lenient().when(coachAttentionQueueService.getAttentionQueue()).thenReturn(List.of());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Nested
    @DisplayName("getRoster")
    class GetRoster {

        @Test
        @DisplayName("deriva status por TSB, inatividade e AtletaStatus")
        void statusDerivado() {
            Atleta active = atletaRoster("active", AtletaStatus.ATIVO, 5.0, HOJE.minusDays(2));
            Atleta warnTsb = atletaRoster("warnTsb", AtletaStatus.ATIVO, -12.0, HOJE.minusDays(1));
            Atleta dangerTsb = atletaRoster("dangerTsb", AtletaStatus.ATIVO, -25.0, HOJE.minusDays(1));
            Atleta warnInativo = atletaRoster("warnInativo", AtletaStatus.ATIVO, 5.0, HOJE.minusDays(8));
            Atleta dangerInativo = atletaRoster("dangerInativo", AtletaStatus.ATIVO, 5.0, HOJE.minusDays(20));
            Atleta paused = atletaRoster("paused", AtletaStatus.INATIVO, 5.0, HOJE.minusDays(1));
            when(atletaRepository.findAllByTenantIdOrderByNome(tenantId))
                    .thenReturn(List.of(active, warnTsb, dangerTsb, warnInativo, dangerInativo, paused));

            List<CoachAtletaResumoDto> roster = service.getRoster();

            assertThat(roster).extracting(CoachAtletaResumoDto::nome, CoachAtletaResumoDto::status)
                    .containsExactly(
                            tuple("active S", "active"),
                            tuple("warnTsb S", "warning"),
                            tuple("dangerTsb S", "danger"),
                            tuple("warnInativo S", "warning"),
                            tuple("dangerInativo S", "danger"),
                            tuple("paused S", "paused"));
        }

        @Test
        @DisplayName("limiares exatos: TSB -10/-20 e inatividade 6/7/14 dias")
        void statusBoundaries() {
            Atleta active6 = atletaRoster("a6", AtletaStatus.ATIVO, 0.0, HOJE.minusDays(6));   // <7 → active
            Atleta warn7 = atletaRoster("w7", AtletaStatus.ATIVO, 0.0, HOJE.minusDays(7));     // ==7 → warning
            Atleta danger14 = atletaRoster("d14", AtletaStatus.ATIVO, 0.0, HOJE.minusDays(14));// ==14 → danger
            Atleta warnTsb10 = atletaRoster("wt", AtletaStatus.ATIVO, -10.0, HOJE.minusDays(1));// ==-10 → warning
            Atleta dangerTsb20 = atletaRoster("dt", AtletaStatus.ATIVO, -20.0, HOJE.minusDays(1));// ==-20 → danger
            when(atletaRepository.findAllByTenantIdOrderByNome(tenantId))
                    .thenReturn(List.of(active6, warn7, danger14, warnTsb10, dangerTsb20));

            assertThat(service.getRoster()).extracting(CoachAtletaResumoDto::status)
                    .containsExactly("active", "warning", "danger", "warning", "danger");
        }

        @Test
        @DisplayName("atleta sem métricas degrada (nulls) e vira warning")
        void semMetricasDegrada() {
            Atleta semDados = Atleta.builder().id(UUID.randomUUID()).nome("Sem").sobrenome("Dados")
                    .ativo(AtletaStatus.ATIVO).build();
            when(atletaRepository.findAllByTenantIdOrderByNome(tenantId)).thenReturn(List.of(semDados));
            when(metricasDiariasRepository.findLatestByAtletaId(semDados.getId())).thenReturn(Optional.empty());
            when(treinoRealizadoRepository.findTopByAtletaIdOrderByDataTreinoDesc(semDados.getId())).thenReturn(Optional.empty());

            CoachAtletaResumoDto dto = service.getRoster().get(0);

            assertThat(dto.ctl()).isNull();
            assertThat(dto.tsb()).isNull();
            assertThat(dto.fase()).isNull();
            assertThat(dto.lastActivity()).isNull();
            assertThat(dto.weeklyVolume()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(dto.status()).isEqualTo("warning"); // sem atividade
        }

        @Test
        @DisplayName("weeklyVolume soma os treinos realizados da semana")
        void weeklyVolume() {
            Atleta a = atletaRoster("vol", AtletaStatus.ATIVO, 5.0, HOJE.minusDays(1));
            when(atletaRepository.findAllByTenantIdOrderByNome(tenantId)).thenReturn(List.of(a));
            when(treinoRealizadoRepository.findByAtletaIdAndDataTreinoBetween(eq(a.getId()), eq(INICIO_SEMANA), eq(FIM_SEMANA)))
                    .thenReturn(List.of(treino(HOJE.minusDays(1), "10.0", 80), treino(HOJE, "5.5", 40)));

            assertThat(service.getRoster().get(0).weeklyVolume()).isEqualByComparingTo(new BigDecimal("15.5"));
        }
    }

    @Nested
    @DisplayName("getCalendarioSemanal")
    class GetCalendarioSemanal {

        @Test
        @DisplayName("agrega treinos de vários atletas com flags e isKeyWorkout")
        void agregaComFlags() {
            Atleta ana = Atleta.builder().id(UUID.randomUUID()).nome("Ana").build();
            Atleta bia = Atleta.builder().id(UUID.randomUUID()).nome("Bia").build();
            when(treinoPlanejadoRepository.findByTenantAndDataBetween(tenantId, INICIO_SEMANA, FIM_SEMANA))
                    .thenReturn(List.of(planejado(ana, HOJE, TipoTreino.INTERVALADO), planejado(bia, HOJE, TipoTreino.REGENERATIVO)));

            CoachCalendarioDto cal = service.getCalendarioSemanal(null);

            assertThat(cal.semanaInicio()).isEqualTo(INICIO_SEMANA);
            assertThat(cal.semanaFim()).isEqualTo(FIM_SEMANA);
            assertThat(cal.treinos()).extracting(t -> t.nomeAtleta(), t -> t.isKeyWorkout(), t -> t.hasAlert(), t -> t.hasPendingSuggestion())
                    .containsExactly(
                            tuple("Ana", true, false, false),
                            tuple("Bia", false, false, false));
        }

        @Test
        @DisplayName("semana default = semana atual (segunda a domingo)")
        void semanaDefault() {
            when(treinoPlanejadoRepository.findByTenantAndDataBetween(tenantId, INICIO_SEMANA, FIM_SEMANA)).thenReturn(List.of());
            CoachCalendarioDto cal = service.getCalendarioSemanal(null);
            assertThat(cal.semanaInicio()).isEqualTo(INICIO_SEMANA);
            assertThat(cal.treinos()).isEmpty();
        }

        @Test
        @DisplayName("hasAlert = true para atleta presente na fila de atenção")
        void hasAlertDaFila() {
            Atleta ana = Atleta.builder().id(UUID.randomUUID()).nome("Ana").build();
            Atleta bia = Atleta.builder().id(UUID.randomUUID()).nome("Bia").build();
            when(treinoPlanejadoRepository.findByTenantAndDataBetween(tenantId, INICIO_SEMANA, FIM_SEMANA))
                    .thenReturn(List.of(planejado(ana, HOJE, TipoTreino.LONGO), planejado(bia, HOJE, TipoTreino.REGENERATIVO)));
            when(coachAttentionQueueService.getAttentionQueue()).thenReturn(List.of(
                    new CoachAttentionItemOutputDto(ana.getId(), "Ana", Severidade.ALTA, 235,
                            MotivoAtencao.SEM_PLANO, MotivoAtencao.SEM_PLANO.getSuggestedAction(),
                            Instant.parse("2026-06-17T12:00:00Z"), List.of())));

            CoachCalendarioDto cal = service.getCalendarioSemanal(null);

            assertThat(cal.treinos()).extracting(t -> t.nomeAtleta(), t -> t.hasAlert())
                    .containsExactly(tuple("Ana", true), tuple("Bia", false));
        }
    }

    @Nested
    @DisplayName("getInsights")
    class GetInsights {

        @Test
        @DisplayName("KPIs contam por status; tendência e top derivados dos realizados")
        void kpisETendencia() {
            Atleta active = atletaRoster("active", AtletaStatus.ATIVO, 5.0, HOJE.minusDays(1));
            Atleta paused = atletaRoster("paused", AtletaStatus.INATIVO, 5.0, HOJE.minusDays(1));
            when(atletaRepository.findAllByTenantIdOrderByNome(tenantId)).thenReturn(List.of(active, paused));
            when(treinoPlanejadoRepository.findByTenantAndDataBetween(eq(tenantId), any(), any())).thenReturn(List.of());
            // mesmo stub serve para a janela semanal (roster) e o período (insights)
            when(treinoRealizadoRepository.findByAtletaIdAndDataTreinoBetween(eq(active.getId()), any(), any()))
                    .thenReturn(List.of(treino(LocalDate.of(2026, 6, 16), "12.0", 90)));
            when(treinoRealizadoRepository.findByAtletaIdAndDataTreinoBetween(eq(paused.getId()), any(), any()))
                    .thenReturn(List.of());

            CoachInsightsDto insights = service.getInsights(null, null);

            assertThat(insights.kpis().totalAtletas()).isEqualTo(2);
            assertThat(insights.kpis().ativos()).isEqualTo(1);
            assertThat(insights.kpis().pausados()).isEqualTo(1);
            assertThat(insights.tendenciaCargaSemanal()).hasSize(1);
            assertThat(insights.tendenciaCargaSemanal().get(0).volumeTotalKm()).isEqualTo(12.0);
            assertThat(insights.topAtletas()).extracting(CoachInsightsDto.TopAtleta::nome).containsExactly("active S");
        }

        @Test
        @DisplayName("sem dados → KPIs zerados e listas vazias")
        void semDados() {
            when(atletaRepository.findAllByTenantIdOrderByNome(tenantId)).thenReturn(List.of());
            when(treinoPlanejadoRepository.findByTenantAndDataBetween(eq(tenantId), any(), any())).thenReturn(List.of());

            CoachInsightsDto insights = service.getInsights(null, null);

            assertThat(insights.kpis().totalAtletas()).isZero();
            assertThat(insights.tendenciaCargaSemanal()).isEmpty();
            assertThat(insights.topAtletas()).isEmpty();
        }

        @Test
        @DisplayName("from depois de to lança DomainRuleViolationException")
        void fromDepoisDeTo() {
            assertThatThrownBy(() -> service.getInsights(LocalDate.of(2026, 6, 10), LocalDate.of(2026, 6, 1)))
                    .isInstanceOf(DomainRuleViolationException.class);
        }
    }

    // ===== helpers =====

    /** Atleta com métrica (tsb) e última atividade stubadas; demais leituras usam defaults (vazias). */
    private Atleta atletaRoster(String nome, AtletaStatus status, Double tsb, LocalDate lastActivity) {
        Atleta a = Atleta.builder().id(UUID.randomUUID()).nome(nome).sobrenome("S").ativo(status).build();
        MetricasDiarias m = new MetricasDiarias();
        m.setCtl(50.0);
        m.setAtl(45.0);
        m.setTsb(tsb);
        when(metricasDiariasRepository.findLatestByAtletaId(a.getId())).thenReturn(Optional.of(m));
        when(treinoRealizadoRepository.findTopByAtletaIdOrderByDataTreinoDesc(a.getId()))
                .thenReturn(Optional.of(treino(lastActivity, "8.0", 60)));
        return a;
    }

    private TreinoRealizado treino(LocalDate data, String km, int tss) {
        TreinoRealizado t = new TreinoRealizado();
        t.setId(UUID.randomUUID());
        t.setDataTreino(data);
        t.setDistanciaKm(new BigDecimal(km));
        t.setTssCalculado(tss);
        return t;
    }

    private TreinoPlanejado planejado(Atleta atleta, LocalDate data, TipoTreino tipo) {
        TreinoPlanejado tp = new TreinoPlanejado();
        tp.setAtleta(atleta);
        tp.setDataTreino(data);
        tp.setTipoTreino(tipo);
        return tp;
    }
}
