package com.menthoros.dto;

import com.menthoros.enums.DiaTreinoEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TreinoPlanejadoDto {

    private DiaTreinoEnum diaSemana;
    private String tipoTreino;
    private String descricao;
    private String fcAlvo;
    private Integer duracaoMin;
    private Double distancia;
    private String ritmoAlvo;

}
