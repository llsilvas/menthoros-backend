package com.menthoros.dto;

import com.menthoros.enums.PlanoStatus;

import java.time.LocalDate;
import java.util.List;

public record PlanoDto(
                       String nome,
                       String descricao,
                       LocalDate dataInicio,
                       LocalDate dataProva,
                       String objetivo,
                       PlanoStatus status,
                       List<TreinoPlanejadoDto> treinoPlanejado,
                       List<TreinoRealizadoDto> treinoRealizado) {
}
