package br.com.menthoros.backend.repository;

import br.com.menthoros.backend.AbstractIntegrationTest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/** V106 (coach-zonas-pace-fc-prescricao): coluna nullable que cabe {@code NENHUM} e rejeita valor fora do enum. */
@Transactional
class EtapaTreinoAlvoPrimarioMigrationTest extends AbstractIntegrationTest {

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    @DisplayName("alvo_primario é VARCHAR(10) nullable — NENHUM (6 caracteres) cabe")
    void colunaNullableQueCabeNenhum() {
        Object[] coluna = (Object[]) entityManager.createNativeQuery("""
                SELECT is_nullable, character_maximum_length FROM information_schema.columns
                WHERE table_name = 'tb_etapa_treino' AND column_name = 'alvo_primario'
                """).getSingleResult();

        assertThat(coluna[0]).isEqualTo("YES");
        assertThat(((Number) coluna[1]).intValue()).isEqualTo(10);
    }

    @Test
    @DisplayName("CHECK aceita exatamente PACE, FC e NENHUM")
    void checkRestringeAoEnum() {
        String definicao = (String) entityManager.createNativeQuery("""
                SELECT pg_get_constraintdef(oid) FROM pg_constraint
                WHERE conname = 'ck_etapa_treino_alvo_primario'
                """).getSingleResult();

        assertThat(definicao).contains("PACE").contains("FC").contains("NENHUM");
    }
}
