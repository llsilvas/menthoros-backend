package com.menthoros.dto.output;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.menthoros.entity.PlanoSemanal;
import com.menthoros.enums.PlanoStatus;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PlanoTreinoOutputDto(
        UUID id,
        String nome,
        String descricao,
        LocalDate dataInicio,
        LocalDate dataProva,
        String objetivo,
        PlanoStatus status,
        List<PlanoSemanal> planoSemanalList
) {}
