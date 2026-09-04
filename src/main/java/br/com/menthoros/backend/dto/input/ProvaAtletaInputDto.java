package br.com.menthoros.backend.dto.input;

import br.com.menthoros.backend.dto.jackson.DurationHhMmSsDeserializer;
import br.com.menthoros.backend.enums.DistanciaProva;
import br.com.menthoros.backend.enums.TipoProva;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;

/**
 * Subconjunto de {@link ProvaInputDto} que um usuário com papel de atleta pode gravar: sem campos
 * de resultado, status ou derivados de preparação. Contrato do front do atleta.
 */
@Schema(description = "Dados que o próprio atleta pode informar ao cadastrar ou editar uma prova")
public record ProvaAtletaInputDto(

        @Schema(description = "Nome da prova", example = "Maratona Internacional de São Paulo", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "Nome da prova é obrigatório")
        @Size(max = 200, message = "Nome da prova deve ter no máximo 200 caracteres")
        String nomeProva,

        @Schema(description = "Data da prova (posterior à data corrente)", example = "2026-12-06", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "Data da prova é obrigatória")
        @Future(message = "Data da prova deve ser posterior a hoje")
        LocalDate dataProva,

        @Schema(description = "Tipo da prova", example = "MARATONA", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "Tipo da prova é obrigatório")
        TipoProva tipoProva,

        @Schema(description = "Distância da prova", example = "KM_42", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "Distância é obrigatória")
        DistanciaProva distancia,

        @Schema(description = "Distância em quilômetros (obrigatória quando distancia = CUSTOMIZADA)", example = "30")
        BigDecimal distanciaKm,

        @Schema(description = "Meta de tempo para a prova (HH:mm:ss)", example = "03:45:00")
        @JsonDeserialize(using = DurationHhMmSsDeserializer.class)
        Duration tempoObjetivo,

        @Schema(description = "Indica se é a prova-alvo", example = "true")
        boolean provaAlvo
) {

    /** Recorta o DTO completo para o subconjunto do atleta — campos fora dele são ignorados. */
    public static ProvaAtletaInputDto from(ProvaInputDto dto) {
        if (dto == null) {
            throw new IllegalArgumentException("ProvaInputDto não pode ser nulo");
        }
        return new ProvaAtletaInputDto(dto.nomeProva(), dto.dataProva(), dto.tipoProva(), dto.distancia(),
                dto.distanciaKm(), dto.tempoObjetivo(), dto.provaAlvo());
    }

    @AssertTrue(message = "Distância customizada exige distanciaKm positivo")
    @Schema(hidden = true)
    public boolean isDistanciaCustomizadaComKm() {
        return distancia != DistanciaProva.CUSTOMIZADA
                || (distanciaKm != null && distanciaKm.signum() > 0);
    }
}
