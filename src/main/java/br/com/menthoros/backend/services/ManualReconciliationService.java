package br.com.menthoros.backend.services;

import br.com.menthoros.backend.entity.TreinoRealizado;

import java.util.UUID;

/**
 * Manual reconciliation actions with audit trail in tb_treino_reconciliacao.
 * Four operations: link manually, mark as not planned, unlink, get state.
 */
public interface ManualReconciliationService {
    TreinoRealizado linkManually(UUID treinoRealizadoId, UUID treinoPlanejadoId, UUID tenantId, String actorId);
    TreinoRealizado markAsNotPlanned(UUID treinoRealizadoId, UUID tenantId, String actorId);
    TreinoRealizado unlinkManually(UUID treinoRealizadoId, UUID tenantId, String actorId);
    TreinoRealizado getReconciliationState(UUID treinoRealizadoId, UUID tenantId);
}
