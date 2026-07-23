package br.com.menthoros.backend.config.asaas;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Propriedades da integração com o Asaas (assessoria-billing-asaas).
 *
 * <p>Vinculadas ao prefixo {@code asaas} no {@code application.yml}. Segredos
 * ({@code apiKey}, {@code webhook.accessToken}) vêm de variável de ambiente por
 * profile (sandbox/produção) — nunca hardcoded. Registrada como {@code @Component}
 * para garantir o bean independentemente de {@code @ConfigurationPropertiesScan}.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "asaas")
public class AsaasProperties {

    /** Base URL da API (sandbox: https://api-sandbox.asaas.com/v3, prod: https://api.asaas.com/v3). */
    private String baseUrl;

    /** API key do Asaas ({@code $aact_hmlg_...} em sandbox, {@code $aact_prod_...} em produção). */
    private String apiKey;

    /** User-Agent obrigatório em toda requisição (exigido pelo Asaas para contas novas). */
    private String userAgent = "menthoros-backend";

    private final Webhook webhook = new Webhook();

    @Getter
    @Setter
    public static class Webhook {
        /** Token estático que o Asaas envia no header {@code asaas-access-token} (CA11). */
        private String accessToken;
    }
}
