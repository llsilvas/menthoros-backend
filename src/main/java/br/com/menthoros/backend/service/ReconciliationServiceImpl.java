package br.com.menthoros.backend.service;

import br.com.menthoros.backend.dto.MatchingCandidate;
import br.com.menthoros.backend.dto.MatchingDecision;
import br.com.menthoros.backend.dto.MatchingScoreResult;
import br.com.menthoros.backend.entity.TreinoPlanejado;
import br.com.menthoros.backend.entity.TreinoRealizado;
import br.com.menthoros.backend.entity.TreinoReconciliacao;
import br.com.menthoros.backend.enums.ReconciliationActionType;
import br.com.menthoros.backend.enums.ReconciliationStatus;
import br.com.menthoros.backend.repository.TreinoPlanejadoRepository;
import br.com.menthoros.backend.repository.TreinoRealizadoRepository;
import br.com.menthoros.backend.repository.TreinoReconciliacaoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Implementação do serviço de reconciliação de atividades.
 * Gerencia o fluxo completo: busca de candidatos, scoring, decisão, auditoria.
 */
@Service
public class ReconciliationServiceImpl implements ReconciliationService {

    private static final int CANDIDATE_WINDOW_DAYS = 1;

    private final TreinoPlanejadoRepository treinoPlanejadoRepository;
    private final TreinoRealizadoRepository treinoRealizadoRepository;
    private final TreinoReconciliacaoRepository treinoReconciliacaoRepository;
    private final MatchingScoreCalculator scoreCalculator;
    private final MatchingDecisionEngine decisionEngine;

    public ReconciliationServiceImpl(
            TreinoPlanejadoRepository treinoPlanejadoRepository,
            TreinoRealizadoRepository treinoRealizadoRepository,
            TreinoReconciliacaoRepository treinoReconciliacaoRepository,
            MatchingScoreCalculator scoreCalculator,
            MatchingDecisionEngine decisionEngine) {
        this.treinoPlanejadoRepository = treinoPlanejadoRepository;
        this.treinoRealizadoRepository = treinoRealizadoRepository;
        this.treinoReconciliacaoRepository = treinoReconciliacaoRepository;
        this.scoreCalculator = scoreCalculator;
        this.decisionEngine = decisionEngine;
    }

    @Override
    @Transactional
    public MatchingDecision reconcile(TreinoRealizado treinoRealizado, UUID tenantId) {
        if (treinoRealizado == null || tenantId == null) {
            throw new IllegalArgumentException("TreinoRealizado e tenantId não podem ser nulos");
        }

        List<TreinoPlanejado> candidates = findCandidates(treinoRealizado, tenantId);

        List<MatchingCandidate> scoredCandidates = candidates.stream()
                .map(candidate -> {
                    MatchingScoreResult score = scoreCalculator.calculate(treinoRealizado, candidate);
                    return new MatchingCandidate(candidate, score, 0);
                })
                .collect(Collectors.toList());

        MatchingDecision decision = decisionEngine.decide(treinoRealizado, scoredCandidates);

        applyDecision(treinoRealizado, decision, ReconciliationActionType.RECONCILIACAO_AUTOMATICA, tenantId, "SYSTEM");

        return decision;
    }

    @Override
    public List<TreinoPlanejado> findCandidates(TreinoRealizado treinoRealizado, UUID tenantId) {
        if (treinoRealizado == null || treinoRealizado.getDataTreino() == null || tenantId == null) {
            return new ArrayList<>();
        }

        LocalDate dataAtividade = treinoRealizado.getDataTreino();
        LocalDate startDate = dataAtividade.minusDays(CANDIDATE_WINDOW_DAYS);
        LocalDate endDate = dataAtividade.plusDays(CANDIDATE_WINDOW_DAYS);

        UUID atletaId = treinoRealizado.getAtleta().getId();

        return treinoPlanejadoRepository.findByTenantIdAndAtletaIdAndDateRange(
                tenantId,
                atletaId,
                startDate,
                endDate.plusDays(1)
        );
    }

    @Override
    @Transactional
    public TreinoRealizado linkManually(
            Long treinoRealizadoId,
            Long treinoPlanejadoId,
            UUID tenantId,
            String actorId) {

        TreinoRealizado realizado = findRealizadoByIdAndTenant(treinoRealizadoId, tenantId);
        TreinoPlanejado planejado = findPlanejadoByIdAndTenant(treinoPlanejadoId, tenantId);

        ReconciliationStatus beforeStatus = realizado.getReconciliationStatus();
        Long beforePlannedId = realizado.getTreinoPlanejadoId();

        realizado.setTreinoPlanejado(planejado);
        realizado.setReconciliationStatus(ReconciliationStatus.VINCULADO_MANUAL);
        realizado.setReconciledAt(Instant.now());
        realizado.setReconciledBy(actorId);

        TreinoRealizado saved = treinoRealizadoRepository.save(realizado);

        createAuditEvent(
                saved,
                ReconciliationActionType.VINCULAR_MANUALMENTE,
                beforeStatus,
                ReconciliationStatus.VINCULADO_MANUAL,
                beforePlannedId,
                planejado.getId(),
                "MANUAL_LINK",
                "Vínculo manual executado pelo treinador",
                actorId,
                tenantId
        );

        return saved;
    }

    @Override
    @Transactional
    public TreinoRealizado markAsNotPlanned(
            Long treinoRealizadoId,
            UUID tenantId,
            String actorId) {

        TreinoRealizado realizado = findRealizadoByIdAndTenant(treinoRealizadoId, tenantId);

        ReconciliationStatus beforeStatus = realizado.getReconciliationStatus();
        Long beforePlannedId = realizado.getTreinoPlanejadoId();

        realizado.setTreinoPlanejado(null);
        realizado.setReconciliationStatus(ReconciliationStatus.NAO_PLANEJADO);
        realizado.setReconciledAt(Instant.now());
        realizado.setReconciledBy(actorId);

        TreinoRealizado saved = treinoRealizadoRepository.save(realizado);

        createAuditEvent(
                saved,
                ReconciliationActionType.MARCAR_NAO_PLANEJADO,
                beforeStatus,
                ReconciliationStatus.NAO_PLANEJADO,
                beforePlannedId,
                null,
                "MARKED_NOT_PLANNED",
                "Marcado como atividade não planejada",
                actorId,
                tenantId
        );

        return saved;
    }

    @Override
    @Transactional
    public TreinoRealizado unlinkManually(
            Long treinoRealizadoId,
            UUID tenantId,
            String actorId) {

        TreinoRealizado realizado = findRealizadoByIdAndTenant(treinoRealizadoId, tenantId);

        ReconciliationStatus beforeStatus = realizado.getReconciliationStatus();
        Long beforePlannedId = realizado.getTreinoPlanejadoId();

        realizado.setTreinoPlanejado(null);
        realizado.setReconciliationStatus(ReconciliationStatus.PENDENTE);
        realizado.setReconciledAt(Instant.now());
        realizado.setReconciledBy(actorId);

        TreinoRealizado saved = treinoRealizadoRepository.save(realizado);

        createAuditEvent(
                saved,
                ReconciliationActionType.DESFAZER_VINCULO,
                beforeStatus,
                ReconciliationStatus.PENDENTE,
                beforePlannedId,
                null,
                "UNLINKED",
                "Vínculo desfeito pelo treinador",
                actorId,
                tenantId
        );

        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public TreinoRealizado getReconciliationState(Long treinoRealizadoId, UUID tenantId) {
        return findRealizadoByIdAndTenant(treinoRealizadoId, tenantId);
    }

    // ===== Helper Methods =====

    private void applyDecision(
            TreinoRealizado realizado,
            MatchingDecision decision,
            ReconciliationActionType actionType,
            UUID tenantId,
            String actorId) {

        ReconciliationStatus beforeStatus = realizado.getReconciliationStatus();
        Long beforePlannedId = realizado.getTreinoPlanejadoId();

        if (decision.getStatus() == ReconciliationStatus.VINCULADO_AUTOMATICO) {
            realizado.setTreinoPlanejado(decision.getSelectedPlanned());
        } else {
            realizado.setTreinoPlanejado(null);
        }

        realizado.setReconciliationStatus(decision.getStatus());
        realizado.setReconciliationScore(decision.getSelectedScore());
        realizado.setReconciliationReasonCode(decision.getReasonCode());
        realizado.setReconciledAt(Instant.now());
        realizado.setReconciledBy(actorId);

        TreinoRealizado saved = treinoRealizadoRepository.save(realizado);

        UUID afterPlannedId = decision.getSelectedPlanned() != null ? decision.getSelectedPlanned().getId() : null;

        createAuditEvent(
                saved,
                actionType,
                beforeStatus,
                decision.getStatus(),
                beforePlannedId,
                afterPlannedId,
                decision.getReasonCode(),
                decision.getReasonText(),
                actorId,
                tenantId
        );
    }

    private void createAuditEvent(
            TreinoRealizado realizado,
            ReconciliationActionType actionType,
            ReconciliationStatus beforeStatus,
            ReconciliationStatus afterStatus,
            Long beforePlannedId,
            UUID afterPlannedId,
            String reasonCode,
            String reasonText,
            String actorId,
            UUID tenantId) {

        TreinoReconciliacao event = new TreinoReconciliacao();
        event.setTreinoRealizado(realizado);
        event.setActionType(actionType);
        event.setBeforeStatus(beforeStatus);
        event.setAfterStatus(afterStatus);
        event.setBeforePlannedId(beforePlannedId);
        event.setAfterPlannedId(afterPlannedId);
        event.setReasonCode(reasonCode);
        event.setReasonText(reasonText);
        event.setActorId(actorId);
        event.setTenantId(tenantId.toString());
        event.setOccurredAt(Instant.now());

        treinoReconciliacaoRepository.save(event);
    }

    private TreinoRealizado findRealizadoByIdAndTenant(Long id, UUID tenantId) {
        TreinoRealizado realizado = treinoRealizadoRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("TreinoRealizado não encontrado: " + id));

        if (!realizado.getTenantId().equals(tenantId)) {
            throw new IllegalArgumentException("Tenant mismatch para TreinoRealizado");
        }

        return realizado;
    }

    private TreinoPlanejado findPlanejadoByIdAndTenant(Long id, UUID tenantId) {
        TreinoPlanejado planejado = treinoPlanejadoRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("TreinoPlanejado não encontrado: " + id));

        if (!planejado.getTenantId().equals(tenantId)) {
            throw new IllegalArgumentException("Tenant mismatch para TreinoPlanejado");
        }

        return planejado;
    }
}
