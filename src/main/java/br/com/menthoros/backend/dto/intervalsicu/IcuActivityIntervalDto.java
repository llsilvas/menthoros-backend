package br.com.menthoros.backend.dto.intervalsicu;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Um intervalo (lap) de uma activity do intervals.icu, vindo em {@code icu_intervals} no corpo de
 * {@code GET /api/v1/activity/{id}?intervals=true}.
 *
 * <p>Contrato verificado contra payload real (atleta {@code i641775}, activity {@code i171415754},
 * 2026-08-02). Três unidades exigem conversão no mapper, e nenhuma é dedutível do nome do campo:
 * <ul>
 *   <li>{@code averageCadence} é de UMA perna — dobrar para a convenção do domínio;</li>
 *   <li>{@code averageVerticalOscillation} vem em MILÍMETROS — dividir por 10 para cm;</li>
 *   <li>{@code averageGradient} vem em FRAÇÃO ({@code 0.0011977} = 0,1%) — multiplicar por 100.</li>
 * </ul>
 *
 * <p>O {@code id} é opaco: não é índice e não ordena nada. A ordem das etapas vem da posição na
 * lista.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record IcuActivityIntervalDto(
        Long id,                                                 // opaco — não use para ordenar
        String type,                                             // WORK, RECOVERY, ...
        String label,                                            // null em corrida livre
        @JsonProperty("start_index") Integer startIndex,
        Double distance,                                         // metros
        @JsonProperty("moving_time") Integer movingTimeSeg,
        @JsonProperty("elapsed_time") Integer elapsedTimeSeg,
        @JsonProperty("average_speed") Double averageSpeed,      // m/s
        @JsonProperty("average_heartrate") Double averageHeartrate,
        @JsonProperty("max_heartrate") Double maxHeartrate,
        @JsonProperty("average_cadence") Double averageCadence,  // perna única — dobrar
        @JsonProperty("average_watts") Double averageWatts,      // null em corrida
        @JsonProperty("total_elevation_gain") Double totalElevationGain,  // metros; não há perda
        @JsonProperty("average_stride") Double averageStride,    // metros
        @JsonProperty("average_stance_time") Double averageStanceTime,               // ms
        @JsonProperty("average_stance_time_balance") Double averageStanceTimeBalance,// % pé esquerdo
        @JsonProperty("average_vertical_oscillation") Double averageVerticalOscillation, // MILÍMETROS
        @JsonProperty("average_vertical_ratio") Double averageVerticalRatio,         // %
        @JsonProperty("average_temp") Double averageTemp,                            // °C
        Integer zone,                                            // zona de FC do intervalo
        Double intensity,                                        // % do limiar
        @JsonProperty("average_gradient") Double averageGradient // FRAÇÃO — multiplicar por 100
) {}
