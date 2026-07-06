package br.com.menthoros.backend.services;

import java.util.UUID;

public interface EncerramentoSemanaService {

    /**
     * Encerra a semana de um plano: marca como PERDIDO os treinos PENDENTE elegíveis e
     * recalcula o status do plano. Elegível é o treino PENDENTE com data no passado; o
     * treino do dia corrente só é finalizado no fim da semana (hoje == semanaFim).
     * Ação explícita do treinador — não aplica carência.
     *
     * <p><b>Idempotent:</b> YES — reexecutar sobre um plano sem treino PENDENTE elegível
     * não altera nada e retorna {@code treinosFinalizados = 0}.
     *
     * <p><b>Side Effects:</b> Database update — treinos elegíveis passam a PERDIDO e o
     * status do plano é recalculado.
     *
     * <p><b>Tenant-aware:</b> YES — resolve e valida o tenant via TenantContext; o plano
     * precisa pertencer ao tenant corrente.
     *
     * @param planoId identificador do plano semanal
     * @return o resumo do encerramento
     * @throws br.com.menthoros.backend.exception.DomainNotFoundException se o plano não
     *                                                                    existir no tenant corrente
     */
    EncerramentoSemanaResultado encerrarSemana(UUID planoId);
}
