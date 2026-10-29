package br.com.menthoros.backend.dto.garmin;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;

import java.time.Duration;

/**
 * Resposta de {@code POST diauth.garmin.com/di-oauth2-service/oauth/token} (code e refresh).
 *
 * <p>Ambos os tokens expiram: {@code expires_in} (~86400 s) e {@code refresh_token_expires_in}
 * (~90 dias, rotativo). O segundo é documentado mas ainda não confirmado contra o sandbox, por isso
 * {@link #refreshTokenExpiresInOuPadrao()} assume 90 dias quando ele vier ausente (D3).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GarminTokenResponse(
        @JsonProperty("access_token") String accessToken,
        @JsonProperty("refresh_token") String refreshToken,
        @JsonProperty("token_type") String tokenType,
        @JsonProperty("expires_in") long expiresIn,
        @JsonProperty("refresh_token_expires_in") @Nullable Long refreshTokenExpiresIn,
        @Nullable String scope
) {
    static final long REFRESH_PADRAO_SEGUNDOS = Duration.ofDays(90).toSeconds();

    public long refreshTokenExpiresInOuPadrao() {
        return refreshTokenExpiresIn != null ? refreshTokenExpiresIn : REFRESH_PADRAO_SEGUNDOS;
    }
}
