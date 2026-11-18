package br.com.menthoros.backend.exception;

/**
 * O plano de temporada está desligado ({@code season-plan.enabled=false}).
 *
 * <p>Mapeada para {@code 404 SEASON_PLAN_DISABLED}. O código próprio existe para o frontend
 * esconder a aba só neste caso; um 404 comum (atleta inexistente ou de outra assessoria) mostra
 * o estado de erro (add-season-plan-generate-tracer, D12).
 */
public class SeasonPlanDisabledException extends RuntimeException {
    public SeasonPlanDisabledException() {
        super("Plano de temporada desabilitado");
    }
}
