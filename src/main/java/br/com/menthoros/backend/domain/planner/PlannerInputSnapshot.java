package br.com.menthoros.backend.domain.planner;

import br.com.menthoros.backend.dto.DecisaoProgressao;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Anti-corruption layer entre a camada de service (entidades JPA) e o {@link PlannerEngine}
 * (design.md Decisao 17). O mapper entity-&gt;record fica na camada de service — o dominio
 * nunca ve {@code DadosPlanoDto} nem entidades.
 *
 * <p>{@code referenceDate} e sempre explicito: o {@link PlannerEngine} nunca chama
 * {@code LocalDate.now()} internamente, garantindo determinismo do golden set.
 */
public record PlannerInputSnapshot(
        AthleteSnapshot athlete,
        DecisaoProgressao decisaoProgressao,
        List<ProvaSnapshot> provas,
        List<TreinoRealizadoSnapshot> historico,
        Optional<OnboardingContext> onboardingContext,
        LocalDate referenceDate
) {
}
