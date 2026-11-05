package br.com.menthoros.backend.entity;

import br.com.menthoros.backend.enums.GarminPingStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.Instant;
import java.util.UUID;

/**
 * Aviso {@code activityFiles} do Garmin, gravado antes do 200 e processado pelo scheduler
 * (garmin-activity-ingestion, D1). Fila durável mínima: o FIT nunca é guardado.
 */
@Entity
@Table(name = "tb_garmin_ping")
@Getter
@Setter
@NoArgsConstructor
@ToString(exclude = "callbackUrl")
public class GarminPing {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "athlete_id", nullable = false)
    private UUID athleteId;

    @Column(name = "garmin_user_id", nullable = false, length = 100)
    private String garminUserId;

    @Column(name = "summary_id", nullable = false, length = 100)
    private String summaryId;

    @Column(name = "activity_id", length = 100)
    private String activityId;

    /** Carrega o token de download do Garmin: nunca logar. */
    @Column(name = "callback_url", nullable = false, length = 1000)
    private String callbackUrl;

    @Column(name = "activity_type", length = 50)
    private String activityType;

    @Column(name = "file_type", length = 10)
    private String fileType;

    @Column(name = "device_name")
    private String deviceName;

    @Column(name = "start_time", nullable = false)
    private Instant startTime;

    @Column(name = "manual", nullable = false)
    private boolean manual;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private GarminPingStatus status;

    @Column(name = "reason", length = 500)
    private String reason;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "syncing_since")
    private Instant syncingSince;

    @Column(name = "completed_workout_id")
    private UUID completedWorkoutId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "processed_at")
    private Instant processedAt;

    @PrePersist
    void prePersist() {
        Instant agora = Instant.now();
        if (createdAt == null) {
            createdAt = agora;
        }
        if (nextAttemptAt == null) {
            nextAttemptAt = agora;
        }
    }
}
