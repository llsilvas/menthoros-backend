package br.com.menthoros.backend.service;

import br.com.menthoros.backend.entity.TreinoRealizado;
import br.com.menthoros.backend.entity.TreinoReconciliacao;
import br.com.menthoros.backend.enums.ReconciliationActionType;
import br.com.menthoros.backend.enums.ReconciliationStatus;
import br.com.menthoros.backend.repository.TreinoRealizadoRepository;
import br.com.menthoros.backend.repository.TreinoReconciliacaoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Serviço de ações manuais de reconciliação.
 * Gerencia: vincular manualmente, marcar não planejado, desfazer vínculo.
 * Com auditoria completa em tb_treino_reconciliacao.
 */
@Service
public class ManualReconciliationService {

    private final TreinoRealizadoRepository treinoRealizadoRepository;
    private final TreinoReconciliacaoRepository treinoReconciliacaoRepository;

    public ManualReconciliationService(
            TreinoRealizadoRepository treinoRealizadoRepository,
            TreinoReconciliacaoRepository treinoReconciliacaoRepository) {
        this.treinoRealizadoRepository = treinoRealizadoRepository;
        this.treinoReconciliacaoRepository = treinoReconciliacaoRepository;
    }

    /**
     * Vincula manualmente uma atividade realizada a um treino planejado.
     * Registra evento de auditoria e persiste estado.
     */
    @Transactional
    public TreinoRealizado linkManually(
            UUID treinoRealizadoId,
            Long treinoPlanejadoId,
            UUID tenantId,
            String actorId) {

        TreinoRealizado realizado = findAndValidate(treinoRealizadoId, tenantId);

        ReconciliationStatus beforeStatus = realizado.getReconciliationStatus();
        Long beforePlannedId = realizado.getTreinoPlanejadoId();

        realizado.setTreinoPlanejadoId(treinoPlanejadoId);
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
                treinoPlanejadoId,
                "MANUAL_LINK",
                "Vínculo manual executado pelo treinador",
                actorId,
                tenantId
        );

        return saved;
    }

    /**
     * Marca uma atividade realizada como não planejada (orfã).
     * Desvincula de qualquer treino planejado e registra auditoria.
     */
    @Transactional
    public TreinoRealizado markAsNotPlanned(
            UUID treinoRealizadoId,
            UUID tenantId,
            String actorId) {

        TreinoRealizado realizado = findAndValidate(treinoRealizadoId, tenantId);

        ReconciliationStatus beforeStatus = realizado.getReconciliationStatus();
        Long beforePlannedId = realizado.getTreinoPlanejadoId();

        realizado.setTreinoPlanejadoId(null);
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

    /**
     * Desfaz o vínculo de uma atividade realizada com um treino planejado.
     * Volta para estado PENDENTE e registra auditoria.
     */
    @Transactional
    public TreinoRealizado unlinkManually(
            UUID treinoRealizadoId,
            UUID tenantId,
            String actorId) {

        TreinoRealizado realizado = findAndValidate(treinoRealizadoId, tenantId);

        ReconciliationStatus beforeStatus = realizado.getReconciliationStatus();
        Long beforePlannedId = realizado.getTreinoPlanejadoId();

        realizado.setTreinoPlanejadoId(null);
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

    /**
     * Recupera estado de reconciliação de uma atividade.
     */
    @Transactional(readOnly = true)
    public TreinoRealizado getReconciliationState(UUID treinoRealizadoId, UUID tenantId) {
        return findAndValidate(treinoRealizadoId, tenantId);
    }

    // ===== Private Helpers =====

    private TreinoRealizado findAndValidate(UUID id, UUID tenantId) {
        TreinoRealizado realizado = treinoRealizadoRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("TreinoRealizado não encontrado: " + id));

        if (!realizado.getTenantId().equals(tenantId)) {
            throw new IllegalArgumentException("Tenant mismatch para TreinoRealizado");
        }

        return realizado;
    }

    private void createAuditEvent(
            TreinoRealizado realizado,
            ReconciliationActionType actionType,
            ReconciliationStatus beforeStatus,
            ReconciliationStatus afterStatus,
            Long beforePlannedId,
            Long afterPlannedId,
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
}
