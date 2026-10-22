package br.com.menthoros.backend.repository;

import br.com.menthoros.backend.AbstractIntegrationTest;
import br.com.menthoros.backend.entity.Assessoria;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.SugestaoCoach;
import br.com.menthoros.backend.enums.AtletaStatus;
import br.com.menthoros.backend.enums.NivelExperiencia;
import br.com.menthoros.backend.enums.PlanoAssessoria;
import br.com.menthoros.backend.enums.StatusSugestao;
import br.com.menthoros.backend.enums.TipoSugestao;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** add-coach-suggestion-edit-delta — contra Postgres real (migration V103 e UPDATEs condicionais). */
class SugestaoCoachEditDeltaIT extends AbstractIntegrationTest {

    @Autowired
    private SugestaoCoachRepository sugestaoCoachRepository;
    @Autowired
    private AssessoriaRepository assessoriaRepository;
    @Autowired
    private AtletaRepository atletaRepository;
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("V103: sugestão nova nasce nunca editada, versao 0, e as constraints antigas seguem")
    void migrationAplicaDefaults() {
        SugestaoCoach s = sugestaoCoachRepository.saveAndFlush(seedSugestaoPendente());

        SugestaoCoach lida = sugestaoCoachRepository.findById(s.getId()).orElseThrow();
        assertThat(lida.getSummaryOriginal()).isNull();
        assertThat(lida.isEditadoPeloCoach()).isFalse();
        assertThat(lida.getEditedAt()).isNull();
        assertThat(lida.getVersao()).isZero();

        Integer constraints = jdbc.queryForObject("""
                SELECT count(*) FROM pg_constraint WHERE conname = 'chk_sugestao_status'
                """, Integer.class);
        Integer indice = jdbc.queryForObject("""
                SELECT count(*) FROM pg_indexes WHERE indexname = 'uk_sugestao_pending'
                """, Integer.class);
        assertThat(constraints).isEqualTo(1);
        assertThat(indice).isEqualTo(1);
    }

    private SugestaoCoach seedSugestaoPendente() {
        Assessoria assessoria = new Assessoria();
        assessoria.setNome("Assessoria Edit Delta " + UUID.randomUUID());
        assessoria.setDominio("edit-delta-" + UUID.randomUUID());
        assessoria.setPlano(PlanoAssessoria.BASIC);
        assessoria = assessoriaRepository.save(assessoria);

        Atleta atleta = new Atleta();
        atleta.setNome("Atleta Edit Delta");
        atleta.setEmail(UUID.randomUUID() + "@test.com");
        atleta.setObjetivo("Correr 10km");
        atleta.setNivelExperiencia(NivelExperiencia.INTERMEDIARIO);
        atleta.setAtivo(AtletaStatus.ATIVO);
        atleta.setAssessoria(assessoria);
        atleta = atletaRepository.save(atleta);

        return SugestaoCoach.builder()
                .tenantId(assessoria.getId())
                .atleta(atleta)
                .tipo(TipoSugestao.RECOVERY)
                .status(StatusSugestao.PENDING)
                .confidence("HIGH")
                .summary("Reduzir volume em 20%")
                .createdAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
    }
}
