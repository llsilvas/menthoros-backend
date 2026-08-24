package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.dto.input.CoachDashboardQueryDto;
import br.com.menthoros.backend.dto.output.CoachAtletaResumoDto;
import br.com.menthoros.backend.dto.output.CoachCalendarioDto;
import br.com.menthoros.backend.dto.output.CoachDashboardOutputDto;
import br.com.menthoros.backend.dto.output.CoachDashboardRosterPageDto;
import br.com.menthoros.backend.dto.output.CoachDashboardSummaryDto;
import br.com.menthoros.backend.dto.output.CoachInsightsDto;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.MetricasDiarias;
import br.com.menthoros.backend.entity.TreinoPlanejado;
import br.com.menthoros.backend.entity.TreinoRealizado;
import br.com.menthoros.backend.enums.AtletaStatus;
import br.com.menthoros.backend.enums.FaixaTsb;
import br.com.menthoros.backend.enums.StatusVencimentoPlano;
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
    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int MAX_PAGE_SIZE = 50;

    /** Tipos que contam como "treino-chave" da semana. */
    private static final Set<TipoTreino> TIPOS_CHAVE =
            Set.of(TipoTreino.INTERVALADO, TipoTreino.TIRO, TipoTreino.LONGO, TipoTreino.TEMPO_RUN);

    private enum DashboardSort {
        PRIORITY,
        NAME,
        VOLUME,
        TSB
    }

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

        return atletaRepository.findAtivosByTenantIdOrderByNome(tenantId).stream()
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
            // D8: cancelado não conta na carga — mesmo predicado usado por TsbService/produtores.
            List<TreinoRealizado> treinos = treinoRealizadoRepository
                    .findByAtletaIdAndDataTreinoBetween(r.atletaId(), inicio, fim).stream()
                    .filter(TreinoRealizado::contaNaCarga)
                    .toList();
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

    @Override
    @Transactional(readOnly = true)
    public CoachDashboardOutputDto getDashboard(CoachDashboardQueryDto query) {
        if (query == null) {
            throw new IllegalArgumentException("query não pode ser nulo");
        }
        
        DashboardSort sortBy = parseSort(query.sortBy());
        int page = query.page() != null ? query.page() : 0;
        int size = query.size() != null ? query.size() : DEFAULT_PAGE_SIZE;
        validarPaginacao(page, size);
        validarStatus(query.status());

        UUID tenantId = TenantContext.getRequiredTenantId();
        log.info("Montando dashboard do coach: tenantId={}, q={}, status={}, sortBy={}, page={}, size={}, from={}, to={}, weekFrom={}",
                tenantId, query.q(), query.status(), sortBy, page, size, query.from(), query.to(), query.weekFrom());

        List<CoachAtletaResumoDto> roster = getRoster().stream()
                .filter(atleta -> matchesSearch(atleta, query.q()))
                .filter(atleta -> matchesStatus(atleta, query.status()))
                .sorted(comparator(sortBy))
                .toList();

        int totalElements = roster.size();
        int totalPages = totalElements == 0 ? 0 : (int) Math.ceil(totalElements / (double) size);
        int fromIndex = Math.min(page * size, totalElements);
        int toIndex = Math.min(fromIndex + size, totalElements);

        List<CoachAtletaResumoDto> pageItems = roster.subList(fromIndex, toIndex);
        CoachInsightsDto insights = getInsights(query.from(), query.to());
        List<CoachAttentionItemOutputDto> attentionQueue = coachAttentionQueueService.getAttentionQueue();
        CoachCalendarioDto calendar = getCalendarioSemanal(query.weekFrom());

        CoachDashboardSummaryDto summary = new CoachDashboardSummaryDto(
                insights.kpis(),
                totalElements,
                attentionQueue.size());

        CoachDashboardRosterPageDto rosterPage = new CoachDashboardRosterPageDto(
                pageItems,
                page,
                size,
                totalElements,
                totalPages);

        CoachDashboardOutputDto dashboard = new CoachDashboardOutputDto(
                clock.instant(),
                summary,
                rosterPage,
                attentionQueue,
                calendar,
                insights);

        log.info("Dashboard do coach montado: tenantId={}, atletasExibidos={}, itensFila={}",
                tenantId, totalElements, attentionQueue.size());
        return dashboard;
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
        UUID tenantId = TenantContext.getRequiredTenantId();
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

        List<TreinoPlanejado> treinosAderencia = treinoPlanejadoRepository
                .findComRealizadoByAtletaAndPeriodo(atletaId, tenantId, inicioSemana.minusWeeks(3));

        Integer aderenciaPercentual = null;
        if (!treinosAderencia.isEmpty()) {
            int totalAderencia = treinosAderencia.size();
            int realizadoAderencia = (int) treinosAderencia.stream()
                    .filter(tp -> tp.getTreinoRealizado() != null)
                    .count();
            aderenciaPercentual = (int) Math.round(realizadoAderencia * 100.0 / totalAderencia);
        }

        return new CoachAtletaResumoDto(atletaId, nomeCompleto(atleta), ctl, atl, tsb, fase,
                deriveStatus(atleta, tsb, lastActivity, hoje), lastActivity, weeklyVolume, aderenciaPercentual,
                FaixaTsb.classificarNome(tsb), atleta.getTipoPlanoAtleta(), atleta.getDataVencimentoPlano(),
                StatusVencimentoPlano.resolver(atleta.getDataVencimentoPlano(), hoje));
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

    private void validarPaginacao(int page, int size) {
        if (page < 0) {
            throw new IllegalArgumentException("page não pode ser negativo");
        }
        if (size <= 0) {
            throw new IllegalArgumentException("size deve ser maior que zero");
        }
        if (size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("size não pode ser maior que " + MAX_PAGE_SIZE);
        }
    }

    private void validarStatus(String status) {
        if (status == null || status.isBlank()) {
            return;
        }
        if (!Set.of("active", "warning", "danger", "paused").contains(status.toLowerCase())) {
            throw new IllegalArgumentException("status inválido: " + status);
        }
    }

    private DashboardSort parseSort(String sortBy) {
        if (sortBy == null || sortBy.isBlank()) {
            return DashboardSort.PRIORITY;
        }
        return switch (sortBy.trim().toLowerCase()) {
            case "priority" -> DashboardSort.PRIORITY;
            case "name" -> DashboardSort.NAME;
            case "volume" -> DashboardSort.VOLUME;
            case "tsb" -> DashboardSort.TSB;
            default -> throw new IllegalArgumentException("sortBy inválido: " + sortBy);
        };
    }

    private boolean matchesSearch(CoachAtletaResumoDto atleta, String q) {
        if (q == null || q.isBlank()) {
            return true;
        }
        String termo = q.trim().toLowerCase();
        return (atleta.nome() != null && atleta.nome().toLowerCase().contains(termo))
                || (atleta.fase() != null && atleta.fase().toLowerCase().contains(termo));
    }

    private boolean matchesStatus(CoachAtletaResumoDto atleta, String status) {
        if (status == null || status.isBlank()) {
            return true;
        }
        return atleta.status() != null && atleta.status().equalsIgnoreCase(status.trim());
    }

    private Comparator<CoachAtletaResumoDto> comparator(DashboardSort sortBy) {
        Comparator<CoachAtletaResumoDto> comparator = switch (sortBy) {
            case NAME -> Comparator.comparing(CoachAtletaResumoDto::nome, Comparator.nullsLast(String::compareToIgnoreCase));
            case VOLUME -> Comparator.comparing(CoachAtletaResumoDto::weeklyVolume, Comparator.nullsLast(Comparator.naturalOrder())).reversed();
            case TSB -> Comparator.comparing(CoachAtletaResumoDto::tsb, Comparator.nullsLast(Comparator.naturalOrder()));
            case PRIORITY -> Comparator
                    .comparingInt((CoachAtletaResumoDto atleta) -> prioridadeStatus(atleta.status()))
                    .reversed()
                    .thenComparing(CoachAtletaResumoDto::tsb, Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(CoachAtletaResumoDto::lastActivity, Comparator.nullsLast(Comparator.naturalOrder()));
        };
        return comparator.thenComparing(CoachAtletaResumoDto::nome, Comparator.nullsLast(String::compareToIgnoreCase));
    }

    private int prioridadeStatus(String status) {
        if (status == null) {
            return 0;
        }
        return switch (status.toLowerCase()) {
            case "danger" -> 4;
            case "warning" -> 3;
            case "paused" -> 2;
            case "active" -> 1;
            default -> 0;
        };
    }

    private String nomeCompleto(Atleta atleta) {
        String nome = atleta.getNome() != null ? atleta.getNome() : "";
        return atleta.getSobrenome() != null ? (nome + " " + atleta.getSobrenome()).trim() : nome;
    }

    private String semanaIso(LocalDate data) {
        return String.format("%d-W%02d", data.get(IsoFields.WEEK_BASED_YEAR), data.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR));
    }
}
