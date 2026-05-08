package br.com.menthoros.backend.dto.output;

import java.util.List;

public record SemanaAdesaoDiariaDto(
    String semana,
    String dataInicio,
    String dataFim,
    Double percentualGeral,
    List<DiaAdesaoDto> dias
) {}
