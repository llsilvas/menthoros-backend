package br.com.menthoros.backend.dto.llm;

import com.fasterxml.jackson.annotation.JsonInclude;
import br.com.menthoros.backend.enums.PlanoStatus;
import lombok.Builder;

import java.time.LocalDate;
import java.util.List;

@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PlanoSemanalLlmDto(
        double volumePlanejadoKm,
        double volumeAlvoKm,
        Double tsbInicio,
        Double tsbFim,
        String status,
        String objetivoSemanal,
        List<TreinoPlanejadoLlmDto> treinosPlanejados
) {}
