package br.com.menthoros.backend.services;

import br.com.menthoros.backend.dto.input.AthleteZoneAnchorsInputDto;
import br.com.menthoros.backend.dto.output.AthleteTrainingZonesOutputDto;

import java.util.UUID;

/**
 * Zonas de pace e FC de um atleta para o treinador, resolvidas pelo mesmo cálculo do prompt da IA.
 */
public interface CoachAthleteZonesService {

    /**
     * Devolve as seis zonas do atleta, as âncoras e o ajuste de pace por fadiga de hoje (informativo).
     *
     * <p><b>Idempotent:</b> YES — leitura pura.
     * <b>Side Effects:</b> NONE.
     * <b>Tenant-aware:</b> YES — o atleta é buscado por {@code (id, tenantId)}.
     *
     * @throws br.com.menthoros.backend.exception.DomainNotFoundException se o atleta não existe no tenant
     */
    AthleteTrainingZonesOutputDto buscarZonas(UUID atletaId);

    /**
     * Grava as âncoras (limiares) de FC e/ou pace e devolve as zonas recalculadas. Grupo ausente não
     * altera aquele eixo; {@code fc.maxima} vazia mantém a vigente.
     *
     * <p><b>Idempotent:</b> YES — reenviar o mesmo corpo regrava os mesmos valores.
     * <b>Side Effects:</b> Database update (colunas do grupo presente, uma transação por grupo).
     * <b>Tenant-aware:</b> YES.
     *
     * @throws br.com.menthoros.backend.exception.DomainNotFoundException se o atleta não existe no tenant
     * @throws br.com.menthoros.backend.exception.InvalidZoneAnchorsException se alguma âncora é inválida
     */
    AthleteTrainingZonesOutputDto salvarAncoras(UUID atletaId, AthleteZoneAnchorsInputDto input);

    /**
     * Devolve as zonas que {@code input} geraria, sem gravar — a prévia do diálogo. Mesmo validador e
     * mesma montagem de {@link #buscarZonas} e {@link #salvarAncoras}.
     *
     * <p><b>Idempotent:</b> YES — leitura pura.
     * <b>Side Effects:</b> NONE.
     * <b>Tenant-aware:</b> YES.
     *
     * @throws br.com.menthoros.backend.exception.DomainNotFoundException se o atleta não existe no tenant
     * @throws br.com.menthoros.backend.exception.InvalidZoneAnchorsException se alguma âncora é inválida
     */
    AthleteTrainingZonesOutputDto simular(UUID atletaId, AthleteZoneAnchorsInputDto input);
}
