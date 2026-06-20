package br.com.menthoros.backend.services;

import br.com.menthoros.backend.dto.output.CoachAttentionItemOutputDto;

import java.util.List;
import java.util.UUID;

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

    /**
     * Sinais de atenção para um atleta específico do tenant, limitados a {@code limite} itens.
     * Idempotent: YES. Side Effects: NONE. Tenant-aware: YES.
     *
     * @param atletaId ID do atleta (deve pertencer ao tenant do contexto)
     * @param limite   número máximo de itens retornados
     * @return sinais ordenados por severidade; vazio se nenhum sinal ativo
     */
    List<CoachAttentionItemOutputDto> getSinaisParaAtleta(UUID atletaId, int limite);
}
