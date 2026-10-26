package br.com.menthoros.backend.dto.input;

import br.com.menthoros.backend.enums.DistanciaProva;
import br.com.menthoros.backend.enums.TipoProva;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Prova alvo OPCIONAL (improve-athlete-onboarding-usability): o atleta que ainda não tem prova
 * conclui sem ela e cadastra depois em Provas. Antes os três campos eram {@code @NotNull} e um
 * iniciante com objetivo "correr 10 km sem parar" era obrigado a inventar uma prova para sair do
 * formulário. A regra "se informar a prova, informar inteira" (data + tipo + distância) fica no
 * serviço, que é quem conhece a semântica — Bean Validation não expressa dependência entre campos.
 */
@Schema(description = "Dados da prova alvo para concluir o onboarding (CA13, design.md Decisão 8). "
        + "Opcional: corpo vazio conclui sem prova alvo; se dataProva vier, tipoProva e distancia são obrigatórios.")
public record OnboardingConclusaoInputDto(
        @Schema(description = "Data da prova alvo; ausente = concluir sem prova", example = "2026-10-12")
        LocalDate dataProva,

        @Schema(description = "Tipo da prova alvo (obrigatório quando dataProva é informada)")
        TipoProva tipoProva,

        @Schema(description = "Distância da prova alvo (obrigatória quando dataProva é informada)")
        DistanciaProva distancia,

        @Schema(description = "Distância em quilômetros (para distâncias customizadas)", example = "42.195")
        BigDecimal distanciaKm,

        @Schema(description = "Nome da prova alvo", example = "Maratona Internacional de São Paulo")
        @Size(max = 200, message = "Nome da prova deve ter no máximo 200 caracteres")
        String nomeProva
) {
}
