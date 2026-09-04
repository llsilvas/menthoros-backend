package br.com.menthoros.backend.repository;

import br.com.menthoros.backend.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * V85 — tb_atleta.sexo passa a guardar o nome do enum ({@code MASCULINO}/{@code FEMININO}/{@code OUTRO}),
 * o mesmo valor que o front envia. Antes era {@code VARCHAR(1)} com CHECK em M/F/O, e todo PUT com o
 * formato do front caía em {@code 22001 value too long}.
 *
 * <p>Nível SQL de propósito: o que está sob teste é a coluna e o CHECK, não o mapeamento.</p>
 */
@DisplayName("V85 tb_atleta.sexo: aceita o nome do enum e rejeita a sigla legada")
class AtletaSexoMigrationIT extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    private UUID inserirAssessoria() {
        var id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO tb_assessoria (id, nome, dominio, plano, ativo, max_atletas, max_tecnicos)
                VALUES (?, ?, ?, 'GRATUITO', true, 10, 1)
                """, id, "Assessoria " + id, "slug-" + id);
        return id;
    }

    private int inserirAtleta(String sexo) {
        return jdbc.update("""
                INSERT INTO tb_atleta (id, nome, objetivo, nivel_experiencia, tenant_id, sexo)
                VALUES (?, 'Atleta', 'Correr 10K', 'INICIANTE', ?, ?)
                """, UUID.randomUUID(), inserirAssessoria(), sexo);
    }

    @Test
    @DisplayName("só resta um CHECK sobre sexo — V1 criou um inline e V6 outro nomeado; ambos caem")
    void restaUmUnicoCheck() {
        Integer checks = jdbc.queryForObject("""
                SELECT count(*) FROM pg_constraint
                WHERE conrelid = 'tb_atleta'::regclass AND contype = 'c'
                  AND pg_get_constraintdef(oid) ILIKE '%sexo%'
                """, Integer.class);

        assertThat(checks).isEqualTo(1);
    }

    @ParameterizedTest(name = "aceita {0}")
    @ValueSource(strings = {"MASCULINO", "FEMININO", "OUTRO"})
    void aceitaNomeDoEnum(String sexo) {
        assertThat(inserirAtleta(sexo)).isEqualTo(1);
    }

    @Test
    @DisplayName("sexo nulo continua permitido — atletas legados sem cadastro")
    void aceitaNulo() {
        assertThat(inserirAtleta(null)).isEqualTo(1);
    }

    @ParameterizedTest(name = "rejeita {0}")
    @ValueSource(strings = {"M", "F", "O", "INVALIDO"})
    void rejeitaValorForaDoEnum(String sexo) {
        assertThatThrownBy(() -> inserirAtleta(sexo))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
