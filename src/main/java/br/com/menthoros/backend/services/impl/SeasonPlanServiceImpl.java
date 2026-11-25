package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.domain.planner.LoadTargetResolver;
import br.com.menthoros.backend.domain.planner.SeasonPlanValidator;
import br.com.menthoros.backend.domain.planner.SeasonPlanner;
import br.com.menthoros.backend.domain.planner.SeasonPlanner.RaceMilestone;
import br.com.menthoros.backend.domain.planner.SeasonPlanner.SeasonPlan;
import br.com.menthoros.backend.domain.planner.SeasonPlanner.WeekPlan;
import br.com.menthoros.backend.domain.planner.SeasonWeekLock;
import br.com.menthoros.backend.domain.planner.TrainingPhase;
import br.com.menthoros.backend.domain.planner.WeeklyVolume;
import br.com.menthoros.backend.dto.output.AthleteSeasonPlanDto;
import br.com.menthoros.backend.dto.output.SeasonPlanResponseDto;
import br.com.menthoros.backend.entity.AthleteBaselineState;
import br.com.menthoros.backend.entity.Usuario;
import br.com.menthoros.backend.repository.UsuarioRepository;
import br.com.menthoros.backend.security.AuthenticatedAtletaResolver;
import br.com.menthoros.backend.security.AuthenticatedPrincipalResolver;
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
import br.com.menthoros.backend.enums.StartVolumeSource;
import br.com.menthoros.backend.enums.TipoTreino;
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
import br.com.menthoros.backend.services.helper.SeasonPlanResponseMapper;
import br.com.menthoros.backend.services.helper.SeasonPlanVersioning;
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
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Gera e lê o plano de temporada (add-season-plan-generate-tracer). O cálculo vive no
 * {@link SeasonPlanner}; aqui ficam as bases de partida (D11), o lock da prova (D13), as regras de
 * gerar de novo (D2) e a persistência.
 */
@Slf4j
@Service
public class SeasonPlanServiceImpl implements SeasonPlanService {

    private static final DateTimeFormatter DATA_BR = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    /** Constante de tempo do CTL no PMC (Banister/Coggan). */
    private static final double CTL_TIME_CONSTANT_DAYS = 42.0;
    private static final int VOLUME_WINDOW_WEEKS = 8;
    private static final int LONG_RUN_WINDOW_WEEKS = 6;
    private static final int NOME_MAX = 100;
    private static final int MOTIVO_MAX = 255;
    private static final List<SeasonPlanStatus> ABERTAS = List.of(SeasonPlanStatus.ACTIVE, SeasonPlanStatus.PROPOSED);
    private static final List<SeasonPlanStatus> SO_ATIVA = List.of(SeasonPlanStatus.ACTIVE);

    private final AtletaRepository atletaRepository;
    private final ProvaRepository provaRepository;
    private final PlanoTreinoRepository planoTreinoRepository;
    private final SeasonWeekRepository seasonWeekRepository;
    private final MetricasDiariasRepository metricasDiariasRepository;
    private final AthleteBaselineStateRepository baselineRepository;
    private final PerfilOnboardingAtletaRepository perfilRepository;
    private final TreinoRealizadoRepository treinoRealizadoRepository;
    private final UsuarioRepository usuarioRepository;
    private final AuthenticatedPrincipalResolver principalResolver;
    private final AuthenticatedAtletaResolver atletaResolver;
    private final SeasonPlanResponseMapper responseMapper;
    private final SeasonPlanVersioning versioning;
    private final Clock clock;
    private final boolean enabled;
    // A graduação da calibração só é avaliada com o shadow do planner (PlannerShadowService); sem ele
    // ninguém gradua, e bloquear a temporada por calibração seria bloquear para sempre.
    private final boolean plannerShadowEnabled;

    public SeasonPlanServiceImpl(AtletaRepository atletaRepository,
                                 ProvaRepository provaRepository,
                                 PlanoTreinoRepository planoTreinoRepository,
                                 SeasonWeekRepository seasonWeekRepository,
                                 MetricasDiariasRepository metricasDiariasRepository,
                                 AthleteBaselineStateRepository baselineRepository,
                                 PerfilOnboardingAtletaRepository perfilRepository,
                                 TreinoRealizadoRepository treinoRealizadoRepository,
                                 UsuarioRepository usuarioRepository,
                                 AuthenticatedPrincipalResolver principalResolver,
                                 AuthenticatedAtletaResolver atletaResolver,
                                 SeasonPlanResponseMapper responseMapper,
                                 SeasonPlanVersioning versioning,
                                 Clock clock,
                                 @Value("${season-plan.enabled:false}") boolean enabled,
                                 @Value("${planner-engine.shadow:false}") boolean plannerShadowEnabled) {
        this.atletaRepository = atletaRepository;
        this.provaRepository = provaRepository;
        this.planoTreinoRepository = planoTreinoRepository;
        this.seasonWeekRepository = seasonWeekRepository;
        this.metricasDiariasRepository = metricasDiariasRepository;
        this.baselineRepository = baselineRepository;
        this.perfilRepository = perfilRepository;
        this.treinoRealizadoRepository = treinoRealizadoRepository;
        this.usuarioRepository = usuarioRepository;
        this.principalResolver = principalResolver;
        this.atletaResolver = atletaResolver;
        this.responseMapper = responseMapper;
        this.versioning = versioning;
        this.clock = clock;
        this.enabled = enabled;
        this.plannerShadowEnabled = plannerShadowEnabled;
    }

    @Override
    @Transactional
    public Generation generate(UUID atletaId) {
        exigirHabilitado();
        UUID tenantId = TenantContext.getRequiredTenantId();
        log.info("Gerando temporada: atletaId={}, tenantId={}", atletaId, tenantId);
        Atleta atleta = buscarAtleta(atletaId, tenantId);
        // Antes do retorno idempotente: atleta em calibração recebe 422 mesmo com proposta existente.
        exigirForaDeCalibracao(atletaId, tenantId);
        LocalDate hoje = LocalDate.now(clock.withZone(SeasonWeekLock.FUSO));

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
            return new Generation(responseMapper.toDto(mesmaProva.get(), tenantId, null), false);
        }
        if (!abertas.isEmpty()) {
            throw new DomainConflictException("O atleta já tem uma temporada proposta para outra prova");
        }

        double ctl = resolverCtl(atletaId, tenantId, hoje);
        StartVolume partida = resolverVolume(atletaId, tenantId, hoje);
        SeasonPlan plano = SeasonPlanner.plan(prova.getDistancia(), prova.getDistanciaKm(),
                prova.getDataProva(), hoje, ctl, partida.km(), partida.longRunKm(),
                preparatorias(atletaId, tenantId, prova, hoje));

        PlanoTreino temporada = planoTreinoRepository.save(novaTemporada(atleta, prova, plano, ctl, partida));
        List<SeasonWeek> weeks = plano.weeks().stream().map(w -> novaSemana(temporada, tenantId, w)).toList();
        seasonWeekRepository.saveAll(weeks);
        SeasonPlanResponseDto dto = responseMapper.toDto(temporada, weeks, tenantId, null);
        log.info("Temporada gerada: id={}, semanas={}, curta={}", temporada.getId(), semanas, plano.shortPreparation());
        // Métrica T9: % de propostas com alerta, por regra.
        log.info("season-plan.generated tenantId={} seasonPlanId={} alerts={}", tenantId, temporada.getId(),
                dto.validations().stream().collect(Collectors.groupingBy(
                        SeasonPlanResponseDto.Validation::rule, TreeMap::new, Collectors.counting())));
        return new Generation(dto, true);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SeasonPlanResponseDto> findCurrent(UUID atletaId) {
        exigirHabilitado();
        UUID tenantId = TenantContext.getRequiredTenantId();
        buscarAtleta(atletaId, tenantId);
        List<PlanoTreino> abertas = planoTreinoRepository
                .findByAtletaIdAndAssessoriaIdAndSeasonStatusIn(atletaId, tenantId, ABERTAS);
        return abertas.stream()
                .min(Comparator.comparing(p -> p.getSeasonStatus() == SeasonPlanStatus.ACTIVE ? 0 : 1))
                .map(p -> responseMapper.toDto(p, tenantId, SeasonPlanVersioning.rascunhoAberto(p, abertas)));
    }

    @Override
    @Transactional
    public SeasonPlanResponseDto approve(UUID atletaId, UUID seasonPlanId, @Nullable String reason) {
        exigirHabilitado();
        UUID tenantId = TenantContext.getRequiredTenantId();
        log.info("Aprovando temporada: id={}, atletaId={}, tenantId={}", seasonPlanId, atletaId, tenantId);
        // A5: trava a prova antes de carregar a temporada, como quem cancela ou troca a alvo (D13). O mesmo
        // lock do ajuste (SeasonPlanVersioning.travar): aprovar e ajustar o mesmo atleta se serializam.
        PlanoTreino temporada = versioning.travar(atletaId, seasonPlanId, tenantId);
        Optional<Prova> prova = Optional.ofNullable(temporada.getProva());

        // O status vem antes da prova: cancelar ou trocar a alvo arquiva a temporada, e isso é 409 (A1).
        if (temporada.getSeasonStatus() == SeasonPlanStatus.ACTIVE) {
            log.info("Temporada já aprovada: id={}", seasonPlanId);
            return responseMapper.toDto(temporada, tenantId, null);
        }
        if (temporada.getSeasonStatus() != SeasonPlanStatus.PROPOSED) {
            throw new DomainConflictException("Só uma temporada proposta pode ser aprovada");
        }
        if (prova.filter(p -> p.isProvaAlvo() && p.getStatusProva() != ProvaStatus.CANCELADA).isEmpty()) {
            throw new DomainRuleViolationException("A prova deixou de ser prova-alvo ou foi cancelada");
        }
        // Proposta gerada antes de a calibração começar não vira ACTIVE enquanto o atleta calibra.
        exigirForaDeCalibracao(atletaId, tenantId);
        if (temporada.getBaseSeasonPlanId() != null) {
            // Ajuste (v2+): a troca com a ACTIVE anterior é do versionamento (add-season-plan-adjust-v2).
            versioning.ativarRascunho(temporada, exigirMotivo(reason), usuarioAutenticado(tenantId), tenantId);
            log.info("Ajuste aprovado: id={}, version={}", seasonPlanId, temporada.getVersion());
            return responseMapper.toDto(temporada, tenantId, null);
        }
        if (!planoTreinoRepository.findByAtletaIdAndAssessoriaIdAndSeasonStatusIn(atletaId, tenantId, SO_ATIVA).isEmpty()) {
            throw new DomainConflictException("O atleta já tem uma temporada ativa");
        }
        temporada.setSeasonStatus(SeasonPlanStatus.ACTIVE);
        temporada.setApprovedBy(usuarioAutenticado(tenantId));
        // Postgres guarda microssegundos: sem truncar, esta resposta difere da releitura (idempotente/GET).
        temporada.setApprovedAt(clock.instant().truncatedTo(ChronoUnit.MICROS));
        // flush aqui: se o índice de uma ACTIVE por atleta recusar, o 409 sai desta chamada, não do commit.
        planoTreinoRepository.saveAndFlush(temporada);
        log.info("Temporada aprovada: id={}, approvedBy={}", seasonPlanId, temporada.getApprovedBy());
        return responseMapper.toDto(temporada, tenantId, null);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AthleteSeasonPlanDto> findForAuthenticatedAthlete() {
        if (!enabled) {
            return Optional.empty();
        }
        UUID tenantId = TenantContext.getRequiredTenantId();
        UUID atletaId = atletaResolver.resolverAtletaIdAtual();
        LocalDate hoje = LocalDate.now(clock.withZone(SeasonWeekLock.FUSO));
        return planoTreinoRepository.findByAtletaIdAndAssessoriaIdAndSeasonStatusIn(atletaId, tenantId, SO_ATIVA)
                .stream()
                .filter(p -> p.getProva() != null && p.getProva().getDataProva() != null
                        && !p.getProva().getDataProva().isBefore(hoje))
                .findFirst()
                .map(p -> paraAtleta(p, tenantId, hoje));
    }

    private AthleteSeasonPlanDto paraAtleta(PlanoTreino p, UUID tenantId, LocalDate hoje) {
        Prova prova = Objects.requireNonNull(p.getProva());
        List<SeasonWeek> weeks = seasonWeekRepository.findBySeasonPlanIdAndTenantIdOrderByWeekIndexAsc(p.getId(), tenantId);
        TrainingPhase fase = weeks.stream()
                .filter(w -> SeasonWeekLock.contains(w.getWeekStart(), hoje))
                .map(SeasonWeek::getPhase)
                .findFirst()
                .orElse(null);
        List<AthleteSeasonPlanDto.Milestone> marcos = responseMapper.marcos(weeks, tenantId).stream()
                .filter(m -> !m.date().isBefore(hoje))
                .map(m -> new AthleteSeasonPlanDto.Milestone(m.name(), m.date(), m.distance()))
                .toList();
        return new AthleteSeasonPlanDto(prova.getNomeProva(), prova.getDataProva(), prova.getDistancia(),
                ChronoUnit.DAYS.between(hoje, prova.getDataProva()), fase, marcos);
    }

    /** {@code Usuario.id} de quem aprovou, nunca o {@code sub} do Keycloak (padrão de SugestaoCoachServiceImpl). */
    private UUID usuarioAutenticado(UUID tenantId) {
        return usuarioRepository.findByKeycloakIdAndAssessoria_Id(principalResolver.getCurrentSubject(), tenantId)
                .map(Usuario::getId)
                .orElseThrow(() -> new DomainNotFoundException("Usuário autenticado não encontrado no tenant atual"));
    }

    /** Motivo do ajuste: obrigatório e no tamanho de {@code version_reason} (V116, VARCHAR(255)). */
    private static String exigirMotivo(@Nullable String reason) {
        if (reason == null || reason.isBlank()) {
            throw new DomainRuleViolationException("Informe o motivo do ajuste da temporada");
        }
        String motivo = reason.trim();
        if (motivo.length() > MOTIVO_MAX) {
            throw new DomainRuleViolationException("O motivo do ajuste tem no máximo " + MOTIVO_MAX + " caracteres");
        }
        return motivo;
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

    /** Mesma regra do status de calibração do onboarding: em calibração = {@code calibracaoIniciadaEm} preenchida. */
    private void exigirForaDeCalibracao(UUID atletaId, UUID tenantId) {
        if (!plannerShadowEnabled) {
            return;
        }
        boolean emCalibracao = baselineRepository.findByAtletaIdAndTenantId(atletaId, tenantId)
                .map(AthleteBaselineState::getCalibracaoIniciadaEm)
                .isPresent();
        if (emCalibracao) {
            log.info("Temporada recusada: atleta em calibração, atletaId={}, tenantId={}", atletaId, tenantId);
            throw new DomainRuleViolationException(
                    "O atleta está em calibração: a temporada fica disponível quando ele concluir a calibração");
        }
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

    /** Ponto de partida da temporada e a evidência dele (season-plan-start-volume, D1/D2). */
    private record StartVolume(BigDecimal km, StartVolumeSource source, List<WeeklyVolume> history,
                               @Nullable BigDecimal longRunKm) {
    }

    /**
     * Volume de partida (D1): mediana dos km das 8 semanas completas antes da corrente, semana sem
     * treino = 0, só treinos que contam na carga; mediana zero, o {@code volumeSemanalMax} do
     * onboarding. Longão de partida (D2): maior treino que conta, fora PROVA, nas 6 semanas.
     */
    private StartVolume resolverVolume(UUID atletaId, UUID tenantId, LocalDate hoje) {
        LocalDate segunda = hoje.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate inicio = segunda.minusWeeks(VOLUME_WINDOW_WEEKS);
        List<TreinoRealizado> treinos = treinoRealizadoRepository.findByAtletaIdAndTenantIdAndDataTreinoBetween(
                        atletaId, tenantId, inicio, segunda.minusDays(1))
                .stream()
                .filter(TreinoRealizado::contaNaCarga)
                .filter(t -> t.getDistanciaKm() != null && t.getDataTreino() != null)
                .toList();

        Map<LocalDate, BigDecimal> porSemana = new TreeMap<>();
        for (int i = 0; i < VOLUME_WINDOW_WEEKS; i++) {
            porSemana.put(inicio.plusWeeks(i), BigDecimal.ZERO);
        }
        treinos.forEach(t -> porSemana.merge(
                t.getDataTreino().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)),
                t.getDistanciaKm(), BigDecimal::add));
        List<WeeklyVolume> historico = porSemana.entrySet().stream()
                .map(e -> new WeeklyVolume(e.getKey().toString(), e.getValue().setScale(1, RoundingMode.HALF_UP)))
                .toList();

        LocalDate inicioLongao = segunda.minusWeeks(LONG_RUN_WINDOW_WEEKS);
        @Nullable BigDecimal longao = treinos.stream()
                .filter(t -> !t.getDataTreino().isBefore(inicioLongao))
                .filter(t -> t.getTipoTreino() != TipoTreino.PROVA)
                .map(TreinoRealizado::getDistanciaKm)
                .filter(km -> km.signum() > 0)
                .max(Comparator.naturalOrder())
                .map(km -> km.setScale(1, RoundingMode.HALF_UP))
                .orElse(null);

        List<BigDecimal> ordenados = porSemana.values().stream().sorted().toList();
        int meio = VOLUME_WINDOW_WEEKS / 2;
        BigDecimal mediana = ordenados.get(meio - 1).add(ordenados.get(meio))
                .divide(BigDecimal.TWO, 1, RoundingMode.HALF_UP);
        // Volume ínfimo arredonda para 0,0 e o motor rejeita base zero: cai no onboarding, como sem treino.
        if (mediana.signum() > 0) {
            return new StartVolume(mediana, StartVolumeSource.MEDIAN, historico, longao);
        }
        BigDecimal onboarding = perfilRepository.findByAtletaIdAndTenantId(atletaId, tenantId)
                .map(PerfilOnboardingAtleta::getVolumeSemanalMax)
                .filter(v -> v > 0)
                .map(BigDecimal::valueOf)
                .orElseThrow(() -> new DomainRuleViolationException(
                        "Sem base de volume: sincronize treinos ou informe o volume semanal no onboarding"));
        return new StartVolume(onboarding, StartVolumeSource.ONBOARDING, historico, longao);
    }

    private static PlanoTreino novaTemporada(Atleta atleta, Prova prova, SeasonPlan plano, double ctl,
                                             StartVolume partida) {
        String nomeProva = prova.getNomeProva();
        PlanoTreino p = new PlanoTreino();
        p.setAtleta(atleta);
        p.setProva(prova);
        p.setAssessoria(atleta.getAssessoria());
        // tb_plano_treino.nome é VARCHAR(100) e nome_prova também chega a 100: o prefixo estouraria.
        String nome = "Temporada " + nomeProva;
        p.setNome(nome.length() > NOME_MAX ? nome.substring(0, NOME_MAX) : nome);
        p.setDescricao("Plano de temporada até " + nomeProva + " em " + prova.getDataProva().format(DATA_BR));
        p.setObjetivo("Chegar preparado a " + nomeProva);
        p.setDataInicio(plano.weeks().get(0).weekStart());
        p.setStatus(PlanoStatus.PLANEJADO);
        p.setSeasonStatus(SeasonPlanStatus.PROPOSED);
        p.setVersion(1);
        p.setTotalWeeks(plano.weeks().size());
        p.setShortPreparation(plano.shortPreparation());
        p.setInitialCtl(ctl);
        BigDecimal volume = partida.km().setScale(1, RoundingMode.HALF_UP);
        p.setInitialVolumeKm(volume);
        p.setProposedVolumeKm(volume);
        p.setInitialVolumeSource(partida.source());
        p.setInitialVolumeHistory(partida.history());
        p.setInitialLongRunKm(partida.longRunKm());
        p.setPeakVolumeKm(plano.weeks().stream().map(WeekPlan::targetVolumeKm)
                .max(Comparator.naturalOrder()).orElseThrow());
        return p;
    }

    /**
     * Provas preparatórias candidatas a marco (T6): futuras, não canceladas, que não são a alvo. Chamado
     * depois do lock da alvo — carregar {@code Prova} antes deixaria a cópia travada sem reler (D13).
     */
    private List<RaceMilestone> preparatorias(UUID atletaId, UUID tenantId, Prova alvo, LocalDate hoje) {
        return provaRepository.findUpcomingByAtletaIdAndTenantId(atletaId, tenantId, hoje).stream()
                .filter(p -> !p.isProvaAlvo() && !alvo.getId().equals(p.getId()) && p.getDataProva() != null)
                .map(p -> new RaceMilestone(p.getId(), p.getDataProva(), Objects.requireNonNullElse(
                        SeasonPlanner.distanciaEfetivaKm(p.getDistancia(), p.getDistanciaKm()), BigDecimal.ZERO)))
                .toList();
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
        s.setDeload(w.deload());
        s.setLongRunKm(w.longRunKm());
        s.setMilestoneRaceId(w.milestoneRaceId());
        s.setValueSource(WeekValueSource.ENGINE);
        return s;
    }
}
