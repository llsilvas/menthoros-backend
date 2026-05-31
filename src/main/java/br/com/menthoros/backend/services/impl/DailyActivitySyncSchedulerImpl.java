package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.services.DailyActivitySyncScheduler;
import br.com.menthoros.backend.services.MatchingScoreCalculator;
import br.com.menthoros.backend.services.MatchingDecisionEngine;
import br.com.menthoros.backend.services.ActivityTypeCompatibilityMatrix;
import br.com.menthoros.backend.dto.MatchingCandidate;
import br.com.menthoros.backend.dto.MatchingDecision;
import br.com.menthoros.backend.dto.MatchingScoreResult;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.TreinoPlanejado;
import br.com.menthoros.backend.entity.TreinoRealizado;
import br.com.menthoros.backend.entity.TreinoReconciliacao;
import br.com.menthoros.backend.enums.ReconciliationActionType;
import br.com.menthoros.backend.enums.ReconciliationStatus;
import br.com.menthoros.backend.enums.StatusSincronizacao;
import br.com.menthoros.backend.enums.TreinoExecucaoStatus;
import br.com.menthoros.backend.repository.AtletaRepository;
import br.com.menthoros.backend.repository.TreinoPlanejadoRepository;
import br.com.menthoros.backend.repository.TreinoRealizadoRepository;
import br.com.menthoros.backend.repository.TreinoReconciliacaoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Scheduler para sincronização diária incremental de atividades Strava.
 *
 * Executa a cada dia às 2 da manhã (fora do horário de pico) e:
 * 1. Busca todos os atletas com Strava conectado
 * 2. Para cada atleta, processa atividades do dia anterior
 * 3. Aplica matching automático e cria eventos de auditoria
 * 4. Marca ambíguas/orfãs para revisão do treinador
 *
 * Cron: "0 2 * * *" = 2:00 AM UTC diariamente
 *
 * Multi-Tenancy:
 * - Este scheduler é um job de sistema sem contexto de requisição HTTP
 * - Processa todos os tenants (assessorias) simultaneamente
 * - Isolamento garantido por:
 *   1. Queries filtram por atletaId (implicitamente por tenantId via athlete->assessoria)
 *   2. Validação explícita: verifica tenantId de cada atividade/candidato antes de processar
 *   3. Auditoria: cada evento registra explicitamente o tenantId
 * - Se detectado tenant mismatch (erro crítico), atividade é pulada com log de segurança
 */
@Service
public class DailyActivitySyncSchedulerImpl implements DailyActivitySyncScheduler {

    private static final Logger logger = LoggerFactory.getLogger(DailyActivitySyncSchedulerImpl.class);

    private final AtletaRepository atletaRepository;
    private final TreinoRealizadoRepository treinoRealizadoRepository;
    private final TreinoPlanejadoRepository treinoPlanejadoRepository;
    private final TreinoReconciliacaoRepository treinoReconciliacaoRepository;
    private final MatchingScoreCalculator matchingScoreCalculator;
    private final MatchingDecisionEngine matchingDecisionEngine;
    private final ActivityTypeCompatibilityMatrix activityTypeCompatibilityMatrix;

    public DailyActivitySyncSchedulerImpl(
            AtletaRepository atletaRepository,
            TreinoRealizadoRepository treinoRealizadoRepository,
            TreinoPlanejadoRepository treinoPlanejadoRepository,
            TreinoReconciliacaoRepository treinoReconciliacaoRepository,
            MatchingScoreCalculator matchingScoreCalculator,
            MatchingDecisionEngine matchingDecisionEngine,
            ActivityTypeCompatibilityMatrix activityTypeCompatibilityMatrix) {
        this.atletaRepository = atletaRepository;
        this.treinoRealizadoRepository = treinoRealizadoRepository;
        this.treinoPlanejadoRepository = treinoPlanejadoRepository;
        this.treinoReconciliacaoRepository = treinoReconciliacaoRepository;
        this.matchingScoreCalculator = matchingScoreCalculator;
        this.matchingDecisionEngine = matchingDecisionEngine;
        this.activityTypeCompatibilityMatrix = activityTypeCompatibilityMatrix;
    }

    /**
     * Executa sincronização diária de atividades Strava.
     * Cron: 2 da manhã, todo dia (horário UTC)
     * Formato: segundo minuto hora dia-do-mês mês dia-da-semana
     *
     * Observação: Implementação base. Versão final requer:
     * - Integração com Strava API para buscar atividades
     * - Uso do MatchingScoreCalculator + MatchingDecisionEngine
     * - Transações e retry logic
     * - Observabilidade (métricas de sucesso/erro)
     */
    @Scheduled(fixedDelay = 10000, initialDelay = 5000)  // TEST: executa a cada 10s, primeira em 5s
//    @Scheduled(cron = "0 0 11 * * *")
    @Transactional
    public void executeDailySync() {
        long startTime = System.currentTimeMillis();
        logger.info("Starting daily activity sync");

        try {
            List<Atleta> atletas = atletaRepository.findAllWithStravaConnected();
            logger.info("Found {} athletes with Strava connected", atletas.size());

            int totalProcessed = 0;
            int totalAutoMatched = 0;
            int totalAmbiguous = 0;
            int totalOrphaned = 0;

            for (Atleta atleta : atletas) {
                try {
                    SyncResult result = syncAtletaActivities(atleta);
                    totalProcessed += result.processedCount;
                    totalAutoMatched += result.autoMatchedCount;
                    totalAmbiguous += result.ambiguousCount;
                    totalOrphaned += result.orphanedCount;
                } catch (Exception e) {
                    logger.error("Error syncing activities for atleta {}: {}", atleta.getId(), e.getMessage(), e);
                }
            }

            long duration = System.currentTimeMillis() - startTime;
            logger.info(
                    "Daily sync completed in {}ms: {} processed, {} auto-matched, {} ambiguous, {} orphaned",
                    duration, totalProcessed, totalAutoMatched, totalAmbiguous, totalOrphaned
            );

            recordMetrics(totalProcessed, totalAutoMatched, totalAmbiguous, totalOrphaned, duration);

        } catch (Exception e) {
            logger.error("Critical error in daily sync scheduler: {}", e.getMessage(), e);
        }
    }

    /**
     * Processa atividades de um atleta específico.
     * Busca candidatos TreinoPlanejado na janela de ±1 dia, calcula scores
     * e aplica decisão automática ou marca para revisão manual.
     *
     * Multi-tenancy: Todas as queries filtram por atletaId (implicitamente por tenantId
     * através da relação athlete->assessoria). Validação adicional garante integridade.
     */
    @Transactional
    private SyncResult syncAtletaActivities(Atleta atleta) {
        SyncResult result = new SyncResult();

        // Garantir que atleta tem tenantId válido (multi-tenant isolation)
        if (atleta.getAssessoria() == null || atleta.getAssessoria().getId() == null) {
            logger.warn("Skipping athlete {} - no valid tenant association", atleta.getId());
            return result;
        }

        LocalDate yesterday = LocalDate.now().minusDays(1);
//        LocalDate yesterday = LocalDate.now();
        LocalDate windowStart = yesterday.minusDays(1);
        LocalDate windowEnd = yesterday.plusDays(1);
        UUID tenantId = atleta.getAssessoria().getId();

        // Busca atividades pendentes de reconciliação do dia anterior
        List<TreinoRealizado> pendingActivities = treinoRealizadoRepository
                .findByAtletaIdAndDataTreinoAndReconciliationStatus(
                        atleta.getId(),
                        yesterday,
                        StatusSincronizacao.PENDENTE
                );

        logger.debug("Found {} pending activities for atleta {} (tenant {}) on {}",
                pendingActivities.size(), atleta.getId(), tenantId, yesterday);

        for (TreinoRealizado activity : pendingActivities) {
            // Multi-tenant validation: verify activity belongs to correct tenant
            if (!activity.getAtleta().getAssessoria().getId().equals(tenantId)) {
                logger.error(
                    "SECURITY: Activity {} belongs to different tenant than expected. Expected: {}, Found: {}",
                    activity.getId(), tenantId, activity.getAtleta().getAssessoria().getId()
                );
                continue; // Skip this activity due to tenant mismatch
            }

            result.processedCount++;

            // Busca candidatos TreinoPlanejado na janela de tempo (D-1 a D+1)
            List<TreinoPlanejado> candidatos = treinoPlanejadoRepository
                    .findByAtletaIdAndDataBetween(
                            atleta.getId(),
                            windowStart,
                            windowEnd
                    );

            logger.debug("Found {} planned workout candidates for activity {} (tenant {}) in window [{}, {}]",
                    candidatos.size(), activity.getId(), tenantId, windowStart, windowEnd);

            // Aplica pré-filtro: compatibilidade de tipo de treino
            List<TreinoPlanejado> compatibleCandidatos = filterCompatibleCandidatos(activity, candidatos);

            // Multi-tenant validation: verify all candidates belong to same tenant
            for (TreinoPlanejado candidato : compatibleCandidatos) {
                if (!candidato.getAtleta().getAssessoria().getId().equals(tenantId)) {
                    logger.error(
                        "SECURITY: Candidate {} belongs to different tenant. Expected: {}, Found: {}",
                        candidato.getId(), tenantId, candidato.getAtleta().getAssessoria().getId()
                    );
                    // Don't process this candidate - it violates multi-tenant isolation
                    compatibleCandidatos = compatibleCandidatos.stream()
                            .filter(c -> c.getAtleta().getAssessoria().getId().equals(tenantId))
                            .toList();
                    break;
                }
            }

            if (compatibleCandidatos.size() < candidatos.size()) {
                logger.debug("Filtered {} -> {} compatible candidates after type check",
                        candidatos.size(), compatibleCandidatos.size());
            }

            // Calcula scores para cada candidato
            List<MatchingCandidate> scoredCandidates = new ArrayList<>();
            int rank = 1;
            for (TreinoPlanejado candidato : compatibleCandidatos) {
                MatchingScoreResult scoreResult = matchingScoreCalculator.calculate(activity, candidato, atleta);
                MatchingCandidate candidate = new MatchingCandidate(candidato, scoreResult, rank++);
                scoredCandidates.add(candidate);
            }

            // Decide usando o matching engine
            MatchingDecision decision = matchingDecisionEngine.decide(activity, scoredCandidates);

            // Persiste decisão e auditoria
            persistMatchingDecision(activity, decision, atleta, result);
        }

        return result;
    }

    @Transactional
    private void persistMatchingDecision(
            TreinoRealizado activity,
            MatchingDecision decision,
            Atleta atleta,
            SyncResult result) {

        BigDecimal scoreToRecord = decision.getSelectedScore() != null ? decision.getSelectedScore() : BigDecimal.ZERO;

        activity.setReconciliationStatus(decision.getStatus());
        activity.setReconciliationScore(scoreToRecord);
        activity.setReconciliationReasonCode(decision.getReasonCode());
        activity.setReconciledAt(Instant.now());
        activity.setReconciledBy("SYSTEM");

        if (decision.getStatus() == ReconciliationStatus.VINCULADO_AUTOMATICO) {
            TreinoPlanejado planned = decision.getSelectedPlanned();
            if (planned != null) {
                planned.setStatusTreino(TreinoExecucaoStatus.REALIZADO);
                planned.setStatusSincronizacao(StatusSincronizacao.SINCRONIZADO);
                activity.setTreinoPlanejado(planned);
            }
            result.autoMatchedCount++;
            logger.info("Auto-matched activity {} to planned workout with score {}",
                    activity.getId(), scoreToRecord);
        } else if (decision.getStatus() == ReconciliationStatus.AMBIGUO) {
            result.ambiguousCount++;
            logger.info("Marked activity {} as ambiguous (score: {}), needs manual review",
                    activity.getId(), scoreToRecord);
        } else if (decision.getStatus() == ReconciliationStatus.NAO_PLANEJADO) {
            result.orphanedCount++;
            logger.info("Activity {} marked as not planned (score: {})",
                    activity.getId(), scoreToRecord);
        }

        // Salva atividade com novo estado
        treinoRealizadoRepository.save(activity);

        // Registra evento de auditoria
        TreinoReconciliacao auditEvent = new TreinoReconciliacao();
        auditEvent.setTreinoRealizado(activity);
        auditEvent.setActionType(ReconciliationActionType.RECONCILIACAO_AUTOMATICA);
        auditEvent.setBeforeStatus(ReconciliationStatus.PENDENTE);
        auditEvent.setAfterStatus(decision.getStatus());
        auditEvent.setBeforePlannedId(null);
        auditEvent.setScore(scoreToRecord);
        auditEvent.setReasonCode(decision.getReasonCode());
        auditEvent.setReasonText(decision.getReasonText());
        auditEvent.setActorId("SYSTEM");
        auditEvent.setTenantId(atleta.getAssessoria().getId().toString());
        auditEvent.setOccurredAt(Instant.now());

        treinoReconciliacaoRepository.save(auditEvent);
    }

    /**
     * Filtra candidatos por compatibilidade de tipo de treino (pré-filtro obrigatório).
     * Usa matriz de compatibilidade real que implementa regra MVP:
     * todos os tipos de corrida (TipoTreino) são compatíveis entre si.
     * Atividades sem tipo definido são compatíveis com qualquer planejado.
     */
    private List<TreinoPlanejado> filterCompatibleCandidatos(
            TreinoRealizado activity,
            List<TreinoPlanejado> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return candidates;
        }

        return candidates.stream()
                .filter(planned ->
                    activityTypeCompatibilityMatrix.isCompatible(
                        activity.getTipoTreino(),
                        planned.getTipoTreino()
                    )
                )
                .toList();
    }

    /**
     * Registra métricas da sincronização para observabilidade.
     * Implementação final pode usar Micrometer, CloudWatch, Datadog, etc.
     */
    private void recordMetrics(int processed, int autoMatched, int ambiguous, int orphaned, long duration) {
        // TODO: Implementar métricas reais
        // metrics.recordCounter("strava.sync.processed", processed);
        // metrics.recordCounter("strava.sync.auto_matched", autoMatched);
        // metrics.recordCounter("strava.sync.ambiguous", ambiguous);
        // metrics.recordCounter("strava.sync.orphaned", orphaned);
        // metrics.recordTimer("strava.sync.duration", duration);

        logger.debug("Metrics recorded: processed={}, auto_matched={}, ambiguous={}, orphaned={}, duration={}ms",
                processed, autoMatched, ambiguous, orphaned, duration);
    }

    /**
     * DTO para resultado de sincronização de um atleta.
     */
    private static class SyncResult {
        int processedCount = 0;
        int autoMatchedCount = 0;
        int ambiguousCount = 0;
        int orphanedCount = 0;
    }
}
