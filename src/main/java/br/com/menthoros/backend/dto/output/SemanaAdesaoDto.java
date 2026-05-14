package br.com.menthoros.backend.dto.output;

public record SemanaAdesaoDto(
    String semana,                      // ISO week string "2026-W18"
    String dataInicio,                  // "2026-05-04"
    String dataFim,                     // "2026-05-10"
    Integer treinosPlanejados,
    Integer treinosRealizados,
    Double percentualRealizacao,        // 0-100
    Integer diasComTreino
) {}
