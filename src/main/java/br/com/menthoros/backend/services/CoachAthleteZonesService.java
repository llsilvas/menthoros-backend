package br.com.menthoros.backend.services;

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
}
