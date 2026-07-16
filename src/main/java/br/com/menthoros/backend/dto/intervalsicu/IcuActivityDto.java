package br.com.menthoros.backend.dto.intervalsicu;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record IcuActivityDto(
        String id,
        @JsonProperty("icu_athlete_id") String athleteId,
        String type,
        String name,
        @JsonProperty("start_date_local") String startDateLocal,
        @JsonProperty("moving_time") Integer movingTimeSeg,
        @JsonProperty("elapsed_time") Integer elapsedTimeSeg,
        Double distance,
        @JsonProperty("average_speed") Double averageSpeed,
        @JsonProperty("average_heartrate") Double averageHeartrate,
        @JsonProperty("max_heartrate") Double maxHeartrate,
        @JsonProperty("total_elevation_gain") Double totalElevationGain,
        @JsonProperty("average_cadence") Double averageCadence,
        @JsonProperty("icu_rpe") Double icuRpe,
        @JsonProperty("icu_training_load") Integer icuTrainingLoad,
        @JsonProperty("device_name") String deviceName,
        Integer calories
) {}
