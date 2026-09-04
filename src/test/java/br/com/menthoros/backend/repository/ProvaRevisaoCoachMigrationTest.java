package br.com.menthoros.backend.repository;

import br.com.menthoros.backend.AbstractIntegrationTest;
import br.com.menthoros.backend.entity.Assessoria;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.enums.AtletaStatus;
import br.com.menthoros.backend.enums.NivelExperiencia;
import br.com.menthoros.backend.enums.PlanoAssessoria;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V87 — o que o service depende do banco: prova inserida sem as colunas novas nasce revisada
 * (default true, motivo nulo), e o índice parcial de pendentes existe.
 */
@DisplayName("V87 tb_prova: default de revisada_pelo_coach e índice parcial")
class ProvaRevisaoCoachMigrationTest extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private AssessoriaRepository assessoriaRepository;

    @Autowired
    private AtletaRepository atletaRepository;

    @Test
    @DisplayName("prova pré-existente fica revisada com motivo nulo")
    void provaPreExistenteNasceRevisada() {
        Assessoria assessoria = new Assessoria();
        assessoria.setNome("Assessoria V87");
        assessoria.setDominio("v87-" + UUID.randomUUID());
        assessoria.setPlano(PlanoAssessoria.BASIC);
        assessoria = assessoriaRepository.save(assessoria);
        UUID tenantId = assessoria.getId();

        Atleta atleta = new Atleta();
        atleta.setNome("Atleta V87");
        atleta.setEmail("atleta-" + UUID.randomUUID() + "@v87.test");
        atleta.setObjetivo("Correr");
        atleta.setNivelExperiencia(NivelExperiencia.INICIANTE);
        atleta.setAtivo(AtletaStatus.ATIVO);
        atleta.setAssessoria(assessoria);
        UUID atletaId = atletaRepository.save(atleta).getId();
        UUID provaId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO tb_prova (id, atleta_id, tenant_id, nome_prova, tipo_prova, data_prova, distancia)
                VALUES (?, ?, ?, 'Prova legada', 'MARATONA', CURRENT_DATE + 90, 3)
                """, provaId, atletaId, tenantId);

        Map<String, Object> linha = jdbc.queryForMap(
                "SELECT revisada_pelo_coach, motivo_revisao, alvo_anterior_nome FROM tb_prova WHERE id = ?", provaId);

        assertThat(linha.get("revisada_pelo_coach")).isEqualTo(Boolean.TRUE);
        assertThat(linha.get("motivo_revisao")).isNull();
        assertThat(linha.get("alvo_anterior_nome")).isNull();
    }

    @Test
    @DisplayName("índice parcial idx_prova_pendente_revisao existe")
    void indiceParcialExiste() {
        Integer total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM pg_indexes WHERE tablename = 'tb_prova' AND indexname = 'idx_prova_pendente_revisao'",
                Integer.class);

        assertThat(total).isEqualTo(1);
    }
}
