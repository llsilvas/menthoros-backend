package com.menthoros.dto.input;

import com.menthoros.enums.DiaSemana;
import com.menthoros.enums.NivelExperiencia;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.Set;

@Schema(description = "Dados de entrada para cadastro ou atualização de atleta")
public record AtletaInputDto(
        @Schema(description = "Nome completo do atleta", example = "João Silva", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "Nome é obrigatório")
        @Size(min = 2, max = 100, message = "Nome deve ter entre 2 e 100 caracteres")
        String nome,

        @Schema(description = "Idade do atleta em anos", example = "30", minimum = "10", maximum = "100", requiredMode = Schema.RequiredMode.REQUIRED)
        @Min(value = 10, message = "Idade mínima é 10 anos")
        @Max(value = 100, message = "Idade máxima é 100 anos")
        int idade,

        @Schema(description = "Peso do atleta em quilogramas", example = "75.5", requiredMode = Schema.RequiredMode.REQUIRED)
        @Positive(message = "Peso deve ser positivo")
        @DecimalMax(value = "300.0", message = "Peso máximo é 300kg")
        BigDecimal pesoKg,

        @Schema(description = "Altura do atleta em centímetros", example = "175.0", requiredMode = Schema.RequiredMode.REQUIRED)
        @Positive(message = "Altura deve ser positiva")
        @DecimalMin(value = "100.0", message = "Altura mínima é 100cm")
        @DecimalMax(value = "250.0", message = "Altura máxima é 250cm")
        BigDecimal alturaCm,

        @Schema(description = "Objetivo do atleta com o treinamento", example = "Completar maratona em menos de 4 horas", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "Objetivo é obrigatório")
        @Size(max = 500, message = "Objetivo deve ter no máximo 500 caracteres")
        String objetivo,

        @Schema(description = "Nível de experiência do atleta em corrida", example = "INTERMEDIARIO", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "Nível de experiência é obrigatório")
        NivelExperiencia nivelExperiencia,

        @Schema(description = "Dias da semana disponíveis para treino", example = "[\"SEGUNDA\", \"QUARTA\", \"SEXTA\"]", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotEmpty(message = "Pelo menos um dia disponível deve ser informado")
        Set<DiaSemana> diasDisponiveis,

        @Schema(description = "Dia preferido para treino longo", example = "SABADO")
        DiaSemana diaPreferidoLongo,

        @Schema(description = "Indica se o atleta possui alguma lesão", example = "false", requiredMode = Schema.RequiredMode.REQUIRED)
        boolean temLesao,

        @Schema(description = "Descrição detalhada da lesão, caso exista", example = "Tendinite no joelho direito")
        @Size(max = 1000, message = "Descrição da lesão deve ter no máximo 1000 caracteres")
        String descricaoLesao
) {
}
