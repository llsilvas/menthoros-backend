package br.com.menthoros.backend.dto.output;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Resposta de {@code GET /api/v1/atletas/me/melhores-esforcos}. {@code integracaoConectada=false}
 * não é erro — é o estado "atleta sem intervals.icu conectado ainda", que o front trata com um CTA
 * de conexão em vez de mensagem de erro genérica.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Melhores esforços do atleta autenticado, numa janela")
public record MelhoresEsforcosOutputDto(

        @Schema(description = "Marcas encontradas dentro da tolerância; pode faltar alguma distância")
        List<MelhorEsforcoDto> marcas,

        @Schema(description = "Se o atleta tem integração intervals.icu ativa")
        boolean integracaoConectada
) {}
