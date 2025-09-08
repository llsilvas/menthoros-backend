package com.menthoros.dto.output;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.menthoros.enums.DiaSemana;
import com.menthoros.enums.NivelExperiencia;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AtletaOutputDto(UUID id,
                              String nome,
                              int idade,
                              BigDecimal pesoKg,
                              BigDecimal alturaCm,
                              String objetivo,
                              NivelExperiencia nivelExperiencia,
                              Set<DiaSemana> diasDisponiveis,
                              DiaSemana diaPreferidoLongo,
                              boolean temLesao,
                              String descricaoLesao,
                              List<ProvaOutputDto> provas) {
}
