package com.menthoros.dto.llm;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.menthoros.enums.PlanoStatus;
import lombok.Builder;

import java.time.LocalDate;
import java.util.List;

@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PlanoSemanalLlmDto(
//        LocalDate semanaInicio,
//        LocalDate semanaFim,
        double volumePlanejadoKm,
        double volumeRealizadoKm,
        double volumeAlvoKm,
        Double tsbInicio,
        Double tsbFim,
        PlanoStatus status,
//        String observacoes,
        String objetivoSemanal,
        List<TreinoPlanejadoLlmDto> treinosPlanejados
) {}
