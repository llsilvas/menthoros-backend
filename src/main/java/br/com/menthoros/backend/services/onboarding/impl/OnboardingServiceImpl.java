package br.com.menthoros.backend.services.onboarding.impl;

import br.com.menthoros.backend.domain.planner.AthleteBaseline;
import br.com.menthoros.backend.domain.planner.AthleteConstraints;
import br.com.menthoros.backend.domain.planner.OnboardingContext;
import br.com.menthoros.backend.domain.planner.PlanningPolicy;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.AthleteBaselineSnapshot;
import br.com.menthoros.backend.entity.PerfilOnboardingAtleta;
import br.com.menthoros.backend.entity.Prova;
import br.com.menthoros.backend.entity.TreinoRealizado;
import br.com.menthoros.backend.enums.DistanciaProva;
import br.com.menthoros.backend.enums.ProvaStatus;
import br.com.menthoros.backend.enums.TipoProva;
import br.com.menthoros.backend.exception.DomainNotFoundException;
import br.com.menthoros.backend.repository.AthleteBaselineSnapshotRepository;
import br.com.menthoros.backend.repository.AtletaRepository;
import br.com.menthoros.backend.repository.PerfilOnboardingAtletaRepository;
import br.com.menthoros.backend.repository.ProvaRepository;
import br.com.menthoros.backend.repository.TreinoRealizadoRepository;
import br.com.menthoros.backend.services.onboarding.ActivityDedupService;
import br.com.menthoros.backend.services.onboarding.ActivityNormalizer;
import br.com.menthoros.backend.services.onboarding.BaselineCalculator;
import br.com.menthoros.backend.services.onboarding.BaselineResult;
import br.com.menthoros.backend.services.onboarding.ConfidenceScoreResult;
import br.com.menthoros.backend.services.onboarding.ConfidenceScorer;
import br.com.menthoros.backend.services.onboarding.ConfidenceScorerInput;
import br.com.menthoros.backend.services.onboarding.NormalizedActivity;
import br.com.menthoros.backend.services.onboarding.OnboardingService;
import br.com.menthoros.backend.services.onboarding.PlanningPolicyResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
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

    private final AtletaRepository atletaRepository;
    private final TreinoRealizadoRepository treinoRealizadoRepository;
    private final PerfilOnboardingAtletaRepository perfilOnboardingAtletaRepository;
    private final AthleteBaselineSnapshotRepository athleteBaselineSnapshotRepository;
    private final ProvaRepository provaRepository;
    private final ActivityNormalizer activityNormalizer;
    private final ActivityDedupService activityDedupService;
    private final BaselineCalculator baselineCalculator;
    private final ConfidenceScorer confidenceScorer;
    private final PlanningPolicyResolver planningPolicyResolver;

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

        return new ConfidenceScorerInput(
                historico,
                onboardingCompleto,
                atleta.getFcMaxima(),
                atleta.getFcRepouso(),
                atleta.getPaceLimiar(),
                temProvaRecente,
                preenchidoPorCoach
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

    private void persistirBaselineSnapshot(UUID atletaId, UUID tenantId, BaselineResult baseline, ConfidenceScoreResult confidenceScore) {
        AthleteBaselineSnapshot snapshot = athleteBaselineSnapshotRepository
                .findByAtletaIdAndTenantId(atletaId, tenantId)
                .orElseGet(AthleteBaselineSnapshot::new);

        if (snapshot.getAtleta() == null) {
            Atleta ref = new Atleta();
            ref.setId(atletaId);
            snapshot.setAtleta(ref);
        }
        snapshot.setTenantId(tenantId);
        snapshot.setCtlEstimado(baseline.ctl());
        snapshot.setAtlEstimado(baseline.atl());
        snapshot.setTsbEstimado(baseline.tsb());
        snapshot.setCtlFlag(baseline.ctlOrigem());
        snapshot.setAtlFlag(baseline.atlOrigem());
        snapshot.setTsbFlag(baseline.tsbOrigem());
        snapshot.setConfidenceScore(confidenceScore.scoreBruto());
        snapshot.setConfidenceTier(confidenceScore.tier().name());
        snapshot.setCalculatedAt(Instant.now());

        athleteBaselineSnapshotRepository.save(snapshot);
    }
}
