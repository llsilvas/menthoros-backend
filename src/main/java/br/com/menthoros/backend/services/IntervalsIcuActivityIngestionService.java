package br.com.menthoros.backend.services;

import br.com.menthoros.backend.dto.output.TreinoRealizadoOutputDto;

import java.util.UUID;

/**
 * Importa uma atividade específica do intervals.icu como {@link
 * br.com.menthoros.backend.entity.TreinoRealizado} — sentido pull da integração (o sentido push,
 * de treinos planejados para o relógio, é a change-mãe {@code intervals-icu-workout-push}).
 */
public interface IntervalsIcuActivityIngestionService {

    /**
     * Importa uma atividade específica do intervals.icu como {@code TreinoRealizado}.
     *
     * <p>Idempotent: YES — re-import da mesma activity retorna o treino existente sem side
     * effects novos.
     * <p>Side Effects: chamada externa (intervals.icu GET), insert em {@code tb_treino_realizado}
     * (quando novo), update de TSB do dia, publicação de {@code TreinoRegistradoEvent}, gravação
     * de decisão de reconciliação.
     * <p>Tenant-aware: YES — conexão resolvida por (atletaId, tenantId); tenant do treino via
     * atleta.
     *
     * @param atletaId   ID do atleta dono da atividade
     * @param activityId ID da activity no intervals.icu (aceita a URL completa colada; o segmento
     *                   final é extraído)
     * @param tenantId   ID do tenant do atleta
     * @return o treino importado (novo ou já existente)
     */
    TreinoRealizadoOutputDto importarAtividade(UUID atletaId, String activityId, UUID tenantId);
}
