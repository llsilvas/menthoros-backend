package br.com.menthoros.backend.services;

import br.com.menthoros.backend.dto.input.BatchGeracaoPlanoInputDto;
import br.com.menthoros.backend.dto.output.BatchJobStatusOutputDto;
import br.com.menthoros.backend.dto.output.BatchLoteAceitoOutputDto;

import java.util.UUID;

/**
 * Orquestra a geração assíncrona de planos de treino em lote.
 */
public interface BatchPlanService {

    /**
     * Cria o job (status PENDENTE) e dispara o processamento assíncrono, retornando
     * imediatamente o id do job e o total (deduplicado) de atletas para o 202.
     *
     * Idempotent: NO — cria um novo job a cada chamada.
     * Side Effects: INSERT do job + disparo assíncrono da geração (chamadas ao LLM).
     * Tenant-aware: YES — o tenantId é capturado na thread HTTP e propagado ao fluxo assíncrono.
     *
     * @param input    atletas e modo de geração (validados no controller)
     * @param tenantId tenant do coach autenticado, resolvido na thread HTTP
     * @return jobId + total de atletas distintos do lote
     */
    BatchLoteAceitoOutputDto iniciarLote(BatchGeracaoPlanoInputDto input, UUID tenantId);

    /**
     * Retorna o estado atual do job (para polling). Detalhes por atleta só são
     * preenchidos no estado terminal.
     *
     * Idempotent: YES — leitura pura.
     * Side Effects: NONE.
     * Tenant-aware: YES — 404 se o job não pertence ao tenant.
     *
     * @param jobId    id do job
     * @param tenantId tenant do coach autenticado
     * @return estado atual do job
     */
    BatchJobStatusOutputDto consultarStatus(UUID jobId, UUID tenantId);
}
