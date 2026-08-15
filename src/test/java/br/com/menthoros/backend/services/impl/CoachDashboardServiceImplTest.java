package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.dto.input.CoachDashboardQueryDto;
import br.com.menthoros.backend.dto.output.CoachAtletaResumoDto;
import br.com.menthoros.backend.dto.output.CoachAttentionItemOutputDto;
import br.com.menthoros.backend.dto.output.CoachCalendarioDto;
import br.com.menthoros.backend.dto.output.CoachDashboardOutputDto;
import br.com.menthoros.backend.dto.output.CoachDashboardRosterPageDto;
import br.com.menthoros.backend.dto.output.CoachDashboardSummaryDto;
import br.com.menthoros.backend.dto.output.CoachInsightsDto;
import br.com.menthoros.backend.dto.output.RecommendationExplanation;
import br.com.menthoros.backend.enums.ExplanationConfidence;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.enums.MotivoAtencao;
import br.com.menthoros.backend.enums.Severidade;
import br.com.menthoros.backend.services.CoachAttentionQueueService;
import br.com.menthoros.backend.entity.MetricasDiarias;
import br.com.menthoros.backend.entity.TreinoPlanejado;
import br.com.menthoros.backend.entity.TreinoRealizado;
import br.com.menthoros.backend.enums.AtletaStatus;
import br.com.menthoros.backend.enums.StatusVencimentoPlano;
import br.com.menthoros.backend.enums.TipoPlanoAtleta;
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
            when(atletaRepository.findAtivosByTenantIdOrderByNome(tenantId))
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
            when(atletaRepository.findAtivosByTenantIdOrderByNome(tenantId))
                    .thenReturn(List.of(active6, warn7, danger14, warnTsb10, dangerTsb20));

            assertThat(service.getRoster()).extracting(CoachAtletaResumoDto::status)
                    .containsExactly("active", "warning", "danger", "warning", "danger");
        }

        @Test
        @DisplayName("atleta sem métricas degrada (nulls) e vira warning")
        void semMetricasDegrada() {
            Atleta semDados = Atleta.builder().id(UUID.randomUUID()).nome("Sem").sobrenome("Dados")
                    .ativo(AtletaStatus.ATIVO).build();
            when(atletaRepository.findAtivosByTenantIdOrderByNome(tenantId)).thenReturn(List.of(semDados));
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
            when(atletaRepository.findAtivosByTenantIdOrderByNome(tenantId)).thenReturn(List.of(a));
            when(treinoRealizadoRepository.findByAtletaIdAndDataTreinoBetween(eq(a.getId()), eq(INICIO_SEMANA), eq(FIM_SEMANA)))
                    .thenReturn(List.of(treino(HOJE.minusDays(1), "10.0", 80), treino(HOJE, "5.5", 40)));

            assertThat(service.getRoster().get(0).weeklyVolume()).isEqualByComparingTo(new BigDecimal("15.5"));
        }

        @Test
        @DisplayName("aderenciaPercentual calculada sobre as últimas 4 semanas")
        void aderenciaPercentual() {
            Atleta a = atletaRoster("ader", AtletaStatus.ATIVO, 5.0, HOJE.minusDays(1));
            when(atletaRepository.findAtivosByTenantIdOrderByNome(tenantId)).thenReturn(List.of(a));

            TreinoPlanejado realizado1 = planejado(a, HOJE.minusDays(7), TipoTreino.REGENERATIVO);
            realizado1.setTreinoRealizado(treino(HOJE.minusDays(7), "5.0", 40));
            TreinoPlanejado realizado2 = planejado(a, HOJE.minusDays(3), TipoTreino.REGENERATIVO);
            realizado2.setTreinoRealizado(treino(HOJE.minusDays(3), "8.0", 60));
            TreinoPlanejado naorealizado = planejado(a, HOJE.minusDays(1), TipoTreino.REGENERATIVO);

            when(treinoPlanejadoRepository.findComRealizadoByAtletaAndPeriodo(
                    eq(a.getId()), eq(tenantId), eq(INICIO_SEMANA.minusWeeks(3))))
                    .thenReturn(List.of(realizado1, realizado2, naorealizado));

            // 2 de 3 realizados = 67%
            assertThat(service.getRoster().get(0).aderenciaPercentual()).isEqualTo(67);
        }

        @Test
        @DisplayName("aderenciaPercentual é null quando atleta não tem plano")
        void aderenciaPercentualNullSemPlano() {
            Atleta a = atletaRoster("semplano", AtletaStatus.ATIVO, 5.0, HOJE.minusDays(1));
            when(atletaRepository.findAtivosByTenantIdOrderByNome(tenantId)).thenReturn(List.of(a));
            when(treinoPlanejadoRepository.findComRealizadoByAtletaAndPeriodo(
                    eq(a.getId()), eq(tenantId), eq(INICIO_SEMANA.minusWeeks(3))))
                    .thenReturn(List.of());

            assertThat(service.getRoster().get(0).aderenciaPercentual()).isNull();
        }

        @Test
        @DisplayName("dataVencimentoPlano nulo → tipoPlanoAtleta e statusVencimentoPlano ausentes")
        void semDadosDeCobranca() {
            Atleta a = atletaSemMetricas("Sem", "Dados");
            when(atletaRepository.findAtivosByTenantIdOrderByNome(tenantId)).thenReturn(List.of(a));
            when(metricasDiariasRepository.findLatestByAtletaId(a.getId())).thenReturn(Optional.empty());
            when(treinoRealizadoRepository.findTopByAtletaIdOrderByDataTreinoDesc(a.getId())).thenReturn(Optional.empty());

            CoachAtletaResumoDto dto = service.getRoster().get(0);

            assertThat(dto.tipoPlanoAtleta()).isNull();
            assertThat(dto.dataVencimentoPlano()).isNull();
            assertThat(dto.statusVencimentoPlano()).isNull();
        }

        @Test
        @DisplayName("dataVencimentoPlano no passado → VENCIDO")
        void dataNoPassadoRetornaVencido() {
            Atleta a = atletaSemMetricas("Ana", "Vencida").toBuilder()
                    .tipoPlanoAtleta(TipoPlanoAtleta.ANUAL)
                    .dataVencimentoPlano(HOJE.minusDays(5))
                    .build();
            when(atletaRepository.findAtivosByTenantIdOrderByNome(tenantId)).thenReturn(List.of(a));
            when(metricasDiariasRepository.findLatestByAtletaId(a.getId())).thenReturn(Optional.empty());
            when(treinoRealizadoRepository.findTopByAtletaIdOrderByDataTreinoDesc(a.getId())).thenReturn(Optional.empty());

            CoachAtletaResumoDto dto = service.getRoster().get(0);

            assertThat(dto.tipoPlanoAtleta()).isEqualTo(TipoPlanoAtleta.ANUAL);
            assertThat(dto.statusVencimentoPlano()).isEqualTo(StatusVencimentoPlano.VENCIDO);
        }

        @Test
        @DisplayName("dataVencimentoPlano dentro de 7 dias → PROXIMO_VENCIMENTO")
        void dataProximaRetornaProximoVencimento() {
            Atleta a = atletaSemMetricas("Bia", "Proxima").toBuilder()
                    .dataVencimentoPlano(HOJE.plusDays(4)).build();
            when(atletaRepository.findAtivosByTenantIdOrderByNome(tenantId)).thenReturn(List.of(a));
            when(metricasDiariasRepository.findLatestByAtletaId(a.getId())).thenReturn(Optional.empty());
            when(treinoRealizadoRepository.findTopByAtletaIdOrderByDataTreinoDesc(a.getId())).thenReturn(Optional.empty());

            assertThat(service.getRoster().get(0).statusVencimentoPlano())
                    .isEqualTo(StatusVencimentoPlano.PROXIMO_VENCIMENTO);
        }

        @Test
        @DisplayName("dataVencimentoPlano fora da janela de alerta → EM_DIA")
        void dataDistanteRetornaEmDia() {
            Atleta a = atletaSemMetricas("Caio", "Distante").toBuilder()
                    .dataVencimentoPlano(HOJE.plusDays(60)).build();
            when(atletaRepository.findAtivosByTenantIdOrderByNome(tenantId)).thenReturn(List.of(a));
            when(metricasDiariasRepository.findLatestByAtletaId(a.getId())).thenReturn(Optional.empty());
            when(treinoRealizadoRepository.findTopByAtletaIdOrderByDataTreinoDesc(a.getId())).thenReturn(Optional.empty());

            assertThat(service.getRoster().get(0).statusVencimentoPlano())
                    .isEqualTo(StatusVencimentoPlano.EM_DIA);
        }

        @Test
        @DisplayName("múltiplos atletas com vencimentos diferentes usam o mesmo 'hoje' no roster")
        void multiplosAtletasVencimentosDiferentes() {
            Atleta vencido = atletaSemMetricas("Vencido", "S").toBuilder()
                    .dataVencimentoPlano(HOJE.minusDays(1)).build();
            Atleta proximo = atletaSemMetricas("Proximo", "S").toBuilder()
                    .dataVencimentoPlano(HOJE.plusDays(1)).build();
            Atleta emDia = atletaSemMetricas("EmDia", "S").toBuilder()
                    .dataVencimentoPlano(HOJE.plusDays(90)).build();
            when(atletaRepository.findAtivosByTenantIdOrderByNome(tenantId))
                    .thenReturn(List.of(vencido, proximo, emDia));
            for (Atleta a : List.of(vencido, proximo, emDia)) {
                when(metricasDiariasRepository.findLatestByAtletaId(a.getId())).thenReturn(Optional.empty());
                when(treinoRealizadoRepository.findTopByAtletaIdOrderByDataTreinoDesc(a.getId())).thenReturn(Optional.empty());
            }

            assertThat(service.getRoster()).extracting(CoachAtletaResumoDto::statusVencimentoPlano)
                    .containsExactly(StatusVencimentoPlano.VENCIDO, StatusVencimentoPlano.PROXIMO_VENCIMENTO,
                            StatusVencimentoPlano.EM_DIA);
        }

        private Atleta atletaSemMetricas(String nome, String sobrenome) {
            return Atleta.builder().id(UUID.randomUUID()).nome(nome).sobrenome(sobrenome)
                    .ativo(AtletaStatus.ATIVO).build();
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
            RecommendationExplanation exp = new RecommendationExplanation(
                    "Atleta sem plano ativo; impossível avaliar carga ou progressão.",
                    List.of("CoachAttentionSignalEvaluator.avaliarSemPlano"),
                    ExplanationConfidence.HIGH);
            when(coachAttentionQueueService.getAttentionQueue()).thenReturn(List.of(
                    new CoachAttentionItemOutputDto(ana.getId(), "Ana", Severidade.ALTA, 235,
                            MotivoAtencao.SEM_PLANO, MotivoAtencao.SEM_PLANO.getSuggestedAction(),
                            Instant.parse("2026-06-17T12:00:00Z"), List.of(), exp)));

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
            when(atletaRepository.findAtivosByTenantIdOrderByNome(tenantId)).thenReturn(List.of(active, paused));
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
            when(atletaRepository.findAtivosByTenantIdOrderByNome(tenantId)).thenReturn(List.of());
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

    @Nested
    @DisplayName("getDashboard")
    class GetDashboard {

        @Test
        @DisplayName("agrega resumo, roster paginado, fila, calendário e insights")
        void agregaDashboard() {
            Atleta bruno = atletaRoster("Bruno", AtletaStatus.ATIVO, -12.0, HOJE.minusDays(2));
            Atleta carla = atletaRoster("Carla", AtletaStatus.ATIVO, 4.0, HOJE.minusDays(1));
            when(atletaRepository.findAtivosByTenantIdOrderByNome(tenantId)).thenReturn(List.of(bruno, carla));
            when(treinoRealizadoRepository.findByAtletaIdAndDataTreinoBetween(eq(bruno.getId()), any(), any()))
                    .thenReturn(List.of(treino(LocalDate.of(2026, 6, 16), "10.0", 80)));
            when(treinoRealizadoRepository.findByAtletaIdAndDataTreinoBetween(eq(carla.getId()), any(), any()))
                    .thenReturn(List.of(treino(LocalDate.of(2026, 6, 16), "8.0", 60)));
            when(treinoPlanejadoRepository.findByTenantAndDataBetween(eq(tenantId), any(), any()))
                    .thenReturn(List.of(planejado(bruno, HOJE, TipoTreino.INTERVALADO)));

            CoachAttentionItemOutputDto atenção = new CoachAttentionItemOutputDto(
                    bruno.getId(),
                    "Bruno S",
                    Severidade.ALTA,
                    240,
                    MotivoAtencao.FADIGA,
                    MotivoAtencao.FADIGA.getSuggestedAction(),
                    Instant.parse("2026-06-17T12:00:00Z"),
                    List.of(),
                    new RecommendationExplanation(
                            "Fadiga alta",
                            List.of("rule-1"),
                            ExplanationConfidence.HIGH));
            when(coachAttentionQueueService.getAttentionQueue()).thenReturn(List.of(atenção));

            CoachDashboardOutputDto dashboard = service.getDashboard(new CoachDashboardQueryDto(
                    "Bruno", "warning", "priority", 0, 1,
                    LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30), HOJE));

            assertThat(dashboard.summary().kpis().totalAtletas()).isEqualTo(2);
            assertThat(dashboard.summary().atletasExibidos()).isEqualTo(1);
            assertThat(dashboard.summary().itensFilaAtencao()).isEqualTo(1);
            assertThat(dashboard.roster().items()).extracting(CoachAtletaResumoDto::nome)
                    .containsExactly("Bruno S");
            assertThat(dashboard.roster().page()).isZero();
            assertThat(dashboard.roster().size()).isEqualTo(1);
            assertThat(dashboard.roster().totalElements()).isEqualTo(1);
            assertThat(dashboard.attentionQueue()).containsExactly(atenção);
            assertThat(dashboard.calendar().semanaInicio()).isEqualTo(INICIO_SEMANA);
            assertThat(dashboard.insights().kpis().treinosPlanejadosSemana()).isGreaterThanOrEqualTo(1);
        }

        @Test
        @DisplayName("rejeita paginação e ordenação inválidas")
        void rejeitaParametrosInvalidos() {
            assertThatThrownBy(() -> service.getDashboard(new CoachDashboardQueryDto(
                    null, null, "invalido", 0, 10, null, null, null)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("sortBy inválido");
        }

        @Test
        @DisplayName("rejeita page negativa e size fora do intervalo")
        void rejeitaPaginaOuTamanhoInvalidos() {
            assertThatThrownBy(() -> service.getDashboard(new CoachDashboardQueryDto(
                    null, null, null, -1, 10, null, null, null)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("page não pode ser negativo");

            assertThatThrownBy(() -> service.getDashboard(new CoachDashboardQueryDto(
                    null, null, null, 0, 0, null, null, null)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("size deve ser maior que zero");
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
