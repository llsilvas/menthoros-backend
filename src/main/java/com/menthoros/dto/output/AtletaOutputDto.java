package com.menthoros.dto.output;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.menthoros.enums.DiaSemanaEnum;
import com.menthoros.enums.NivelExperiencia;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AtletaOutputDto(UUID id,
                              String nome,
                              int idade,
                              double pesoKg,
                              double alturaCm,
                              String objetivo,
                              NivelExperiencia nivelExperiencia,
                              Set<DiaSemanaEnum> diasDisponiveis,
                              DiaSemanaEnum diaPreferidoLongo,
                              boolean temLesao,
                              String descricaoLesao,
                              List<ProvaOutputDto> provas) {
}
