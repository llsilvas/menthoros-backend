package com.menthoros.dto.output;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.menthoros.enums.ProvaStatus;
import com.menthoros.enums.TipoProva;

import java.time.LocalDate;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProvaOutputDto(
        UUID id,
        String nomeProva,
        LocalDate dataProva,
        Double distanciaKm,
        TipoProva tipoProva,
        ProvaStatus statusProva,
        boolean provaAlvo,
        String objetivo
) {}
