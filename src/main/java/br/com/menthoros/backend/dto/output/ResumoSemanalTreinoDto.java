package br.com.menthoros.backend.dto.output;

import java.util.Map;
import java.util.UUID;

public record ResumoSemanalTreinoDto(
    UUID atletaId,
    String nomeAtleta,
    String semana,                  // "2026-W18"
    String dataInicio,              // "2026-05-04"
    String dataFim,                 // "2026-05-10"
    Resumo resumo
) {
    public record Resumo(
        Integer totalTreinos,
        Double volumeTotalKm,
        Double tssTotalSemana,
        Double tempoTotalMinutos,
        Integer diasComTreino,
        Integer diasSemTreino,
        String ultimoTreino,                            // "2026-05-08"
        Map<String, ResumoDetalhesDto> diasDaSemana    // key: "MONDAY", "TUESDAY", etc.
    ) {}
}
