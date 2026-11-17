package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.domain.planner.LoadTargetResolver;
import br.com.menthoros.backend.domain.planner.SeasonPlanner;
import br.com.menthoros.backend.domain.planner.SeasonPlanner.SeasonPlan;
import br.com.menthoros.backend.domain.planner.SeasonPlanner.WeekPlan;
import br.com.menthoros.backend.dto.output.SeasonPlanResponseDto;
import br.com.menthoros.backend.entity.AthleteBaselineState;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.MetricasDiarias;
import br.com.menthoros.backend.entity.PerfilOnboardingAtleta;
import br.com.menthoros.backend.entity.PlanoTreino;
import br.com.menthoros.backend.entity.Prova;
import br.com.menthoros.backend.entity.SeasonWeek;
import br.com.menthoros.backend.entity.TreinoRealizado;
import br.com.menthoros.backend.enums.PlanoStatus;
import br.com.menthoros.backend.enums.ProvaStatus;
import br.com.menthoros.backend.enums.SeasonPlanStatus;
import br.com.menthoros.backend.enums.WeekValueSource;
import br.com.menthoros.backend.exception.DomainConflictException;
import br.com.menthoros.backend.exception.DomainNotFoundException;
import br.com.menthoros.backend.exception.DomainRuleViolationException;
import br.com.menthoros.backend.exception.SeasonPlanDisabledException;
import br.com.menthoros.backend.multitenancy.TenantContext;
import br.com.menthoros.backend.repository.AthleteBaselineStateRepository;
import br.com.menthoros.backend.repository.AtletaRepository;
import br.com.menthoros.backend.repository.MetricasDiariasRepository;
import br.com.menthoros.backend.repository.PerfilOnboardingAtletaRepository;
import br.com.menthoros.backend.repository.PlanoTreinoRepository;
import br.com.menthoros.backend.repository.ProvaRepository;
import br.com.menthoros.backend.repository.SeasonWeekRepository;
import br.com.menthoros.backend.repository.TreinoRealizadoRepository;
import br.com.menthoros.backend.services.SeasonPlanService;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Gera e lê o plano de temporada (add-season-plan-generate-tracer). O cálculo vive no
 * {@link SeasonPlanner}; aqui ficam as bases de partida (D11), o lock da prova (D13), as regras de
 * gerar de novo (D2) e a persistência.
 */
@Slf4j
@Service
public class SeasonPlanServiceImpl implements SeasonPlanService {

    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");
    private static final DateTimeFormatter DATA_BR = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    /** Constante de tempo do CTL no PMC (Banister/Coggan). */
    private static final double CTL_TIME_CONSTANT_DAYS = 42.0;
    private static final int VOLUME_WINDOW_WEEKS = 4;
    private static final List<SeasonPlanStatus> ABERTAS = List.of(SeasonPlanStatus.ACTIVE, SeasonPlanStatus.PROPOSED);

    private final AtletaRepository atletaRepository;
    private final ProvaRepository provaRepository;
    private final PlanoTreinoRepository planoTreinoRepository;
    private final SeasonWeekRepository seasonWeekRepository;
    private final MetricasDiariasRepository metricasDiariasRepository;
    private final AthleteBaselineStateRepository baselineRepository;
    private final PerfilOnboardingAtletaRepository perfilRepository;
    private final TreinoRealizadoRepository treinoRealizadoRepository;
    private final Clock clock;
    private final boolean enabled;

    public SeasonPlanServiceImpl(AtletaRepository atletaRepository,
                                 ProvaRepository provaRepository,
                                 PlanoTreinoRepository planoTreinoRepository,
                                 SeasonWeekRepository seasonWeekRepository,
                                 MetricasDiariasRepository metricasDiariasRepository,
                                 AthleteBaselineStateRepository baselineRepository,
                                 PerfilOnboardingAtletaRepository perfilRepository,
                                 TreinoRealizadoRepository treinoRealizadoRepository,
                                 Clock clock,
                                 @Value("${season-plan.enabled:false}") boolean enabled) {
        this.atletaRepository = atletaRepository;
        this.provaRepository = provaRepository;
        this.planoTreinoRepository = planoTreinoRepository;
        this.seasonWeekRepository = seasonWeekRepository;
        this.metricasDiariasRepository = metricasDiariasRepository;
        this.baselineRepository = baselineRepository;
        this.perfilRepository = perfilRepository;
        this.treinoRealizadoRepository = treinoRealizadoRepository;
        this.clock = clock;
        this.enabled = enabled;
    }

    @Override
    @Transactional
    public Generation generate(UUID atletaId) {
        exigirHabilitado();
        UUID tenantId = TenantContext.getRequiredTenantId();
        log.info("Gerando temporada: atletaId={}, tenantId={}", atletaId, tenantId);
        Atleta atleta = buscarAtleta(atletaId, tenantId);
        LocalDate hoje = LocalDate.now(clock.withZone(FUSO));

        Prova prova = travarProvaAlvo(atletaId, tenantId);
        if (prova.getDataProva() == null) {
            throw new DomainRuleViolationException("A prova-alvo não tem data definida");
        }
        int semanas = SeasonPlanner.totalWeeks(prova.getDataProva(), hoje);
        if (semanas < 2) {
            throw new DomainRuleViolationException("A prova-alvo precisa estar a pelo menos 2 semanas");
        }

        List<PlanoTreino> abertas = planoTreinoRepository
                .findByAtletaIdAndAssessoriaIdAndSeasonStatusIn(atletaId, tenantId, ABERTAS);
        if (abertas.stream().anyMatch(p -> p.getSeasonStatus() == SeasonPlanStatus.ACTIVE)) {
            throw new DomainConflictException("O atleta já tem uma temporada ativa; ajuste é replanejamento");
        }
        Optional<PlanoTreino> mesmaProva = abertas.stream()
                .filter(p -> p.getProva() != null && prova.getId().equals(p.getProva().getId()))
                .findFirst();
        if (mesmaProva.isPresent()) {
            log.info("Temporada PROPOSED já existe: id={}", mesmaProva.get().getId());
            return new Generation(toDto(mesmaProva.get(), tenantId), false);
        }
        if (!abertas.isEmpty()) {
            throw new DomainConflictException("O atleta já tem uma temporada proposta para outra prova");
        }

        double ctl = resolverCtl(atletaId, tenantId, hoje);
        BigDecimal volume = resolverVolume(atletaId, tenantId, hoje);
        SeasonPlan plano = SeasonPlanner.plan(prova.getDistancia(), prova.getDistanciaKm(),
                prova.getDataProva(), hoje, ctl, volume);

        PlanoTreino temporada = planoTreinoRepository.save(novaTemporada(atleta, prova, plano, ctl, hoje));
        List<SeasonWeek> weeks = plano.weeks().stream().map(w -> novaSemana(temporada, tenantId, w)).toList();
        seasonWeekRepository.saveAll(weeks);
        log.info("Temporada gerada: id={}, semanas={}, curta={}", temporada.getId(), semanas, plano.shortPreparation());
        return new Generation(toDto(temporada, weeks), true);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SeasonPlanResponseDto> findCurrent(UUID atletaId) {
        exigirHabilitado();
        UUID tenantId = TenantContext.getRequiredTenantId();
        buscarAtleta(atletaId, tenantId);
        return planoTreinoRepository.findByAtletaIdAndAssessoriaIdAndSeasonStatusIn(atletaId, tenantId, ABERTAS)
                .stream()
                .min(Comparator.comparing(p -> p.getSeasonStatus() == SeasonPlanStatus.ACTIVE ? 0 : 1))
                .map(p -> toDto(p, tenantId));
    }

    private void exigirHabilitado() {
        if (!enabled) {
            throw new SeasonPlanDisabledException();
        }
    }

    private Atleta buscarAtleta(UUID atletaId, UUID tenantId) {
        return atletaRepository.findByIdAndTenantId(atletaId, tenantId)
                .orElseThrow(() -> new DomainNotFoundException("Atleta não encontrado: " + atletaId));
    }

    /**
     * Trava a prova-alvo e confere de novo, já com o lock, que ela continua alvo e não foi cancelada
     * (D13) — cancelar mantém {@code provaAlvo = true}, então só o status denuncia o cancelamento.
     */
    private Prova travarProvaAlvo(UUID atletaId, UUID tenantId) {
        UUID provaId = provaRepository.findTargetRaceIds(atletaId, tenantId).stream().findFirst()
                .orElseThrow(() -> new DomainRuleViolationException("O atleta não tem prova-alvo"));
        return provaRepository.findByIdAndTenantIdForUpdate(provaId, tenantId)
                .filter(p -> p.isProvaAlvo() && p.getStatusProva() != ProvaStatus.CANCELADA)
                .orElseThrow(() -> new DomainRuleViolationException("A prova deixou de ser prova-alvo ou foi cancelada"));
    }

    /**
     * CTL de partida (D11): o último do PMC projetado até hoje com o decaimento {@code e^(−dias/42)},
     * se o histórico tem ao menos 42 dias; senão o {@code ctlEstimado} do onboarding, limitado ao
     * teto do cold-start.
     */
    private double resolverCtl(UUID atletaId, UUID tenantId, LocalDate hoje) {
        Optional<MetricasDiarias> ultima = metricasDiariasRepository.findLatestByAtletaId(atletaId);
        if (ultima.isPresent()) {
            LocalDate primeira = metricasDiariasRepository.findDataPrimeiraMetrica(atletaId);
            if (primeira != null && ChronoUnit.DAYS.between(primeira, hoje) >= CTL_TIME_CONSTANT_DAYS) {
                long dias = Math.max(0, ChronoUnit.DAYS.between(ultima.get().getData(), hoje));
                double ctl = Objects.requireNonNullElse(ultima.get().getCtl(), 0.0)
                        * Math.exp(-dias / CTL_TIME_CONSTANT_DAYS);
                if (ctl > 0) {
                    return ctl;
                }
            }
        }
        return baselineRepository.findByAtletaIdAndTenantId(atletaId, tenantId)
                .map(AthleteBaselineState::getCtlEstimado)
                .filter(c -> c > 0)
                .map(c -> Math.min(c, LoadTargetResolver.TETO_CTL_COLD_START))
                .orElseThrow(() -> new DomainRuleViolationException(
                        "Sem base de carga (CTL): sincronize treinos ou complete o onboarding"));
    }

    /**
     * Volume de partida (D11): média de km das 4 semanas completas antes da corrente, só treinos que
     * contam na carga; sem treino, o {@code volumeSemanalMax} do onboarding.
     */
    private BigDecimal resolverVolume(UUID atletaId, UUID tenantId, LocalDate hoje) {
        LocalDate segunda = hoje.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        BigDecimal soma = treinoRealizadoRepository.findByAtletaIdAndTenantIdAndDataTreinoBetween(
                        atletaId, tenantId, segunda.minusWeeks(VOLUME_WINDOW_WEEKS), segunda.minusDays(1))
                .stream()
                .filter(TreinoRealizado::contaNaCarga)
                .map(TreinoRealizado::getDistanciaKm)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (soma.signum() > 0) {
            return soma.divide(BigDecimal.valueOf(VOLUME_WINDOW_WEEKS), 1, RoundingMode.HALF_UP);
        }
        return perfilRepository.findByAtletaIdAndTenantId(atletaId, tenantId)
                .map(PerfilOnboardingAtleta::getVolumeSemanalMax)
                .filter(v -> v > 0)
                .map(BigDecimal::valueOf)
                .orElseThrow(() -> new DomainRuleViolationException(
                        "Sem base de volume: sincronize treinos ou informe o volume semanal no onboarding"));
    }

    private static PlanoTreino novaTemporada(Atleta atleta, Prova prova, SeasonPlan plano, double ctl, LocalDate hoje) {
        String nomeProva = prova.getNomeProva();
        PlanoTreino p = new PlanoTreino();
        p.setAtleta(atleta);
        p.setProva(prova);
        p.setAssessoria(atleta.getAssessoria());
        p.setNome("Temporada " + nomeProva);
        p.setDescricao("Plano de temporada até " + nomeProva + " em " + prova.getDataProva().format(DATA_BR));
        p.setObjetivo("Chegar preparado a " + nomeProva);
        p.setDataInicio(plano.weeks().get(0).weekStart());
        p.setStatus(PlanoStatus.PLANEJADO);
        p.setSeasonStatus(SeasonPlanStatus.PROPOSED);
        p.setVersion(1);
        p.setTotalWeeks(plano.weeks().size());
        p.setShortPreparation(plano.shortPreparation());
        p.setInitialCtl(ctl);
        p.setPeakVolumeKm(plano.weeks().stream().map(WeekPlan::targetVolumeKm)
                .max(Comparator.naturalOrder()).orElseThrow());
        return p;
    }

    private static SeasonWeek novaSemana(PlanoTreino temporada, UUID tenantId, WeekPlan w) {
        SeasonWeek s = new SeasonWeek();
        s.setTenantId(tenantId);
        s.setSeasonPlan(temporada);
        s.setWeekIndex(w.weekIndex());
        s.setWeekStart(w.weekStart());
        s.setPhase(w.phase());
        s.setTargetVolumeKm(w.targetVolumeKm());
        s.setTargetTss(w.targetTss());
        s.setValueSource(WeekValueSource.ENGINE);
        return s;
    }

    private SeasonPlanResponseDto toDto(PlanoTreino p, UUID tenantId) {
        return toDto(p, seasonWeekRepository.findBySeasonPlanIdAndTenantIdOrderByWeekIndexAsc(p.getId(), tenantId));
    }

    private static SeasonPlanResponseDto toDto(PlanoTreino p, List<SeasonWeek> weeks) {
        @Nullable Prova prova = p.getProva();
        return new SeasonPlanResponseDto(
                p.getId(),
                p.getSeasonStatus(),
                prova == null ? null : prova.getId(),
                prova == null ? null : prova.getNomeProva(),
                prova == null ? null : prova.getDataProva(),
                Boolean.TRUE.equals(p.getShortPreparation()),
                p.getTotalWeeks(),
                p.getInitialCtl(),
                p.getPeakVolumeKm(),
                weeks.stream().map(w -> new SeasonPlanResponseDto.Week(
                        w.getWeekIndex() == null ? 0 : w.getWeekIndex(),
                        w.getWeekStart(),
                        w.getPhase(),
                        w.getTargetVolumeKm(),
                        w.getTargetTss(),
                        w.isDeload(),
                        w.getLongRunKm(),
                        w.getValueSource())).toList());
    }
}
