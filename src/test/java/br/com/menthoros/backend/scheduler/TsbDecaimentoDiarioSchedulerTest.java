package br.com.menthoros.backend.scheduler;

import br.com.menthoros.backend.entity.Assessoria;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.multitenancy.TenantContext;
import br.com.menthoros.backend.repository.AtletaRepository;
import br.com.menthoros.backend.repository.MetricasDiariasRepository;
import br.com.menthoros.backend.repository.projection.SerieMetricasAtleta;
import br.com.menthoros.backend.services.TsbService;
import br.com.menthoros.backend.services.helper.AtletaHojeResolver;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Relógio fixo em 2026-10-20T15:00Z: em São Paulo é dia 20; em Auckland (UTC+13) já é dia 21.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TsbDecaimentoDiarioScheduler")
class TsbDecaimentoDiarioSchedulerTest {

    private static final Clock RELOGIO = Clock.fixed(Instant.parse("2026-10-20T15:00:00Z"), ZoneOffset.UTC);
    private static final LocalDate HOJE_SP = LocalDate.of(2026, 10, 20);

    @Mock private MetricasDiariasRepository metricasRepo;
    @Mock private AtletaRepository atletaRepo;
    @Mock private TsbService tsbService;

    private SimpleMeterRegistry meterRegistry;
    private TsbDecaimentoDiarioScheduler scheduler;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        scheduler = new TsbDecaimentoDiarioScheduler(metricasRepo, atletaRepo, tsbService,
                new AtletaHojeResolver(RELOGIO), RELOGIO, meterRegistry);
    }

    @AfterEach
    void limpaTenant() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("série em dia: não recalcula")
    void emDiaNaoChama() {
        Atleta atleta = atleta("America/Sao_Paulo");
        series(serie(atleta, HOJE_SP, null));
        when(atletaRepo.findById(atleta.getId())).thenReturn(Optional.of(atleta));

        scheduler.executar();

        verify(tsbService, never()).recalcularDesde(any(), any(), any());
    }

    @Test
    @DisplayName("série atrasada: recalcula de ultima+1 até hoje")
    void atrasadoChamaComIntervaloCerto() {
        Atleta atleta = atleta("America/Sao_Paulo");
        series(serie(atleta, HOJE_SP.minusDays(3), null));
        when(atletaRepo.findById(atleta.getId())).thenReturn(Optional.of(atleta));

        scheduler.executar();

        verify(tsbService).recalcularDesde(atleta.getId(), HOJE_SP.minusDays(2), HOJE_SP);
    }

    @Test
    @DisplayName("buraco no meio de série em dia: recalcula a partir do buraco")
    void buracoVence() {
        Atleta atleta = atleta("America/Sao_Paulo");
        series(serie(atleta, HOJE_SP, HOJE_SP.minusDays(5)));
        when(atletaRepo.findById(atleta.getId())).thenReturn(Optional.of(atleta));

        scheduler.executar();

        verify(tsbService).recalcularDesde(atleta.getId(), HOJE_SP.minusDays(5), HOJE_SP);
    }

    @Test
    @DisplayName("atraso de 900 dias: passa o intervalo inteiro, o corte de 400 dias é do recálculo")
    void atrasoLongoNaoCortaNoJob() {
        Atleta atleta = atleta("America/Sao_Paulo");
        series(serie(atleta, HOJE_SP.minusDays(900), null));
        when(atletaRepo.findById(atleta.getId())).thenReturn(Optional.of(atleta));

        scheduler.executar();

        verify(tsbService).recalcularDesde(atleta.getId(), HOJE_SP.minusDays(899), HOJE_SP);
    }

    @Test
    @DisplayName("exceção num atleta não impede o seguinte")
    void falhaNaoParaOLote() {
        Atleta falha = atleta("America/Sao_Paulo");
        Atleta ok = atleta("America/Sao_Paulo");
        series(serie(falha, HOJE_SP.minusDays(2), null), serie(ok, HOJE_SP.minusDays(2), null));
        when(atletaRepo.findById(falha.getId())).thenReturn(Optional.of(falha));
        when(atletaRepo.findById(ok.getId())).thenReturn(Optional.of(ok));
        doThrow(new IllegalStateException("falha simulada"))
                .when(tsbService).recalcularDesde(falha.getId(), HOJE_SP.minusDays(1), HOJE_SP);

        scheduler.executar();

        verify(tsbService).recalcularDesde(ok.getId(), HOJE_SP.minusDays(1), HOJE_SP);
    }

    @Test
    @DisplayName("hoje vem do fuso do atleta: em Auckland o dia 20 já está atrasado")
    void hojeDoFusoDoAtleta() {
        Atleta auckland = atleta("Pacific/Auckland");
        Atleta saoPaulo = atleta("America/Sao_Paulo");
        series(serie(auckland, HOJE_SP, null), serie(saoPaulo, HOJE_SP, null));
        when(atletaRepo.findById(auckland.getId())).thenReturn(Optional.of(auckland));
        when(atletaRepo.findById(saoPaulo.getId())).thenReturn(Optional.of(saoPaulo));

        scheduler.executar();

        verify(tsbService).recalcularDesde(auckland.getId(), HOJE_SP.plusDays(1), HOJE_SP.plusDays(1));
        verify(tsbService, never()).recalcularDesde(eq(saoPaulo.getId()), any(), any());
    }

    @Test
    @DisplayName("dois tenants consecutivos: cada recálculo roda com o tenant do próprio atleta e nada vaza")
    void tenantPorAtletaSemVazamento() {
        Atleta a = atleta("America/Sao_Paulo");
        Atleta b = atleta("America/Sao_Paulo");
        series(serie(a, HOJE_SP.minusDays(2), null), serie(b, HOJE_SP.minusDays(2), null));
        when(atletaRepo.findById(a.getId())).thenReturn(Optional.of(a));
        when(atletaRepo.findById(b.getId())).thenReturn(Optional.of(b));
        List<UUID> tenantsVistos = new ArrayList<>();
        doAnswer(inv -> tenantsVistos.add(TenantContext.getTenantId()))
                .when(tsbService).recalcularDesde(any(), any(), any());

        scheduler.executar();

        assertThat(tenantsVistos).containsExactly(a.getAssessoria().getId(), b.getAssessoria().getId());
        assertThat(TenantContext.getTenantId()).as("tenant limpo ao fim").isNull();
    }

    @Test
    @DisplayName("tenant é limpo mesmo quando o recálculo falha")
    void tenantLimpoNaFalha() {
        Atleta a = atleta("America/Sao_Paulo");
        series(serie(a, HOJE_SP.minusDays(2), null));
        when(atletaRepo.findById(a.getId())).thenReturn(Optional.of(a));
        doThrow(new IllegalStateException("falha")).when(tsbService).recalcularDesde(any(), any(), any());

        scheduler.executar();

        assertThat(TenantContext.getTenantId()).isNull();
    }

    @Test
    @DisplayName("atleta removido: pula sem falhar")
    void atletaInexistente() {
        Atleta fantasma = atleta("America/Sao_Paulo");
        series(serie(fantasma, HOJE_SP.minusDays(2), null));
        when(atletaRepo.findById(fantasma.getId())).thenReturn(Optional.empty());

        scheduler.executar();

        verify(tsbService, never()).recalcularDesde(any(), any(), any());
    }

    @Test
    @DisplayName("gauge conta atletas com última métrica anterior a ontem, medido depois da passada")
    void gaugeDefasados() {
        Atleta defasado = atleta("America/Sao_Paulo");
        Atleta ontem = atleta("America/Sao_Paulo");
        Atleta emDia = atleta("America/Sao_Paulo");
        // 1ª leitura (laço): as três; 2ª (gauge): o defasado continua sem alcançar.
        SerieMetricasAtleta sDefasado = serie(defasado, HOJE_SP.minusDays(2), null);
        SerieMetricasAtleta sOntem = serie(ontem, HOJE_SP.minusDays(1), null);
        SerieMetricasAtleta sEmDia = serie(emDia, HOJE_SP, null);
        when(metricasRepo.findSeriePorAtleta()).thenReturn(List.of(sDefasado, sOntem, sEmDia));
        when(atletaRepo.findById(any())).thenAnswer(inv -> Optional.of(
                List.of(defasado, ontem, emDia).stream().filter(x -> x.getId().equals(inv.getArgument(0))).findFirst().orElseThrow()));

        scheduler.executar();

        assertThat(meterRegistry.get("tsb.atletas_defasados").gauge().value()).isEqualTo(1.0);
    }

    private void series(SerieMetricasAtleta... series) {
        when(metricasRepo.findSeriePorAtleta()).thenReturn(List.of(series));
    }

    private static Atleta atleta(String timezone) {
        Assessoria assessoria = new Assessoria();
        assessoria.setId(UUID.randomUUID());
        Atleta atleta = new Atleta();
        atleta.setId(UUID.randomUUID());
        atleta.setAssessoria(assessoria);
        atleta.setTimezone(timezone);
        return atleta;
    }

    private static SerieMetricasAtleta serie(Atleta atleta, LocalDate ultima, LocalDate buraco) {
        return new SerieMetricasAtleta() {
            @Override public UUID getAtletaId() { return atleta.getId(); }
            @Override public LocalDate getUltimaData() { return ultima; }
            @Override public LocalDate getPrimeiroBuraco() { return buraco; }
        };
    }
}
