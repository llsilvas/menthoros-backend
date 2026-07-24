package br.com.menthoros.backend.services;

import br.com.menthoros.backend.entity.PlanoSemanal;
import br.com.menthoros.backend.entity.RevisaoSemanal;

import java.util.UUID;

/**
 * Consolidação determinística da revisão semanal (Fatia 1 — add-weekly-review-consolidation).
 */
public interface RevisaoSemanalService {

    /**
     * Consolida — <b>sem persistir</b> — o sinal determinístico da revisão a partir de um
     * {@code PlanoSemanal} encerrado: aderência por contagem na janela do plano,
     * {@code dadosSuficientes} e {@code recommendationType} sobre {@code tsbFim} (ADR-0006).
     */
    RevisaoSemanal consolidar(PlanoSemanal plano);

    /**
     * Gera e <b>congela</b> a revisão de um plano recém-encerrado (hook do
     * {@code SemanaEncerradaEvent}). Insert-if-absent: se o plano não está {@code CONCLUIDO}
     * (evento de perdidos-only) ou já tem revisão, é no-op — preserva o congelamento (CA1, CA6).
     * Escopado ao tenant (CA7).
     */
    void gerarNoEncerramento(UUID planoId, UUID tenantId);
}
