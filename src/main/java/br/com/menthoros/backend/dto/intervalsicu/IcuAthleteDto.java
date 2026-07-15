package br.com.menthoros.backend.dto.intervalsicu;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record IcuAthleteDto(String id, String name) {}
