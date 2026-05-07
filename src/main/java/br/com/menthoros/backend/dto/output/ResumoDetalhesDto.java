package br.com.menthoros.backend.dto.output;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ResumoDetalhesDto {
    private Integer treinos;
    private Double km;
    private Double tss;
}
