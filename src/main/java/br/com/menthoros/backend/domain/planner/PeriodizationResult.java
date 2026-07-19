package br.com.menthoros.backend.domain.planner;

import java.util.List;
import java.util.Optional;

/**
 * Saida do {@code PeriodizationPlanner} (design.md Decisao 7): a fase macro da semana, a prova
 * que a determinou (a prova-alvo, ou a mais proxima quando nao ha alvo explicito) e provas
 * preparatorias que caem dentro da semana como constraint estrutural, sem alterar a fase macro.
 */
public record PeriodizationResult(
        TrainingPhase phase,
        Optional<ProvaSnapshot> provaDeterminante,
        List<ProvaSnapshot> provasPreparatoriasNaSemana
) {
}
