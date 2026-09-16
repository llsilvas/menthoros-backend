package br.com.menthoros.backend.services.helper;

import org.jspecify.annotations.Nullable;

import java.util.regex.Pattern;

/**
 * Redação de PII em blocos de texto livre congelados em fixtures de eval
 * (plan-generation-eval-set) — ao lado de {@link LlmCallLedger#redigirNome}, que só cobre nome do
 * atleta. Cobre idade exata, nome de prova e cidade/clube, quando presentes em texto livre como
 * {@code justificativaIa} ou {@code review_comment}.
 *
 * <p>Idempotent: YES — leitura pura, sem mutação de estado. Side Effects: NONE. Tenant-aware: NÃO —
 * opera sobre texto solto, não sobre entidade tenant-scoped.
 *
 * <p>Deliberadamente sem {@code @Component} — nunca instanciada pelo Spring (achado do /qa).
 */
public class EvalPiiRedactor {

    static final String MARCADOR = "[REDIGIDO]";

    /**
     * Redige todo o PII conhecido em {@code alvo} dentro de {@code texto}. Idempotent: YES. Side
     * Effects: NONE. Tenant-aware: NÃO.
     */
    public String redigir(@Nullable String texto, @Nullable PiiAlvo alvo) {
        if (texto == null || texto.isBlank() || alvo == null) {
            return texto;
        }
        String resultado = LlmCallLedger.redigirNome(texto, alvo.nomeAtleta());
        resultado = redigirIdade(resultado, alvo.idade());
        resultado = redigirTermo(resultado, alvo.nomeProva());
        resultado = redigirTermo(resultado, alvo.cidade());
        resultado = redigirTermo(resultado, alvo.clube());
        return resultado;
    }

    private String redigirIdade(String texto, @Nullable Integer idade) {
        if (idade == null) {
            return texto;
        }
        // Só redige quando o número aparece junto de "anos" — evita apagar paces/distâncias/TSS
        // que coincidentemente batem com o valor da idade.
        return texto.replaceAll("(?iuU)\\b" + idade + "\\s*anos\\b", MARCADOR);
    }

    private String redigirTermo(String texto, @Nullable String termo) {
        if (termo == null || termo.isBlank()) {
            return texto;
        }
        String resultado = texto.replaceAll("(?iu)" + Pattern.quote(termo.trim()), MARCADOR);
        for (String parte : termo.trim().split("\\s+")) {
            if (parte.length() >= 3) {
                resultado = resultado.replaceAll("(?iuU)\\b" + Pattern.quote(parte) + "\\b", MARCADOR);
            }
        }
        return resultado;
    }

    /** Campos de PII conhecidos do atleta/geração a redigir num bloco de texto livre. */
    public record PiiAlvo(@Nullable String nomeAtleta, @Nullable Integer idade,
                           @Nullable String nomeProva, @Nullable String cidade, @Nullable String clube) {
    }
}
