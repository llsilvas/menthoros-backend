package com.menthoros.dto.input;

import com.menthoros.enums.DiaSemanaEnum;
import com.menthoros.enums.FonteDados;

import java.time.LocalDateTime;

public record PlanoMetaDadosInputDto(
        Double volumeSemanalAnterior,
        Integer tsbInicial,
        DiaSemanaEnum diaPreferidoLongo,
        FonteDados fonteDados,
        LocalDateTime dataCriacao
) {}
