package br.com.menthoros.backend.services.helper;

import org.jspecify.annotations.Nullable;

/**
 * Família de treino que escolhe a receita de normalização. Retorno de {@link #de(String)} é fechado:
 * um tipo novo (ou desconhecido) cai em {@link #PADRAO} — visível como decisão, não como omissão.
 *
 * <p>A comparação é exata ({@code equals}), como em {@code PlanoLlmValidator.normalizarTreino} antes
 * desta change — sem {@code equalsIgnoreCase}, pra não mudar comportamento.</p>
 */
public enum FamiliaTreino {

    /** INTERVALADO e TIRO: expansão NxDist, gate estrutural, normalização de tiros, recheck de duração. */
    INTERVALADO_TIRO,

    /** FARTLEK: expansão "Nx (AccelMin + RecovMin)" e reconciliação de distância. */
    FARTLEK,

    /** REGENERATIVO, CONTINUO, TEMPO_RUN e LONGO: reparo e validação da estrutura de 3 etapas. */
    TRES_ETAPAS,

    /** FACIL, SUBIDA, PROVA, DESCANSO e qualquer tipo desconhecido: só a cauda comum. */
    PADRAO;

    public static FamiliaTreino de(@Nullable String tipoTreino) {
        if (tipoTreino == null) return PADRAO;
        return switch (tipoTreino) {
            case "INTERVALADO", "TIRO" -> INTERVALADO_TIRO;
            case "FARTLEK" -> FARTLEK;
            case "REGENERATIVO", "CONTINUO", "TEMPO_RUN", "LONGO" -> TRES_ETAPAS;
            default -> PADRAO;
        };
    }
}
