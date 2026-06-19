package br.com.menthoros.backend.services;

import br.com.menthoros.backend.dto.output.CoachAttentionItemOutputDto;

import java.util.List;

/**
 * Fila de atenção do treinador: consolida e prioriza, on-demand e read-only, os sinais já produzidos
 * pelo backend para os atletas do tenant. Tenant-aware (resolve o tenant via TenantContext).
 */
public interface CoachAttentionQueueService {

    /**
     * Itens de atenção priorizados do tenant (apenas severidade ≥ ALTA na v1).
     * Idempotent: YES. Side Effects: NONE. Tenant-aware: YES.
     *
     * @return itens ordenados por severidade/priorityScore (no máx. {@code MAX_ITENS}); vazio se nada exige ação
     */
    List<CoachAttentionItemOutputDto> getAttentionQueue();
}
