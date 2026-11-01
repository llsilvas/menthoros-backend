package br.com.menthoros.backend.dto.garmin;

import java.time.LocalDate;

/** Item da lista de schedules da Training API V2: qual workout cai em qual dia. */
public record GarminSchedule(Long scheduleId, Long workoutId, LocalDate date) {
}
