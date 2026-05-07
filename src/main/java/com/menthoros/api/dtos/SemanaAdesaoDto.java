package com.menthoros.api.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SemanaAdesaoDto {
    private String semana;                    // ISO week string "2026-W18"
    private String dataInicio;                // "2026-05-04"
    private String dataFim;                   // "2026-05-10"
    private Integer treinosPlanejados;
    private Integer treinosRealizados;
    private Double percentualRealizacao;      // 0-100
    private Integer diasComTreino;
}
