package br.com.menthoros.backend.dto.llm.v2;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Contrato de saída da LLM por treino, schema v2 (semantic-session-schema) — substitui
 * {@code etapas: [...]} de {@code TreinoPlanejadoLlmDto} (v1) por {@code blocos}. Sem
 * {@code fcAlvo}/{@code duracaoMin}/{@code distanciaKm}/{@code ritmoAlvo}: o
 * {@link br.com.menthoros.backend.services.helper.SessionResolver} calcula esses campos a partir
 * dos blocos resolvidos.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TreinoPlanejadoLlmDtoV2(
        @Schema(description = "Dia da semana")
        String diaSemana,

        @Schema(description = "Tipo do treino")
        String tipoTreino,

        @Schema(description = "Justificativa da LLM para as escolhas deste treino")
        String justificativaIa,

        @Schema(description = "Blocos qualitativos do treino")
        List<BlocoDto> blocos
) {
}
