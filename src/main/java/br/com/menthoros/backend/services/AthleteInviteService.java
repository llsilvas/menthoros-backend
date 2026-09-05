package br.com.menthoros.backend.services;

import java.util.UUID;

/**
 * Convite de acesso do atleta por token do backend (change add-athlete-invite-token-link).
 * Substitui o invite-user do Keycloak Organizations como canal do convite.
 */
public interface AthleteInviteService {

    /**
     * Gera (ou regenera) o convite do atleta e envia o e-mail pelo carteiro próprio.
     * Reenvio invalida o convite aberto anterior.
     *
     * <p><strong>Idempotent:</strong> NO — cada chamada gera token novo e envia outro e-mail.
     * <p><strong>Side Effects:</strong> Database insert/update + External API (SMTP).
     * <p><strong>Tenant-aware:</strong> YES — resolve o atleta no tenant do contexto.
     */
    void invite(UUID atletaId);
}
