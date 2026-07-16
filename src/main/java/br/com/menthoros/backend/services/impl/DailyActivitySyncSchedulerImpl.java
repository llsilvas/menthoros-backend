package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.dto.MatchingDecision;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.TreinoPlanejado;
import br.com.menthoros.backend.entity.TreinoRealizado;
import br.com.menthoros.backend.enums.ReconciliationStatus;
import br.com.menthoros.backend.enums.StatusSincronizacao;
import br.com.menthoros.backend.repository.AtletaRepository;
import br.com.menthoros.backend.repository.TreinoRealizadoRepository;
import br.com.menthoros.backend.services.DailyActivitySyncScheduler;
import br.com.menthoros.backend.services.helper.CandidateSelector;
import br.com.menthoros.backend.services.helper.ReconciliationDecisionExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Scheduler de RECONCILIAÇÃO diária — decide/grava o vínculo entre {@link TreinoRealizado}
 * já {@code PENDENTE} e {@code TreinoPlanejado} candidatos na janela D-1..D+1.
 *
 * <p><b>Não é o caminho de ingestão do Strava</b> (achado de implementação do Bloco 6 da change
 * {@code intervals-icu-activity-ingestion}, ver design.md D5.2 "Guarda no(s) scheduler(s)"): este
 * componente nunca busca nem insere atividades novas da API do Strava — a busca/inserção diária
 * real é feita por {@code StravaActivitySyncScheduler} (pacote {@code services}, sem sufixo
 * {@code Impl}). Este scheduler apenas reconcilia registros que JÁ existem com
 * {@code statusSincronizacao=PENDENTE}, sejam eles de origem Strava, {@code .fit} ou
 * intervals.icu.
 *
 * <p>Delega a seleção de candidatos para {@link CandidateSelector} e a decisão/persistência para
 * {@link ReconciliationDecisionExecutor} — os mesmos colaboradores usados pelo import inline do
 * intervals.icu, garantindo que o scheduler batch e o import inline produzam sempre a mesma
 * decisão para o mesmo caso (design.md D4).
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
    private final CandidateSelector candidateSelector;
    private final ReconciliationDecisionExecutor reconciliationDecisionExecutor;

    public DailyActivitySyncSchedulerImpl(
            AtletaRepository atletaRepository,
            TreinoRealizadoRepository treinoRealizadoRepository,
            CandidateSelector candidateSelector,
            ReconciliationDecisionExecutor reconciliationDecisionExecutor) {
        this.atletaRepository = atletaRepository;
        this.treinoRealizadoRepository = treinoRealizadoRepository;
        this.candidateSelector = candidateSelector;
        this.reconciliationDecisionExecutor = reconciliationDecisionExecutor;
    }

    /**
     * Executa reconciliação diária de treinos realizados pendentes.
     * Cron: 2 da manhã, todo dia (horário UTC)
     */
//    @Scheduled(fixedDelay = 10000, initialDelay = 5000)  // TEST: executa a cada 10s, primeira em 5s
    @Scheduled(fixedDelayString = "PT2H", initialDelayString = "PT10M")
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
     * Processa atividades de um atleta específico: para cada pendente do dia anterior, delega a
     * seleção de candidatos ao {@link CandidateSelector} e a decisão/persistência ao
     * {@link ReconciliationDecisionExecutor}.
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
        UUID tenantId = atleta.getAssessoria().getId();

        // Busca atividades pendentes de reconciliação do dia anterior
        List<TreinoRealizado> pendingActivities = treinoRealizadoRepository
                .findByAtletaIdAndDataTreinoAndStatusSincronizacao(atleta.getId(), yesterday, StatusSincronizacao.PENDENTE);

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

            List<TreinoPlanejado> candidatos = candidateSelector.buscarCandidatos(activity, tenantId);

            MatchingDecision decision = reconciliationDecisionExecutor.executar(activity, candidatos, atleta);

            if (decision.getStatus() == ReconciliationStatus.VINCULADO_AUTOMATICO) {
                result.autoMatchedCount++;
            } else if (decision.getStatus() == ReconciliationStatus.AMBIGUO) {
                result.ambiguousCount++;
            } else if (decision.getStatus() == ReconciliationStatus.NAO_PLANEJADO) {
                result.orphanedCount++;
            }
        }

        return result;
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
