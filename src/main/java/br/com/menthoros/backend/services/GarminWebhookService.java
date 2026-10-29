package br.com.menthoros.backend.services;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;

/** Pings do Garmin (D6). Chaves desconhecidas são ignoradas; {@code userId} desconhecido é no-op. */
public interface GarminWebhookService {

    /**
     * Roteia pela chave do corpo: {@code deregistrations} desativa a conexão e zera tokens;
     * {@code userPermissionsChange} regrava {@code scopes}.
     *
     * <p>Idempotent: YES — repetir o mesmo ping converge para o mesmo estado.
     * <p>Side Effects: Database update por {@code userId}. Nunca loga o corpo.
     * <p>Tenant-aware: NO — o ping não tem tenant; a linha é achada pela query global de userId.
     */
    void processar(Map<String, List<JsonNode>> corpo);
}
