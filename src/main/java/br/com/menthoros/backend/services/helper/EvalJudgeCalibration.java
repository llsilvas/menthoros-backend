package br.com.menthoros.backend.services.helper;

import java.util.List;

/**
 * Calibração do juiz-LLM (plan-generation-eval-set, fatia 2) — compara a nota do juiz com a nota
 * humana de um coach nas mesmas fixtures, por quadrante (rejeita/revisar/aprova), não por valor
 * exato. "Dado insuficiente" (menos de {@value #MINIMO_CASOS} pares) é tratado como não-calibrado,
 * não como erro — é o estado real desta versão (task 2.3: só 1 fixture de auditoria disponível,
 * nenhum coach ainda avaliou nada).
 *
 * <p>Idempotent: YES. Side Effects: NONE. Tenant-aware: NÃO.
 */
public class EvalJudgeCalibration {

    private static final int MINIMO_CASOS = 20;
    private static final double LIMIAR_CONCORDANCIA = 0.8; // 16/20

    public enum Quadrante {REJEITA, REVISAR, APROVA}

    /** Um par de notas (1-5) para a mesma fixture: a do juiz e a do coach humano. */
    public record ParNotas(int notaJuiz, int notaCoach) {
    }

    public record Resultado(int totalCasos, int concordantes, boolean calibrado) {

        public double taxaConcordancia() {
            return totalCasos == 0 ? 0.0 : (double) concordantes / totalCasos;
        }
    }

    /** Idempotent: YES. Side Effects: NONE. Tenant-aware: NÃO. */
    public Resultado avaliar(List<ParNotas> pares) {
        int concordantes = (int) pares.stream()
                .filter(p -> quadrante(p.notaJuiz()) == quadrante(p.notaCoach()))
                .count();
        boolean casosSuficientes = pares.size() >= MINIMO_CASOS;
        boolean calibrado = casosSuficientes
                && pares.size() > 0
                && (double) concordantes / pares.size() >= LIMIAR_CONCORDANCIA;
        return new Resultado(pares.size(), concordantes, calibrado);
    }

    static Quadrante quadrante(int nota) {
        if (nota <= 2) {
            return Quadrante.REJEITA;
        }
        if (nota == 3) {
            return Quadrante.REVISAR;
        }
        return Quadrante.APROVA;
    }
}
