package br.com.menthoros.backend.enums;

/**
 * Estado do job de geração de planos em lote ({@code tb_batch_plan_job}).
 *
 * <p>Serializado como o nome (string) — o contrato do frontend é uma união de
 * strings, não um enum-objeto. Transições: PENDENTE → EM_PROGRESSO →
 * (CONCLUIDO | CONCLUIDO_COM_ERROS). Os dois últimos são terminais.
 */
public enum BatchJobStatus {
    PENDENTE,
    EM_PROGRESSO,
    CONCLUIDO,
    CONCLUIDO_COM_ERROS
}
