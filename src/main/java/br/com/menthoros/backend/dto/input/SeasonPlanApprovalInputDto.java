package br.com.menthoros.backend.dto.input;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Corpo opcional da aprovação da temporada (add-season-plan-adjust-v2). O motivo é obrigatório a partir
 * da versão 2; tamanho e branco são validados no serviço (422), como as demais regras do ajuste.
 */
@Schema(description = "Aprovação da temporada; o motivo é obrigatório ao aprovar um ajuste (versão 2 em diante)")
public record SeasonPlanApprovalInputDto(
        @Schema(description = "Por que a temporada mudou, até 255 caracteres", example = "Atleta lesionou e perdeu duas semanas")
        String reason
) {
}
