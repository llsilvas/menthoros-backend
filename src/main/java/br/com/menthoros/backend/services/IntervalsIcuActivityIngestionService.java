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

    /**
     * Mesmo pipeline de {@link #importarAtividade}, para o pull agendado: sem o limite de
     * retroatividade do import manual. O limite protege a thread do request do recálculo de TSB de
     * uma atividade muito antiga; no scheduler esse custo é assíncrono, e aplicá-lo fazia backlog com
     * mais de 90 dias ser descartado como erro permanente (fix-sync-cursor-data-loss D4).
     *
     * <p>Idempotent: YES. Side Effects: os mesmos de {@link #importarAtividade}. Tenant-aware: YES.
     *
     * @return o treino e se esta chamada o inseriu
     */
    ImportacaoResultado importarAtividadeAgendada(UUID atletaId, String activityId, UUID tenantId);
}
