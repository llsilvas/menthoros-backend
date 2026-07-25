package br.com.menthoros.backend.services.quality;

import br.com.menthoros.backend.enums.RecommendationType;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.List;

/**
 * Verifica, de forma determinística, se a narrativa do foco não contraria o
 * {@code recommendationType} congelado da revisão (D10 — mesmo espírito do
 * {@link PlanQualityChecker}: validar saída de LLM offline, sem outra chamada de modelo).
 *
 * <p>Regra: sob {@code RECOVERY} ou {@code MAINTAIN}, a narrativa não pode conter uma diretiva de
 * progressão. Narrativa reprovada não é persistida — a revisão fica com o template determinístico.
 *
 * <p>Deliberadamente conservador: a checagem é por termo, sem interpretar negação. "Evite aumentar
 * a intensidade" é reprovado mesmo sendo semanticamente aceitável — o custo de um falso positivo é
 * cair no template (que sempre existe e é coerente), enquanto o custo de um falso negativo é
 * entregar ao coach uma recomendação que contradiz o sinal congelado.
 */
@Component
public class WeeklyFocusConsistencyChecker {

    /** Radicais de diretiva de progressão, sem acento e em minúsculas. */
    private static final List<String> TERMOS_DE_PROGRESSAO = List.of(
            "aument",      // aumente, aumentar, aumento
            "progred",     // progredir, progrida
            "eleve", "elevar",
            "increment",   // incremente, incrementar
            "acrescent",   // acrescente, acrescentar
            "suba", "subir",
            "mais volume", "mais carga", "mais intensidade", "mais longo"
    );

    /**
     * Idempotent: YES — função pura, sem estado nem efeito.
     * Side Effects: NONE
     * Tenant-aware: N/A
     *
     * @return {@code true} quando a narrativa pode ser persistida; {@code false} quando deve cair
     *         no template determinístico.
     */
    public boolean isConsistent(@Nullable String narrativa, @Nullable RecommendationType tipo) {
        if (narrativa == null || narrativa.isBlank() || tipo == null) {
            return false;
        }
        if (tipo == RecommendationType.PROGRESS) {
            return true; // progressão é exatamente o que o tipo pede
        }
        String normalizada = normalizar(narrativa);
        return TERMOS_DE_PROGRESSAO.stream().noneMatch(normalizada::contains);
    }

    private String normalizar(String texto) {
        return Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
                .toLowerCase();
    }
}
