package br.com.menthoros.backend.services;

import br.com.menthoros.backend.dto.output.TreinoRealizadoOutputDto;

/**
 * Resultado de um import agendado do intervals.icu. {@code inserida} é {@code true} só quando esta
 * chamada criou o registro e a transação dela commitou — {@code false} para "já importada" e para o
 * vencedor de uma corrida concorrente (fix-sync-cursor-data-loss D4).
 */
public record ImportacaoResultado(TreinoRealizadoOutputDto treino, boolean inserida) {
}
