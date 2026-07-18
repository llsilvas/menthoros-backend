package br.com.menthoros.backend.domain.planner;

import java.time.LocalDate;

/**
 * Recorte de {@code TreinoRealizado} mapeado na camada de service. Usado pelo
 * {@code LoadTargetResolver} (consome executado, nao planejado — design.md Decisao 5) e pelo
 * {@code InjuryRiskEvaluator} (monotonia via janela de 7 dias).
 */
public record TreinoRealizadoSnapshot(
        LocalDate dataTreino,
        Integer tssCalculado
) {
}
