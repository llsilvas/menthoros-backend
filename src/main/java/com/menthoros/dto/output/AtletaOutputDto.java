package com.menthoros.dto.output;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.menthoros.enums.DiaSemana;

import java.util.Set;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AtletaOutputDto(UUID id,
                              String nome,
                              int idade,
                              String objetivo,
                              Set<DiaSemana> diasDisponiveis,
                              DiaSemana diaPreferidoLongo) {
}
