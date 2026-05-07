package br.com.menthoros.backend.dto.output;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.Map;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ResumoSemanalTreinoDto {
    private UUID atletaId;
    private String nomeAtleta;
    private String semana;                        // "2026-W18"
    private String dataInicio;                    // "2026-05-04"
    private String dataFim;                       // "2026-05-10"
    private Resumo resumo;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Resumo {
        private Integer totalTreinos;
        private Double volumeTotalKm;
        private Double tssTotalSemana;
        private Double tempoTotalMinutos;
        private Integer diasComTreino;
        private Integer diasSemTreino;
        private String ultimoTreino;              // "2026-05-08"
        private Map<String, ResumoDetalhesDto> diasDaSemana;  // key: "MONDAY", "TUESDAY", etc.
    }
}
