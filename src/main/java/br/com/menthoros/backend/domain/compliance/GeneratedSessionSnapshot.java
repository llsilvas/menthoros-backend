package br.com.menthoros.backend.domain.compliance;

import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Recorte de uma sessao do plano gerado pelo LLM (pre ou pos-redistribuicao), mapeado na
 * camada de service — o {@code SkeletonComplianceChecker} nunca ve o DTO do LLM nem entidades.
 *
 * @param distanciaKm distancia prescrita, nula quando o treino nao tem — usada so pelo check do
 *                    longao da temporada (add-season-plan-load-enforcement)
 */
public record GeneratedSessionSnapshot(
        LocalDate data,
        String tipoTreino,
        Integer tssPlanejado,
        String intensidadeZona,
        @Nullable BigDecimal distanciaKm
) {

    /** Sessao sem distancia. */
    public GeneratedSessionSnapshot(LocalDate data, String tipoTreino, Integer tssPlanejado, String intensidadeZona) {
        this(data, tipoTreino, tssPlanejado, intensidadeZona, null);
    }
}
