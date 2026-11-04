package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.domain.workout.HrTarget;
import br.com.menthoros.backend.domain.workout.IntensityTarget;
import br.com.menthoros.backend.domain.workout.PaceTarget;
import br.com.menthoros.backend.domain.workout.StructuredWorkout;
import br.com.menthoros.backend.domain.workout.WorkoutStep;
import br.com.menthoros.backend.exception.DomainRuleViolationException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GarminWorkoutPayloadMapperTest {

    private final GarminWorkoutPayloadMapper mapper = new GarminWorkoutPayloadMapper(new ObjectMapper());

    private static StructuredWorkout treino(String prefixo, String descricao, List<WorkoutStep> steps) {
        return new StructuredWorkout("menthoros-1", "Tiros", prefixo, LocalDate.of(2026, 11, 2), descricao, steps);
    }

    @Test
    @DisplayName("CA1: aquecimento, 6x(400 m no pace + 200 m sem objetivo) e desaquecimento")
    void treinoDeCorrida() {
        StructuredWorkout workout = treino(null, "Tiros de 400", List.of(
                WorkoutStep.simples("Aquecimento", 600, null, null),
                WorkoutStep.bloco("6x", 6, List.of(
                        WorkoutStep.simples("Tiro", null, 400, new PaceTarget(240, 250)),
                        WorkoutStep.simples("Trote", null, 200, null))),
                WorkoutStep.simples("Desaquecimento", 300, null, null)));

        JsonNode payload = mapper.montar(workout);

        assertThat(payload.get("sport").asText()).isEqualTo("RUNNING");
        assertThat(payload.get("workoutName").asText()).isEqualTo("Tiros");
        assertThat(payload.get("workoutProvider").asText()).isEqualTo("Menthoros");
        assertThat(payload.get("workoutSourceId").asText()).isEqualTo("Menthoros");
        assertThat(payload.get("isSessionTransitionEnabled").asBoolean()).isFalse();
        assertThat(payload.get("segments")).hasSize(1);

        JsonNode steps = payload.get("segments").get(0).get("steps");
        assertThat(steps).hasSize(3);

        JsonNode aquecimento = steps.get(0);
        assertThat(aquecimento.get("type").asText()).isEqualTo("WorkoutStep");
        assertThat(aquecimento.get("durationType").asText()).isEqualTo("TIME");
        assertThat(aquecimento.get("durationValue").asInt()).isEqualTo(600);
        assertThat(aquecimento.get("targetType").asText()).isEqualTo("OPEN");
        assertThat(aquecimento.get("intensity").asText()).isEqualTo("ACTIVE");

        JsonNode bloco = steps.get(1);
        assertThat(bloco.get("type").asText()).isEqualTo("WorkoutRepeatStep");
        assertThat(bloco.get("repeatType").asText()).isEqualTo("REPEAT_UNTIL_STEPS_CMPLT");
        assertThat(bloco.get("repeatValue").asInt()).isEqualTo(6);
        JsonNode tiro = bloco.get("steps").get(0);
        assertThat(tiro.get("durationType").asText()).isEqualTo("DISTANCE");
        assertThat(tiro.get("durationValue").asInt()).isEqualTo(400);
        assertThat(tiro.get("targetType").asText()).isEqualTo("PACE");
        assertThat(tiro.get("targetValueLow").asDouble()).isEqualTo(4.0);
        assertThat(tiro.get("targetValueHigh").asDouble()).isEqualTo(4.167);
        assertThat(tiro.get("intensity").asText()).isEqualTo("ACTIVE");
        JsonNode trote = bloco.get("steps").get(1);
        assertThat(trote.get("targetType").asText()).isEqualTo("OPEN");
        assertThat(trote.get("intensity").asText()).isEqualTo("REST");
    }

    @Test
    @DisplayName("stepOrder e sequencial no treino inteiro, contando o bloco e os filhos")
    void stepOrderGlobal() {
        StructuredWorkout workout = treino(null, null, List.of(
                WorkoutStep.simples("A", 60, null, null),
                WorkoutStep.bloco("2x", 2, List.of(
                        WorkoutStep.simples("B", 60, null, null),
                        WorkoutStep.simples("C", 60, null, null))),
                WorkoutStep.simples("D", 60, null, null)));

        JsonNode steps = mapper.montar(workout).get("segments").get(0).get("steps");

        assertThat(steps.get(0).get("stepOrder").asInt()).isEqualTo(1);
        assertThat(steps.get(1).get("stepOrder").asInt()).isEqualTo(2);
        assertThat(steps.get(1).get("steps").get(0).get("stepOrder").asInt()).isEqualTo(3);
        assertThat(steps.get(1).get("steps").get(1).get("stepOrder").asInt()).isEqualTo(4);
        assertThat(steps.get(2).get("stepOrder").asInt()).isEqualTo(5);
    }

    @Test
    @DisplayName("CA2: FC em bpm absoluto vai como HEART_RATE, sem conversao")
    void fcAbsoluta() {
        StructuredWorkout workout = treino(null, null, List.of(
                WorkoutStep.simples("Z2", 1800, null, new HrTarget(140, 150))));

        JsonNode step = mapper.montar(workout).get("segments").get(0).get("steps").get(0);

        assertThat(step.get("targetType").asText()).isEqualTo("HEART_RATE");
        assertThat(step.get("targetValueLow").asInt()).isEqualTo(140);
        assertThat(step.get("targetValueHigh").asInt()).isEqualTo(150);
    }

    @Test
    @DisplayName("pace unico vira faixa de +-5 s/km: largura zero faria o relogio alertar o tempo todo")
    void paceUnicoGanhaTolerancia() {
        StructuredWorkout workout = treino(null, null, List.of(
                WorkoutStep.simples("Ritmo", 600, null, new PaceTarget(270, 270))));

        JsonNode step = mapper.montar(workout).get("segments").get(0).get("steps").get(0);

        // 4:30/km -> faixa 4:25-4:35/km: 1000/275 e 1000/265 m/s
        assertThat(step.get("targetValueLow").asDouble()).isEqualTo(3.636);
        assertThat(step.get("targetValueHigh").asDouble()).isEqualTo(3.774);
    }

    @Test
    @DisplayName("faixa de pace explicita nao ganha tolerancia")
    void faixaDePaceSemTolerancia() {
        StructuredWorkout workout = treino(null, null, List.of(
                WorkoutStep.simples("Ritmo", 600, null, new PaceTarget(270, 280))));

        JsonNode step = mapper.montar(workout).get("segments").get(0).get("steps").get(0);

        assertThat(step.get("targetValueLow").asDouble()).isEqualTo(3.571);
        assertThat(step.get("targetValueHigh").asDouble()).isEqualTo(3.704);
    }

    @Test
    @DisplayName("etapa sem duracao nem distancia vira OPEN (botao lap)")
    void etapaAberta() {
        StructuredWorkout workout = treino(null, null, List.of(WorkoutStep.simples("Livre", null, null, null)));

        JsonNode step = mapper.montar(workout).get("segments").get(0).get("steps").get(0);

        assertThat(step.get("durationType").asText()).isEqualTo("OPEN");
        assertThat(step.has("durationValue")).isFalse();
    }

    @Test
    @DisplayName("pace com valor zero ou nulo nao divide por zero: vira OPEN")
    void paceInvalidoViraOpen() {
        StructuredWorkout workout = treino(null, null, List.of(
                WorkoutStep.simples("Zero", 600, null, new PaceTarget(0, 250)),
                WorkoutStep.simples("Nulo", 600, null, new PaceTarget(null, null))));

        JsonNode steps = mapper.montar(workout).get("segments").get(0).get("steps");

        assertThat(steps.get(0).get("targetType").asText()).isEqualTo("OPEN");
        assertThat(steps.get(1).get("targetType").asText()).isEqualTo("OPEN");
    }

    @Test
    @DisplayName("nome leva o prefixo e a descricao e cortada em 1024 caracteres")
    void nomeEDescricao() {
        StructuredWorkout workout = treino("[Menthoros]", "x".repeat(1500),
                List.of(WorkoutStep.simples("A", 60, null, IntensityTarget.SEM_OBJETIVO)));

        JsonNode payload = mapper.montar(workout);

        assertThat(payload.get("workoutName").asText()).isEqualTo("[Menthoros] Tiros");
        assertThat(payload.get("description").asText()).hasSize(1024);
    }

    @Test
    @DisplayName("sem descricao, o campo nao vai no payload")
    void semDescricao() {
        StructuredWorkout workout = treino(null, null, List.of(WorkoutStep.simples("A", 60, null, null)));

        assertThat(mapper.montar(workout).has("description")).isFalse();
    }

    @Test
    @DisplayName("mais de 100 steps rejeita antes de qualquer chamada")
    void limiteDeCemSteps() {
        List<WorkoutStep> muitos = new ArrayList<>();
        for (int i = 0; i < 101; i++) {
            muitos.add(WorkoutStep.simples("s" + i, 60, null, null));
        }

        assertThatThrownBy(() -> mapper.montar(treino(null, null, muitos)))
                .isInstanceOf(DomainRuleViolationException.class)
                .hasMessageContaining("100");
    }

    @Test
    @DisplayName("exatamente 100 steps passa")
    void cemStepsPassa() {
        List<WorkoutStep> cem = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            cem.add(WorkoutStep.simples("s" + i, 60, null, null));
        }

        assertThat(mapper.montar(treino(null, null, cem)).get("segments").get(0).get("steps")).hasSize(100);
    }
}
