package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.dto.MatchingCandidate;
import br.com.menthoros.backend.dto.MatchingDecision;
import br.com.menthoros.backend.dto.MatchingScoreResult;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.TreinoPlanejado;
import br.com.menthoros.backend.entity.TreinoReconciliacao;
import br.com.menthoros.backend.entity.TreinoRealizado;
import br.com.menthoros.backend.enums.ReconciliationActionType;
import br.com.menthoros.backend.enums.ReconciliationStatus;
import br.com.menthoros.backend.enums.StatusSincronizacao;
import br.com.menthoros.backend.enums.TreinoExecucaoStatus;
import br.com.menthoros.backend.repository.TreinoPlanejadoRepository;
import br.com.menthoros.backend.repository.TreinoReconciliacaoRepository;
import br.com.menthoros.backend.repository.TreinoRealizadoRepository;
import br.com.menthoros.backend.services.MatchingDecisionEngine;
import br.com.menthoros.backend.services.MatchingScoreCalculator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Calcula scores, decide e persiste a reconciliação de um {@link TreinoRealizado} contra
 * candidatos {@link TreinoPlanejado} — extraído do {@code DailyActivitySyncSchedulerImpl}
 * (design.md D4) para ser reutilizado tanto pelo scheduler batch quanto pelo import inline do
 * intervals.icu, gravando exatamente o que {@code persistMatchingDecision} gravava antes, com
 * duas correções (achados do pre-mortem):
 * <ul>
 *   <li>salva o {@link TreinoPlanejado} vinculado EXPLICITAMENTE (achado #7 — o scheduler original
 *   mutava em memória contando com a entidade gerenciada na mesma TX de leitura, o que não pode
 *   ser assumido quando os candidatos vêm de um {@link CandidateSelector} chamado por outro
 *   caller);</li>
 *   <li>guarda absoluta contra campos ausentes (achado #13, estendida ao lado planejado no 2º
 *   pre-mortem): {@code VINCULADO_AUTOMATICO} é proibido quando duração ou distância — de
 *   qualquer um dos dois lados — estão ausentes, independentemente do score calculado. Duração
 *   ausente é {@link Duration#ZERO} (sentinela — a coluna {@code duracao_min} é {@code NOT NULL},
 *   ver {@code TreinoBase.java:45}), distância ausente é {@code null} literal (coluna
 *   nullable).</li>
 * </ul>
 *
 * <p>Idempotent: NO — persiste estado a cada chamada.
 * <p>Side Effects: Database mutation (TreinoRealizado, TreinoPlanejado quando vinculado,
 * TreinoReconciliacao de auditoria).
 * <p>Tenant-aware: YES — opera sobre entidades já resolvidas pelo caller; não lê TenantContext.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReconciliationDecisionExecutor {

    private final MatchingScoreCalculator matchingScoreCalculator;
    private final MatchingDecisionEngine matchingDecisionEngine;
    private final TreinoRealizadoRepository treinoRealizadoRepository;
    private final TreinoPlanejadoRepository treinoPlanejadoRepository;
    private final TreinoReconciliacaoRepository treinoReconciliacaoRepository;

    public MatchingDecision executar(TreinoRealizado realizado, List<TreinoPlanejado> candidatos, Atleta atleta) {
        List<MatchingCandidate> scoredCandidates = new ArrayList<>();
        int rank = 1;
        for (TreinoPlanejado candidato : candidatos) {
            MatchingScoreResult scoreResult = matchingScoreCalculator.calculate(realizado, candidato, atleta);
            scoredCandidates.add(new MatchingCandidate(candidato, scoreResult, rank++));
        }

        MatchingDecision decision = matchingDecisionEngine.decide(realizado, scoredCandidates);
        decision = aplicarGuardaCamposAusentes(realizado, decision);

        persistir(realizado, decision, atleta);
        return decision;
    }

    /**
     * Guarda absoluta (Bloco 3.3): rebaixa VINCULADO_AUTOMATICO para AMBIGUO quando duração ou
     * distância — do realizado OU do planejado selecionado — estão ausentes, independentemente
     * do score. Veto categórico, não penalização de score.
     */
    private MatchingDecision aplicarGuardaCamposAusentes(TreinoRealizado realizado, MatchingDecision decision) {
        if (decision.getStatus() != ReconciliationStatus.VINCULADO_AUTOMATICO) {
            return decision;
        }

        TreinoPlanejado planejado = decision.getSelectedPlanned();
        boolean campoAusente = Duration.ZERO.equals(realizado.getDuracaoMin())
                || realizado.getDistanciaKm() == null
                || (planejado != null && Duration.ZERO.equals(planejado.getDuracaoMin()))
                || (planejado != null && planejado.getDistanciaKm() == null);

        if (!campoAusente) {
            return decision;
        }

        log.info("Guarda de campos ausentes ativada: treino {} rebaixado de VINCULADO_AUTOMATICO para AMBIGUO "
                + "(duração ou distância ausente de um dos lados)", realizado.getId());
        return new MatchingDecision(
                ReconciliationStatus.AMBIGUO,
                planejado,
                decision.getRankedCandidates(),
                "CAMPO_AUSENTE_VETO",
                "Duração ou distância ausente (realizado ou planejado) — vínculo automático vetado independentemente do score");
    }

    private void persistir(TreinoRealizado realizado, MatchingDecision decision, Atleta atleta) {
        BigDecimal scoreToRecord = decision.getSelectedScore() != null ? decision.getSelectedScore() : BigDecimal.ZERO;

        realizado.setReconciliationStatus(decision.getStatus());
        realizado.setReconciliationScore(scoreToRecord);
        realizado.setReconciliationReasonCode(decision.getReasonCode());
        realizado.setReconciledAt(Instant.now());
        realizado.setReconciledBy("SYSTEM");

        if (decision.getStatus() == ReconciliationStatus.VINCULADO_AUTOMATICO) {
            TreinoPlanejado planned = decision.getSelectedPlanned();
            if (planned != null) {
                planned.setStatusTreino(TreinoExecucaoStatus.REALIZADO);
                planned.setStatusSincronizacao(StatusSincronizacao.SINCRONIZADO);
                realizado.setTreinoPlanejado(planned);
                treinoPlanejadoRepository.save(planned);
            }
            log.info("Auto-matched activity {} to planned workout with score {}", realizado.getId(), scoreToRecord);
        } else if (decision.getStatus() == ReconciliationStatus.AMBIGUO) {
            log.info("Marked activity {} as ambiguous (score: {}), needs manual review", realizado.getId(), scoreToRecord);
        } else if (decision.getStatus() == ReconciliationStatus.NAO_PLANEJADO) {
            log.info("Activity {} marked as not planned (score: {})", realizado.getId(), scoreToRecord);
        }

        treinoRealizadoRepository.save(realizado);

        TreinoReconciliacao auditEvent = new TreinoReconciliacao();
        auditEvent.setTreinoRealizado(realizado);
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
}
