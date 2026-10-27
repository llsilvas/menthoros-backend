package br.com.menthoros.backend.services;

import br.com.menthoros.backend.dto.garmin.GarminTokenResponse;

import java.util.List;

/**
 * Client HTTP do canal direto Garmin (D1). Token OAuth2 Bearer é por atleta e vai por chamada.
 * Erro HTTP vira {@link br.com.menthoros.backend.exception.GarminApiException} com status;
 * falha de transporte vira a mesma exceção com status nulo. Nenhuma mensagem carrega token.
 * Workout, schedule e download de arquivo entram nas changes seguintes, nesta interface.
 */
public interface GarminClient {

    /** POST token endpoint, {@code grant_type=authorization_code} + {@code code_verifier} (PKCE). */
    GarminTokenResponse trocarCode(String code, String codeVerifier);

    /** POST token endpoint, {@code grant_type=refresh_token}. O refresh token devolvido é novo (rotativo). */
    GarminTokenResponse renovar(String refreshToken);

    /** GET /partner-gateway/rest/user/id — identificador estável do usuário Garmin. */
    String userId(String token);

    /** GET /partner-gateway/rest/user/permissions — ex.: {@code ACTIVITY_EXPORT}, {@code WORKOUT_IMPORT}. */
    List<String> permissoes(String token);

    /**
     * DELETE /partner-gateway/rest/user/registration — remove o registro do usuário no app
     * (exigência da licença ao desconectar). Lança em falha; quem desconecta decide o best-effort.
     */
    void removerRegistro(String token);
}
