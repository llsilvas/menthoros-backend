package br.com.menthoros.backend.services;

import br.com.menthoros.backend.config.core.WorkoutAnalysisProperties;
import br.com.menthoros.backend.entity.TreinoRealizado;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Elegibilidade compartilhada entre o listener (gera análise) e o endpoint do atleta
 * (devolve 200 PENDING antes de a linha existir) — Codex #2: as duas pontas não podem divergir.
 */
class WorkoutAnalysisEligibilityTest {

    private WorkoutAnalysisProperties properties;
    private WorkoutAnalysisEligibility eligibility;

    @BeforeEach
    void setUp() {
        properties = new WorkoutAnalysisProperties();
        properties.setMaxIdadeDias(30);
        eligibility = new WorkoutAnalysisEligibility(properties);
    }

    private TreinoRealizado treino(Integer rpe, LocalDate data) {
        TreinoRealizado t = new TreinoRealizado();
        t.setPercepcaoEsforco(rpe);
        t.setDataTreino(data);
        return t;
    }

    @Test
    void elegivel_com_rpe_e_data_recente() {
        assertTrue(eligibility.elegivel(treino(7, LocalDate.now().minusDays(1))));
    }

    @Test
    void nao_elegivel_sem_rpe() {
        assertFalse(eligibility.elegivel(treino(null, LocalDate.now())));
    }

    @Test
    void nao_elegivel_mais_antigo_que_max_idade_dias() {
        assertFalse(eligibility.elegivel(treino(7, LocalDate.now().minusDays(31))));
    }

    @Test
    void elegivel_exatamente_no_limite_de_idade() {
        // Mesmo comportamento do guard original do listener: isBefore(limite) exclui, igual inclui.
        assertTrue(eligibility.elegivel(treino(7, LocalDate.now().minusDays(30))));
    }

    @Test
    void elegivel_com_data_nula() {
        // Defensivo (CA8 do ingestor): ausência de dataTreino não presume "antigo demais".
        assertTrue(eligibility.elegivel(treino(7, null)));
    }
}
