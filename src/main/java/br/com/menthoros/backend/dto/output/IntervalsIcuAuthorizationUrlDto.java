package br.com.menthoros.backend.dto.output;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * URL de autorização do intervals.icu para o front redirecionar o atleta.
 *
 * <p>DTO tipado e não {@code Map}, seguindo o Controller Standards do módulo — o cliente do front
 * é gerado a partir do OpenAPI, e um {@code Map} vira um tipo inútil do outro lado.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "URL de autorização OAuth2 do intervals.icu")
public record IntervalsIcuAuthorizationUrlDto(
        @Schema(description = "URL completa de consentimento, com state assinado",
                example = "https://intervals.icu/oauth/authorize?client_id=663&...")
        String authorizationUrl
) {}
