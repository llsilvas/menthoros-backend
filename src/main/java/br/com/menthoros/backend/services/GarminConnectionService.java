package br.com.menthoros.backend.services;

import br.com.menthoros.backend.dto.garmin.GarminTokenResponse;
import br.com.menthoros.backend.dto.output.GarminConnectionStatusDto;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.IntegracaoExterna;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Conexão do atleta com o Garmin direto: ativação com exclusividade de canal (D4), desconexão,
 * status e consulta do canal ativo (D5). O tenant é sempre explícito; o controller o resolve.
 */
public interface GarminConnectionService {

    /**
     * Cria ou reativa a linha GARMIN, desativa a linha INTERVALS_ICU e pausa o Strava (D4).
     *
     * <p>Idempotent: NO — regrava tokens a cada chamada.
     * <p>Side Effects: Database update (GARMIN, INTERVALS_ICU e Strava do atleta).
     * <p>Tenant-aware: YES — tenant por parâmetro.
     */
    void ativar(Atleta atleta, UUID tenantId, GarminTokenResponse token, String userId, List<String> permissoes);

    /**
     * Desconecta: {@code removerRegistro} no Garmin (best-effort), {@code ativo=false}, tokens e
     * expirações nulos. Não toca em {@code TreinoRealizado} (CA8). Remoto antes de local, porque
     * a desconexão local descarta o token.
     *
     * <p>Idempotent: YES — sem conexão ativa é no-op.
     * <p>Side Effects: chamada HTTP externa (best-effort) + Database update.
     * <p>Tenant-aware: YES — tenant por parâmetro.
     */
    void desconectar(UUID atletaId, UUID tenantId);

    /**
     * Idempotent: YES — leitura pura.
     * Side Effects: NONE.
     * Tenant-aware: YES — tenant por parâmetro.
     *
     * @return vazio quando o atleta nunca conectou; presente (ativo ou não) caso contrário
     */
    Optional<GarminConnectionStatusDto> status(UUID atletaId, UUID tenantId);

    /**
     * Conexão de canal ativa (intervals.icu ou Garmin) do atleta (D5).
     *
     * <p>Idempotent: YES — leitura pura.
     * <p>Side Effects: NONE.
     * <p>Tenant-aware: YES — tenant por parâmetro.
     */
    Optional<IntegracaoExterna> conexaoDeCanal(UUID atletaId, UUID tenantId);
}
