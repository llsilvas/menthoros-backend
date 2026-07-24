package br.com.menthoros.backend.services;

import br.com.menthoros.backend.entity.PlanoSemanal;
import br.com.menthoros.backend.entity.RevisaoSemanal;

/**
 * Consolidação determinística da revisão semanal (Fatia 1 — add-weekly-review-consolidation).
 */
public interface RevisaoSemanalService {

    /**
     * Consolida — <b>sem persistir</b> — o sinal determinístico da revisão a partir de um
     * {@code PlanoSemanal} encerrado: aderência por contagem na janela do plano,
     * {@code dadosSuficientes} e {@code recommendationType} sobre {@code tsbFim} (ADR-0006).
     * A persistência idempotente e o hook no encerramento são do bloco 3.
     */
    RevisaoSemanal consolidar(PlanoSemanal plano);
}
