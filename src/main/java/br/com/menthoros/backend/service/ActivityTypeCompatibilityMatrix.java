package br.com.menthoros.backend.service;

import br.com.menthoros.backend.enums.TipoTreino;

/**
 * Matriz de compatibilidade entre tipos de atividades Strava e TipoTreino planejado.
 *
 * Regra MVP: Todos os TipoTreino (corrida) são compatíveis entre si.
 * Atividades sem tipo definido são compatíveis com qualquer planejado.
 */
public class ActivityTypeCompatibilityMatrix {

    private ActivityTypeCompatibilityMatrix() {
        // Utility class - prevent instantiation
    }

    /**
     * Verifica se tipo de atividade é compatível com tipo de treino planejado.
     *
     * @param activityType tipo da atividade realizada (pode ser null)
     * @param plannedType tipo do treino planejado (pode ser null)
     * @return true se compatível, false se incompatível
     */
    public static boolean isCompatible(TipoTreino activityType, TipoTreino plannedType) {
        // Se algum for null, compatível por padrão
        if (activityType == null || plannedType == null) {
            return true;
        }

        // MVP: todos os TipoTreino são de corrida, portanto compatíveis
        // Futuro: expandir com regras granulares (ex: natação ≠ ciclismo)
        return true;
    }
}
