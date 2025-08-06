package com.menthoros.dto.input;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.menthoros.enums.PlanoStatus;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PlanoSemanalInputDto(
        UUID id,
        @NotNull
        UUID atletaId,
        UUID planoTreinoId,
        @NotNull
        LocalDate semanaInicio,
        @NotNull
        LocalDate semanaFim,
        double volumePlanejadoKm,
        double volumeRealizadoKm,
        double volumeAlvoKm,
        Double tsbInicio,
        Double tsbFim,
        @NotNull
        PlanoStatus status,
        String observacoes,
        String objetivoSemanal,
        List<TreinoPlanejadoInputDto> treinosPlanejados
) {}
