package br.com.menthoros.backend.dto.intervalsicu;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Resposta de {@code GET /api/v1/athlete/{id}/pace-curves.json} — só os campos que a feature
 * de melhores esforços usa do {@code DataCurveSetPaceCurve} real. {@code activities} (metadado de
 * qual atividade gerou cada marca) fica de fora de propósito (fora do escopo desta change).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record IcuPaceCurveDto(
        List<Curva> list
) {
    /**
     * {@code distance} e {@code values} são arrays paralelos: {@code distance[i]} é a distância em
     * metros do ponto {@code i}, {@code values[i]} é o tempo em segundos pra cobrir aquela
     * distância na janela consultada.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Curva(
            List<Double> distance,
            List<Integer> values
    ) {}
}
