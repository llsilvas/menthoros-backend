package br.com.menthoros.backend.services;

import br.com.menthoros.backend.enums.TipoTreino;

/**
 * Compatibility matrix for activity types.
 * Determines if a realized activity type is compatible with a planned workout type.
 */
public interface ActivityTypeCompatibilityMatrix {
    boolean isCompatible(TipoTreino activityType, TipoTreino plannedType);
}
