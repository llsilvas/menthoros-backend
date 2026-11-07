package br.com.menthoros.backend.dto.input;

import br.com.menthoros.backend.enums.WorkoutEditReason;

import br.com.menthoros.backend.enums.TipoTreino;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

@Schema(description = "Campos editáveis de um treino planejado durante revisão de plano. Campos null são ignorados (patch semântico).")
public record TreinoPlanejadoPatchDto(

        @Schema(description = "Tipo do treino", example = "LONGO")
        TipoTreino tipoTreino,

        @Size(max = 500)
        @Schema(description = "Descrição do treino", example = "18km em Z2 — reduzido após semana de prova")
        String descricao,

        @Positive
        @Schema(description = "Distância em quilômetros", example = "18.0")
        BigDecimal distanciaKm,

        @Schema(description = "Duração do treino em formato ISO-8601 (ex: PT90M = 90 min, PT1H30M = 1h30). "
                             + "Atenção: o campo de saída equivalente é serializado como HH:MM:SS.", example = "PT90M")
        Duration duracaoMin,

        @Size(max = 50)
        @Schema(description = "Zona alvo do treino", example = "z2")
        String zonaAlvo,

        @Min(1) @Max(500)
        @Schema(description = "TSS planejado — se informado, prevalece sobre o recálculo automático", example = "65")
        Integer tssPlanejado,

        @Min(1) @Max(10)
        @Schema(description = "Percepção de esforço esperada (1-10)", example = "6")
        Integer percepcaoEsforcoEsperada,

        @Size(max = 500)
        @Schema(description = "Observações do treinador sobre o treino")
        String observacao,

        @Valid
        @Size(max = 20)
        @Schema(description = "Lista de etapas do treino — quando informada, substitui todas as etapas existentes")
        List<EtapaInputDto> etapas,

        @Schema(description = "Motivo opcional da edição de treino em plano já aprovado; ignorado em plano em revisão",
                example = "LOAD")
        WorkoutEditReason editReason,

        @Schema(description = "Nova data do treino, dentro da semana do plano; o dia da semana é derivado dela. "
                             + "Em plano aprovado, só hoje ou futura, pela mesma regra do treino editado.",
                example = "2026-07-04")
        LocalDate dataTreino

) {
    /** Construtor sem a data: mantém os chamadores anteriores a coach-alterar-data-treino. */
    public TreinoPlanejadoPatchDto(TipoTreino tipoTreino, String descricao, BigDecimal distanciaKm,
                                   Duration duracaoMin, String zonaAlvo, Integer tssPlanejado,
                                   Integer percepcaoEsforcoEsperada, String observacao,
                                   List<EtapaInputDto> etapas, WorkoutEditReason editReason) {
        this(tipoTreino, descricao, distanciaKm, duracaoMin, zonaAlvo, tssPlanejado,
                percepcaoEsforcoEsperada, observacao, etapas, editReason, null);
    }
}
