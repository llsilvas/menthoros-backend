package br.com.menthoros.backend.dto.intervalsicu;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IcuActivityDtoTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("desserializa todos os campos presentes no payload real do intervals.icu")
    void desserializaCamposPresentes() throws Exception {
        String json = """
                {
                  "id": "i86400275",
                  "athlete_id": "i641775",
                  "type": "Run",
                  "name": "Corrida matinal",
                  "start_date_local": "2026-07-16T06:30:00",
                  "moving_time": 1800,
                  "elapsed_time": 1850,
                  "distance": 5000.0,
                  "average_speed": 2.78,
                  "average_heartrate": 145.0,
                  "max_heartrate": 168.0,
                  "total_elevation_gain": 42.0,
                  "average_cadence": 172.0,
                  "icu_rpe": 6.0,
                  "icu_training_load": 55,
                  "device_name": "Garmin Forerunner 965",
                  "calories": 420
                }
                """;

        IcuActivityDto dto = objectMapper.readValue(json, IcuActivityDto.class);

        assertThat(dto.id()).isEqualTo("i86400275");
        assertThat(dto.athleteId()).isEqualTo("i641775");
        assertThat(dto.type()).isEqualTo("Run");
        assertThat(dto.startDateLocal()).isEqualTo("2026-07-16T06:30:00");
        assertThat(dto.movingTimeSeg()).isEqualTo(1800);
        assertThat(dto.elapsedTimeSeg()).isEqualTo(1850);
        assertThat(dto.distance()).isEqualTo(5000.0);
        assertThat(dto.averageSpeed()).isEqualTo(2.78);
        assertThat(dto.averageHeartrate()).isEqualTo(145.0);
        assertThat(dto.maxHeartrate()).isEqualTo(168.0);
        assertThat(dto.totalElevationGain()).isEqualTo(42.0);
        assertThat(dto.averageCadence()).isEqualTo(172.0);
        assertThat(dto.icuRpe()).isEqualTo(6.0);
        assertThat(dto.icuTrainingLoad()).isEqualTo(55);
        assertThat(dto.deviceName()).isEqualTo("Garmin Forerunner 965");
        assertThat(dto.calories()).isEqualTo(420);
    }

    @Test
    @DisplayName("campos ausentes viram null, sem lançar exceção (summary incompleto, ex. esteira sem GPS)")
    void camposAusentesViramNull() throws Exception {
        String json = """
                {
                  "id": "i86400276",
                  "type": "Treadmill",
                  "start_date_local": "2026-07-16T06:30:00"
                }
                """;

        IcuActivityDto dto = objectMapper.readValue(json, IcuActivityDto.class);

        assertThat(dto.id()).isEqualTo("i86400276");
        assertThat(dto.athleteId()).isNull();
        assertThat(dto.distance()).isNull();
        assertThat(dto.movingTimeSeg()).isNull();
        assertThat(dto.averageHeartrate()).isNull();
        assertThat(dto.icuRpe()).isNull();
        assertThat(dto.deviceName()).isNull();
    }

    @Test
    @DisplayName("campos extras desconhecidos são ignorados, não quebram a desserialização")
    void camposExtrasSaoIgnorados() throws Exception {
        String json = """
                {
                  "id": "i86400277",
                  "type": "Run",
                  "start_date_local": "2026-07-16T06:30:00",
                  "campo_novo_do_provedor": "valor qualquer",
                  "gap_note": {"aninhado": true}
                }
                """;

        IcuActivityDto dto = objectMapper.readValue(json, IcuActivityDto.class);

        assertThat(dto.id()).isEqualTo("i86400277");
        assertThat(dto.type()).isEqualTo("Run");
    }
}
