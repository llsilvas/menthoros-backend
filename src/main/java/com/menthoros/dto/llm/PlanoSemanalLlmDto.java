package com.menthoros.dto.llm;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.menthoros.enums.PlanoStatus;
import lombok.Builder;

import java.time.LocalDate;
import java.util.List;

@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PlanoSemanalLlmDto(
        double volumePlanejadoKm,
        double volumeAlvoKm,
        Double tsbInicio,
        Double tsbFim,
        String status,
        String objetivoSemanal,
        List<TreinoPlanejadoLlmDto> treinosPlanejados
) {}
