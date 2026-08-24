package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.dto.input.RaceProjectionRequestDto;
import br.com.menthoros.backend.dto.output.AthleteProjectionViewDto;
import br.com.menthoros.backend.dto.output.RaceProjectionSnapshotOutputDto;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.Prova;
import br.com.menthoros.backend.entity.RaceProjectionSnapshot;
import br.com.menthoros.backend.entity.TreinoRealizado;
import br.com.menthoros.backend.exception.ResourceNotFoundException;
import br.com.menthoros.backend.mapper.AthleteProfileMapper;
import br.com.menthoros.backend.repository.AtletaRepository;
import br.com.menthoros.backend.repository.ProvaRepository;
import br.com.menthoros.backend.repository.RaceProjectionSnapshotRepository;
import br.com.menthoros.backend.repository.TreinoRealizadoRepository;
import br.com.menthoros.backend.services.RaceProjectionService;
import br.com.menthoros.backend.skills.race.CoachGoalOverride;
import br.com.menthoros.backend.skills.race.CTLForecast;
import br.com.menthoros.backend.skills.race.GoalGapAnalysis;
import br.com.menthoros.backend.skills.race.LoadProjection;
import br.com.menthoros.backend.skills.race.PastRace;
import br.com.menthoros.backend.skills.race.RaceProjection;
import br.com.menthoros.backend.skills.race.RaceProjectionInput;
import br.com.menthoros.backend.skills.race.RaceProjectionOutput;
import br.com.menthoros.backend.skills.race.RaceProjectionSkill;
import br.com.menthoros.backend.skills.race.TrainingHistory;
import br.com.menthoros.backend.skills.race.WorkoutSummary;
import br.com.menthoros.backend.skills.race.enums.DataQuality;
import br.com.menthoros.backend.skills.race.enums.RaceConditions;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RaceProjectionServiceImpl implements RaceProjectionService {

    private static final int HISTORY_WEEKS = 16;
    private static final int QUALIFYING_TYPES_FULL_THRESHOLD = 12;
    private static final int QUALIFYING_TYPES_PARTIAL_THRESHOLD = 6;

    private final AtletaRepository atletaRepository;
    private final TreinoRealizadoRepository treinoRealizadoRepository;
    private final ProvaRepository provaRepository;
    private final RaceProjectionSnapshotRepository snapshotRepository;
    private final RaceProjectionSkill raceProjectionSkill;
    private final AthleteProfileMapper athleteProfileMapper;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public RaceProjectionSnapshotOutputDto generate(UUID atletaId, UUID tenantId, UUID coachId,
                                                     RaceProjectionRequestDto request) {
        log.info("Gerando projeção de prova: atletaId={}, tenantId={}, provaId={}",
                atletaId, tenantId, request.provaId());

        Atleta atleta = atletaRepository.findByIdAndTenantId(atletaId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Atleta não encontrado: " + atletaId));

        Prova prova = null;
        if (request.provaId() != null) {
            prova = provaRepository.findById(request.provaId())
                    .filter(p -> p.getAtleta().getId().equals(atletaId))
                    .orElseThrow(() -> new ResourceNotFoundException("Prova não encontrada: " + request.provaId()));
        }

        List<WorkoutSummary> workouts = buildWorkoutHistory(atleta);
        List<PastRace> pastRaces = buildPastRaces(atleta);
        TrainingHistory trainingHistory = buildTrainingHistory(workouts);
        LoadProjection loadProjection = buildLoadProjection(request.loadProjection());
        CoachGoalOverride goalOverride = buildGoalOverride(request.coachGoalOverride());

        RaceProjectionInput input = new RaceProjectionInput(
                athleteProfileMapper.from(atleta),
                trainingHistory,
                loadProjection,
                pastRaces,
                request.targetDistances(),
                goalOverride,
                request.weeksToRaceOverride());

        RaceProjectionOutput output = raceProjectionSkill.execute(input);

        RaceProjectionSnapshot snapshot = buildSnapshot(atleta, prova, tenantId, coachId, request, output);
        snapshotRepository.save(snapshot);

        log.info("Projeção gerada e persistida: snapshotId={}, atletaId={}, confidence={}",
                snapshot.getId(), atletaId, resolveOverallConfidence(output));

        return toSnapshotDto(snapshot, output);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RaceProjectionSnapshotOutputDto> getHistory(UUID atletaId, UUID provaId, UUID tenantId) {
        validateAtleta(atletaId, tenantId);
        return snapshotRepository.findByAthleteIdAndRaceIdOrderByGeneratedAtDesc(atletaId, provaId)
                .stream()
                .map(s -> toSnapshotDtoFromEntity(s))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<RaceProjectionSnapshotOutputDto> getOfficial(UUID atletaId, UUID provaId, UUID tenantId) {
        validateAtleta(atletaId, tenantId);
        return snapshotRepository.findOfficialByAthleteIdAndRaceId(atletaId, provaId)
                .map(this::toSnapshotDtoFromEntity);
    }

    @Override
    @Transactional
    public RaceProjectionSnapshotOutputDto markAsOfficial(UUID snapshotId, UUID atletaId, UUID provaId,
                                                           UUID tenantId, UUID coachId) {
        validateAtleta(atletaId, tenantId);

        RaceProjectionSnapshot snapshot = snapshotRepository.findById(snapshotId)
                .filter(s -> s.getAthlete().getId().equals(atletaId))
                .orElseThrow(() -> new ResourceNotFoundException("Snapshot não encontrado: " + snapshotId));

        snapshotRepository.clearOfficialByAthleteIdAndRaceId(atletaId, provaId);

        snapshot.setOfficial(true);
        snapshot.setCoachReviewedAt(Instant.now());
        snapshotRepository.save(snapshot);

        log.info("Projeção marcada como oficial: snapshotId={}, atletaId={}", snapshotId, atletaId);

        return toSnapshotDtoFromEntity(snapshot);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AthleteProjectionViewDto> getAthleteView(UUID atletaId, UUID provaId, UUID tenantId) {
        validateAtleta(atletaId, tenantId);
        return snapshotRepository.findOfficialByAthleteIdAndRaceId(atletaId, provaId)
                .filter(s -> s.getCoachReviewedAt() != null)
                .map(this::toAthleteViewDto);
    }

    // --- helpers ---

    private void validateAtleta(UUID atletaId, UUID tenantId) {
        if (!atletaRepository.findByIdAndTenantId(atletaId, tenantId).isPresent()) {
            throw new ResourceNotFoundException("Atleta não encontrado: " + atletaId);
        }
    }

    private List<WorkoutSummary> buildWorkoutHistory(Atleta atleta) {
        LocalDate cutoff = LocalDate.now().minusWeeks(HISTORY_WEEKS);
        return treinoRealizadoRepository
                .findByAtletaAndDataTreinoGreaterThanEqualOrderByDataTreinoDesc(atleta, cutoff)
                .stream()
                // D8: cancelado não conta na carga — mesmo predicado usado por TsbService/produtores.
                .filter(TreinoRealizado::contaNaCarga)
                .filter(tr -> tr.getDistanciaKm() != null && tr.getPaceMedia() != null)
                .map(this::toWorkoutSummary)
                .collect(Collectors.toList());
    }

    private WorkoutSummary toWorkoutSummary(TreinoRealizado tr) {
        int distanceM = tr.getDistanciaKm().multiply(BigDecimal.valueOf(1000)).intValue();
        int durationSec = (int) tr.getDuracaoMin().toSeconds();
        int avgPaceSecPerKm = (int) tr.getPaceMedia().toSeconds();
        return new WorkoutSummary(
                tr.getDataTreino(),
                tr.getTipoTreinoEfetivo(),
                distanceM,
                durationSec,
                avgPaceSecPerKm,
                tr.getFcMedia(),
                tr.getTssCalculado(),
                tr.getPercepcaoEsforco());
    }

    private List<PastRace> buildPastRaces(Atleta atleta) {
        return provaRepository.findByAtletaOrderByDataProvaAsc(atleta)
                .stream()
                .filter(p -> Boolean.TRUE.equals(p.getFoiRealizada()) && p.getTempoRealizado() != null)
                .map(this::toPastRace)
                .collect(Collectors.toList());
    }

    private PastRace toPastRace(Prova prova) {
        int distanceM = resolveDistanceM(prova);
        long finishTimeSec = prova.getTempoRealizado().toSecondOfDay();
        return new PastRace(distanceM, finishTimeSec, prova.getDataProva(), RaceConditions.IDEAL);
    }

    private int resolveDistanceM(Prova prova) {
        if (prova.getDistanciaKm() != null) {
            return prova.getDistanciaKm().multiply(BigDecimal.valueOf(1000)).intValue();
        }
        return switch (prova.getDistancia()) {
            case KM_5  -> 5000;
            case KM_10 -> 10000;
            case KM_21 -> 21097;
            case KM_42 -> 42195;
        };
    }

    private TrainingHistory buildTrainingHistory(List<WorkoutSummary> workouts) {
        long qualifying = workouts.stream()
                .filter(w -> w.type() == br.com.menthoros.backend.enums.TipoTreino.TEMPO_RUN
                          || w.type() == br.com.menthoros.backend.enums.TipoTreino.LONGO)
                .count();
        DataQuality quality = qualifying >= QUALIFYING_TYPES_FULL_THRESHOLD ? DataQuality.FULL
                : qualifying >= QUALIFYING_TYPES_PARTIAL_THRESHOLD ? DataQuality.PARTIAL
                : DataQuality.SPARSE;
        int weeksAvailable = workouts.isEmpty() ? 0
                : (int) java.time.temporal.ChronoUnit.WEEKS.between(
                        workouts.stream().map(WorkoutSummary::date)
                                .min(Comparator.naturalOrder()).orElse(LocalDate.now()),
                        LocalDate.now());
        return new TrainingHistory(workouts, quality, weeksAvailable);
    }

    private LoadProjection buildLoadProjection(RaceProjectionRequestDto.LoadProjectionData d) {
        return new LoadProjection(
                d.currentCtl(), d.currentAtl(), d.currentTsb(),
                d.targetRaceDate(), d.weeksToRace(),
                d.plannedPeriodizationPhase(),
                d.projectedCtlOnRaceDay(), d.projectedTsbOnRaceDay());
    }

    private CoachGoalOverride buildGoalOverride(RaceProjectionRequestDto.CoachGoalData data) {
        if (data == null) return null;
        return new CoachGoalOverride(data.goalTimeSeconds(), data.targetDistanceM());
    }

    private RaceProjectionSnapshot buildSnapshot(Atleta atleta, Prova prova,
                                                   UUID tenantId, UUID coachId,
                                                   RaceProjectionRequestDto request,
                                                   RaceProjectionOutput output) {
        RaceProjectionSnapshot s = new RaceProjectionSnapshot();
        s.setAthlete(atleta);
        s.setRace(prova);
        s.setTenantId(tenantId);
        s.setCoachId(coachId);
        s.setWeeksToRaceAtGeneration(request.loadProjection().weeksToRace());
        s.setConfidence(resolveOverallConfidence(output).name());
        s.setProjectionsJson(serializeOutput(output));
        s.setCtlAtGeneration(BigDecimal.valueOf(request.loadProjection().currentCtl()));
        s.setTsbAtGeneration(BigDecimal.valueOf(request.loadProjection().currentTsb()));
        s.setTrainingWeeksUsed(request.loadProjection().weeksToRace());
        return s;
    }

    private String serializeOutput(RaceProjectionOutput output) {
        try {
            return objectMapper.writeValueAsString(output);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Falha ao serializar RaceProjectionOutput", e);
        }
    }

    private RaceProjectionOutput deserializeOutput(String json) {
        try {
            return objectMapper.readValue(json, RaceProjectionOutput.class);
        } catch (Exception e) {
            log.warn("Falha ao deserializar projectionsJson: {}", e.getMessage());
            return null;
        }
    }

    private br.com.menthoros.backend.skills.race.enums.ProjectionConfidence resolveOverallConfidence(
            RaceProjectionOutput output) {
        if (output.projections() == null || output.projections().isEmpty()) {
            return br.com.menthoros.backend.skills.race.enums.ProjectionConfidence.LOW;
        }
        return output.projections().values().stream()
                .map(RaceProjection::confidence)
                .min(Comparator.naturalOrder())
                .orElse(br.com.menthoros.backend.skills.race.enums.ProjectionConfidence.LOW);
    }

    private RaceProjectionSnapshotOutputDto toSnapshotDto(RaceProjectionSnapshot snapshot, RaceProjectionOutput output) {
        UUID provaId = snapshot.getRace() != null ? snapshot.getRace().getId() : null;
        return new RaceProjectionSnapshotOutputDto(
                snapshot.getId(),
                snapshot.getAthlete().getId(),
                provaId,
                snapshot.getGeneratedAt(),
                snapshot.getWeeksToRaceAtGeneration(),
                resolveOverallConfidence(output),
                snapshot.isOfficial(),
                snapshot.getCoachReviewedAt(),
                toProjectionDtoMap(output.projections()),
                output.progressionNarrative(),
                output.keyAssumptions(),
                output.coachNote(),
                toCtlForecastDto(output.ctlForecast()),
                toGoalGapDto(output.goalGapAnalysis()),
                snapshot.getRegressionRSquared(),
                snapshot.getRiegelExponentUsed(),
                snapshot.isRiegelCalibrated(),
                snapshot.getTrainingWeeksUsed());
    }

    private RaceProjectionSnapshotOutputDto toSnapshotDtoFromEntity(RaceProjectionSnapshot snapshot) {
        RaceProjectionOutput output = deserializeOutput(snapshot.getProjectionsJson());
        if (output == null) {
            return fallbackSnapshotDto(snapshot);
        }
        return toSnapshotDto(snapshot, output);
    }

    private RaceProjectionSnapshotOutputDto fallbackSnapshotDto(RaceProjectionSnapshot snapshot) {
        UUID provaId = snapshot.getRace() != null ? snapshot.getRace().getId() : null;
        return new RaceProjectionSnapshotOutputDto(
                snapshot.getId(), snapshot.getAthlete().getId(), provaId,
                snapshot.getGeneratedAt(), snapshot.getWeeksToRaceAtGeneration(),
                br.com.menthoros.backend.skills.race.enums.ProjectionConfidence.valueOf(snapshot.getConfidence()),
                snapshot.isOfficial(), snapshot.getCoachReviewedAt(),
                Map.of(), null, List.of(), null, null, null,
                snapshot.getRegressionRSquared(), snapshot.getRiegelExponentUsed(),
                snapshot.isRiegelCalibrated(), snapshot.getTrainingWeeksUsed());
    }

    private Map<Integer, RaceProjectionSnapshotOutputDto.ProjectionDto> toProjectionDtoMap(
            Map<Integer, RaceProjection> projections) {
        if (projections == null) return Map.of();
        return projections.entrySet().stream().collect(Collectors.toMap(
                Map.Entry::getKey,
                e -> toProjectionDto(e.getValue())));
    }

    private RaceProjectionSnapshotOutputDto.ProjectionDto toProjectionDto(RaceProjection p) {
        return new RaceProjectionSnapshotOutputDto.ProjectionDto(
                p.distanceM(), p.projectedTimeSeconds(), p.projectedPaceSecPerKm(),
                p.timeRangeOptimisticSec(), p.timeRangeConservativeSec(),
                p.confidence(), p.prPotential());
    }

    private RaceProjectionSnapshotOutputDto.CtlForecastDto toCtlForecastDto(CTLForecast f) {
        if (f == null) return null;
        return new RaceProjectionSnapshotOutputDto.CtlForecastDto(
                f.currentCtl(), f.projectedCtlRaceDay(), f.ctlTrend(), f.weeksToPeak());
    }

    private RaceProjectionSnapshotOutputDto.GoalGapDto toGoalGapDto(GoalGapAnalysis g) {
        if (g == null) return null;
        return new RaceProjectionSnapshotOutputDto.GoalGapDto(
                g.goalTimeSeconds(), g.projectedTimeSeconds(), g.gapSeconds(),
                g.gapPct(), g.gapAssessment(), g.coachNoteGap());
    }

    private AthleteProjectionViewDto toAthleteViewDto(RaceProjectionSnapshot snapshot) {
        RaceProjectionOutput output = deserializeOutput(snapshot.getProjectionsJson());
        UUID provaId = snapshot.getRace() != null ? snapshot.getRace().getId() : null;

        Map<Integer, AthleteProjectionViewDto.ProjectionSummary> summaries = output == null ? Map.of()
                : output.projections().entrySet().stream().collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> new AthleteProjectionViewDto.ProjectionSummary(
                                e.getValue().distanceM(),
                                e.getValue().projectedTimeSeconds(),
                                e.getValue().projectedPaceSecPerKm(),
                                e.getValue().prPotential())));

        return new AthleteProjectionViewDto(
                provaId,
                snapshot.getGeneratedAt(),
                summaries,
                output != null ? output.progressionNarrative() : null,
                output != null ? output.keyAssumptions() : List.of());
    }
}
