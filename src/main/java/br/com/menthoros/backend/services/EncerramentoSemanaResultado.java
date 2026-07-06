package br.com.menthoros.backend.services;

import br.com.menthoros.backend.enums.PlanoStatus;

import java.util.List;
import java.util.UUID;

/**
 * Resultado do encerramento da semana de um plano.
 *
 * @param planoId                identificador do plano encerrado
 * @param novoStatus             status do plano após o encerramento
 * @param treinosFinalizados     quantidade de treinos marcados como PERDIDO
 * @param treinosPerdidos        ids dos treinos marcados como PERDIDO
 * @param prontoParaProximaSemana true quando o plano ficou CONCLUIDO (semana fechada)
 * @param aviso                  mensagem quando a semana ainda não terminou; null caso contrário
 */
public record EncerramentoSemanaResultado(
        UUID planoId,
        PlanoStatus novoStatus,
        int treinosFinalizados,
        List<UUID> treinosPerdidos,
        boolean prontoParaProximaSemana,
        String aviso
) {
}
