package br.com.menthoros.backend.events;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Evento publicado quando um plano semanal é deletado.
 *
 * <p>Consumidores DEVEM usar {@code @TransactionalEventListener(phase = AFTER_COMMIT)}: o evento
 * é publicado dentro da transação de deleção e não deve ser processado em rollback.
 * Consumidor nesta change: {@code IntervalsIcuPlanoDeletadoListener} (remoção dos eventos
 * {@code menthoros-*} órfãos no intervals.icu).
 *
 * <p>A janela ({@code semanaInicio}/{@code semanaFim}) é capturada ANTES do delete — o cascade da
 * exclusão do {@code PlanoSemanal} apaga os {@code TreinoPlanejado} vinculados, então esses dados
 * não estariam mais disponíveis depois.
 *
 * @param planoId      plano deletado
 * @param atletaId     atleta dono do plano
 * @param tenantId     assessoria (tenant) do plano
 * @param semanaInicio início da janela do plano deletado (inclusive)
 * @param semanaFim    fim da janela do plano deletado (inclusive)
 */
public record PlanoDeletadoEvent(UUID planoId, UUID atletaId, UUID tenantId, LocalDate semanaInicio,
                                  LocalDate semanaFim) {
}
