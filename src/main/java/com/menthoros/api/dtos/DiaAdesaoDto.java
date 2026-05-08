package com.menthoros.api.dtos;

public record DiaAdesaoDto(
    String data,
    String diaSemana,
    Integer treinosPlanejados,
    Integer treinosRealizados,
    Double percentual
) {}
