package com.menthoros.api.dtos;

import java.util.List;

public record SemanaAdesaoDiariaDto(
    String semana,
    String dataInicio,
    String dataFim,
    Double percentualGeral,
    List<DiaAdesaoDto> dias
) {}
