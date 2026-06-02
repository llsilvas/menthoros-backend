package br.com.menthoros.backend.skills.eligibility;

import java.util.List;

/**
 * Payload estruturado retornado pela {@link IntervaladoElegibilidadeSkill}.
 *
 * <p>Encapsula a decisão de elegibilidade para treino intervalado, o motivo
 * determinístico que embasou a decisão e as condições avaliadas.</p>
 *
 * @param elegivel   {@code true} se o atleta está apto a realizar treino intervalado.
 * @param motivo     Descrição humana do principal fator que determinou a decisão.
 *                   Injeta-se no prompt do LLM para que ele não contrarie a decisão.
 * @param condicoes  Lista de condições avaliadas pelos portões de decisão.
 *                   Inclui tanto as condições que permitiram quanto as que bloquearam.
 */
public record IntervaladoElegibilidadePayload(
        boolean elegivel,
        String motivo,
        List<String> condicoes
) {}
