package br.com.menthoros.backend.config.lgpd;

import br.com.menthoros.backend.enums.ConsentEnforcementMode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * Propriedades do consentimento LGPD do coach (add-coach-lgpd-consent).
 *
 * <p>Vinculadas ao prefixo {@code app.lgpd}. Registrada como {@code @Component} para garantir o bean
 * independentemente de {@code @ConfigurationPropertiesScan}, seguindo o padrão de
 * {@code AsaasProperties}.
 *
 * <p><b>{@code @Validated} não é enfeite:</b> um valor inválido precisa derrubar o boot. Se
 * {@code consentEnforcement} caísse silenciosamente em {@code OFF} por causa de um typo no yml ou de
 * uma variável de ambiente errada, o enforcement estaria desligado em produção sem nenhum sinal — o
 * exato modo de falha que a flag existe para evitar.
 */
@Getter
@Setter
@Component
@Validated
@ConfigurationProperties(prefix = "app.lgpd")
public class LgpdProperties {

    /**
     * Estágio de rollout do bloqueio. Default {@code OFF}: o deploy nunca liga o enforcement
     * sozinho — virar para {@code ON} é decisão operacional, com procedimento próprio.
     */
    @NotNull
    private ConsentEnforcementMode consentEnforcement = ConsentEnforcementMode.OFF;

    /**
     * Data de vigência da Política de Privacidade em vigor, formato {@code YYYY-MM-DD}.
     *
     * <p>Precisa bater com a data exibida na página pública da Política. Divergência significa que o
     * coach leu um texto e o sistema registrou outra versão.
     */
    @NotBlank
    private String policyVersion;

    /** Data de vigência dos Termos de Uso em vigor, formato {@code YYYY-MM-DD}. */
    @NotBlank
    private String termsVersion;
}
