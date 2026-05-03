package br.com.menthoros.backend.integration;

import br.com.menthoros.backend.entity.Assessoria;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.TreinoRealizado;
import br.com.menthoros.backend.enums.ReconciliationStatus;
import br.com.menthoros.backend.repository.AssessoriaRepository;
import br.com.menthoros.backend.repository.AtletaRepository;
import br.com.menthoros.backend.repository.TreinoRealizadoRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import br.com.menthoros.backend.TestcontainersConfiguration;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for Task 1.2: Deduplication by (externalId, atletaId).
 *
 * Tests the UNIQUE composite index uk_treino_realizado_external_id_atleta
 * added in V23 migration to prevent duplicate activity ingestion.
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class DeduplicationConstraintTest {

    @Autowired
    private TreinoRealizadoRepository treinoRealizadoRepository;

    @Autowired
    private AtletaRepository atletaRepository;

    @Autowired
    private AssessoriaRepository assessoriaRepository;

    @Autowired
    private EntityManager entityManager;

    private Atleta atleta;
    private Assessoria assessoria;

    @BeforeEach
    void setup() {
        // Create tenant (assessoria)
        assessoria = new Assessoria();
        assessoria.setId(UUID.randomUUID());
        assessoria.setNome("Test Assessoria");
        assessoria = assessoriaRepository.save(assessoria);

        // Create athlete
        atleta = new Atleta();
        atleta.setId(UUID.randomUUID());
        atleta.setNome("Test Athlete");
        atleta.setAssessoria(assessoria);
        atleta = atletaRepository.save(atleta);
    }

    @Test
    @DisplayName("1.2.1: Should allow single activity with unique (externalId, atletaId)")
    void shouldAllowSingleActivityWithUniqueKey() {
        TreinoRealizado activity = createActivity("strava_12345", atleta);
        TreinoRealizado saved = treinoRealizadoRepository.save(activity);

        assertNotNull(saved.getId());
        assertEquals("strava_12345", saved.getExternalId());
    }

    @Test
    @DisplayName("1.2.2: Should reject duplicate (externalId, atletaId) pair")
    void shouldRejectDuplicateExternalIdAtletaIdPair() {
        String externalId = "strava_99999";

        // Insert first activity
        TreinoRealizado activity1 = createActivity(externalId, atleta);
        treinoRealizadoRepository.save(activity1);

        // Try to insert duplicate with same externalId + atletaId
        TreinoRealizado activity2 = createActivity(externalId, atleta);

        // Should raise UNIQUE constraint violation on flush
        assertThrows(
            DataIntegrityViolationException.class,
            () -> {
                treinoRealizadoRepository.save(activity2);
                // Force flush to execute constraint check
                entityManager.flush();
            },
            "Duplicate (externalId, atletaId) should violate UNIQUE constraint"
        );
    }

    @Test
    @DisplayName("1.2.3: Should allow same externalId for different athletes")
    void shouldAllowSameExternalIdForDifferentAthletes() {
        String externalId = "strava_shared_12345";

        // Create second athlete in same tenant
        Atleta atleta2 = new Atleta();
        atleta2.setId(UUID.randomUUID());
        atleta2.setNome("Test Athlete 2");
        atleta2.setAssessoria(assessoria);
        atleta2 = atletaRepository.save(atleta2);

        // Insert activity for first athlete
        TreinoRealizado activity1 = createActivity(externalId, atleta);
        treinoRealizadoRepository.save(activity1);

        // Insert same externalId but for different athlete - should succeed
        TreinoRealizado activity2 = createActivity(externalId, atleta2);
        TreinoRealizado saved = treinoRealizadoRepository.save(activity2);

        assertNotNull(saved.getId());
        assertEquals(externalId, saved.getExternalId());
        assertEquals(atleta2.getId(), saved.getAtleta().getId());
    }

    @Test
    @DisplayName("1.2.4: Should allow NULL externalId without constraint violation")
    void shouldAllowNullExternalIdMultipleTimes() {
        // Create multiple activities without externalId - should not violate constraint
        TreinoRealizado activity1 = new TreinoRealizado();
        activity1.setId(UUID.randomUUID());
        activity1.setAtleta(atleta);
        activity1.setDataTreino(LocalDate.now());
        activity1.setDuracaoMin(Duration.ofMinutes(60));
        activity1.setDistanciaKm(new BigDecimal("10.00"));
        activity1.setExternalId(null); // NULL externalId
        treinoRealizadoRepository.save(activity1);

        TreinoRealizado activity2 = new TreinoRealizado();
        activity2.setId(UUID.randomUUID());
        activity2.setAtleta(atleta);
        activity2.setDataTreino(LocalDate.now().plusDays(1));
        activity2.setDuracaoMin(Duration.ofMinutes(45));
        activity2.setDistanciaKm(new BigDecimal("8.00"));
        activity2.setExternalId(null); // NULL externalId
        TreinoRealizado saved = treinoRealizadoRepository.save(activity2);

        assertNotNull(saved.getId());
        assertNull(saved.getExternalId());
    }

    @Test
    @DisplayName("1.2.5: Should prevent duplicate via findByExternalIdAndAtletaId query")
    void shouldPreventDuplicateViaQuery() {
        String externalId = "strava_detect_dup";

        // Save first activity
        TreinoRealizado activity1 = createActivity(externalId, atleta);
        treinoRealizadoRepository.save(activity1);
        entityManager.flush();

        // Query for existing activity (as application does in upsert logic)
        var existing = treinoRealizadoRepository.findByExternalIdAndAtletaId(externalId, atleta.getId());

        assertTrue(existing.isPresent(), "Should find existing activity by externalId + atletaId");
        assertEquals(externalId, existing.get().getExternalId());

        // Try to save duplicate (application should prevent via upsert, DB constraint enforces)
        TreinoRealizado activity2 = createActivity(externalId, atleta);
        assertThrows(
            DataIntegrityViolationException.class,
            () -> {
                treinoRealizadoRepository.save(activity2);
                entityManager.flush();
            },
            "Duplicate should fail at DB level even if app logic misses it"
        );
    }

    private TreinoRealizado createActivity(String externalId, Atleta atleta) {
        TreinoRealizado activity = new TreinoRealizado();
        activity.setId(UUID.randomUUID());
        activity.setAtleta(atleta);
        activity.setExternalId(externalId);
        activity.setDataTreino(LocalDate.now());
        activity.setDuracaoMin(Duration.ofMinutes(60));
        activity.setDistanciaKm(new BigDecimal("10.00"));
        activity.setReconciliationStatus(ReconciliationStatus.PENDENTE);
        return activity;
    }
}
