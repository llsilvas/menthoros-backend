package br.com.menthoros.backend.domain.compliance;

import java.util.List;

/**
 * Plano gerado pelo LLM (pre-redistribuicao) ou treinos ja redistribuidos (pos-redistribuicao),
 * na forma que o {@code SkeletonComplianceChecker} consome para compliance hipotetico.
 */
public record GeneratedPlanSnapshot(
        List<GeneratedSessionSnapshot> sessoes
) {
    public int tssTotal() {
        return sessoes.stream()
                .mapToInt(s -> s.tssPlanejado() != null ? s.tssPlanejado() : 0)
                .sum();
    }
}
