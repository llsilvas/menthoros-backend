package br.com.menthoros.backend.services.onboarding.impl;

import br.com.menthoros.backend.domain.planner.AthleteBaseline;
import br.com.menthoros.backend.domain.planner.AthleteConstraints;
import br.com.menthoros.backend.domain.planner.InjuryRiskLevel;
import br.com.menthoros.backend.domain.planner.OnboardingContext;
import br.com.menthoros.backend.domain.planner.PlanningPolicy;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.AthleteBaselineHistory;
import br.com.menthoros.backend.entity.AthleteBaselineState;
import br.com.menthoros.backend.entity.PerfilOnboardingAtleta;
import br.com.menthoros.backend.entity.Prova;
import br.com.menthoros.backend.entity.TreinoRealizado;
import br.com.menthoros.backend.enums.DispositivoMarca;
import br.com.menthoros.backend.enums.DistanciaProva;
import br.com.menthoros.backend.enums.ProvaStatus;
import br.com.menthoros.backend.enums.TipoProva;
import br.com.menthoros.backend.exception.DomainConflictException;
import br.com.menthoros.backend.exception.DomainNotFoundException;
import br.com.menthoros.backend.repository.AthleteBaselineHistoryRepository;
import br.com.menthoros.backend.repository.AthleteBaselineStateRepository;
import br.com.menthoros.backend.repository.AtletaRepository;
import br.com.menthoros.backend.repository.PerfilOnboardingAtletaRepository;
import br.com.menthoros.backend.repository.ProvaRepository;
import br.com.menthoros.backend.repository.TreinoRealizadoRepository;
import br.com.menthoros.backend.services.onboarding.ActivityDedupService;
import br.com.menthoros.backend.services.onboarding.ActivityNormalizer;
import br.com.menthoros.backend.services.onboarding.BaselineCalculator;
import br.com.menthoros.backend.services.onboarding.BaselineResult;
import br.com.menthoros.backend.services.onboarding.CalibrationEvaluation;
import br.com.menthoros.backend.services.onboarding.CalibrationService;
import br.com.menthoros.backend.services.onboarding.ConfidenceScoreResult;
import br.com.menthoros.backend.services.onboarding.ConfidenceScorer;
import br.com.menthoros.backend.services.onboarding.ConfidenceScorerInput;
import br.com.menthoros.backend.services.onboarding.ConfidenceTier;
import br.com.menthoros.backend.services.onboarding.NormalizedActivity;
import br.com.menthoros.backend.services.onboarding.OnboardingDraftInput;
import br.com.menthoros.backend.services.onboarding.OnboardingService;
import br.com.menthoros.backend.services.onboarding.PlanningPolicyResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Implementacao do OnboardingService (design.md, athlete-onboarding-baseline) —
 * orquestrador fino: cada colaborador ja e testado isoladamente, este servico
 * so compoe a chamada e monta o {@code OnboardingContext}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OnboardingServiceImpl implements OnboardingService {

    private static final int JANELA_PROVA_RECENTE_DIAS = 90;
    private static final String EVENTO_RECALCULO_ONBOARDING_CONTEXT = "RECALCULO_ONBOARDING_CONTEXT";

    private final AtletaRepository atletaRepository;
    private final TreinoRealizadoRepository treinoRealizadoRepository;
    private final PerfilOnboardingAtletaRepository perfilOnboardingAtletaRepository;
    private final AthleteBaselineStateRepository athleteBaselineStateRepository;
    private final AthleteBaselineHistoryRepository athleteBaselineHistoryRepository;
    private final ProvaRepository provaRepository;
    private final ActivityNormalizer activityNormalizer;
    private final ActivityDedupService activityDedupService;
    private final BaselineCalculator baselineCalculator;
    private final ConfidenceScorer confidenceScorer;
    private final PlanningPolicyResolver planningPolicyResolver;
    private final CalibrationService calibrationService;

    @Override
    @Transactional(readOnly = true)
    public boolean possuiBaseline(UUID atletaId, UUID tenantId) {
        if (atletaId == null || tenantId == null) {
            throw new IllegalArgumentException("atletaId e tenantId nao podem ser nulos");
        }
        return athleteBaselineStateRepository.findByAtletaIdAndTenantId(atletaId, tenantId).isPresent();
    }

    @Override
    @Transactional
    public OnboardingContext montarContexto(UUID atletaId, UUID tenantId) {
        if (atletaId == null || tenantId == null) {
            throw new IllegalArgumentException("atletaId e tenantId nao podem ser nulos");
        }

        Atleta atleta = atletaRepository.findByIdAndTenantId(atletaId, tenantId)
                .orElseThrow(() -> new DomainNotFoundException("Atleta nao encontrado: " + atletaId));

        Optional<PerfilOnboardingAtleta> perfil = perfilOnboardingAtletaRepository
                .findByAtletaIdAndTenantId(atletaId, tenantId);

        List<NormalizedActivity> historicoDeduplicado = normalizarEDeduplicarHistorico(atletaId, tenantId);

        BaselineResult baseline = baselineCalculator.calcular(
                atletaId, atleta.getNivelExperiencia(), historicoDeduplicado);

        ConfidenceScorerInput confidenceInput = montarConfidenceInput(atleta, perfil, historicoDeduplicado);
        ConfidenceScoreResult confidenceScore = confidenceScorer.calcular(confidenceInput);

        PlanningPolicy planningPolicy = planningPolicyResolver.resolver(confidenceScore.tier());

        persistirBaselineSnapshot(atletaId, tenantId, baseline, confidenceScore);

        AthleteBaseline athleteBaseline = new AthleteBaseline(baseline.ctl(), LocalDate.now());
        AthleteConstraints constraints = montarConstraints(atleta, perfil);
        double confidenceScoreNormalizado = confidenceScore.scoreBruto() / 100.0;

        log.info("OnboardingContext montado para atleta {}: ctl={}, confidenceScore={}, tier={}",
                atletaId, baseline.ctl(), confidenceScoreNormalizado, confidenceScore.tier());

        return new OnboardingContext(athleteBaseline, confidenceScoreNormalizado, planningPolicy, constraints);
    }

    @Override
    @Transactional
    public Prova criarOuAtualizarProvaAlvo(UUID atletaId, UUID tenantId, LocalDate dataProva,
                                            TipoProva tipoProva, DistanciaProva distancia,
                                            BigDecimal distanciaKm, String nomeProva) {
        if (atletaId == null || tenantId == null || dataProva == null || tipoProva == null || distancia == null) {
            throw new IllegalArgumentException(
                    "atletaId, tenantId, dataProva, tipoProva e distancia nao podem ser nulos");
        }
        Atleta atleta = atletaRepository.findByIdAndTenantId(atletaId, tenantId)
                .orElseThrow(() -> new DomainNotFoundException("Atleta nao encontrado: " + atletaId));

        List<Prova> provasAlvoAtuais = provaRepository.findByAtletaAndProvaAlvoTrue(atleta);

        Prova provaAlvo = provasAlvoAtuais.stream()
                .filter(p -> dataProva.equals(p.getDataProva()) && distancia == p.getDistancia())
                .findFirst()
                .orElseGet(Prova::new);

        if (provaAlvo.getId() == null) {
            provaAlvo.setAtleta(atleta);
            provaAlvo.setAssessoria(atleta.getAssessoria());
            provaAlvo.setDataProva(dataProva);
            provaAlvo.setDistancia(distancia);
            provaAlvo.setStatusProva(ProvaStatus.PLANEJADA);
        }
        provaAlvo.setTipoProva(tipoProva);
        provaAlvo.setDistanciaKm(distanciaKm);
        provaAlvo.setNomeProva(nomeProva != null && !nomeProva.isBlank() ? nomeProva : "Prova alvo (onboarding)");
        provaAlvo.setProvaAlvo(true);

        Prova salva = provaRepository.save(provaAlvo);

        provasAlvoAtuais.stream()
                .filter(p -> !p.getId().equals(salva.getId()))
                .forEach(p -> {
                    p.setProvaAlvo(false);
                    provaRepository.save(p);
                });

        return salva;
    }

    @Override
    @Transactional
    public PerfilOnboardingAtleta salvarRascunho(UUID atletaId, UUID tenantId, OnboardingDraftInput input) {
        if (atletaId == null || tenantId == null || input == null) {
            throw new IllegalArgumentException("atletaId, tenantId e input nao podem ser nulos");
        }
        atletaRepository.findByIdAndTenantId(atletaId, tenantId)
                .orElseThrow(() -> new DomainNotFoundException("Atleta nao encontrado: " + atletaId));

        PerfilOnboardingAtleta perfil = perfilOnboardingAtletaRepository
                .findByAtletaIdAndTenantId(atletaId, tenantId)
                .orElseGet(() -> {
                    PerfilOnboardingAtleta novo = new PerfilOnboardingAtleta();
                    Atleta ref = new Atleta();
                    ref.setId(atletaId);
                    novo.setAtleta(ref);
                    novo.setTenantId(tenantId);
                    return novo;
                });

        perfil.setObjetivo(input.objetivo());
        perfil.setNivelExperiencia(input.nivelExperiencia());
        perfil.setDiasDisponiveis(input.diasDisponiveis());
        perfil.setVolumeSemanalMax(input.volumeSemanalMax());
        perfil.setTemLesao(input.temLesao());
        perfil.setDescricaoLesao(input.descricaoLesao());
        perfil.setDataUltimaLesao(input.dataUltimaLesao());
        perfil.setHistoricoLesoes(input.historicoLesoes());
        perfil.setMaiorTreinoRecenteKm(input.maiorTreinoRecenteKm());
        perfil.setDuracaoDisponivelMin(input.duracaoDisponivelMin());
        perfil.setRestricoes(input.restricoes());
        perfil.setModalidade(input.modalidade());
        perfil.setPercepcaoCondicionamento(input.percepcaoCondicionamento());
        perfil.setPreenchidoPorCoach(input.preenchidoPorCoach());
        perfil.setCanalIntegracao(input.canalIntegracao());
        perfil.setDispositivoMarca(input.dispositivoMarca());
        perfil.setDispositivoModelo(input.dispositivoModelo());

        return perfilOnboardingAtletaRepository.save(perfil);
    }

    @Override
    @Transactional
    public PerfilOnboardingAtleta concluirOnboarding(UUID atletaId, UUID tenantId) {
        if (atletaId == null || tenantId == null) {
            throw new IllegalArgumentException("atletaId e tenantId nao podem ser nulos");
        }
        Atleta atleta = atletaRepository.findByIdAndTenantId(atletaId, tenantId)
                .orElseThrow(() -> new DomainNotFoundException("Atleta nao encontrado: " + atletaId));
        PerfilOnboardingAtleta perfil = perfilOnboardingAtletaRepository
                .findByAtletaIdAndTenantId(atletaId, tenantId)
                .orElseThrow(() -> new DomainNotFoundException("Rascunho de onboarding nao encontrado para atleta: " + atletaId));

        if (atleta.getUpdatedAt() != null) {
            Instant atletaAtualizadoEm = atleta.getUpdatedAt().atZone(ZoneId.systemDefault()).toInstant();
            if (atletaAtualizadoEm.isAfter(perfil.getCriadoEm())) {
                throw new DomainConflictException(
                        "Atleta " + atletaId + " foi editado depois do inicio do rascunho de onboarding — "
                                + "conclua novamente apos revisar os dados atuais.");
            }
        }

        atleta.setObjetivo(perfil.getObjetivo());
        atleta.setNivelExperiencia(perfil.getNivelExperiencia());
        atleta.setDiasDisponiveis(perfil.getDiasDisponiveis());
        atleta.setVolumeSemanalMax(perfil.getVolumeSemanalMax());
        atleta.setTemLesao(perfil.getTemLesao());
        atleta.setDescricaoLesao(perfil.getDescricaoLesao());
        atleta.setDataUltimaLesao(perfil.getDataUltimaLesao());
        atleta.setHistoricoLesoes(perfil.getHistoricoLesoes());
        atletaRepository.save(atleta);

        perfil.setStatus("COMPLETO");
        return perfilOnboardingAtletaRepository.save(perfil);
    }

    @Override
    @Transactional
    public Optional<CalibrationEvaluation> avaliarCalibracaoSeAplicavel(
            UUID atletaId, UUID tenantId, LocalDate dataReferenciaSemana, InjuryRiskLevel injuryRiskLevel) {
        if (atletaId == null || tenantId == null || dataReferenciaSemana == null || injuryRiskLevel == null) {
            throw new IllegalArgumentException(
                    "atletaId, tenantId, dataReferenciaSemana e injuryRiskLevel nao podem ser nulos");
        }

        AthleteBaselineState estado = athleteBaselineStateRepository
                .findByAtletaIdAndTenantId(atletaId, tenantId)
                .orElse(null);
        if (estado == null || estado.getCalibracaoIniciadaEm() == null) {
            return Optional.empty();
        }

        Atleta atleta = atletaRepository.findByIdAndTenantId(atletaId, tenantId)
                .orElseThrow(() -> new DomainNotFoundException("Atleta nao encontrado: " + atletaId));

        LocalDate inicioCalibracao = estado.getCalibracaoIniciadaEm().atZone(ZoneId.systemDefault()).toLocalDate();
        int semanaDesdeInicioCalibracao = (int) ChronoUnit.WEEKS.between(inicioCalibracao, dataReferenciaSemana) + 1;

        List<NormalizedActivity> historicoDeduplicado = normalizarEDeduplicarHistorico(atletaId, tenantId);
        Optional<PerfilOnboardingAtleta> perfil = perfilOnboardingAtletaRepository
                .findByAtletaIdAndTenantId(atletaId, tenantId);
        ConfidenceScorerInput confidenceInput = montarConfidenceInput(atleta, perfil, historicoDeduplicado);

        CalibrationEvaluation evaluation = calibrationService.avaliarSemana(
                atletaId, tenantId, semanaDesdeInicioCalibracao, dataReferenciaSemana,
                atleta.getNivelExperiencia(), historicoDeduplicado, confidenceInput, injuryRiskLevel);

        if (evaluation.elegivelParaSairDaCalibracao()) {
            estado.setCalibracaoIniciadaEm(null);
            athleteBaselineStateRepository.save(estado);
        }

        return Optional.of(evaluation);
    }

    private List<NormalizedActivity> normalizarEDeduplicarHistorico(UUID atletaId, UUID tenantId) {
        List<TreinoRealizado> historicoBruto = treinoRealizadoRepository.findByAtletaIdOrderByDataTreinoDesc(atletaId);
        List<NormalizedActivity> normalizado = historicoBruto.stream()
                .map(activityNormalizer::toCanonical)
                .toList();
        return activityDedupService.deduplicar(normalizado, tenantId);
    }

    private ConfidenceScorerInput montarConfidenceInput(
            Atleta atleta, Optional<PerfilOnboardingAtleta> perfil, List<NormalizedActivity> historico) {
        boolean onboardingCompleto = perfil.map(PerfilOnboardingAtleta::isCompleto).orElse(false);
        boolean preenchidoPorCoach = perfil.map(PerfilOnboardingAtleta::isPreenchidoPorCoach).orElse(false);
        boolean temProvaRecente = temProvaRecente(atleta);
        DispositivoMarca dispositivoMarca = perfil.map(PerfilOnboardingAtleta::getDispositivoMarca).orElse(null);

        return new ConfidenceScorerInput(
                historico,
                onboardingCompleto,
                atleta.getFcMaxima(),
                atleta.getFcRepouso(),
                atleta.getPaceLimiar(),
                temProvaRecente,
                preenchidoPorCoach,
                dispositivoMarca
        );
    }

    private boolean temProvaRecente(Atleta atleta) {
        if (atleta.getProvas() == null) {
            return false;
        }
        LocalDate limite = LocalDate.now().minusDays(JANELA_PROVA_RECENTE_DIAS);
        return atleta.getProvas().stream()
                .map(Prova::getDataProva)
                .filter(java.util.Objects::nonNull)
                .anyMatch(data -> !data.isBefore(limite) && !data.isAfter(LocalDate.now()));
    }

    private AthleteConstraints montarConstraints(Atleta atleta, Optional<PerfilOnboardingAtleta> perfil) {
        List<java.time.DayOfWeek> diasDisponiveis = atleta.getDiasDisponiveis() == null
                ? List.of()
                : atleta.getDiasDisponiveis().stream().map(this::mapDiaSemana).toList();
        Integer duracaoMaximaMinutos = perfil.map(PerfilOnboardingAtleta::getDuracaoDisponivelMin).orElse(null);
        Integer maxSessoesPorSemana = diasDisponiveis.isEmpty() ? null : diasDisponiveis.size();

        return new AthleteConstraints(diasDisponiveis, maxSessoesPorSemana, duracaoMaximaMinutos, List.of());
    }

    private java.time.DayOfWeek mapDiaSemana(br.com.menthoros.backend.enums.DiaSemana dia) {
        return switch (dia) {
            case DOMINGO -> java.time.DayOfWeek.SUNDAY;
            case SEGUNDA -> java.time.DayOfWeek.MONDAY;
            case TERCA -> java.time.DayOfWeek.TUESDAY;
            case QUARTA -> java.time.DayOfWeek.WEDNESDAY;
            case QUINTA -> java.time.DayOfWeek.THURSDAY;
            case SEXTA -> java.time.DayOfWeek.FRIDAY;
            case SABADO -> java.time.DayOfWeek.SATURDAY;
        };
    }

    /**
     * Persiste o estado ATUAL do baseline (upsert, 1 linha por atleta) e uma linha nova no
     * histórico append-only (sessão de grilling 2026-07-21) — o estado sozinho não preserva a
     * evolução do score durante a calibração, dado necessário para calibrar as próprias
     * heurísticas hardcoded desta change com dado real de produção.
     */
    private void persistirBaselineSnapshot(UUID atletaId, UUID tenantId, BaselineResult baseline, ConfidenceScoreResult confidenceScore) {
        Instant calculatedAt = Instant.now();

        AthleteBaselineState estado = athleteBaselineStateRepository
                .findByAtletaIdAndTenantId(atletaId, tenantId)
                .orElseGet(AthleteBaselineState::new);

        if (estado.getAtleta() == null) {
            Atleta ref = new Atleta();
            ref.setId(atletaId);
            estado.setAtleta(ref);
        }
        estado.setTenantId(tenantId);
        estado.setCtlEstimado(baseline.ctl());
        estado.setAtlEstimado(baseline.atl());
        estado.setTsbEstimado(baseline.tsb());
        estado.setCtlFlag(baseline.ctlOrigem());
        estado.setAtlFlag(baseline.atlOrigem());
        estado.setTsbFlag(baseline.tsbOrigem());
        estado.setConfidenceScore(confidenceScore.scoreBruto());
        estado.setConfidenceTier(confidenceScore.tier().name());
        estado.setCalculatedAt(calculatedAt);

        if (confidenceScore.tier() != ConfidenceTier.A && estado.getCalibracaoIniciadaEm() == null) {
            estado.setCalibracaoIniciadaEm(calculatedAt);
        }

        athleteBaselineStateRepository.save(estado);

        AthleteBaselineHistory historico = new AthleteBaselineHistory();
        historico.setAtletaId(atletaId);
        historico.setTenantId(tenantId);
        historico.setEvento(EVENTO_RECALCULO_ONBOARDING_CONTEXT);
        historico.setCtlEstimado(baseline.ctl());
        historico.setAtlEstimado(baseline.atl());
        historico.setTsbEstimado(baseline.tsb());
        historico.setCtlFlag(baseline.ctlOrigem());
        historico.setAtlFlag(baseline.atlOrigem());
        historico.setTsbFlag(baseline.tsbOrigem());
        historico.setConfidenceScore(confidenceScore.scoreBruto());
        historico.setConfidenceTier(confidenceScore.tier().name());
        historico.setCalculatedAt(calculatedAt);

        athleteBaselineHistoryRepository.save(historico);
    }
}
