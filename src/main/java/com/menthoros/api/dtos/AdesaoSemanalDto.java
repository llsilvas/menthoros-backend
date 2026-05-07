package com.menthoros.api.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdesaoSemanalDto {
    private String atletaId;
    private String nomeAtleta;
    private SemanaAdesaoDto semanaAtual;
    private List<SemanaAdesaoDto> ultimas4Semanas;
    private Double mediaUltimas4Semanas;
}
