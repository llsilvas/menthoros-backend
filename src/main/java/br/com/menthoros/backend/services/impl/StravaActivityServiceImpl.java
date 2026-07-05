package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.dto.output.StravaSyncResponseDto;
import br.com.menthoros.backend.dto.output.StravaSyncStatusDto;
import br.com.menthoros.backend.dto.output.TreinoRealizadoOutputDto;
import br.com.menthoros.backend.events.TreinoRegistradoEvent;
import br.com.menthoros.backend.exception.DomainNotFoundException;
import br.com.menthoros.backend.exception.DomainRuleViolationException;
import br.com.menthoros.backend.mapper.TreinoMapper;
import br.com.menthoros.backend.dto.strava.StravaActivityDto;
import br.com.menthoros.backend.dto.strava.StravaSplitDto;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.EtapaRealizada;
import br.com.menthoros.backend.entity.IntegracaoExterna;
import br.com.menthoros.backend.entity.TreinoRealizado;
import br.com.menthoros.backend.enums.DiaSemana;
import br.com.menthoros.backend.enums.FonteDados;
import br.com.menthoros.backend.enums.StatusSincronizacao;
import br.com.menthoros.backend.enums.TipoTreino;
import br.com.menthoros.backend.enums.TreinoExecucaoStatus;
import br.com.menthoros.backend.exception.DuplicateResourceException;
import br.com.menthoros.backend.exception.ResourceNotFoundException;
import br.com.menthoros.backend.exception.StravaRateLimitException;
import br.com.menthoros.backend.multitenancy.TenantContext;
import br.com.menthoros.backend.services.TsbService;
import br.com.menthoros.backend.services.helper.TreinoDedupHelper;
import br.com.menthoros.backend.repository.AtletaRepository;
import br.com.menthoros.backend.repository.IntegracaoExternaRepository;
import br.com.menthoros.backend.repository.TreinoRealizadoRepository;
import br.com.menthoros.backend.services.StravaActivityService;
import br.com.menthoros.backend.services.StravaOAuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.time.format.DateTimeParseException;
import java.util.*;

@Slf4j
@Service
public class StravaActivityServiceImpl implements StravaActivityService {

    private static final FonteDados STRAVA = FonteDados.STRAVA;
    private static final Set<String> RUN_SPORT_TYPES = Set.of("Run", "TrailRun", "VirtualRun");

    private final AtletaRepository atletaRepository;
    private final TreinoRealizadoRepository treinoRealizadoRepository;
    private final IntegracaoExternaRepository integracaoExternaRepository;
    private final StravaOAuthService stravaOAuthService;
    private final TsbService tsbService;
    private final TreinoMapper treinoMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final WebClient stravaWebClient;
    private final TreinoDedupHelper treinoDedupHelper;

    public StravaActivityServiceImpl(AtletaRepository atletaRepository, TreinoRealizadoRepository treinoRealizadoRepository, IntegracaoExternaRepository integracaoExternaRepository, StravaOAuthService stravaOAuthService, TsbService tsbService, TreinoMapper treinoMapper, ApplicationEventPublisher eventPublisher, @Qualifier("stravaWebClient")WebClient stravaWebClient, TreinoDedupHelper treinoDedupHelper) {
        this.atletaRepository = atletaRepository;
        this.treinoRealizadoRepository = treinoRealizadoRepository;
        this.integracaoExternaRepository = integracaoExternaRepository;
        this.stravaOAuthService = stravaOAuthService;
        this.tsbService = tsbService;
        this.treinoMapper = treinoMapper;
        this.eventPublisher = eventPublisher;
        this.stravaWebClient = stravaWebClient;
        this.treinoDedupHelper = treinoDedupHelper;
    }

    @Transactional(readOnly = true)
    public List<StravaActivityDto> fetchActivities(String accessToken, Instant after, int page) {
        return fetchActivitiesWithHeaders(accessToken, after, page).activities();
    }

    @Transactional(readOnly = true)
    public List<StravaSplitDto> fetchActivityLaps(String accessToken, Long activityId) {
        ResponseEntity<List<StravaSplitDto>> response = stravaWebClient.get()
                .uri(uriBuilder -> uriBuilder.path("/activities/{id}/laps").build(activityId))
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .toEntityList(StravaSplitDto.class)
                .block();

        if (response == null) {
            return Collections.emptyList();
        }
        checkRateLimit(response.getHeaders());
        return response.getBody() == null ? Collections.emptyList() : response.getBody();
    }

    public TreinoRealizado mapToTreinoRealizado(StravaActivityDto activity, Atleta atleta) {
        TreinoRealizado treino = new TreinoRealizado();
        mergeActivityIntoTreino(treino, activity, atleta);
        return treino;
    }

    public EtapaRealizada mapToEtapaRealizada(StravaSplitDto split) {
        EtapaRealizada etapa = new EtapaRealizada();
        etapa.setSplitIndex(split.lapIndex());
        etapa.setOrdem(split.lapIndex() != null ? split.lapIndex() : 1);
        etapa.setDescricao("Lap " + (split.lapIndex() != null ? split.lapIndex() : 1));
        etapa.setDuracao(Duration.ofSeconds(defaultInt(split.movingTime())));
        etapa.setDistanciaKm(toKm(split.distance(), 3));
        etapa.setFcMedia(toNullableInt(split.averageHeartrate()));
        etapa.setFcMax(toNullableInt(split.maxHeartrate()));
        etapa.setVelocidadeMedia(toKmh(split.averageSpeed(), 2));
        etapa.setCadenciaMedia(sanitizeCadence(convertCadence(split.averageCadence())));
        etapa.setPotenciaMedia(toNullableInt(split.averageWatts()));

        double elevationDiff = split.elevationDifference() == null ? 0.0 : split.elevationDifference();
        if (elevationDiff >= 0) {
            etapa.setElevacaoGanhoMetros((int) Math.round(elevationDiff));
            etapa.setElevacaoPerdaMetros(0);
        } else {
            etapa.setElevacaoGanhoMetros(0);
            etapa.setElevacaoPerdaMetros((int) Math.round(Math.abs(elevationDiff)));
        }
        return etapa;
    }

    @Transactional
    public int syncActivities(UUID atletaId) {
        UUID tenantId = TenantContext.getRequiredTenantId();
        Atleta atleta = atletaRepository.findByIdAndTenantId(atletaId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Atleta não encontrado"));

        IntegracaoExterna integracao = integracaoExternaRepository
                .findActiveByAtletaIdAndPlataformaAndTenantId(atletaId, STRAVA, tenantId)
                .orElseThrow(() -> new IllegalStateException("Atleta sem integração Strava ativa"));

        String accessToken = stravaOAuthService.getValidToken(atletaId);
        Instant after = integracao.getUltimaSincronizacao() != null
                ? integracao.getUltimaSincronizacao()
                : Instant.now().minus(Duration.ofDays(90));

        return syncActivitiesInternal(atleta, integracao, accessToken, after);
    }

    @Transactional
    public void syncSingleActivityById(Atleta atleta, IntegracaoExterna integracao, Long activityId) {
        String accessToken = stravaOAuthService.getValidToken(atleta.getId());
        StravaActivityDto activity = stravaWebClient.get()
                .uri(uriBuilder -> uriBuilder.path("/activities/{id}").build(activityId))
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .bodyToMono(StravaActivityDto.class)
                .block();

        if (activity == null) {
            return;
        }

        TreinoRealizado treino = treinoRealizadoRepository
                .findByExternalIdAndAtletaId(String.valueOf(activity.id()), atleta.getId())
                .orElseGet(TreinoRealizado::new);

        mergeActivityIntoTreino(treino, activity, atleta);
        attachLaps(treino, accessToken, activity.id());
        // Task 1.2: Idempotent save handles concurrent deduplication (constraint violation retry)
        treinoDedupHelper.saveIdempotent(treino, String.valueOf(activity.id()), atleta.getId());
        integracao.setUltimaSincronizacao(Instant.now());
        atualizarTsbDia(atleta.getId(), treino.getDataTreino());
        integracaoExternaRepository.save(integracao);
    }

    private void atualizarTsbDia(UUID atletaId, LocalDate dataTreino) {
        log.info("Atualizando métricas TSB para atleta {} na data {}", atletaId, dataTreino);
        tsbService.atualizarTsbDia(atletaId, dataTreino);
    }

    @Transactional
    @Override
    public StravaSyncResponseDto syncActivitiesForAtleta(UUID atletaId, UUID tenantId) {
        IntegracaoExterna integracao = integracaoExternaRepository
                .findByAtletaIdAndPlataformaAndTenantId(atletaId, FonteDados.STRAVA, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Atleta sem integração Strava ativa"));

        if (isRecentlySynced(integracao)) {
            throw new DuplicateResourceException("Sincronização já em progresso. Aguarde conclusão.");
        }

        try {
            int imported = syncActivities(atletaId);
            integracao.setSyncActivityCount(imported);
            integracao.setUltimaSincronizacao(Instant.now());
            integracao.setLastSyncError(null);
            integracaoExternaRepository.save(integracao);

            return new StravaSyncResponseDto(imported, imported + " atividades importadas com sucesso");
        } catch (StravaRateLimitException e) {
            throw e;
        } catch (Exception e) {
            integracao.setAtivo(false);
            integracao.setLastSyncError(truncateErrorMessage(e.getMessage()));
            integracaoExternaRepository.save(integracao);
            throw e;
        }
    }

    @Transactional(readOnly = true)
    @Override
    public StravaSyncStatusDto getSyncStatus(UUID atletaId, UUID tenantId) {
        IntegracaoExterna integracao = integracaoExternaRepository
                .findByAtletaIdAndPlataformaAndTenantId(atletaId, FonteDados.STRAVA, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Integração Strava não encontrada"));

        boolean syncing = isSyncing(integracao);
        int imported = integracao.getSyncActivityCount() != null ? integracao.getSyncActivityCount() : 0;

        return new StravaSyncStatusDto(
                integracao.isAtivo(),
                syncing,
                imported,
                integracao.getLastSyncError(),
                integracao.getUltimaSincronizacao(),
                integracao.getExternalAthleteId()
        );
    }

    @Override
    @Transactional
    public TreinoRealizadoOutputDto enriquecerTreinoComStrava(UUID treinoRealizadoId, UUID tenantId) {
        TreinoRealizado treino = treinoRealizadoRepository
                .findByIdAndTenantId(treinoRealizadoId, tenantId)
                .orElseThrow(() -> new DomainNotFoundException("Treino não encontrado: " + treinoRealizadoId));

        if (treino.getFonteDados() != STRAVA || treino.getExternalId() == null) {
            throw new DomainRuleViolationException("Treino não é proveniente do Strava ou não tem ID externo");
        }

        Long activityId;
        try {
            activityId = Long.parseLong(treino.getExternalId());
        } catch (NumberFormatException e) {
            throw new DomainRuleViolationException("ID externo inválido: " + treino.getExternalId());
        }

        String accessToken = stravaOAuthService.getValidToken(treino.getAtleta().getId());

        log.info("Enriquecendo treino {} com detalhes da atividade Strava {}", treinoRealizadoId, activityId);

        StravaActivityDto detail = stravaWebClient.get()
                .uri(uriBuilder -> uriBuilder.path("/activities/{id}").build(activityId))
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .bodyToMono(StravaActivityDto.class)
                .block();

        if (detail == null) {
            throw new DomainRuleViolationException("Strava não retornou dados para a atividade " + activityId);
        }

        boolean rpePreenchido = false;
        if (detail.perceivedExertion() != null && treino.getPercepcaoEsforco() == null) {
            treino.setPercepcaoEsforco((int) Math.round(detail.perceivedExertion()));
            rpePreenchido = true;
            log.info("RPE preenchido automaticamente: {} para treino {}", treino.getPercepcaoEsforco(), treinoRealizadoId);
        }

        TreinoRealizado salvo = treinoRealizadoRepository.save(treino);

        if (rpePreenchido) {
            eventPublisher.publishEvent(new TreinoRegistradoEvent(salvo.getId(), salvo.getTenantId()));
        }

        return treinoMapper.toOutputDto(salvo);
    }

    private int syncActivitiesInternal(Atleta atleta, IntegracaoExterna integracao, String accessToken, Instant after) {
        int page = 1;
        int imported = 0;
        Instant latestProcessed = integracao.getUltimaSincronizacao();

        try {
            while (true) {
                ActivitiesPage response = fetchActivitiesWithHeaders(accessToken, after, page);
                if (response.activities().isEmpty()) {
                    break;
                }

                for (StravaActivityDto activity : response.activities()) {
                    if (activity.id() == null) {
                        continue;
                    }

                    TreinoRealizado treino = treinoRealizadoRepository
                            .findByExternalIdAndAtletaId(String.valueOf(activity.id()), atleta.getId())
                            .orElseGet(TreinoRealizado::new);

                    mergeActivityIntoTreino(treino, activity, atleta);
                    attachLaps(treino, accessToken, activity.id());
                    // Task 1.2: Idempotent save handles concurrent deduplication (constraint violation retry)
                    treinoDedupHelper.saveIdempotent(treino, String.valueOf(activity.id()), atleta.getId());
                    imported++;

                    Instant activityInstant = parseActivityInstant(activity.startDateLocal());
                    if (activityInstant != null && (latestProcessed == null || activityInstant.isAfter(latestProcessed))) {
                        latestProcessed = activityInstant;
                    }
                }
                page++;
            }
        } catch (StravaRateLimitException rateLimitException) {
            log.warn("Rate limit Strava atingido durante sync do atleta {}: {}", atleta.getId(), rateLimitException.getMessage());
        }

        if (latestProcessed != null) {
            integracao.setUltimaSincronizacao(latestProcessed);
        } else {
            integracao.setUltimaSincronizacao(Instant.now());
        }
        integracaoExternaRepository.save(integracao);
        return imported;
    }

    private void attachLaps(TreinoRealizado treino, String accessToken, Long activityId) {
        List<StravaSplitDto> splits = fetchActivityLaps(accessToken, activityId);
        List<EtapaRealizada> etapas = new ArrayList<>();
        for (int i = 0; i < splits.size(); i++) {
            EtapaRealizada etapa = mapToEtapaRealizada(splits.get(i));
            if (etapa.getOrdem() == null) {
                etapa.setOrdem(i + 1);
            }
            etapa.setTreinoRealizado(treino);
            etapas.add(etapa);
        }
        treino.getEtapasRealizadas().clear();
        treino.getEtapasRealizadas().addAll(etapas);
    }

    private ActivitiesPage fetchActivitiesWithHeaders(String accessToken, Instant after, int page) {
        ResponseEntity<List<StravaActivityDto>> response = stravaWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/athlete/activities")
                        .queryParam("after", after.getEpochSecond())
                        .queryParam("page", page)
                        .queryParam("per_page", 30)
                        .build())
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .toEntityList(StravaActivityDto.class)
                .block();

        if (response == null) {
            return new ActivitiesPage(Collections.emptyList(), HttpHeaders.EMPTY);
        }
        checkRateLimit(response.getHeaders());

        List<StravaActivityDto> runs = Objects.requireNonNull(response.getBody()).stream()
                .filter(activity -> RUN_SPORT_TYPES.contains(activity.sportType()))
                .toList();

        return new ActivitiesPage(runs, response.getHeaders());
    }

    private void mergeActivityIntoTreino(TreinoRealizado treino, StravaActivityDto activity, Atleta atleta) {
        LocalDate treinoDate = parseActivityDate(activity.startDateLocal());
        if (treinoDate == null) {
            treinoDate = LocalDate.now();
        }

        treino.setAtleta(atleta);
        treino.setTenantId(atleta.getAssessoria().getId());
        treino.setDataTreino(treinoDate);
        treino.setDiaSemana(mapDiaSemana(treinoDate));
        treino.setTipoTreino(inferTipoTreino(activity, atleta));
        treino.setDescricao(activity.name());
        treino.setDuracaoMin(Duration.ofSeconds(defaultInt(activity.movingTime())));
        treino.setDistanciaKm(toKm(activity.distance(), 2));
        treino.setRitmoAlvo(null);
        treino.setFonteDados(STRAVA);
        treino.setStatus(TreinoExecucaoStatus.REALIZADO);
        treino.setExternalId(activity.id() != null ? String.valueOf(activity.id()) : null);
        treino.setStatusSincronizacao(StatusSincronizacao.SINCRONIZADO);
        treino.setSincronizadoEm(Instant.now());
        treino.setUrlExterno(activity.id() != null ? "https://www.strava.com/activities/" + activity.id() : null);
        treino.setMetadadosSincronizacao("{\"manual\":" + Boolean.TRUE.equals(activity.manual()) + "}");
        treino.setElapsedTimeSeg(activity.elapsedTime());
        treino.setSufferScore(activity.sufferScore());
        treino.setDeviceName(activity.deviceName());
        treino.setStatusSincronizacao(StatusSincronizacao.PENDENTE);
        treino.setGearName(activity.gear() != null ? activity.gear().name() : null);

        treino.setFcMedia(sanitizeHeartRate(toNullableInt(activity.averageHeartrate()), 40, 250));
        treino.setFcMax(sanitizeHeartRate(toNullableInt(activity.maxHeartrate()), 80, 250));
        treino.setVelocidadeMedia(toKmh(activity.averageSpeed(), 2) != null ? toKmh(activity.averageSpeed(), 2).doubleValue() : 0d);
        treino.setCadenciaMedia(sanitizeCadence(convertCadence(activity.averageCadence())));
        treino.setPercepcaoEsforco(activity.perceivedExertion() != null ? (int) Math.round(activity.perceivedExertion()) : null);
        treino.setPaceMedia(calculatePace(activity.movingTime(), activity.distance()));
        treino.setElevacaoGanhoMetros(activity.totalElevationGain() != null ? (int) Math.round(activity.totalElevationGain()) : null);
        treino.setElevacaoPerdaMetros(null);
        treino.setCriadoPor("STRAVA");
    }

    private void checkRateLimit(HttpHeaders headers) {
        String remaining = headers.getFirst("X-RateLimit-Remaining");
        String usage    = headers.getFirst("X-RateLimit-Usage");
        String limit    = headers.getFirst("X-RateLimit-Limit");

        // Loga consumo real para monitoramento antes de habilitar detail fetch por atividade.
        // Formato Strava: "15min,daily"  ex.: Usage=12,345  Limit=100,1000  Remaining=88,655
        if (usage != null && limit != null) {
            log.info("strava_rate_limit usage={} limit={} remaining={}", usage, limit,
                    remaining != null ? remaining : "n/a");
        }

        if (remaining == null || remaining.isBlank()) {
            return;
        }

        int minRemaining = Integer.MAX_VALUE;
        String[] chunks = remaining.split(",");
        for (String chunk : chunks) {
            try {
                minRemaining = Math.min(minRemaining, Integer.parseInt(chunk.trim()));
            } catch (NumberFormatException ignored) {
                // ignora partes não parseáveis
            }
        }

        if (minRemaining == 0) {
            throw new StravaRateLimitException("Limite de requisições Strava atingido");
        }
    }

    private LocalDate parseActivityDate(String startDateLocal) {
        Instant instant = parseActivityInstant(startDateLocal);
        return instant != null ? instant.atZone(ZoneId.systemDefault()).toLocalDate() : null;
    }

    private Instant parseActivityInstant(String startDateLocal) {
        if (startDateLocal == null || startDateLocal.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(startDateLocal);
        } catch (DateTimeParseException ignored) {
            try {
                return OffsetDateTime.parse(startDateLocal).toInstant();
            } catch (DateTimeParseException ignoredAgain) {
                try {
                    return LocalDateTime.parse(startDateLocal).atZone(ZoneId.systemDefault()).toInstant();
                } catch (DateTimeParseException ignoredThird) {
                    return null;
                }
            }
        }
    }

    private TipoTreino inferTipoTreino(StravaActivityDto activity, Atleta atleta) {
        String sportType = activity.sportType();
        Integer workoutType = activity.workoutType();
        Integer fcMedia = toNullableInt(activity.averageHeartrate());

        if ("Run".equalsIgnoreCase(sportType) && Integer.valueOf(1).equals(workoutType)) {
            return TipoTreino.PROVA;
        }
        if ("Run".equalsIgnoreCase(sportType) && Integer.valueOf(2).equals(workoutType)) {
            return TipoTreino.LONGO;
        }
        if ("Run".equalsIgnoreCase(sportType) && Integer.valueOf(3).equals(workoutType)) {
            Integer limiar = atleta.getFcLimiarCalculada();
            if (limiar != null && fcMedia != null && fcMedia >= limiar) {
                return TipoTreino.TEMPO_RUN;
            }
            return TipoTreino.INTERVALADO;
        }
        if ("TrailRun".equalsIgnoreCase(sportType)) {
            if (activity.totalElevationGain() != null && activity.totalElevationGain() > 100d) {
                return TipoTreino.SUBIDA;
            }
            return TipoTreino.LONGO;
        }
        if ("VirtualRun".equalsIgnoreCase(sportType)) {
            return TipoTreino.CONTINUO;
        }

        int movingTime = defaultInt(activity.movingTime());
        if (movingTime >= 5400) {
            return TipoTreino.LONGO;
        }
        if (fcMedia != null && atleta.getFcLimiarCalculada() != null && fcMedia >= atleta.getFcLimiarCalculada()) {
            return TipoTreino.TEMPO_RUN;
        }
        if (movingTime <= 1800) {
            return TipoTreino.FACIL;
        }
        return TipoTreino.CONTINUO;
    }

    private DiaSemana mapDiaSemana(LocalDate date) {
        return switch (date.getDayOfWeek()) {
            case MONDAY -> DiaSemana.SEGUNDA;
            case TUESDAY -> DiaSemana.TERCA;
            case WEDNESDAY -> DiaSemana.QUARTA;
            case THURSDAY -> DiaSemana.QUINTA;
            case FRIDAY -> DiaSemana.SEXTA;
            case SATURDAY -> DiaSemana.SABADO;
            case SUNDAY -> DiaSemana.DOMINGO;
        };
    }

    private Duration calculatePace(Integer movingTime, Double distanceMeters) {
        if (movingTime == null || movingTime <= 0 || distanceMeters == null || distanceMeters <= 0d) {
            return Duration.ZERO;
        }
        double distanceKm = distanceMeters / 1000d;
        long secondsPerKm = Math.round(movingTime / distanceKm);
        return Duration.ofSeconds(Math.max(secondsPerKm, 0));
    }

    private BigDecimal toKm(Double meters, int scale) {
        if (meters == null) {
            return BigDecimal.ZERO.setScale(scale, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(meters / 1000d).setScale(scale, RoundingMode.HALF_UP);
    }

    private BigDecimal toKmh(Double metersPerSecond, int scale) {
        if (metersPerSecond == null) {
            return null;
        }
        return BigDecimal.valueOf(metersPerSecond * 3.6d).setScale(scale, RoundingMode.HALF_UP);
    }

    private Integer toNullableInt(Double value) {
        if (value == null) {
            return null;
        }
        return (int) Math.round(value);
    }

    private int defaultInt(Double value) {
        return value == null ? 0 : (int) Math.round(value);
    }

    private int defaultInt(Integer value) {
        return value == null ? 0 : value;
    }

    private int convertCadence(Double averageCadence) {
        if (averageCadence == null) {
            return 0;
        }
        return (int) Math.round(averageCadence * 2d);
    }

    private Integer sanitizeCadence(int cadence) {
        if (cadence < 60 || cadence > 200) {
            return null;
        }
        return cadence;
    }

    private Integer sanitizeHeartRate(Integer hr, int min, int max) {
        if (hr == null || hr < min || hr > max) {
            return null;
        }
        return hr;
    }

    private boolean isSyncing(IntegracaoExterna integracao) {
        if (integracao.getUltimaSincronizacao() == null) {
            return false;
        }

        Instant oneMinuteAgo = Instant.now().minus(Duration.ofMinutes(1));
        return oneMinuteAgo.isBefore(integracao.getUltimaSincronizacao()) && !integracao.isAtivo();
    }

    private boolean isRecentlySynced(IntegracaoExterna integracao) {
        if (integracao.getUltimaSincronizacao() == null) {
            return false;
        }

        Instant thirtySecondsAgo = Instant.now().minus(Duration.ofSeconds(30));
        return thirtySecondsAgo.isBefore(integracao.getUltimaSincronizacao());
    }

    private String truncateErrorMessage(String message) {
        if (message == null) {
            return "Erro desconhecido";
        }
        return message.length() <= 500 ? message : message.substring(0, 500);
    }

    private record ActivitiesPage(List<StravaActivityDto> activities, HttpHeaders headers) {
    }

}
