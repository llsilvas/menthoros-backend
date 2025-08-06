package com.menthoros.dto.input;

import com.menthoros.enums.PlanoStatus;

import java.time.LocalDate;
import java.util.UUID;

public record PlanoTreinoInputDto(
        String nome,
        String descricao,
        LocalDate dataInicio,
        LocalDate dataProva,
        String objetivo,
        PlanoStatus status,
        UUID atletaId
) {}
