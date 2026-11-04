package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.domain.workout.HrTarget;
import br.com.menthoros.backend.domain.workout.PaceTarget;
import br.com.menthoros.backend.domain.workout.StructuredWorkout;
import br.com.menthoros.backend.domain.workout.WorkoutStep;
import br.com.menthoros.backend.exception.DomainRuleViolationException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Converte o {@link StructuredWorkout} canônico no JSON de workout da Training API V2 do Garmin
 * (design D5). Sem estado e sem I/O: o adapter de push só envia o que sai daqui.
 *
 * <p>Alvo de FC vai em bpm absoluto e pace em faixa de m/s; nunca zona.
 */
@Component
public class GarminWorkoutPayloadMapper {

    /** Valor de {@code workoutProvider} e {@code workoutSourceId}: marca os workouts do Menthoros na reconciliação. */
    static final String PROVEDOR = "Menthoros";

    private static final int MAX_STEPS = 100;
    private static final int MAX_DESCRICAO = 1024;
    // shortcut: tolerância fixa para pace de valor único; trocar por percentual se o founder preferir.
    private static final int TOLERANCIA_PACE_UNICO_SEGS_KM = 5;

    private final ObjectMapper objectMapper;

    public GarminWorkoutPayloadMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * @throws DomainRuleViolationException se o treino passar de 100 steps (limite do Garmin)
     */
    public JsonNode montar(StructuredWorkout workout) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("workoutName", nomeComPrefixo(workout));
        if (workout.description() != null && !workout.description().isBlank()) {
            String d = workout.description();
            payload.put("description", d.length() > MAX_DESCRICAO ? d.substring(0, MAX_DESCRICAO) : d);
        }
        payload.put("sport", "RUNNING");
        payload.put("workoutProvider", PROVEDOR);
        payload.put("workoutSourceId", PROVEDOR);
        payload.put("isSessionTransitionEnabled", false);

        int[] ordem = {0};
        ArrayNode steps = objectMapper.createArrayNode();
        for (WorkoutStep step : workout.steps()) {
            steps.add(montarStep(step, false, ordem));
        }
        if (ordem[0] > MAX_STEPS) {
            throw new DomainRuleViolationException(
                    "Treino com " + ordem[0] + " steps passa do limite de " + MAX_STEPS + " do Garmin");
        }

        ObjectNode segmento = objectMapper.createObjectNode();
        segmento.put("segmentOrder", 1);
        segmento.put("sport", "RUNNING");
        segmento.set("steps", steps);
        payload.set("segments", objectMapper.createArrayNode().add(segmento));
        return payload;
    }

    private ObjectNode montarStep(WorkoutStep step, boolean dentroDeBloco, int[] ordem) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("stepOrder", ++ordem[0]);

        if (step.reps() != null) {
            node.put("type", "WorkoutRepeatStep");
            node.put("repeatType", "REPEAT_UNTIL_STEPS_CMPLT");
            node.put("repeatValue", step.reps());
            ArrayNode filhos = objectMapper.createArrayNode();
            List<WorkoutStep> sub = step.steps() == null ? List.of() : step.steps();
            for (WorkoutStep filho : sub) {
                filhos.add(montarStep(filho, true, ordem));
            }
            node.set("steps", filhos);
            return node;
        }

        node.put("type", "WorkoutStep");
        if (step.durationSeconds() != null) {
            node.put("durationType", "TIME");
            node.put("durationValue", step.durationSeconds());
        } else if (step.distanceMeters() != null) {
            node.put("durationType", "DISTANCE");
            node.put("durationValue", step.distanceMeters());
        } else {
            node.put("durationType", "OPEN");
        }

        boolean comAlvo = aplicarAlvo(node, step);
        // shortcut: aquecimento e desaquecimento vão como ACTIVE; subir para WARMUP/COOLDOWN se um atleta
        // reclamar da tela do relógio. REST só para "sem objetivo" dentro de bloco (o trote entre tiros).
        node.put("intensity", dentroDeBloco && !comAlvo ? "REST" : "ACTIVE");
        return node;
    }

    /** Devolve {@code true} quando a etapa tem alvo. Pace nulo ou zero vira OPEN: não há como dividir. */
    private boolean aplicarAlvo(ObjectNode node, WorkoutStep step) {
        if (step.meta() instanceof PaceTarget pace && positivo(pace.startSecsPerKm()) && positivo(pace.endSecsPerKm())) {
            int rapido = Math.min(pace.startSecsPerKm(), pace.endSecsPerKm());
            int lento = Math.max(pace.startSecsPerKm(), pace.endSecsPerKm());
            if (rapido == lento) {
                // Faixa de largura zero faz o relógio alertar "fora do ritmo" a cada oscilação; o
                // Garmin só aceita valor único como zona (targetValue 1-5), então vira faixa.
                rapido -= TOLERANCIA_PACE_UNICO_SEGS_KM;
                lento += TOLERANCIA_PACE_UNICO_SEGS_KM;
            }
            node.put("targetType", "PACE");
            node.put("targetValueLow", metrosPorSegundo(lento));
            node.put("targetValueHigh", metrosPorSegundo(rapido));
            return true;
        }
        if (step.meta() instanceof HrTarget hr && hr.startBpm() != null && hr.endBpm() != null) {
            node.put("targetType", "HEART_RATE");
            node.put("targetValueLow", hr.startBpm());
            node.put("targetValueHigh", hr.endBpm());
            return true;
        }
        node.put("targetType", "OPEN");
        return false;
    }

    private static boolean positivo(Integer valor) {
        return valor != null && valor > 0;
    }

    private static double metrosPorSegundo(int segundosPorKm) {
        return Math.round(1000.0 / segundosPorKm * 1000.0) / 1000.0;
    }

    private static String nomeComPrefixo(StructuredWorkout workout) {
        return workout.namePrefix() != null ? workout.namePrefix() + " " + workout.name() : workout.name();
    }
}
