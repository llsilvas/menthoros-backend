package br.com.menthoros.backend.enums;

/**
 * O que um {@link br.com.menthoros.backend.entity.PlanoSemanal} fez com a
 * {@link br.com.menthoros.backend.entity.RevisaoSemanal} que consumiu como insumo
 * (termo "Desfecho da Revisão Consumida" no CONTEXT.md).
 *
 * <p>Vive no plano, não na revisão: uma revisão é 1:N com os planos que a consomem (rejeitar e
 * regerar faz dois planos consumirem a mesma revisão), e num campo único da revisão o segundo
 * desfecho sobrescreveria o primeiro — apagando justamente a rejeição que interessa ao moat.
 *
 * <p><b>Leitura honesta:</b> é proxy de segunda ordem — mede se algum treino do plano foi mexido,
 * o que capta tanto discordância do foco quanto ajuste por logística. Usar agregado, nunca como
 * decisão individual sobre o julgamento do coach.
 */
public enum ConsumedReviewOutcome {

    /** Revisão consumida; o plano ainda não foi aprovado nem rejeitado. */
    PENDING,

    /** Aprovado pelo coach sem nenhum treino editado ou adicionado. */
    NO_ADJUSTMENT,

    /** Aprovado pelo coach com ao menos um treino editado ou adicionado. */
    ADJUSTED,

    /** Rejeitado pelo coach. */
    PLAN_REJECTED,

    /**
     * A revisão não entrou no insumo — flag desligada, revisão ausente ou fora da janela de
     * validade. É ausência de dado, não julgamento negativo: fica fora do denominador do sinal.
     */
    NOT_CONSUMED,

    /**
     * Plano auto-aprovado por {@link OrigemAprovacao#AUTO_CONFIANCA_ALTA} — não houve coach para
     * julgar o foco. Contar como aceitação inflaria o sinal com planos que ninguém revisou.
     */
    NO_COACH_IN_LOOP
}
