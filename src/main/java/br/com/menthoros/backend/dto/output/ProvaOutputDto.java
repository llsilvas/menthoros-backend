package br.com.menthoros.backend.dto.output;

import com.fasterxml.jackson.annotation.JsonInclude;
import br.com.menthoros.backend.enums.DistanciaProva;
import br.com.menthoros.backend.enums.ProvaStatus;
import br.com.menthoros.backend.enums.TipoProva;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

@Schema(description = "Dados de saída de uma prova do atleta")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProvaOutputDto(

        @Schema(description = "Identificador único da prova")
        UUID id,

        @Schema(description = "Nome da prova", example = "Maratona Internacional de São Paulo")
        String nomeProva,

        @Schema(description = "Data da prova", example = "2025-10-12")
        LocalDate dataProva,

        @Schema(description = "Tipo da prova")
        TipoProva tipoProva,

        @Schema(description = "Distância da prova")
        DistanciaProva distancia,

        @Schema(description = "Distância em quilômetros (para distâncias customizadas)", example = "42.195")
        BigDecimal distanciaKm,

        @Schema(description = "Indica se é a prova alvo principal da temporada", example = "true")
        boolean provaAlvo,

        @Schema(description = "Status da prova", example = "PLANEJADA")
        ProvaStatus statusProva,

        @Schema(description = "Meta de tempo para a prova", example = "01:45:00")
        LocalTime tempoObjetivo,

        @Schema(description = "Pace objetivo em min/km", example = "4.50")
        BigDecimal paceObjetivo,

        @Schema(description = "TSB alvo no dia da prova", example = "7.0")
        Double tsbIdealProva,

        @Schema(description = "Indica se a prova já foi realizada", example = "false")
        Boolean foiRealizada,

        @Schema(description = "Tempo realizado na prova", example = "01:48:30")
        LocalTime tempoRealizado,

        @Schema(description = "Posição geral na prova", example = "150")
        Integer posicaoGeral,

        @Schema(description = "Posição na categoria de idade", example = "12")
        Integer posicaoCategoria,

        @Schema(description = "TSS da prova", example = "220")
        Integer tssProva,

        @Schema(description = "Percepção de esforço na prova (1-10)", example = "9")
        Integer percepcaoEsforcoProva,

        @Schema(description = "Feedback sobre a prova")
        String feedbackProva,

        @Schema(description = "Quantidade de semanas de preparação", example = "16")
        Integer semanasPreparacao,

        @Schema(description = "Data de início da preparação", example = "2025-06-22")
        LocalDate inicioPreparacao,

        @Schema(description = "Dias faltando para a prova", example = "45")
        int diasFaltando
) {
}
