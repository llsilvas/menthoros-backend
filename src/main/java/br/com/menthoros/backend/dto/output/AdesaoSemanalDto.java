package br.com.menthoros.backend.dto.output;

import java.util.List;

public record AdesaoSemanalDto(
    String atletaId,
    String nomeAtleta,
    SemanaAdesaoDto semanaAtual,
    List<SemanaAdesaoDto> ultimas4Semanas,
    Double mediaUltimas4Semanas
) {}
