package br.com.menthoros.backend.dto.input;

import br.com.menthoros.backend.enums.DistanciaProva;
import br.com.menthoros.backend.enums.ProvaStatus;
import br.com.menthoros.backend.enums.TipoProva;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

@Schema(description = "Dados de entrada para cadastro ou atualização de prova do atleta")
public record ProvaInputDto(

        @Schema(description = "Nome da prova", example = "Maratona Internacional de São Paulo", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "Nome da prova é obrigatório")
        @Size(max = 200, message = "Nome da prova deve ter no máximo 200 caracteres")
        String nomeProva,

        @Schema(description = "Data da prova", example = "2025-10-12", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "Data da prova é obrigatória")
        LocalDate dataProva,

        @Schema(description = "Tipo da prova", example = "CORRIDA_RUA", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "Tipo da prova é obrigatório")
        TipoProva tipoProva,

        @Schema(description = "Distância da prova", example = "KM_42", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "Distância é obrigatória")
        DistanciaProva distancia,

        @Schema(description = "Distância em quilômetros (para distâncias customizadas)", example = "42.195")
        BigDecimal distanciaKm,

        @Schema(description = "Indica se é a prova alvo principal da temporada", example = "true")
        boolean provaAlvo,

        @Schema(description = "Status da prova", example = "PLANEJADA")
        ProvaStatus statusProva,

        @Schema(description = "Meta de tempo para a prova (HH:mm:ss)", example = "01:45:00")
        LocalTime tempoObjetivo,

        @Schema(description = "Pace objetivo em min/km", example = "4.50")
        BigDecimal paceObjetivo,

        @Schema(description = "TSB alvo no dia da prova", example = "7.0")
        Double tsbIdealProva,

        @Schema(description = "Indica se a prova já foi realizada", example = "false")
        Boolean foiRealizada,

        @Schema(description = "Tempo realizado na prova (HH:mm:ss)", example = "01:48:30")
        LocalTime tempoRealizado,

        @Schema(description = "Posição geral na prova", example = "150")
        Integer posicaoGeral,

        @Schema(description = "Posição na categoria de idade", example = "12")
        Integer posicaoCategoria,

        @Schema(description = "TSS da prova (Training Stress Score)", example = "220")
        Integer tssProva,

        @Schema(description = "Percepção de esforço na prova (1-10)", example = "9")
        Integer percepcaoEsforcoProva,

        @Schema(description = "Feedback sobre a prova", example = "Excelente condição climática, ritmo controlado")
        @Size(max = 2000, message = "Feedback deve ter no máximo 2000 caracteres")
        String feedbackProva,

        @Schema(description = "Quantidade de semanas de preparação para esta prova", example = "16")
        Integer semanasPreparacao,

        @Schema(description = "Data de início da preparação para esta prova", example = "2025-06-22")
        LocalDate inicioPreparacao
) {

    @jakarta.validation.constraints.AssertTrue(message = "Distância customizada exige distanciaKm positivo")
    @Schema(hidden = true)
    public boolean isDistanciaCustomizadaComKm() {
        return distancia != DistanciaProva.CUSTOMIZADA
                || (distanciaKm != null && distanciaKm.signum() > 0);
    }
}
