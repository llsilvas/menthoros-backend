package br.com.menthoros.backend.service;

import br.com.menthoros.backend.enums.TipoTreino;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ActivityTypeCompatibilityMatrix")
class ActivityTypeCompatibilityMatrixTest {

    @Test
    @DisplayName("deve aceitar quando tipos são iguais")
    void testSameType() {
        assertTrue(ActivityTypeCompatibilityMatrix.isCompatible(
            TipoTreino.FACIL, TipoTreino.FACIL
        ));
        assertTrue(ActivityTypeCompatibilityMatrix.isCompatible(
            TipoTreino.INTERVALADO, TipoTreino.INTERVALADO
        ));
    }

    @Test
    @DisplayName("deve aceitar quando tipos de corrida são diferentes (MVP)")
    void testDifferentRunTypes() {
        assertTrue(ActivityTypeCompatibilityMatrix.isCompatible(
            TipoTreino.FACIL, TipoTreino.INTERVALADO
        ));
        assertTrue(ActivityTypeCompatibilityMatrix.isCompatible(
            TipoTreino.LONGO, TipoTreino.TEMPO_RUN
        ));
    }

    @Test
    @DisplayName("deve aceitar quando atividade é null")
    void testNullActivity() {
        assertTrue(ActivityTypeCompatibilityMatrix.isCompatible(
            null, TipoTreino.FACIL
        ));
    }

    @Test
    @DisplayName("deve aceitar quando planejado é null")
    void testNullPlanned() {
        assertTrue(ActivityTypeCompatibilityMatrix.isCompatible(
            TipoTreino.FACIL, null
        ));
    }

    @Test
    @DisplayName("deve aceitar quando ambos são null")
    void testBothNull() {
        assertTrue(ActivityTypeCompatibilityMatrix.isCompatible(null, null));
    }
}
