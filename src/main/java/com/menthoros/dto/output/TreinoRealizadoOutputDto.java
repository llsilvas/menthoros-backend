package com.menthoros.dto.output;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.menthoros.enums.DiaSemana;
import com.menthoros.enums.FonteDados;
import com.menthoros.enums.TipoTreino;
import com.menthoros.enums.TreinoExecucaoStatus;

import java.time.LocalDate;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TreinoRealizadoOutputDto(
        UUID id,
        Integer cadenciaMedia,
        LocalDate dataTreino,
        DiaSemana diaSemana,
        TipoTreino tipoTreino,
        Integer duracaoMin,
        Double distanciaKm,
        Integer fcMedia,
        Integer fcMax,
        String ritmoMedio,
        Integer potenciaMedia,
        String comentario,
        FonteDados fonteDados,
        TreinoExecucaoStatus status,
        Integer percepcaoEsforco
) {}
