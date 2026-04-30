package br.com.menthoros.backend.dto.strava;

import com.fasterxml.jackson.annotation.JsonProperty;

public record StravaSplitDto(
        @JsonProperty("split") Integer lapIndex,
        @JsonProperty("distance") Double distance,
        @JsonProperty("elapsed_time") Integer elapsedTime,
        @JsonProperty("moving_time") Integer movingTime,
        @JsonProperty("average_speed") Double averageSpeed,
        @JsonProperty("average_heartrate") Double averageHeartrate,
        @JsonProperty("max_heartrate") Double maxHeartrate,
        @JsonProperty("average_cadence") Double averageCadence,
        @JsonProperty("average_watts") Double averageWatts,
        @JsonProperty("elevation_difference") Double elevationDifference
) {}
