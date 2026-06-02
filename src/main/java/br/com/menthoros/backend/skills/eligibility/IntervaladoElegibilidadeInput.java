package br.com.menthoros.backend.skills.eligibility;

/**
 * Input da skill de elegibilidade para treinos intervalados.
 *
 * <p>Contém apenas os dados fisiológicos e de contexto que a skill efetivamente
 * lê para tomar a decisão de elegibilidade. Não carrega entidades JPA — o serviço
 * chamador é responsável por mapear {@code PlanoMetaDados} e {@code Atleta} para
 * este record antes de invocar {@code IntervaladoElegibilidadeSkill.execute()}.</p>
 *
 * @param tsbProntidaoAtual      TSB pré-treino do atleta (pré-carga do dia).
 *                               Usado nos portões fisiológicos para decidir aptidão.
 * @param ctlAtual               Carga aeróbica crônica atual do atleta.
 * @param rampRateAtual          Taxa de incremento semanal de carga (CTL semanal).
 *                               Limiar seguro: ≤ 8.0. Acima disso indica risco de overtraining.
 * @param temLesaoAtiva          {@code true} se o atleta possui lesão ativa — bloqueador absoluto.
 * @param diasDesdeUltimoIntervalado Dias desde o último treino de alta intensidade.
 *                               Mínimo recomendado: 2 dias. Abaixo disso = recuperação insuficiente.
 * @param fasePeriodizacao       Fase atual de periodização do plano.
 *                               Valores aceitos: "BASE", "BUILD", "ESPECIFICO", "TAPER",
 *                               "SEMANA_PROVA", "POS_PROVA", "DESENVOLVIMENTO_GERAL", "RECOVERY".
 *                               "TAPER" e "RECOVERY" bloqueiam intervalados.
 */
public record IntervaladoElegibilidadeInput(
        double tsbProntidaoAtual,
        double ctlAtual,
        double rampRateAtual,
        boolean temLesaoAtiva,
        int diasDesdeUltimoIntervalado,
        String fasePeriodizacao
) {}
