package br.com.menthoros.backend.service;

import br.com.menthoros.backend.dto.MatchingDecision;
import br.com.menthoros.backend.entity.TreinoPlanejado;
import br.com.menthoros.backend.entity.TreinoRealizado;

import java.util.List;
import java.util.UUID;

/**
 * Serviço de orquestração de reconciliação de atividades realizadas com treinos planejados.
 *
 * Responsabilidades:
 * - Buscar candidatos de treino planejado para uma atividade realizada
 * - Calcular scores de correspondência
 * - Tomar decisões automáticas ou ambíguas
 * - Persistir estado de reconciliação e eventos de auditoria
 * - Permitir ações manuais (vincular, desmarcar, desfazer)
 */
public interface ReconciliationService {

    /**
     * Processa a reconciliação automática de uma atividade realizada.
     *
     * @param treinoRealizado atividade realizada (do Strava)
     * @param tenantId identificador do tenant
     * @return decisão com status e candidato selecionado (se houver)
     */
    MatchingDecision reconcile(TreinoRealizado treinoRealizado, UUID tenantId);

    /**
     * Busca candidatos de treino planejado para uma atividade realizada.
     * Janela de busca: D-1 a D+1 onde D é a data da atividade.
     *
     * @param treinoRealizado atividade realizada
     * @param tenantId identificador do tenant
     * @return lista de candidatos (pode estar vazia)
     */
    List<TreinoPlanejado> findCandidates(TreinoRealizado treinoRealizado, UUID tenantId);

    /**
     * Vincula manualmente uma atividade realizada a um treino planejado.
     * Registra auditoria com o ator que fez a ação.
     *
     * @param treinoRealizadoId ID da atividade realizada
     * @param treinoPlanejadoId ID do treino planejado
     * @param tenantId identificador do tenant
     * @param actorId ID do usuário que está executando a ação
     * @return atividade realizada atualizada
     * @throws IllegalArgumentException se os IDs não existirem ou forem de tenants diferentes
     */
    TreinoRealizado linkManually(
            Long treinoRealizadoId,
            Long treinoPlanejadoId,
            UUID tenantId,
            String actorId);

    /**
     * Marca uma atividade realizada como não planejada.
     * Registra auditoria com o ator que fez a ação.
     *
     * @param treinoRealizadoId ID da atividade realizada
     * @param tenantId identificador do tenant
     * @param actorId ID do usuário que está executando a ação
     * @return atividade realizada atualizada
     * @throws IllegalArgumentException se o ID não existir
     */
    TreinoRealizado markAsNotPlanned(
            Long treinoRealizadoId,
            UUID tenantId,
            String actorId);

    /**
     * Desfaz o vínculo de uma atividade realizada (remove o vínculo com treino planejado).
     * Registra auditoria com o ator que fez a ação.
     *
     * @param treinoRealizadoId ID da atividade realizada
     * @param tenantId identificador do tenant
     * @param actorId ID do usuário que está executando a ação
     * @return atividade realizada atualizada (com treinoPlanejadoId = null)
     * @throws IllegalArgumentException se o ID não existir
     */
    TreinoRealizado unlinkManually(
            Long treinoRealizadoId,
            UUID tenantId,
            String actorId);

    /**
     * Recupera o estado de reconciliação atual de uma atividade realizada.
     *
     * @param treinoRealizadoId ID da atividade realizada
     * @param tenantId identificador do tenant
     * @return atividade realizada com estado atual
     * @throws IllegalArgumentException se o ID não existir
     */
    TreinoRealizado getReconciliationState(Long treinoRealizadoId, UUID tenantId);
}
