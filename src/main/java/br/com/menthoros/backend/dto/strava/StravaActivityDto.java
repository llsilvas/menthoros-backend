package br.com.menthoros.backend.dto.strava;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record StravaActivityDto(
        @JsonProperty("id") Long id,
        @JsonProperty("name") String name,
        @JsonProperty("sport_type") String sportType,
        @JsonProperty("start_date_local") String startDateLocal,
        @JsonProperty("distance") Double distance,
        @JsonProperty("moving_time") Integer movingTime,
        @JsonProperty("elapsed_time") Integer elapsedTime,
        @JsonProperty("total_elevation_gain") Double totalElevationGain,
        @JsonProperty("average_speed") Double averageSpeed,
        @JsonProperty("average_heartrate") Double averageHeartrate,
        @JsonProperty("max_heartrate") Double maxHeartrate,
        @JsonProperty("has_heartrate") Boolean hasHeartrate,
        @JsonProperty("suffer_score") Integer sufferScore,
        @JsonProperty("perceived_exertion") Double perceivedExertion,
        @JsonProperty("description") String description,
        @JsonProperty("manual") Boolean manual,
        @JsonProperty("workout_type") Integer workoutType,
        @JsonProperty("average_cadence") Double averageCadence,
        @JsonProperty("device_name") String deviceName,
        @JsonProperty("gear") StravaGearDto gear,
        @JsonProperty("splits_metric") List<StravaSplitDto> splitsMetric
) {}
