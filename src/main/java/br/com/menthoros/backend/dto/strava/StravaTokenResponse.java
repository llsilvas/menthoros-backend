package br.com.menthoros.backend.dto.strava;

import com.fasterxml.jackson.annotation.JsonProperty;

public record StravaTokenResponse(
        @JsonProperty("token_type") String tokenType,
        @JsonProperty("expires_at") Long expiresAt,
        @JsonProperty("expires_in") Integer expiresIn,
        @JsonProperty("refresh_token") String refreshToken,
        @JsonProperty("access_token") String accessToken,
        @JsonProperty("athlete") StravaAthleteDto athlete
) {}
