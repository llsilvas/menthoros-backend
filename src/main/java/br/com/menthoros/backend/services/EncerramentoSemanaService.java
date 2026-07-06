package br.com.menthoros.backend.services;

import java.time.LocalDate;
import java.util.List;
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

    /**
     * Encerra a semana corrente dos atletas do tenant corrente (ação on-demand do treinador, origem
     * ON_DEMAND, sem carência). Cada atleta é encerrado em sua própria transação — a falha de um é
     * registrada em {@code falhas} e não aborta o lote.
     *
     * <p><b>Idempotent:</b> YES. <b>Side Effects:</b> Database update por atleta. <b>Tenant-aware:</b> YES.
     *
     * @param atletaIds filtro opcional — quando não vazio, encerra só esses atletas (interseção com os do
     *                  tenant, para não vazar cross-tenant); vazio/null encerra todos da assessoria
     * @return resumo consolidado (processados, sem-plano, concluídos, perdidos, resultados, falhas)
     */
    EncerramentoLoteResultado encerrarSemanaLoteAssessoria(List<UUID> atletaIds);

    /**
     * Projeta o impacto do encerramento em lote SEM persistir nada (dry-run) — para a tela de
     * confirmação do treinador.
     *
     * <p><b>Idempotent:</b> YES. <b>Side Effects:</b> NONE (read-only). <b>Tenant-aware:</b> YES.
     *
     * @param atletaIds filtro opcional (mesma semântica de {@link #encerrarSemanaLoteAssessoria(List)})
     */
    EncerramentoLoteResultado previewLoteAssessoria(List<UUID> atletaIds);

    /**
     * Fallback automático: encerra (origem AUTOMATICO) os planos do tenant não CONCLUIDO cuja semana
     * terminou há pelo menos {@code carenciaDias} dias. Cada plano é encerrado em sua própria transação.
     *
     * <p><b>Idempotent:</b> YES. <b>Side Effects:</b> Database update por plano. <b>Tenant-aware:</b> YES
     * (o tenant é passado explicitamente; o {@code TenantContext} deve estar populado pelo chamador para a
     * marcação unitária).
     *
     * @param tenantId     assessoria alvo
     * @param hoje         data corrente (fuso resolvido pelo chamador)
     * @param carenciaDias dias de carência após {@code semanaFim}
     * @return quantidade de planos encerrados
     */
    int encerrarPlanosElegiveis(UUID tenantId, LocalDate hoje, int carenciaDias);
}
