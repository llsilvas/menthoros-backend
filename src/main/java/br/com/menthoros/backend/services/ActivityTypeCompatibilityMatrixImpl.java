package br.com.menthoros.backend.services;

import br.com.menthoros.backend.enums.TipoTreino;
import org.springframework.stereotype.Service;
import java.util.EnumSet;

/**
 * Matriz de compatibilidade entre tipos de atividades Strava e TipoTreino planejado.
 *
 * Regra: dois tipos são compatíveis se pertencem ao mesmo **esporte** (modalidade).
 * MVP: Todos os TipoTreino (10 tipos) são modalidade corrida → todos compatíveis entre si.
 * Futuro: Quando natação/ciclismo/etc forem adicionados ao enum, expandir TIPOS_CORRIDA.
 */
@Service
public class ActivityTypeCompatibilityMatrixImpl implements ActivityTypeCompatibilityMatrix {

    // MVP: todos os 10 TipoTreino (REGENERATIVO, INTERVALADO, etc) são corrida
    // Quando natação/ciclismo/etc forem adicionados ao enum, adicionar novo Set/método
    private static final EnumSet<TipoTreino> TIPOS_CORRIDA = EnumSet.allOf(TipoTreino.class);

    // Instance constructor for Spring DI and interface implementation
    public ActivityTypeCompatibilityMatrixImpl() {
    }

    // Static constructor for utility pattern (backward compatibility)
    private ActivityTypeCompatibilityMatrixImpl(boolean utility) {
        // Utility class - prevent direct instantiation
    }

    /**
     * Verifica se tipo de atividade é compatível com tipo de treino planejado.
     * Compatibilidade baseada em modalidade (esporte): ambos devem pertencer à mesma modalidade.
     *
     * @param activityType tipo da atividade realizada (pode ser null)
     * @param plannedType tipo do treino planejado (pode ser null)
     * @return true se compatível (mesmo esporte), false se incompatível
     */
    @Override
    public boolean isCompatible(TipoTreino activityType, TipoTreino plannedType) {
        return isCompatibleStatic(activityType, plannedType);
    }

    /**
     * Static variant for backward compatibility.
     * @deprecated Use the instance method instead via Spring DI.
     */
    @Deprecated(since = "2.0", forRemoval = false)
    public static boolean isCompatibleStatic(TipoTreino activityType, TipoTreino plannedType) {
        // Se algum for null, compatível por padrão (sem restrição de tipo)
        if (activityType == null || plannedType == null) {
            return true;
        }

        // Compatível se ambos pertencem ao mesmo esporte (modalidade)
        return getEsporte(activityType).equals(getEsporte(plannedType));
    }

    /**
     * Retorna a modalidade (esporte) associada a um TipoTreino.
     * MVP: todos os TipoTreino são CORRIDA.
     * Futuro: adicionar NATACAO, CICLISMO, etc quando enum for expandido.
     *
     * Nota: Retorna esporte explícito (não apenas boolean de pertença a set).
     * Isso previne o bug: false==false (dois tipos não-corrida sendo compatíveis).
     */
    private static String getEsporte(TipoTreino tipo) {
        if (tipo != null && TIPOS_CORRIDA.contains(tipo)) {
            return "CORRIDA";
        }
        // Tipo desconhecido ou fora de todos os conjuntos conhecidos
        // Retorna esporte único para cada tipo desconhecido (previne compatibilidade falsa)
        return tipo != null ? tipo.name() + "_DESCONHECIDO" : "NULL";
    }
}
