package br.com.menthoros.backend.scheduler;

import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.multitenancy.TenantContext;
import br.com.menthoros.backend.repository.AtletaRepository;
import br.com.menthoros.backend.repository.MetricasDiariasRepository;
import br.com.menthoros.backend.repository.projection.SerieMetricasAtleta;
import br.com.menthoros.backend.services.TsbService;
import br.com.menthoros.backend.services.helper.AtletaHojeResolver;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Materializa os dias de descanso no TSB (fix-tsb-rest-days-materialization, R3/R4/R5/R8): dia sem
 * treino não gera linha em {@code tb_metricas_diarias}, e sem este job o PMC fica com buraco e o
 * seed do próximo treino cai para zero.
 *
 * <p>Roda de hora em hora porque "hoje" é por atleta (fuso) — quem já está em dia é pulado, então a
 * passada é barata e pega Acre/Manaus depois da virada do fuso deles. Cross-tenant por natureza:
 * cada atleta roda com o tenant dele no {@link TenantContext}, sem o qual a busca de melhor esforço
 * é descartada e o limiar de pace regride de {@code MELHOR_ESFORCO} para {@code MEDIA_TREINOS}.
 */
@Slf4j
@Component
public class TsbDecaimentoDiarioScheduler {

    static final ZoneId FUSO_SERVIDOR = ZoneId.of("America/Sao_Paulo");

    /** Valor do gauge quando a última execução não conseguiu medir: zero seria "tudo em dia" por engano. */
    static final int CONTAGEM_INDISPONIVEL = -1;

    private final MetricasDiariasRepository metricasDiariasRepository;
    private final AtletaRepository atletaRepository;
    private final TsbService tsbService;
    private final AtletaHojeResolver atletaHojeResolver;
    private final Clock clock;
    private final AtomicInteger atletasDefasados = new AtomicInteger();

    public TsbDecaimentoDiarioScheduler(MetricasDiariasRepository metricasDiariasRepository,
                                        AtletaRepository atletaRepository,
                                        TsbService tsbService,
                                        AtletaHojeResolver atletaHojeResolver,
                                        Clock clock,
                                        MeterRegistry meterRegistry) {
        this.metricasDiariasRepository = metricasDiariasRepository;
        this.atletaRepository = atletaRepository;
        this.tsbService = tsbService;
        this.atletaHojeResolver = atletaHojeResolver;
        this.clock = clock;
        Gauge.builder("tsb.atletas_defasados", atletasDefasados, AtomicInteger::get)
                .description("Atletas cuja última métrica diária é anterior a ontem, ao fim do job de decaimento")
                .register(meterRegistry);
    }

    /**
     * Recalcula, para cada atleta com série atrasada ou com buraco, do primeiro dia pendente até o
     * "hoje" dele. Atraso acima de 400 dias avança em blocos, uma execução por bloco
     * ({@code TsbRecalculoTransacional.MAX_DIAS_NOVOS_POR_EXECUCAO}).
     *
     * <p><b>Idempotent:</b> YES — sem pendência não faz nada; com pendência converge para o mesmo estado.
     * <p><b>Side Effects:</b> Database insert/update de métricas diárias; atualiza o gauge.
     * <p><b>Tenant-aware:</b> YES — seta e limpa o tenant por atleta.
     */
    @Scheduled(cron = "0 30 * * * *", zone = "America/Sao_Paulo")
    public void executar() {
        int processados = 0;
        int pulados = 0;
        int falhas = 0;

        List<SerieMetricasAtleta> series;
        try {
            series = metricasDiariasRepository.findSeriePorAtleta();
        } catch (RuntimeException e) {
            log.warn("[tsb-decaimento] falha ao consultar as séries: {}", e.getMessage(), e);
            atletasDefasados.set(CONTAGEM_INDISPONIVEL);
            return;
        }

        for (SerieMetricasAtleta serie : series) {
            try {
                Atleta atleta = atletaRepository.findById(serie.getAtletaId()).orElse(null);
                if (atleta == null) {
                    pulados++;
                    continue;
                }
                LocalDate hoje = atletaHojeResolver.hojeDe(atleta);
                Optional<LocalDate> pendente = serie.primeiroDiaPendente(hoje);
                if (pendente.isEmpty()) {
                    pulados++;
                    continue;
                }
                TenantContext.setTenantId(atleta.getAssessoria().getId());
                tsbService.recalcularDesde(atleta.getId(), pendente.get(), hoje);
                processados++;
            } catch (RuntimeException e) {
                falhas++;
                log.warn("[tsb-decaimento] falha no atleta {}: {}", serie.getAtletaId(), e.getMessage(), e);
            } finally {
                TenantContext.clear();
            }
        }

        atualizarGauge();
        log.info("[tsb-decaimento] processados={} pulados={} falhas={} defasados={}",
                processados, pulados, falhas, atletasDefasados.get());
    }

    /**
     * Defasado = sem métrica de ontem em diante, medido depois da passada: o sinal de que o job não
     * alcança. Usa o dia de São Paulo, não o de cada atleta: quem está à frente de SP (fuso fora do
     * Brasil) pode aparecer um dia a menos; quem está atrás (Acre/Manaus) nunca é superestimado.
     */
    private void atualizarGauge() {
        try {
            LocalDate ontem = LocalDate.now(clock.withZone(FUSO_SERVIDOR)).minusDays(1);
            int defasados = (int) metricasDiariasRepository.findSeriePorAtleta().stream()
                    .filter(s -> s.getUltimaData().isBefore(ontem))
                    .count();
            atletasDefasados.set(defasados);
        } catch (RuntimeException e) {
            log.warn("[tsb-decaimento] falha ao atualizar o gauge tsb.atletas_defasados: {}", e.getMessage());
            atletasDefasados.set(CONTAGEM_INDISPONIVEL);
        }
    }
}
