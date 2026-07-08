package br.com.menthoros.backend.services;

import br.com.menthoros.backend.dto.DecisaoProgressao;
import br.com.menthoros.backend.dto.ProgressaoHistoricoResumo;

import java.util.UUID;

public interface ProgressaoTreinoService {

    /**
     * Consolida o histórico de treinos do atleta nas janelas de 7, 21 e 42 dias.
     *
     * Idempotent: YES — leitura pura, sem efeitos colaterais.
     * Side Effects: NONE
     * Tenant-aware: YES — usa TenantContext para buscar treinos planejados.
     */
    ProgressaoHistoricoResumo calcularHistorico(UUID atletaId);

    /**
     * Produz a decisão de progressão a partir do histórico consolidado.
     *
     * Idempotent: YES — cálculo determinístico para o mesmo input.
     * Side Effects: NONE
     * Tenant-aware: NO — opera exclusivamente sobre o resumo pré-calculado.
     */
    DecisaoProgressao calcularDecisao(ProgressaoHistoricoResumo resumo);
}
