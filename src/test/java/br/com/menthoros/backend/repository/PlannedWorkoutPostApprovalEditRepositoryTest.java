package br.com.menthoros.backend.repository;

import br.com.menthoros.backend.AbstractIntegrationTest;
import br.com.menthoros.backend.entity.Assessoria;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.PlanoMetaDados;
import br.com.menthoros.backend.entity.PlanoSemanal;
import br.com.menthoros.backend.entity.PlannedWorkoutPostApprovalEdit;
import br.com.menthoros.backend.entity.TreinoPlanejado;
import br.com.menthoros.backend.enums.AtletaStatus;
import br.com.menthoros.backend.enums.DiaSemana;
import br.com.menthoros.backend.enums.NivelExperiencia;
import br.com.menthoros.backend.enums.PlanoAssessoria;
import br.com.menthoros.backend.enums.PlanoReviewStatus;
import br.com.menthoros.backend.enums.PlanoStatus;
import br.com.menthoros.backend.enums.TipoTreino;
import br.com.menthoros.backend.enums.WorkoutEditReason;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** V109 (coach-editar-treino-aprovado, D3/D7): marca no treino e registro da edição pós-aprovação. */
@Transactional
@DisplayName("V109 — edição pós-aprovação")
class PlannedWorkoutPostApprovalEditRepositoryTest extends AbstractIntegrationTest {

    @Autowired private AssessoriaRepository assessoriaRepository;
    @Autowired private AtletaRepository atletaRepository;
    @Autowired private PlanoMetadadosRepository planoMetadadosRepository;
    @Autowired private PlanoSemanalRepository planoSemanalRepository;
    @Autowired private PlannedWorkoutPostApprovalEditRepository editRepository;
    @Autowired private EntityManager entityManager;

    private TreinoPlanejado treino;

    @BeforeEach
    void setUp() {
        PlanoSemanal plano = planoSemanalRepository.save(novoPlano());
        treino = plano.getTreinosPlanejados().getFirst();
        entityManager.flush();
    }

    @Test
    @DisplayName("edited_after_approval_at é TIMESTAMPTZ nullable, sem backfill")
    void colunaNullable() {
        Object[] coluna = (Object[]) entityManager.createNativeQuery("""
                SELECT is_nullable, data_type FROM information_schema.columns
                WHERE table_name = 'tb_treino_planejado' AND column_name = 'edited_after_approval_at'
                """).getSingleResult();

        assertThat(coluna[0]).isEqualTo("YES");
        assertThat(coluna[1]).isEqualTo("timestamp with time zone");
        entityManager.clear();
        assertThat(entityManager.find(TreinoPlanejado.class, treino.getId()).getEditedAfterApprovalAt()).isNull();
    }

    @Test
    @DisplayName("grava e lê o registro com JSONB e motivo nulo")
    void roundTripSemMotivo() {
        PlannedWorkoutPostApprovalEdit edit = novoRegistro(null);
        UUID id = editRepository.save(edit).getId();
        entityManager.flush();
        entityManager.clear();

        PlannedWorkoutPostApprovalEdit lido = editRepository.findById(id).orElseThrow();
        assertThat(lido.getReason()).isNull();
        assertThat(lido.getTenantId()).isEqualTo(treino.getTenantId());
        assertThat(lido.getSnapshotBefore()).contains("\"duracaoMin\"");
        assertThat(lido.getSnapshotAfter()).contains("50");
    }

    @Test
    @DisplayName("grava o motivo pelo nome do enum")
    void roundTripComMotivo() {
        UUID id = editRepository.save(novoRegistro(WorkoutEditReason.LOAD)).getId();
        entityManager.flush();
        entityManager.clear();

        assertThat(editRepository.findById(id).orElseThrow().getReason()).isEqualTo(WorkoutEditReason.LOAD);
    }

    @Test
    @DisplayName("excluir o treino apaga o registro em cascata (LGPD, D7)")
    void cascataComOTreino() {
        UUID id = editRepository.save(novoRegistro(WorkoutEditReason.PAIN_INJURY)).getId();
        entityManager.flush();

        entityManager.createNativeQuery("DELETE FROM tb_treino_planejado WHERE id = :id")
                .setParameter("id", treino.getId())
                .executeUpdate();
        entityManager.clear();

        assertThat(editRepository.findById(id)).isEmpty();
    }

    private PlannedWorkoutPostApprovalEdit novoRegistro(WorkoutEditReason reason) {
        PlannedWorkoutPostApprovalEdit edit = new PlannedWorkoutPostApprovalEdit();
        edit.setTenantId(treino.getTenantId());
        edit.setPlannedWorkoutId(treino.getId());
        edit.setWeeklyPlanId(treino.getPlanoSemanal().getId());
        edit.setEditedBy("coach-sub");
        edit.setEditedAt(Instant.now().truncatedTo(ChronoUnit.MICROS));
        edit.setReason(reason);
        edit.setSnapshotBefore("{\"duracaoMin\": 40}");
        edit.setSnapshotAfter("{\"duracaoMin\": 50}");
        return edit;
    }

    private PlanoSemanal novoPlano() {
        Assessoria assessoria = new Assessoria();
        assessoria.setNome("Assessoria Edicao");
        assessoria.setDominio("edicao-" + UUID.randomUUID());
        assessoria.setPlano(PlanoAssessoria.BASIC);
        assessoria = assessoriaRepository.save(assessoria);

        Atleta atleta = new Atleta();
        atleta.setNome("Atleta Edicao");
        atleta.setObjetivo("Correr 10km");
        atleta.setNivelExperiencia(NivelExperiencia.INTERMEDIARIO);
        atleta.setAtivo(AtletaStatus.ATIVO);
        atleta.setAssessoria(assessoria);
        atleta = atletaRepository.save(atleta);

        PlanoMetaDados meta = new PlanoMetaDados();
        meta.setAtleta(atleta);
        meta.setAssessoria(assessoria);
        meta.setDiaPreferidoLongo(DiaSemana.SABADO);
        meta = planoMetadadosRepository.save(meta);

        PlanoSemanal plano = new PlanoSemanal();
        plano.setAtleta(atleta);
        plano.setAssessoria(assessoria);
        plano.setPlanoMetaDados(meta);
        plano.setSemanaInicio(LocalDate.of(2026, 9, 28));
        plano.setSemanaFim(LocalDate.of(2026, 10, 4));
        plano.setVolumePlanejadoKm(BigDecimal.valueOf(30));
        plano.setStatus(PlanoStatus.PLANEJADO);
        plano.setReviewStatus(PlanoReviewStatus.APROVADO);

        TreinoPlanejado t = new TreinoPlanejado();
        t.setPlanoSemanal(plano);
        t.setAtleta(atleta);
        t.setTenantId(assessoria.getId());
        t.setDiaSemana(DiaSemana.TERCA);
        t.setTipoTreino(TipoTreino.CONTINUO);
        t.setDuracaoMin(Duration.ofMinutes(40));
        t.setDataTreino(LocalDate.of(2026, 9, 29));
        plano.setTreinosPlanejados(new java.util.ArrayList<>(List.of(t)));
        return plano;
    }
}
