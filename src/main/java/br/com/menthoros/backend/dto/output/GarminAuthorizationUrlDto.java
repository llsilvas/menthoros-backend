package br.com.menthoros.backend.dto.output;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "URL de autorização OAuth2 do Garmin")
public record GarminAuthorizationUrlDto(
        @Schema(description = "URL completa de consentimento, com state assinado e code_challenge PKCE",
                example = "https://connect.garmin.com/oauth2Confirm?client_id=...&state=...")
        String authorizationUrl
) {}
