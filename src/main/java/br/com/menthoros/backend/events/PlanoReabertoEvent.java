package br.com.menthoros.backend.events;

import br.com.menthoros.backend.enums.MotivoReaberturaRevisao;

import java.util.UUID;

/**
 * Evento publicado quando uma prova reabre a revisão de um plano já aprovado
 * (prova-no-plano-semanal, D4). Fora do escopo desta change consumir para a fila de atenção do
 * coach — o evento existe para não ter que voltar a mexer no ponto de publicação depois.
 *
 * @param planoId  plano reaberto
 * @param atletaId atleta dono do plano
 * @param tenantId assessoria (tenant) do plano
 * @param motivo   por que a revisão reabriu
 */
public record PlanoReabertoEvent(UUID planoId, UUID atletaId, UUID tenantId, MotivoReaberturaRevisao motivo) {
}
