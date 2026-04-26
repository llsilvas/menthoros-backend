package com.menthoros.dto.strava;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

public record StravaWebhookEventDto(
        @JsonProperty("object_type") String objectType,
        @JsonProperty("aspect_type") String aspectType,
        @JsonProperty("object_id") Long objectId,
        @JsonProperty("owner_id") Long ownerId,
        @JsonProperty("event_time") Long eventTime,
        @JsonProperty("updates") Map<String, Object> updates
) {}
