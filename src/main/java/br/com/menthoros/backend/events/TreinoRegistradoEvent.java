package br.com.menthoros.backend.events;

import java.util.UUID;

/**
 * Evento publicado após persistência bem-sucedida de um TreinoRealizado.
 * Consumido assincronamente pelo WorkoutAnalysisListener.
 */
public record TreinoRegistradoEvent(UUID treinoRealizadoId, UUID tenantId) {}
