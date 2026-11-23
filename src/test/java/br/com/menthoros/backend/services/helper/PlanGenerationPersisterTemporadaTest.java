package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.domain.planner.SeasonWeekSnapshot;
import br.com.menthoros.backend.domain.planner.TrainingPhase;
import br.com.menthoros.backend.entity.PlanoSemanal;
import br.com.menthoros.backend.entity.TreinoPlanejado;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Vínculo com a semana da temporada e desvio do alvo (add-season-plan-weekly-context, 4.1). */
class PlanGenerationPersisterTemporadaTest {

    @Nested
    @DisplayName("registrarSemanaDaTemporada")
    class RegistrarSemanaDaTemporada {

        @Test
        @DisplayName("grava a semana e o desvio com uma casa: (330 - 300) / 300 = +10,0%")
        void desvioNormal() {
            PlanoSemanal plano = planoCom(100, 120, 110);
            SeasonWeekSnapshot semana = semana(300.0);

            PlanGenerationPersister.registrarSemanaDaTemporada(plano, Optional.of(semana));

            assertThat(plano.getSeasonWeekId()).isEqualTo(semana.seasonWeekId());
            assertThat(plano.getTargetDeviationPct()).isEqualByComparingTo("10.0");
        }

        @Test
        @DisplayName("arredonda para uma casa: (200 - 300) / 300 = -33,3%")
        void arredonda() {
            PlanoSemanal plano = planoCom(200);

            PlanGenerationPersister.registrarSemanaDaTemporada(plano, Optional.of(semana(300.0)));

            assertThat(plano.getTargetDeviationPct()).isEqualByComparingTo("-33.3");
        }

        @Test
        @DisplayName("treino sem tssPlanejado conta como 0")
        void treinoSemTss() {
            PlanoSemanal plano = planoCom(150, null);

            PlanGenerationPersister.registrarSemanaDaTemporada(plano, Optional.of(semana(300.0)));

            assertThat(plano.getTargetDeviationPct()).isEqualByComparingTo("-50.0");
        }

        @Test
        @DisplayName("alvo nulo ou zero: grava a semana, desvio fica nulo")
        void alvoNuloOuZero() {
            PlanoSemanal semAlvo = planoCom(150);
            PlanoSemanal alvoZero = planoCom(150);
            SeasonWeekSnapshot nula = semana(null);

            PlanGenerationPersister.registrarSemanaDaTemporada(semAlvo, Optional.of(nula));
            PlanGenerationPersister.registrarSemanaDaTemporada(alvoZero, Optional.of(semana(0.0)));

            assertThat(semAlvo.getSeasonWeekId()).isEqualTo(nula.seasonWeekId());
            assertThat(semAlvo.getTargetDeviationPct()).isNull();
            assertThat(alvoZero.getTargetDeviationPct()).isNull();
        }

        @Test
        @DisplayName("sem temporada, nada é gravado")
        void semTemporada() {
            PlanoSemanal plano = planoCom(150);

            PlanGenerationPersister.registrarSemanaDaTemporada(plano, Optional.empty());

            assertThat(plano.getSeasonWeekId()).isNull();
            assertThat(plano.getTargetDeviationPct()).isNull();
        }
    }

    private static PlanoSemanal planoCom(Integer... tss) {
        List<TreinoPlanejado> treinos = Arrays.stream(tss).map(t -> {
            TreinoPlanejado treino = new TreinoPlanejado();
            treino.setTssPlanejado(t);
            return treino;
        }).toList();
        PlanoSemanal plano = new PlanoSemanal();
        plano.setTreinosPlanejados(treinos);
        return plano;
    }

    private static SeasonWeekSnapshot semana(Double targetTss) {
        return new SeasonWeekSnapshot(UUID.randomUUID(), 2, 10, TrainingPhase.BUILD, targetTss, null, null, false,
                Optional.empty());
    }
}
