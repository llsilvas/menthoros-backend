package com.menthoros.dto.output;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.menthoros.enums.DiaSemana;
import com.menthoros.enums.FonteDados;

import java.time.LocalDateTime;
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PlanoMetaDadosOutputDto(
        Double volumeSemanalAnterior,
        Integer tsbInicial,
        DiaSemana diaPreferidoLongo,
        FonteDados fonteDados,
        LocalDateTime dataCriacao
) {}
