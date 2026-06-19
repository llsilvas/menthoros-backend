package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.dto.output.CoachAtletaResumoDto;
import br.com.menthoros.backend.dto.output.CoachCalendarioDto;
import br.com.menthoros.backend.dto.output.CoachInsightsDto;
import br.com.menthoros.backend.entity.Atleta;
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
import br.com.menthoros.backend.dto.output.CoachAttentionItemOutputDto;
import br.com.menthoros.backend.services.CoachAttentionQueueService;
import br.com.menthoros.backend.services.CoachDashboardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.IsoFields;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Implementação read-only dos dashboards do coach. Agrega no escopo do tenant
 * (via {@link TenantContext}); não recebe resource-id (por isso não usa {@code @RequireTenant}).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CoachDashboardServiceImpl implements CoachDashboardService {

    private static final int SEMANAS_INSIGHTS = 12;
    private static final int TOP_ATLETAS = 5;

    /** Tipos que contam como "treino-chave" da semana. */
    private static final Set<TipoTreino> TIPOS_CHAVE =
            Set.of(TipoTreino.INTERVALADO, TipoTreino.TIRO, TipoTreino.LONGO, TipoTreino.TEMPO_RUN);

    private final AtletaRepository atletaRepository;
    private final MetricasDiariasRepository metricasDiariasRepository;
    private final PlanoMetadadosRepository planoMetadadosRepository;
    private final TreinoRealizadoRepository treinoRealizadoRepository;
    private final TreinoPlanejadoRepository treinoPlanejadoRepository;
    private final CoachAttentionQueueService coachAttentionQueueService;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public List<CoachAtletaResumoDto> getRoster() {
        UUID tenantId = TenantContext.getRequiredTenantId();
        LocalDate hoje = LocalDate.now(clock);
        LocalDate inicioSemana = hoje.with(DayOfWeek.MONDAY);
        LocalDate fimSemana = inicioSemana.plusDays(6);

        return atletaRepository.findAllByTenantIdOrderByNome(tenantId).stream()
                .map(atleta -> montarResumo(atleta, hoje, inicioSemana, fimSemana))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public CoachCalendarioDto getCalendarioSemanal(LocalDate from) {
        UUID tenantId = TenantContext.getRequiredTenantId();
        LocalDate base = (from != null) ? from : LocalDate.now(clock);
        LocalDate inicio = base.with(DayOfWeek.MONDAY);
        LocalDate fim = inicio.plusDays(6);

        Set<UUID> atletasEmAtencao = coachAttentionQueueService.getAttentionQueue().stream()
                .map(CoachAttentionItemOutputDto::atletaId)
                .collect(Collectors.toSet());

        List<CoachCalendarioDto.TreinoAgendado> treinos =
                treinoPlanejadoRepository.findByTenantAndDataBetween(tenantId, inicio, fim).stream()
                        .map(tp -> montarTreinoAgendado(tp, atletasEmAtencao))
                        .toList();

        return new CoachCalendarioDto(inicio, fim, treinos);
    }

    @Override
    @Transactional(readOnly = true)
    public CoachInsightsDto getInsights(LocalDate from, LocalDate to) {
        LocalDate fim = (to != null) ? to : LocalDate.now(clock);
        LocalDate inicio = (from != null) ? from : fim.minusWeeks(SEMANAS_INSIGHTS);
        if (inicio.isAfter(fim)) {
            throw new DomainRuleViolationException("Intervalo inválido: 'from' não pode ser depois de 'to'");
        }

        // Custo: O(N atletas) — reusa getRoster() (status/KPIs) + 1 query de realizados por atleta.
        // Aceitável para o roster de um tenant; ver follow-up de batch-loading se crescer.
        List<CoachAtletaResumoDto> roster = getRoster();
        CoachInsightsDto.Kpis kpis = new CoachInsightsDto.Kpis(
                roster.size(),
                (int) roster.stream().filter(r -> "active".equals(r.status())).count(),
                (int) roster.stream().filter(r -> "warning".equals(r.status()) || "danger".equals(r.status())).count(),
                (int) roster.stream().filter(r -> "paused".equals(r.status())).count(),
                contarTreinosPlanejadosSemanaAtual());

        // Agrega volume/TSS realizados por semana (ISO) e volume total por atleta no período.
        Map<String, Double> volumePorSemana = new LinkedHashMap<>();
        Map<String, Integer> tssPorSemana = new LinkedHashMap<>();
        List<CoachInsightsDto.TopAtleta> volumePorAtleta = new ArrayList<>();

        for (CoachAtletaResumoDto r : roster) {
            double volumeAtleta = 0.0;
            List<TreinoRealizado> treinos =
                    treinoRealizadoRepository.findByAtletaIdAndDataTreinoBetween(r.atletaId(), inicio, fim);
            for (TreinoRealizado t : treinos) {
                double km = t.getDistanciaKm() != null ? t.getDistanciaKm().doubleValue() : 0.0;
                int tss = t.getTssCalculado() != null ? t.getTssCalculado() : 0;
                volumeAtleta += km;
                String semana = semanaIso(t.getDataTreino());
                volumePorSemana.merge(semana, km, Double::sum);
                tssPorSemana.merge(semana, tss, Integer::sum);
            }
            if (volumeAtleta > 0) {
                volumePorAtleta.add(new CoachInsightsDto.TopAtleta(r.atletaId(), r.nome(), volumeAtleta));
            }
        }

        List<CoachInsightsDto.PontoCargaSemanal> tendencia = volumePorSemana.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> new CoachInsightsDto.PontoCargaSemanal(e.getKey(), e.getValue(), tssPorSemana.get(e.getKey())))
                .toList();

        List<CoachInsightsDto.TopAtleta> top = volumePorAtleta.stream()
                .sorted(Comparator.comparingDouble(CoachInsightsDto.TopAtleta::volumeKm).reversed())
                .limit(TOP_ATLETAS)
                .toList();

        return new CoachInsightsDto(kpis, tendencia, top);
    }

    // ===== Helpers =====

    /** Conta os treinos planejados do tenant na semana atual — sem passar por getCalendarioSemanal
     *  (que aciona a fila de atenção), evitando custo O(N) extra no cálculo dos KPIs. */
    private int contarTreinosPlanejadosSemanaAtual() {
        UUID tenantId = TenantContext.getRequiredTenantId();
        LocalDate inicio = LocalDate.now(clock).with(DayOfWeek.MONDAY);
        LocalDate fim = inicio.plusDays(6);
        return treinoPlanejadoRepository.findByTenantAndDataBetween(tenantId, inicio, fim).size();
    }

    private CoachAtletaResumoDto montarResumo(Atleta atleta, LocalDate hoje, LocalDate inicioSemana, LocalDate fimSemana) {
        UUID atletaId = atleta.getId();
        MetricasDiarias metrica = metricasDiariasRepository.findLatestByAtletaId(atletaId).orElse(null);

        String fase = planoMetadadosRepository.findByAtletaId(atletaId)
                .map(m -> m.getFasePeriodizacao() != null ? m.getFasePeriodizacao().name() : null)
                .orElse(null);

        LocalDate lastActivity = treinoRealizadoRepository.findTopByAtletaIdOrderByDataTreinoDesc(atletaId)
                .map(TreinoRealizado::getDataTreino).orElse(null);

        BigDecimal weeklyVolume = treinoRealizadoRepository
                .findByAtletaIdAndDataTreinoBetween(atletaId, inicioSemana, fimSemana).stream()
                .map(TreinoRealizado::getDistanciaKm)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Double ctl = metrica != null ? metrica.getCtl() : null;
        Double atl = metrica != null ? metrica.getAtl() : null;
        Double tsb = metrica != null ? metrica.getTsb() : null;

        return new CoachAtletaResumoDto(atletaId, nomeCompleto(atleta), ctl, atl, tsb, fase,
                deriveStatus(atleta, tsb, lastActivity, hoje), lastActivity, weeklyVolume);
    }

    private CoachCalendarioDto.TreinoAgendado montarTreinoAgendado(TreinoPlanejado tp, Set<UUID> atletasEmAtencao) {
        Atleta atleta = tp.getAtleta();
        TipoTreino tipo = tp.getTipoTreino();
        boolean hasAlert = atleta != null && atletasEmAtencao.contains(atleta.getId());
        return new CoachCalendarioDto.TreinoAgendado(
                atleta != null ? atleta.getId() : null,
                atleta != null ? nomeCompleto(atleta) : null,
                tp.getDataTreino(),
                tipo != null ? tipo.name() : null,
                tipo != null && TIPOS_CHAVE.contains(tipo),
                hasAlert,  // atleta presente na fila de atenção (add-coach-attention-queue)
                false); // hasPendingSuggestion — fonte: add-coach-suggestion-inbox (não entregue)
    }

    /**
     * Deriva o status de atenção do coach (provisório até `add-coach-attention-queue`):
     * paused (inativo) > danger (TSB ≤ -20 ou inativo ≥ 14d) > warning (TSB ≤ -10, sem atividade,
     * ou inativo ≥ 7d) > active.
     */
    private String deriveStatus(Atleta atleta, Double tsb, LocalDate lastActivity, LocalDate hoje) {
        if (atleta.getAtivo() == AtletaStatus.INATIVO) {
            return "paused";
        }
        Long diasInativo = lastActivity != null ? java.time.temporal.ChronoUnit.DAYS.between(lastActivity, hoje) : null;

        boolean danger = (tsb != null && tsb <= -20.0) || (diasInativo != null && diasInativo >= 14);
        if (danger) return "danger";

        boolean warning = (tsb != null && tsb <= -10.0) || lastActivity == null || (diasInativo != null && diasInativo >= 7);
        if (warning) return "warning";

        return "active";
    }

    private String nomeCompleto(Atleta atleta) {
        String nome = atleta.getNome() != null ? atleta.getNome() : "";
        return atleta.getSobrenome() != null ? (nome + " " + atleta.getSobrenome()).trim() : nome;
    }

    private String semanaIso(LocalDate data) {
        return String.format("%d-W%02d", data.get(IsoFields.WEEK_BASED_YEAR), data.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR));
    }
}
