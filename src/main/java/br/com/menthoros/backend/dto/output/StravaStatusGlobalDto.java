package br.com.menthoros.backend.dto.output;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StravaStatusGlobalDto {
    private Integer totalAtletas;
    private Integer atletasConectados;
    private Double percentualConectado;
}
