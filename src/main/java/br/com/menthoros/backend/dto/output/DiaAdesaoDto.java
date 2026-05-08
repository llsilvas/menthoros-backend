package br.com.menthoros.backend.dto.output;

public record DiaAdesaoDto(
    String data,
    String diaSemana,
    Integer treinosPlanejados,
    Integer treinosRealizados,
    Double percentual
) {}
