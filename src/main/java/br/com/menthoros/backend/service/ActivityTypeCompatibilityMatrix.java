package br.com.menthoros.backend.service;

import br.com.menthoros.backend.enums.TipoTreino;
import java.util.EnumSet;

/**
 * Matriz de compatibilidade entre tipos de atividades Strava e TipoTreino planejado.
 *
 * Regra: dois tipos são compatíveis se pertencem ao mesmo **esporte** (modalidade).
 * MVP: Todos os TipoTreino (10 tipos) são modalidade corrida → todos compatíveis entre si.
 * Futuro: Quando natação/ciclismo/etc forem adicionados ao enum, expandir TIPOS_CORRIDA.
 */
public class ActivityTypeCompatibilityMatrix {

    // MVP: todos os 10 TipoTreino (REGENERATIVO, INTERVALADO, etc) são corrida
    // Quando natação/ciclismo/etc forem adicionados ao enum, adicionar novo Set/método
    private static final EnumSet<TipoTreino> TIPOS_CORRIDA = EnumSet.allOf(TipoTreino.class);

    private ActivityTypeCompatibilityMatrix() {
        // Utility class - prevent instantiation
    }

    /**
     * Verifica se tipo de atividade é compatível com tipo de treino planejado.
     * Compatibilidade baseada em modalidade (esporte): ambos devem pertencer à mesma modalidade.
     *
     * @param activityType tipo da atividade realizada (pode ser null)
     * @param plannedType tipo do treino planejado (pode ser null)
     * @return true se compatível (mesmo esporte), false se incompatível
     */
    public static boolean isCompatible(TipoTreino activityType, TipoTreino plannedType) {
        // Se algum for null, compatível por padrão (sem restrição de tipo)
        if (activityType == null || plannedType == null) {
            return true;
        }

        // Compatível se ambos pertencem ao mesmo esporte (modalidade)
        return mesmoEsporte(activityType, plannedType);
    }

    /**
     * Verifica se dois tipos pertencem ao mesmo esporte.
     * MVP: ambos precisam estar em TIPOS_CORRIDA.
     */
    private static boolean mesmoEsporte(TipoTreino a, TipoTreino b) {
        return TIPOS_CORRIDA.contains(a) == TIPOS_CORRIDA.contains(b);
    }
}
