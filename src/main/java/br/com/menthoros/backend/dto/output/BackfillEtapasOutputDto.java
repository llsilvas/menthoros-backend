package br.com.menthoros.backend.dto.output;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Resultado do backfill de etapas de treinos intervals.icu importados antes da ingestão de etapas")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record BackfillEtapasOutputDto(

        @Schema(description = "Treinos do atleta sem etapas encontrados", example = "12")
        int candidatos,

        @Schema(description = "Treinos que receberam etapas", example = "10")
        int atualizados,

        @Schema(description = "Treinos cuja activity genuinamente não tem intervalos na fonte", example = "1")
        int semIntervalos,

        @Schema(description = "Treinos cuja busca na fonte falhou — seguem elegíveis na próxima execução", example = "1")
        int falhas,

        @Schema(description = "Treinos que ficaram de fora desta execução, por limite de lote. "
                        + "Basta disparar o backfill de novo para continuar de onde parou.", example = "0")
        int restantes
) {}
