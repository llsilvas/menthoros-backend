package com.menthoros.dto.output;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDate;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProvaOutputDto(
        UUID id,
        String nome,
        LocalDate data,
        String distancia,
        String objetivo
) {}
