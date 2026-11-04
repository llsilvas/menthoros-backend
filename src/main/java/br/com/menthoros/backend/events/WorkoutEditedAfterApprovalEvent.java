package br.com.menthoros.backend.events;

import java.util.UUID;

/**
 * Evento publicado quando o treinador edita um treino de plano já aprovado e o atleta tem canal
 * de treinos conectado (coach-editar-treino-aprovado, D2).
 *
 * <p>Consumidores DEVEM usar {@code @TransactionalEventListener(phase = AFTER_COMMIT)}: o evento é
 * publicado dentro da transação do PATCH. O canal não viaja no evento — o consumidor o resolve de
 * novo, porque a conexão pode cair entre o commit e o processamento.
 *
 * @param treinoId treino editado
 * @param planoId  plano aprovado do treino
 * @param atletaId atleta dono do plano
 * @param tenantId assessoria (tenant) do plano
 */
public record WorkoutEditedAfterApprovalEvent(UUID treinoId, UUID planoId, UUID atletaId, UUID tenantId) {
}
