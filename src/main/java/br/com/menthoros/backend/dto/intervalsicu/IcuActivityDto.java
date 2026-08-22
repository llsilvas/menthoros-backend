package br.com.menthoros.backend.dto.intervalsicu;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record IcuActivityDto(
        String id,
        @JsonProperty("icu_athlete_id") String athleteId,
        String type,
        String name,
        @JsonProperty("start_date_local") String startDateLocal,
        /*
         * Instante UTC (ex.: 2026-08-21T10:15:06Z). Vem na listagem e no detalhe (gate 0.2); é a
         * base do cursor do scheduler, que não pode depender do fuso de start_date_local.
         */
        @JsonProperty("start_date") String startDate,
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
        Integer calories,

        /*
         * Só vem quando a activity é buscada com ?intervals=true; ausente/null caso contrário.
         * O mapper trata null como lista vazia.
         */
        @JsonProperty("icu_lap_count") Integer lapCount,
        @JsonProperty("icu_intervals") List<IcuActivityIntervalDto> intervalos
) {}
