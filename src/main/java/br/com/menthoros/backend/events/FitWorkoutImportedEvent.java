package br.com.menthoros.backend.events;

import java.util.UUID;

/**
 * Treino realizado de corrida recém-inserido a partir de um .fit (ping Garmin ou upload manual).
 * Consumido no AFTER_COMMIT para conciliar com o planejado numa transação própria
 * (fix-garmin-realizado-pos-ingestao): a falha da conciliação nunca reverte a ingestão.
 */
public record FitWorkoutImportedEvent(UUID treinoRealizadoId, UUID tenantId) {}
