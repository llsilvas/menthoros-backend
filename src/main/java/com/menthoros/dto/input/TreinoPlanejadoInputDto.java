package com.menthoros.dto.input;

import com.menthoros.enums.DiaSemana;
import com.menthoros.enums.TipoTreino;

import java.time.LocalDate;
import java.util.UUID;

public record TreinoPlanejadoInputDto(
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
        UUID planoSemanalId,
        UUID atletaId
) {}
