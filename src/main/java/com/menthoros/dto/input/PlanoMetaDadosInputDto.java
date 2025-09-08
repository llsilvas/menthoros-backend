package com.menthoros.dto.input;

import com.menthoros.enums.DiaSemana;
import com.menthoros.enums.FonteDados;

import java.time.LocalDateTime;

public record PlanoMetaDadosInputDto(
        Double volumeSemanalAnterior,
        Integer tsbInicial,
        DiaSemana diaPreferidoLongo,
        FonteDados fonteDados,
        LocalDateTime dataCriacao
) {}
