package br.com.menthoros.backend.events;

import br.com.menthoros.backend.enums.OrigemEncerramento;

import java.util.UUID;

/**
 * Evento publicado após o encerramento de uma semana de treino (plano fechado).
 *
 * <p>Consumidores DEVEM usar {@code @TransactionalEventListener(phase = AFTER_COMMIT)}: o evento
 * é publicado dentro da transação do encerramento e não deve ser processado se a transação sofrer
 * rollback. Nesta change não há consumidor — o evento é ponto de extensão (ex.: geração do próximo
 * plano continua disparada pelo treinador, nunca por este evento).
 *
 * @param planoId         plano encerrado
 * @param atletaId        atleta dono do plano
 * @param tenantId        assessoria (tenant) do plano
 * @param treinosPerdidos quantidade de treinos marcados como PERDIDO no encerramento
 * @param origem          se o encerramento foi ON_DEMAND (treinador) ou AUTOMATICO (scheduler)
 */
public record SemanaEncerradaEvent(
        UUID planoId,
        UUID atletaId,
        UUID tenantId,
        int treinosPerdidos,
        OrigemEncerramento origem
) {
}
