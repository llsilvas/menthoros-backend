package br.com.menthoros.backend.enums;

/** Estado de um ping {@code activityFiles} do Garmin na fila de ingestão (garmin-activity-ingestion, D1). */
public enum GarminPingStatus {
    PENDING,
    SYNCING,
    PROCESSED,
    DISCARDED,
    ERROR
}
