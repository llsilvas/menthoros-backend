package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.domain.planner.SeasonPlanValidator;
import br.com.menthoros.backend.domain.planner.SeasonPlanner;
import br.com.menthoros.backend.domain.planner.SeasonPlanner.WeekPlan;
import br.com.menthoros.backend.domain.planner.SeasonWeekLock;
import br.com.menthoros.backend.dto.output.SeasonPlanResponseDto;
import br.com.menthoros.backend.entity.PlanoTreino;
import br.com.menthoros.backend.entity.Prova;
import br.com.menthoros.backend.entity.SeasonWeek;
import br.com.menthoros.backend.enums.ProvaStatus;
import br.com.menthoros.backend.repository.ProvaRepository;
import br.com.menthoros.backend.repository.SeasonWeekRepository;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Monta o {@link SeasonPlanResponseDto} da temporada: semanas com a trava do ajuste, marcos ainda
 * válidos e as validações brandas recalculadas a cada leitura. Compartilhado pela geração/aprovação
 * ({@code SeasonPlanServiceImpl}) e pelo ajuste ({@code SeasonPlanAdjustmentServiceImpl}).
 */
@Component
public class SeasonPlanResponseMapper {

    private static final String SEVERITY_WARN = "WARN";

    private final ProvaRepository provaRepository;
    private final SeasonWeekRepository seasonWeekRepository;
    private final Clock clock;

    public SeasonPlanResponseMapper(ProvaRepository provaRepository, SeasonWeekRepository seasonWeekRepository,
                                    Clock clock) {
        this.provaRepository = provaRepository;
        this.seasonWeekRepository = seasonWeekRepository;
        this.clock = clock;
    }

    /** Lê as semanas da temporada (por tenant) e monta o DTO. */
    public SeasonPlanResponseDto toDto(PlanoTreino p, UUID tenantId, @Nullable UUID openDraftId) {
        if (p == null) {
            throw new IllegalArgumentException("PlanoTreino não pode ser nulo");
        }
        return toDto(p, seasonWeekRepository.findBySeasonPlanIdAndTenantIdOrderByWeekIndexAsc(p.getId(), tenantId),
                tenantId, openDraftId);
    }

    public SeasonPlanResponseDto toDto(PlanoTreino p, List<SeasonWeek> weeks, UUID tenantId,
                                       @Nullable UUID openDraftId) {
        if (p == null || weeks == null) {
            throw new IllegalArgumentException("PlanoTreino e semanas não podem ser nulos");
        }
        @Nullable Prova prova = p.getProva();
        @Nullable LocalDate dataProva = prova == null ? null : prova.getDataProva();
        LocalDate hoje = LocalDate.now(clock.withZone(SeasonWeekLock.FUSO));
        List<SeasonPlanResponseDto.Validation> validations = dataProva == null
                ? List.of()
                : SeasonPlanValidator.validate(weeks.stream().map(SeasonPlanResponseMapper::comoSemana).toList(),
                                dataProva,
                                SeasonPlanner.distanciaEfetivaKm(prova.getDistancia(), prova.getDistanciaKm())).stream()
                        .map(a -> new SeasonPlanResponseDto.Validation(a.rule(), SEVERITY_WARN, a.weekIndex(),
                                a.measured(), a.limit(), a.message()))
                        .toList();
        return new SeasonPlanResponseDto(
                p.getId(),
                p.getSeasonStatus(),
                p.getApprovedAt(),
                prova == null ? null : prova.getId(),
                prova == null ? null : prova.getNomeProva(),
                dataProva,
                Boolean.TRUE.equals(p.getShortPreparation()),
                p.getTotalWeeks(),
                p.getInitialCtl(),
                p.getPeakVolumeKm(),
                weeks.stream().map(w -> semana(w, hoje, dataProva, SeasonPlanVersioning.primeiraProposta(p))).toList(),
                marcos(weeks, tenantId),
                validations,
                p.getVersion(),
                p.getBaseSeasonPlanId(),
                openDraftId,
                p.getInitialVolumeKm());
    }

    private static SeasonPlanResponseDto.Week semana(SeasonWeek w, LocalDate hoje, @Nullable LocalDate dataProva,
                                                     boolean primeiraProposta) {
        SeasonWeekLock.@Nullable Reason trava = w.getWeekStart() == null
                ? null
                : SeasonWeekLock.forPlan(primeiraProposta, w.getWeekStart(), hoje, dataProva).orElse(null);
        return new SeasonPlanResponseDto.Week(
                w.getWeekIndex() == null ? 0 : w.getWeekIndex(),
                w.getWeekStart(),
                w.getPhase(),
                w.getTargetVolumeKm(),
                w.getTargetTss(),
                w.isDeload(),
                w.getLongRunKm(),
                w.getValueSource(),
                w.getKeySessions(),
                trava != null,
                trava);
    }

    /**
     * Marcos das semanas (T2): prova cancelada, excluída ou de outra assessoria sai da lista, e também a
     * remarcada para fora da semana gravada — até a proposta ser regerada.
     */
    public List<SeasonPlanResponseDto.Milestone> marcos(List<SeasonWeek> weeks, UUID tenantId) {
        List<UUID> ids = weeks.stream().map(SeasonWeek::getMilestoneRaceId).filter(Objects::nonNull).toList();
        if (ids.isEmpty()) {
            return List.of();
        }
        Map<UUID, Prova> provas = provaRepository.findByIdInAndAssessoriaId(ids, tenantId).stream()
                .filter(pr -> pr.getStatusProva() != ProvaStatus.CANCELADA)
                .collect(Collectors.toMap(Prova::getId, pr -> pr));
        return weeks.stream()
                .filter(w -> w.getMilestoneRaceId() != null && provas.containsKey(w.getMilestoneRaceId()))
                .filter(w -> SeasonWeekLock.contains(w.getWeekStart(), provas.get(w.getMilestoneRaceId()).getDataProva()))
                .map(w -> {
                    Prova pr = provas.get(w.getMilestoneRaceId());
                    return new SeasonPlanResponseDto.Milestone(w.getWeekIndex(), pr.getId(), pr.getNomeProva(),
                            pr.getDataProva(), pr.getDistancia());
                })
                .toList();
    }

    private static WeekPlan comoSemana(SeasonWeek w) {
        return new WeekPlan(w.getWeekIndex() == null ? 0 : w.getWeekIndex(), w.getWeekStart(), w.getPhase(),
                w.getTargetVolumeKm(), w.getTargetTss() == null ? 0 : w.getTargetTss(), w.isDeload(),
                w.getLongRunKm(), w.getMilestoneRaceId());
    }
}
