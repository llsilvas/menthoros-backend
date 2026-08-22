package br.com.menthoros.backend.domain.workout;

/**
 * Ritmo alvo para uma etapa de treino.
 * Se o ritmo é único, startSecsPerKm == endSecsPerKm.
 */
public record PaceTarget(Integer startSecsPerKm, Integer endSecsPerKm) implements IntensityTarget {}
