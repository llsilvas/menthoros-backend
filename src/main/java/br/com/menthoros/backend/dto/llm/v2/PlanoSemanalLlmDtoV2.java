package br.com.menthoros.backend.dto.llm.v2;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;

import java.util.List;

/**
 * Contrato de saída da LLM para a semana inteira, schema v2 — mesmos campos de nível-plano que
 * {@link br.com.menthoros.backend.dto.llm.PlanoSemanalLlmDto} (v1), só {@code treinosPlanejados}
 * muda de shape.
 */
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PlanoSemanalLlmDtoV2(
        double volumePlanejadoKm,
        double volumeAlvoKm,
        Double tsbInicio,
        Double tsbFim,
        String status,
        String objetivoSemanal,
        List<TreinoPlanejadoLlmDtoV2> treinosPlanejados
) {
}
