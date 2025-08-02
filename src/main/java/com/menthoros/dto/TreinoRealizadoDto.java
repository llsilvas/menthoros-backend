package com.menthoros.dto;

import com.menthoros.enums.DiaSemana;
import com.menthoros.enums.FonteDados;

import java.time.LocalDate;
import java.util.UUID;

public record TreinoRealizadoDto(
        UUID atletaId,
        UUID planoTreinoId,
        Integer cadenciaMedia,
        LocalDate data,
        DiaSemana diaSemana,
        String tipoTreino,
        Integer duracaoMin,
        Double distanciaKm,
        Integer fcMedia,
        Integer fcMax,
        String ritmoMedio,
        Integer potenciaMedia,
        String comentario,
        FonteDados fonteDados
) {
}
