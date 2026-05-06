package br.com.menthoros.backend.dto.strava;

import com.fasterxml.jackson.annotation.JsonProperty;

public record StravaAthleteDto(
        @JsonProperty("id") Long id,
        @JsonProperty("username") String username,
        @JsonProperty("firstname") String firstname,
        @JsonProperty("lastname") String lastname,
        @JsonProperty("city") String city,
        @JsonProperty("country") String country,
        @JsonProperty("sex") String sex,
        @JsonProperty("profile") String profile
) {}
