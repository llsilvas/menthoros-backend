package br.com.menthoros.backend.dto.llm.v2;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;

import java.util.List;

/**
 * Contrato de saída da LLM para a semana inteira, schema v2 — mesmos campos de nível-plano que
 * {@link br.com.menthoros.backend.dto.llm.PlanoSemanalLlmDto} (v1), só {@code treinosPlanejados}
 * muda de shape.
 */
@Builder(toBuilder = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PlanoSemanalLlmDtoV2(
        double volumePlanejadoKm,
        double volumeAlvoKm,
        Double tsbInicio,
        Double tsbFim,
        String status,
        String objetivoSemanal,
        List<TreinoPlanejadoLlmDtoV2> treinosPlanejados,

        /** Dias de descanso, fora da lista de treinos (add-descanso-explicito-por-fadiga). */
        List<br.com.menthoros.backend.dto.llm.RestDayLlmDto> restDays
) {
    public PlanoSemanalLlmDtoV2 {
        restDays = restDays == null ? List.of() : List.copyOf(restDays);
    }
}
