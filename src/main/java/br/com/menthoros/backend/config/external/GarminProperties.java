package br.com.menthoros.backend.config.external;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuração do canal direto Garmin (OAuth2 PKCE + Training/Activity API).
 *
 * <p>Diferente de {@link IntervalsIcuProperties}, nada aqui é {@code @NotBlank}: o canal nasce
 * atrás da flag {@code habilitado} (D11) e o contexto precisa subir sem nenhuma var {@code GARMIN_*}.
 * O mesmo risco do secret vazio como chave do HMAC do {@code state} existe aqui, e é fechado em
 * {@link #configurado()}: o service recusa gerar URL de autorização enquanto client ID ou secret
 * estiverem vazios, então nenhum state é assinado com chave conhecida.
 */
@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "app.garmin")
public class GarminProperties {

    /** Flag de rollout ({@code GARMIN_HABILITADO}). Removida em {@code garmin-activity-ingestion}. */
    private boolean habilitado = false;

    private String clientId;

    /** Credencial do token endpoint e chave do HMAC do {@code state}. */
    private String clientSecret;

    private String redirectUri;

    private String authorizationUri;

    private String tokenUri;

    private String apiBaseUrl;

    /** Último segmento do path do webhook; vazio significa webhook desligado (404). */
    private String webhookSecret;

    /** Segredo curto cai em força bruta: menos que isso conta como webhook não configurado (404). */
    public static final int WEBHOOK_SECRET_MIN = 32;

    public boolean webhookConfigurado() {
        return webhookSecret != null && webhookSecret.length() >= WEBHOOK_SECRET_MIN;
    }

    public boolean habilitado() {
        return habilitado;
    }

    /** Client ID e secret presentes — condição para assinar state e trocar code. */
    public boolean configurado() {
        return naoVazio(clientId) && naoVazio(clientSecret);
    }

    private static boolean naoVazio(String valor) {
        return valor != null && !valor.isBlank();
    }
}
