package com.menthoros.dto.output;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.menthoros.enums.PlanoStatus;
import lombok.Builder;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PlanoSemanalOutputDto(
        UUID atletaId,
        UUID planoTreinoId,
        LocalDate semanaInicio,
        LocalDate semanaFim,
        double volumePlanejadoKm,
        double volumeRealizadoKm,
        double volumeAlvoKm,
        Double tsbInicio,
        Double tsbFim,
        PlanoStatus status,
        String observacoes,
        String objetivoSemanal,
        List<TreinoPlanejadoOutputDto> treinosPlanejados
) {}
