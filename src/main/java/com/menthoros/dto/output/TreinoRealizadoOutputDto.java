package com.menthoros.dto.output;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.menthoros.enums.DiaSemanaEnum;
import com.menthoros.enums.FonteDados;
import com.menthoros.enums.StatusTreino;

import java.time.LocalDate;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TreinoRealizadoOutputDto(
        UUID id,
        Integer cadenciaMedia,
        LocalDate dataTreino,
        DiaSemanaEnum diaSemana,
        String tipoTreino,
        Integer duracaoMin,
        Double distanciaKm,
        Integer fcMedia,
        Integer fcMax,
        String ritmoMedio,
        Integer potenciaMedia,
        String comentario,
        FonteDados fonteDados,
        StatusTreino status,
        Integer percepcaoEsforco
) {}
