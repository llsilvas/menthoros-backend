package br.com.menthoros.backend.dto.output;

import br.com.menthoros.backend.enums.TipoTreino;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * DTO de saída para sugestão de reclassificação de tipo de treino.
 *
 * <p>Gerado pelo validator estrutural quando a distribuição de IF nas etapas
 * diverge do tipo declarado pelo coach. A sugestão é informativa — o tipo
 * original nunca é alterado automaticamente.
 */
@Schema(description = "Sugestão de reclassificação do tipo de treino gerada por análise estrutural das etapas")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SugestaoReclassificacaoDto(

        @Schema(description = "Tipo de treino sugerido com base na distribuição de IF nas etapas", example = "CONTINUO")
        TipoTreino tipoSugerido,

        @Schema(description = "Confiança da sugestão (0.0 a 1.0)", example = "0.85")
        double confianca,

        @Schema(description = "Motivo da sugestão de reclassificação",
                example = "Etapa com IF=0.88 detectada; IF médio 0.68 sugere CONTINUO em vez de REGENERATIVO")
        String motivo
) {}
