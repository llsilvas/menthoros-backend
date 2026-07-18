package br.com.menthoros.backend.domain.planner;

import java.util.List;

/**
 * Saida do {@code ConstraintValidator} — violacoes de constraints duras (dias, max sessoes,
 * duracao, equipamento) do {@link OnboardingContext} ou do snapshot legado.
 */
public record ConstraintValidationResult(
        boolean valid,
        List<String> violations
) {
}
