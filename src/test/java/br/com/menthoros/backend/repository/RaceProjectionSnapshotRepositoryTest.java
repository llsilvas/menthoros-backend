package br.com.menthoros.backend.repository;

import br.com.menthoros.backend.AbstractIntegrationTest;
import br.com.menthoros.backend.entity.Assessoria;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.Prova;
import br.com.menthoros.backend.entity.RaceProjectionSnapshot;
import br.com.menthoros.backend.enums.AtletaStatus;
import br.com.menthoros.backend.enums.DistanciaProva;
import br.com.menthoros.backend.enums.NivelExperiencia;
import br.com.menthoros.backend.enums.PlanoAssessoria;
import br.com.menthoros.backend.enums.ProvaStatus;
import br.com.menthoros.backend.enums.TipoProva;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link RaceProjectionSnapshotRepository}.
 *
 * Verifica as invariantes de negócio do modelo append-only:
 * - Snapshots nunca são deletados após persistência
 * - Troca de projeção oficial respeita a regra D5 (no máximo uma por atleta/prova)
 * - Queries de histórico e de oficial retornam os resultados corretos
 */
@Transactional
class RaceProjectionSnapshotRepositoryTest extends AbstractIntegrationTest {

    @Autowired private RaceProjectionSnapshotRepository snapshotRepository;
    @Autowired private AssessoriaRepository assessoriaRepository;
    @Autowired private AtletaRepository atletaRepository;
    @Autowired private ProvaRepository provaRepository;

    private Assessoria assessoria;
    private Atleta atleta;
    private Prova prova;
    private UUID tenantId;
    private UUID coachId;

    @BeforeEach
    void setup() {
        tenantId = UUID.randomUUID();
        coachId = UUID.randomUUID();

        assessoria = new Assessoria();
        assessoria.setNome("Assessoria Test Projection");
        assessoria.setDominio("proj-test-" + UUID.randomUUID());
        assessoria.setPlano(PlanoAssessoria.BASIC);
        assessoria = assessoriaRepository.save(assessoria);

        atleta = new Atleta();
        atleta.setNome("Atleta Projeção");
        atleta.setEmail("projecao-" + UUID.randomUUID() + "@test.com");
        atleta.setObjetivo("Maratona sub-4h");
        atleta.setNivelExperiencia(NivelExperiencia.INTERMEDIARIO);
        atleta.setAtivo(AtletaStatus.ATIVO);
        atleta.setAssessoria(assessoria);
        atleta = atletaRepository.save(atleta);

        prova = new Prova();
        prova.setNomeProva("Maratona SP Test");
        prova.setDataProva(LocalDate.now().plusMonths(3));
        prova.setDistancia(DistanciaProva.KM_42);
        prova.setTipoProva(TipoProva.CORRIDA_RUA);
        prova.setStatusProva(ProvaStatus.CONFIRMADA);
        prova.setAtleta(atleta);
        prova.setAssessoria(assessoria);
        prova = provaRepository.save(prova);
    }

    // sc_001: append-only — salvar dois snapshots → ambos persistidos, nenhum sobrescrito
    @Test
    @DisplayName("save: append-only — múltiplos snapshots do mesmo atleta/prova coexistem")
    void save_appendOnly_multipleSnapshotsCoexist() {
        snapshotRepository.save(buildSnapshot(false));
        snapshotRepository.save(buildSnapshot(false));

        List<RaceProjectionSnapshot> all = snapshotRepository.findByAthleteIdAndRaceIdOrderByGeneratedAtDesc(
                atleta.getId(), prova.getId());

        assertThat(all).hasSize(2);
    }

    // sc_002: findLatestByAthleteId retorna o snapshot mais recente
    @Test
    @DisplayName("findLatestByAthleteId: retorna o snapshot mais recente do atleta")
    void findLatestByAthleteId_returnsLatest() {
        snapshotRepository.save(buildSnapshot(false));
        RaceProjectionSnapshot second = snapshotRepository.save(buildSnapshot(false));

        Optional<RaceProjectionSnapshot> latest = snapshotRepository.findLatestByAthleteId(atleta.getId());

        assertThat(latest).isPresent();
        assertThat(latest.get().getId()).isEqualTo(second.getId());
    }

    // sc_003: clearOfficialByAthleteIdAndRaceId + marcar novo → apenas um oficial por atleta/prova
    @Test
    @DisplayName("clearOfficial + markOfficial: no máximo um oficial por atleta/prova (regra D5)")
    void clearAndMarkOfficial_onlyOneOfficialPerAthleteRace() {
        RaceProjectionSnapshot first = buildSnapshot(true);
        snapshotRepository.save(first);

        // Troca de oficial: limpa existente, marca novo
        snapshotRepository.clearOfficialByAthleteIdAndRaceId(atleta.getId(), prova.getId());
        RaceProjectionSnapshot second = buildSnapshot(true);
        snapshotRepository.save(second);

        Optional<RaceProjectionSnapshot> official = snapshotRepository
                .findOfficialByAthleteIdAndRaceId(atleta.getId(), prova.getId());

        assertThat(official).isPresent();
        assertThat(official.get().getId()).isEqualTo(second.getId());

        // Confirma que o primeiro não é mais oficial
        List<RaceProjectionSnapshot> all = snapshotRepository
                .findByAthleteIdAndRaceIdOrderByGeneratedAtDesc(atleta.getId(), prova.getId());
        long officialsCount = all.stream().filter(RaceProjectionSnapshot::isOfficial).count();
        assertThat(officialsCount).isEqualTo(1);
    }

    // sc_004: findOfficialByAthleteIdAndRaceId → empty quando nenhum oficial existe
    @Test
    @DisplayName("findOfficialByAthleteIdAndRaceId: empty quando nenhum snapshot é oficial")
    void findOfficialByAthleteIdAndRaceId_noOfficial_returnsEmpty() {
        snapshotRepository.save(buildSnapshot(false));

        Optional<RaceProjectionSnapshot> official = snapshotRepository
                .findOfficialByAthleteIdAndRaceId(atleta.getId(), prova.getId());

        assertThat(official).isEmpty();
    }

    // sc_005: findByAthleteIdAndRaceIdOrderByGeneratedAtDesc → lista vazia quando sem snapshots
    @Test
    @DisplayName("findByAthleteIdAndRaceId: lista vazia quando atleta sem projeções para esta prova")
    void findByAthleteIdAndRaceId_noSnapshots_returnsEmpty() {
        List<RaceProjectionSnapshot> result = snapshotRepository
                .findByAthleteIdAndRaceIdOrderByGeneratedAtDesc(atleta.getId(), UUID.randomUUID());

        assertThat(result).isEmpty();
    }

    // --- helper ---

    private RaceProjectionSnapshot buildSnapshot(boolean official) {
        RaceProjectionSnapshot s = new RaceProjectionSnapshot();
        s.setAthlete(atleta);
        s.setRace(prova);
        s.setTenantId(tenantId);
        s.setCoachId(coachId);
        s.setProjectionsJson("{\"10000\":{\"projected_time_seconds\":2700}}");
        s.setConfidence("HIGH");
        s.setOfficial(official);
        s.setRiegelCalibrated(false);
        return s;
    }
}
