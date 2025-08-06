package com.menthoros.dto.output;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.menthoros.enums.DiaSemana;
import com.menthoros.enums.TipoTreino;

import java.time.LocalDate;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TreinoPlanejadoOutputDto(
        DiaSemana diaSemana,
        TipoTreino tipoTreino,
        String descricao,
        String observacao,
        String fcAlvo,
        LocalDate dataTreino,
        Integer percepcaoEsforcoEsperada,
        Integer duracaoMin,
        Double distanciaKm,
        String ritmoAlvo,
        UUID planoSemanalId
) {}
