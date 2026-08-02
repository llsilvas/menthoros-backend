package br.com.menthoros.backend.services;

import br.com.menthoros.backend.dto.output.BackfillEtapasOutputDto;

import java.util.UUID;

/**
 * Completa com etapas os treinos intervals.icu importados ANTES da ingestão de etapas existir
 * (D9 da change {@code intervals-icu-activity-laps}).
 *
 * <p>O guard de idempotência do import impede corrigi-los reimportando a activity: ele devolve o
 * registro existente sem tocar a rede. O backfill contorna isso porque ATUALIZA o registro em vez
 * de inserir outro.
 */
public interface IntervalsIcuLapsBackfillService {

    /**
     * Idempotente: YES — treinos já corrigidos saem do conjunto de candidatos na execução seguinte.
     * Side Effects: Database update (apenas inserção de etapas; o summary do treino não é tocado).
     * Tenant-aware: YES — {@code tenantId} recebido por parâmetro e aplicado na query de candidatos.
     */
    BackfillEtapasOutputDto backfillEtapas(UUID atletaId, UUID tenantId);
}
