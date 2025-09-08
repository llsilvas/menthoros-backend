package com.menthoros.dto.input;

import com.menthoros.enums.DiaSemana;
import com.menthoros.enums.FonteDados;
import com.menthoros.enums.TipoTreino;
import com.menthoros.enums.TreinoExecucaoStatus;

import java.time.LocalDate;
import java.util.UUID;

public record TreinoRealizadoInputDto(
        UUID atletaId,
        UUID planoSemanalId,
        UUID treinoPlanejadoId,
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
        Integer percepcaoEsforco,
        String externalId,
        Integer tempoExecucaoSegundos,
        Integer elevacaoTotalMetros
) {}
