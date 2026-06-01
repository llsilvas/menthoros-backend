package br.com.menthoros.backend.skills.race;

import br.com.menthoros.backend.enums.NivelExperiencia;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Perfil fisiológico mínimo do atleta necessário para a RaceProjectionSkill.
 * Mapeado a partir de Atleta pela AthleteProfileMapper — nunca importar Atleta aqui.
 * fcMaxima e fcLimiar nullable: ausência força confidence=LOW (D7).
 */
@Schema(description = "Perfil do atleta para entrada na RaceProjectionSkill")
public record AthleteProfile(

        @NotNull
        @Schema(description = "Identificador único do atleta")
        UUID id,

        @NotBlank
        @Schema(description = "Nome do atleta", example = "João")
        String nome,

        @Schema(description = "Sobrenome do atleta", example = "Silva")
        String sobrenome,

        @Schema(description = "FC máxima em bpm. Null força confidence=LOW e uso de pace bruto.", example = "182")
        Integer fcMaxima,

        @Schema(description = "FC de limiar em bpm. Se null, estimada como fcMaxima × 0.88.", example = "160")
        Integer fcLimiar,

        @Schema(description = "VO2max estimado (ml/kg/min). Usado apenas para contexto narrativo.", example = "52.5")
        BigDecimal vo2maxEstimado,

        @NotNull
        @Schema(description = "Nível de experiência do atleta")
        NivelExperiencia nivelExperiencia
) {}
