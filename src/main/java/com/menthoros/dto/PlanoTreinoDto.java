package com.menthoros.dto;

import com.menthoros.enums.PlanoStatus;

import java.time.LocalDate;
import java.util.UUID;

public record PlanoTreinoDto(
        UUID id,
        String nome,
        String descricao,
        LocalDate dataInicio,
        LocalDate dataProva,
        String objetivo,
        PlanoStatus status
) {}

