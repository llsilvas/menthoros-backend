package br.com.menthoros.backend.integration;

import br.com.menthoros.backend.entity.Assessoria;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.TreinoRealizado;
import br.com.menthoros.backend.enums.ReconciliationStatus;
import br.com.menthoros.backend.repository.AssessoriaRepository;
import br.com.menthoros.backend.repository.AtletaRepository;
import br.com.menthoros.backend.repository.TreinoRealizadoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for Task 1.3: Multi-tenant isolation in daily sync flow.
 *
 * Tests that the scheduler and reconciliation logic properly isolate data
 * by tenant (assessoria) and prevent cross-tenant data leakage.
 */
@SpringBootTest
@ActiveProfiles("integration")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class MultiTenantIsolationTest {

    @Autowired
    private TreinoRealizadoRepository treinoRealizadoRepository;

    @Autowired
    private AtletaRepository atletaRepository;

    @Autowired
    private AssessoriaRepository assessoriaRepository;

    private Assessoria tenant1;
    private Assessoria tenant2;
    private Atleta athlete1_tenant1;
    private Atleta athlete2_tenant1;
    private Atleta athlete1_tenant2;

    @BeforeEach
    void setup() {
        // Create two separate tenants (assessorias)
        tenant1 = new Assessoria();
        tenant1.setId(UUID.randomUUID());
        tenant1.setNome("Assessoria Tenant 1");
        tenant1 = assessoriaRepository.save(tenant1);

        tenant2 = new Assessoria();
        tenant2.setId(UUID.randomUUID());
        tenant2.setNome("Assessoria Tenant 2");
        tenant2 = assessoriaRepository.save(tenant2);

        // Create athletes in tenant 1
        athlete1_tenant1 = new Atleta();
        athlete1_tenant1.setId(UUID.randomUUID());
        athlete1_tenant1.setNome("Athlete 1 - Tenant 1");
        athlete1_tenant1.setAssessoria(tenant1);
        athlete1_tenant1 = atletaRepository.save(athlete1_tenant1);

        athlete2_tenant1 = new Atleta();
        athlete2_tenant1.setId(UUID.randomUUID());
        athlete2_tenant1.setNome("Athlete 2 - Tenant 1");
        athlete2_tenant1.setAssessoria(tenant1);
        athlete2_tenant1 = atletaRepository.save(athlete2_tenant1);

        // Create athlete in tenant 2
        athlete1_tenant2 = new Atleta();
        athlete1_tenant2.setId(UUID.randomUUID());
        athlete1_tenant2.setNome("Athlete 1 - Tenant 2");
        athlete1_tenant2.setAssessoria(tenant2);
        athlete1_tenant2 = atletaRepository.save(athlete1_tenant2);
    }

    @Test
    @DisplayName("1.3.1: Activities for athlete in tenant1 should not appear in tenant2 queries")
    void shouldIsolateTenantData() {
        // Create activity for athlete in tenant 1
        TreinoRealizado activity1 = createActivity("strava_101", athlete1_tenant1, LocalDate.now());
        treinoRealizadoRepository.save(activity1);

        // Query activities for athlete in tenant 1 should find the activity
        var activities1 = treinoRealizadoRepository.findByAtletaIdAndDataTreinoAndReconciliationStatus(
            athlete1_tenant1.getId(),
            LocalDate.now(),
            ReconciliationStatus.PENDENTE
        );
        assertEquals(1, activities1.size(), "Should find activity for tenant1 athlete");

        // Query for athlete in tenant 2 should NOT find tenant1's activity
        var activities2 = treinoRealizadoRepository.findByAtletaIdAndDataTreinoAndReconciliationStatus(
            athlete1_tenant2.getId(),
            LocalDate.now(),
            ReconciliationStatus.PENDENTE
        );
        assertEquals(0, activities2.size(), "Tenant2 athlete should not see tenant1 activities");
    }

    @Test
    @DisplayName("1.3.2: Each athlete should only see their own activities")
    void shouldIsolateByAthlete() {
        // Create activity for athlete 1 in tenant 1
        TreinoRealizado activity1 = createActivity("strava_201", athlete1_tenant1, LocalDate.now());
        treinoRealizadoRepository.save(activity1);

        // Create activity for athlete 2 in tenant 1
        TreinoRealizado activity2 = createActivity("strava_202", athlete2_tenant1, LocalDate.now());
        treinoRealizadoRepository.save(activity2);

        // Athlete 1 should only see their own activity
        var athlete1Activities = treinoRealizadoRepository.findByAtletaIdAndDataTreinoAndReconciliationStatus(
            athlete1_tenant1.getId(),
            LocalDate.now(),
            ReconciliationStatus.PENDENTE
        );
        assertEquals(1, athlete1Activities.size(), "Athlete1 should see only their activity");
        assertEquals("strava_201", athlete1Activities.get(0).getExternalId());

        // Athlete 2 should only see their own activity
        var athlete2Activities = treinoRealizadoRepository.findByAtletaIdAndDataTreinoAndReconciliationStatus(
            athlete2_tenant1.getId(),
            LocalDate.now(),
            ReconciliationStatus.PENDENTE
        );
        assertEquals(1, athlete2Activities.size(), "Athlete2 should see only their activity");
        assertEquals("strava_202", athlete2Activities.get(0).getExternalId());
    }

    @Test
    @DisplayName("1.3.3: Athlete's tenantId is always accessible from activity")
    void shouldAlwaysProvideAthleteWithTenant() {
        // Create activity
        TreinoRealizado activity = createActivity("strava_301", athlete1_tenant1, LocalDate.now());
        TreinoRealizado saved = treinoRealizadoRepository.save(activity);

        // Verify tenant information is accessible
        assertNotNull(saved.getAtleta(), "Activity must have athlete");
        assertNotNull(saved.getAtleta().getAssessoria(), "Athlete must have assessoria (tenant)");
        assertEquals(tenant1.getId(), saved.getAtleta().getAssessoria().getId(),
            "Activity's athlete must belong to tenant1");
    }

    @Test
    @DisplayName("1.3.4: Different tenants can have activities with same externalId for different athletes")
    void shouldAllowSameExternalIdAcrossTenants() {
        String sameExternalId = "strava_shared_999";

        // Create activity with same externalId for athlete in tenant 1
        TreinoRealizado activity1 = createActivity(sameExternalId, athlete1_tenant1, LocalDate.now());
        treinoRealizadoRepository.save(activity1);

        // Create activity with same externalId for athlete in tenant 2
        // Should succeed because it's different athlete + different tenant
        TreinoRealizado activity2 = createActivity(sameExternalId, athlete1_tenant2, LocalDate.now());
        TreinoRealizado saved = treinoRealizadoRepository.save(activity2);

        assertNotNull(saved.getId(), "Should allow same externalId across different tenants");

        // Verify isolation: each athlete sees only their own activity
        var tenant1Activities = treinoRealizadoRepository.findByAtletaIdAndDataTreinoAndReconciliationStatus(
            athlete1_tenant1.getId(),
            LocalDate.now(),
            ReconciliationStatus.PENDENTE
        );
        assertEquals(1, tenant1Activities.size(), "Tenant1 athlete should see 1 activity");

        var tenant2Activities = treinoRealizadoRepository.findByAtletaIdAndDataTreinoAndReconciliationStatus(
            athlete1_tenant2.getId(),
            LocalDate.now(),
            ReconciliationStatus.PENDENTE
        );
        assertEquals(1, tenant2Activities.size(), "Tenant2 athlete should see 1 activity");
    }

    @Test
    @DisplayName("1.3.5: Query by athleteId implicitly provides tenant isolation")
    void shouldProvideImplicitTenantIsolationViaAthleteId() {
        // Create multiple activities
        TreinoRealizado activity1 = createActivity("strava_401", athlete1_tenant1, LocalDate.of(2026, 5, 1));
        TreinoRealizado activity2 = createActivity("strava_402", athlete1_tenant1, LocalDate.of(2026, 5, 2));
        TreinoRealizado activity3 = createActivity("strava_403", athlete1_tenant2, LocalDate.of(2026, 5, 1));

        treinoRealizadoRepository.save(activity1);
        treinoRealizadoRepository.save(activity2);
        treinoRealizadoRepository.save(activity3);

        // Query for tenant1 athlete on specific date
        var tenant1ActivitiesDay1 = treinoRealizadoRepository.findByAtletaIdAndDataTreinoAndReconciliationStatus(
            athlete1_tenant1.getId(),
            LocalDate.of(2026, 5, 1),
            ReconciliationStatus.PENDENTE
        );

        // Should only find tenant1's activity on that day, not tenant2's
        assertEquals(1, tenant1ActivitiesDay1.size(), "Should only find tenant1 athlete's activity");
        assertEquals(athlete1_tenant1.getId(), tenant1ActivitiesDay1.get(0).getAtleta().getId());
    }

    private TreinoRealizado createActivity(String externalId, Atleta atleta, LocalDate data) {
        TreinoRealizado activity = new TreinoRealizado();
        activity.setId(UUID.randomUUID());
        activity.setAtleta(atleta);
        activity.setExternalId(externalId);
        activity.setDataTreino(data);
        activity.setDuracaoMin(Duration.ofMinutes(60));
        activity.setDistanciaKm(new BigDecimal("10.00"));
        activity.setReconciliationStatus(ReconciliationStatus.PENDENTE);
        return activity;
    }
}
