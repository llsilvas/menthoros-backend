package br.com.menthoros.backend.domain.planner;

import java.time.LocalDate;

/**
 * Recorte de {@code Prova} mapeado na camada de service. Base da selecao propria do
 * {@code PeriodizationPlanner} (design.md Decisao 7) — prova-alvo explicita vence; sem alvo,
 * mais proxima por data, desempate por maior distancia.
 */
public record ProvaSnapshot(
        LocalDate dataProva,
        Double distanciaKm,
        boolean provaAlvo,
        boolean cancelada
) {
}
