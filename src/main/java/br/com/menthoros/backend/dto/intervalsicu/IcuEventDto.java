package br.com.menthoros.backend.dto.intervalsicu;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record IcuEventDto(
        Long id,
        @JsonProperty("external_id") String externalId,
        String name,
        @JsonProperty("start_date_local") String startDateLocal
) {}
