package br.com.menthoros.backend.skills.recovery;

/**
 * Payload produzido pela {@link RecoveryCargaSkill} após analisar o estado de recuperação do atleta.
 *
 * <p>Encapsula a classificação fisiológica do estado atual do atleta e os flags
 * de impacto na prescrição de treino. Usado pelo orquestrador e pelo gerador de prompts
 * para contextualizar o LLM sobre as condições do atleta.</p>
 *
 * @param classificacao          Estado do atleta: "FRESCO", "NEUTRO", "FATIGADO" ou "SOBRECARREGADO"
 * @param descricao              Descrição textual detalhada do estado fisiológico identificado
 * @param requerDescanso         {@code true} se o atleta necessita de descanso imediato
 * @param bloqueiaAltaIntensidade {@code true} se treinos de alta intensidade estão contraindicados
 */
public record RecoveryCargaPayload(
        String classificacao,
        String descricao,
        boolean requerDescanso,
        boolean bloqueiaAltaIntensidade
) {}
