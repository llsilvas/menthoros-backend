package br.com.menthoros.backend.dto.strava;

import com.fasterxml.jackson.annotation.JsonProperty;

public record StravaGearDto(
        @JsonProperty("id") String id,
        @JsonProperty("name") String name
) {}
