package br.com.menthoros.backend.dto.llm;

import com.fasterxml.jackson.annotation.JsonInclude;
import br.com.menthoros.backend.enums.PlanoStatus;
import lombok.Builder;

import java.time.LocalDate;
import java.util.List;

@Builder(toBuilder = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PlanoSemanalLlmDto(
        double volumePlanejadoKm,
        double volumeAlvoKm,
        Double tsbInicio,
        Double tsbFim,
        String status,
        String objetivoSemanal,
        List<TreinoPlanejadoLlmDto> treinosPlanejados,

        /**
         * Dias prescritos como descanso, fora da lista de treinos
         * (add-descanso-explicito-por-fadiga). Nunca nulo: lista vazia quando a semana é toda de
         * treino — assim ninguém precisa checar null ao somar ou iterar.
         */
        List<RestDayLlmDto> restDays
) {
    public PlanoSemanalLlmDto {
        restDays = restDays == null ? List.of() : List.copyOf(restDays);
    }
}
