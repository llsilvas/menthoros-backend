package br.com.menthoros.backend.dto.output;

import java.util.List;

public record AdesaoDiariaDto(
    String atletaId,
    String nomeAtleta,
    List<SemanaAdesaoDiariaDto> semanas
) {}
