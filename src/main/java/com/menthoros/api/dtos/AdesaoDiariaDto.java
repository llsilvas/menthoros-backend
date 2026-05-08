package com.menthoros.api.dtos;

import java.util.List;

public record AdesaoDiariaDto(
    String atletaId,
    String nomeAtleta,
    List<SemanaAdesaoDiariaDto> semanas
) {}
