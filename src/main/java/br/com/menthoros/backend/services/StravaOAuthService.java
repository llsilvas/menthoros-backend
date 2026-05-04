package br.com.menthoros.backend.services;

import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.IntegracaoExterna;

import java.util.Map;
import java.util.UUID;

/**
 * OAuth2 integration with Strava platform.
 * Handles: token exchange, refresh, validation, authorization URL generation, connection status.
 */
public interface StravaOAuthService {
    String getAuthorizationUrl(UUID atletaId);
    IntegracaoExterna exchangeCodeForToken(String code, Atleta atleta);
    String refreshAccessToken(IntegracaoExterna integracao);
    String getValidToken(UUID atletaId);
    boolean isConnected(UUID atletaId);
    Map<String, Object> getStatus(UUID atletaId);
    void disconnect(UUID atletaId);
    Atleta findAtletaForCallback(UUID atletaId);
}
