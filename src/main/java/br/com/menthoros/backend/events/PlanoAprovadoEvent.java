package br.com.menthoros.backend.events;

import java.util.UUID;

/**
 * Evento publicado quando o coach aprova um plano semanal.
 *
 * <p>Consumidores DEVEM usar {@code @TransactionalEventListener(phase = AFTER_COMMIT)}: o evento
 * é publicado dentro da transação de aprovação e não deve ser processado em rollback.
 * Consumidor nesta change: {@code IntervalsIcuPushListener} (push de treinos ao relógio).
 *
 * @param planoId  plano aprovado
 * @param atletaId atleta dono do plano
 * @param tenantId assessoria (tenant) do plano
 */
public record PlanoAprovadoEvent(UUID planoId, UUID atletaId, UUID tenantId) {
}
