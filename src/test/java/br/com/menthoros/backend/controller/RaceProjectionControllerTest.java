package br.com.menthoros.backend.controller;

import br.com.menthoros.backend.dto.input.RaceProjectionRequestDto;
import br.com.menthoros.backend.dto.output.AthleteProjectionViewDto;
import br.com.menthoros.backend.dto.output.RaceProjectionSnapshotOutputDto;
import br.com.menthoros.backend.exception.ResourceNotFoundException;
import br.com.menthoros.backend.multitenancy.TenantContext;
import br.com.menthoros.backend.services.RaceProjectionService;
import br.com.menthoros.backend.skills.race.enums.CtlTrend;
import br.com.menthoros.backend.skills.race.enums.PeriodizationPhase;
import br.com.menthoros.backend.skills.race.enums.ProjectionConfidence;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RaceProjectionControllerTest {

    @Mock private RaceProjectionService raceProjectionService;
    @Mock private Authentication authentication;

    @InjectMocks private RaceProjectionController controller;

    private UUID atletaId;
    private UUID provaId;
    private UUID tenantId;
    private UUID coachId;
    private UUID snapshotId;

    private RaceProjectionSnapshotOutputDto snapshotDto;

    @BeforeEach
    void setUp() {
        atletaId  = UUID.randomUUID();
        provaId   = UUID.randomUUID();
        tenantId  = UUID.randomUUID();
        coachId   = UUID.randomUUID();
        snapshotId = UUID.randomUUID();

        TenantContext.setTenantId(tenantId);
        when(authentication.getName()).thenReturn(coachId.toString());

        snapshotDto = buildSnapshotDto(snapshotId, atletaId, provaId, false, null);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // --- 9.4 tc_001: geração bem-sucedida → 201 ---

    @Test
    void generate_validRequest_returns201WithSnapshot() {
        when(raceProjectionService.generate(eq(atletaId), eq(tenantId), eq(coachId), any()))
                .thenReturn(snapshotDto);

        ResponseEntity<RaceProjectionSnapshotOutputDto> response =
                controller.generate(atletaId, buildRequest(), authentication);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().id()).isEqualTo(snapshotId);
        assertThat(response.getBody().confidence()).isEqualTo(ProjectionConfidence.HIGH);
    }

    // --- 9.4 tc_002: atleta não encontrado → propaga ResourceNotFoundException ---

    @Test
    void generate_atletaNotFound_propagatesException() {
        when(raceProjectionService.generate(any(), any(), any(), any()))
                .thenThrow(new ResourceNotFoundException("Atleta não encontrado"));

        assertThatThrownBy(() -> controller.generate(atletaId, buildRequest(), authentication))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // --- 9.4 tc_003: visão-atleta sem projeção oficial revisada → 404 ---

    @Test
    void getAthleteView_noOfficialReviewed_returns404() {
        when(raceProjectionService.getAthleteView(atletaId, provaId, tenantId))
                .thenReturn(Optional.empty());

        ResponseEntity<AthleteProjectionViewDto> response =
                controller.getAthleteView(atletaId, provaId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // --- 9.4 tc_004: visão-atleta com projeção oficial revisada → 200 sem campos coach-only ---

    @Test
    void getAthleteView_officialReviewed_returns200WithoutCoachFields() {
        AthleteProjectionViewDto view = new AthleteProjectionViewDto(
                provaId, Instant.now(),
                Map.of(10000, new AthleteProjectionViewDto.ProjectionSummary(10000, 2633, 263, true)),
                "Progressão consistente.", List.of("Premissa 1"));

        when(raceProjectionService.getAthleteView(atletaId, provaId, tenantId))
                .thenReturn(Optional.of(view));

        ResponseEntity<AthleteProjectionViewDto> response =
                controller.getAthleteView(atletaId, provaId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().projections()).containsKey(10000);
        // Visão do atleta não expõe confidence nem goalGapAnalysis
        assertThat(response.getBody().getClass().getDeclaredFields())
                .noneMatch(f -> f.getName().equals("confidence") || f.getName().equals("goalGapAnalysis"));
    }

    // --- 9.4 tc_005: marcar como oficial troca corretamente → 200 com isOfficial=true ---

    @Test
    void markAsOfficial_swapsCorrectly_returns200WithOfficialTrue() {
        RaceProjectionSnapshotOutputDto officialDto = buildSnapshotDto(snapshotId, atletaId, provaId, true, Instant.now());

        when(raceProjectionService.markAsOfficial(snapshotId, atletaId, provaId, tenantId, coachId))
                .thenReturn(officialDto);

        ResponseEntity<RaceProjectionSnapshotOutputDto> response =
                controller.markAsOfficial(atletaId, snapshotId, provaId, authentication);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isOfficial()).isTrue();
        assertThat(response.getBody().coachReviewedAt()).isNotNull();
    }

    // --- 9.4 tc_006: histórico retorna lista ordenada ---

    @Test
    void getHistory_returnsListOrderedByDate() {
        UUID snap1 = UUID.randomUUID();
        UUID snap2 = UUID.randomUUID();
        List<RaceProjectionSnapshotOutputDto> history = List.of(
                buildSnapshotDto(snap1, atletaId, provaId, true, Instant.now()),
                buildSnapshotDto(snap2, atletaId, provaId, false, null));

        when(raceProjectionService.getHistory(atletaId, provaId, tenantId)).thenReturn(history);

        ResponseEntity<List<RaceProjectionSnapshotOutputDto>> response =
                controller.getHistory(atletaId, provaId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(2);
        assertThat(response.getBody().get(0).isOfficial()).isTrue();
    }

    // --- 9.4 tc_007: getOfficial sem projeção → 404 ---

    @Test
    void getOfficial_noOfficialSnapshot_returns404() {
        when(raceProjectionService.getOfficial(atletaId, provaId, tenantId))
                .thenReturn(Optional.empty());

        ResponseEntity<RaceProjectionSnapshotOutputDto> response =
                controller.getOfficial(atletaId, provaId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // --- builders ---

    private RaceProjectionRequestDto buildRequest() {
        return new RaceProjectionRequestDto(
                provaId,
                List.of(10000, 21097),
                new RaceProjectionRequestDto.LoadProjectionData(
                        60.0, 65.0, -5.0,
                        LocalDate.now().plusWeeks(4), 4,
                        PeriodizationPhase.TAPER_OPTIMAL, 65.0, 5.0),
                null,
                null);
    }

    private RaceProjectionSnapshotOutputDto buildSnapshotDto(UUID id, UUID atletaId, UUID provaId,
                                                               boolean isOfficial, Instant reviewedAt) {
        return new RaceProjectionSnapshotOutputDto(
                id, atletaId, provaId,
                Instant.now(), 4, ProjectionConfidence.HIGH,
                isOfficial, reviewedAt,
                Map.of(10000, new RaceProjectionSnapshotOutputDto.ProjectionDto(
                        10000, 2633, 263, 2554, 2712, ProjectionConfidence.HIGH, true)),
                "Progressão consistente.", List.of("Premissa 1"), "Manter taper.",
                new RaceProjectionSnapshotOutputDto.CtlForecastDto(60.0, 65.0, CtlTrend.BUILDING, null),
                null,
                null, null, false, 12);
    }
}
