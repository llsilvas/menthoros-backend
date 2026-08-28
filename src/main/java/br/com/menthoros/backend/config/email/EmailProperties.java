package br.com.menthoros.backend.config.email;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * Remetente e outbox do e-mail transacional do backend ({@code app.email.*}).
 *
 * <p>Credenciais SMTP ficam em {@code spring.mail.*}, lidas de {@code SMTP_*} — os mesmos valores
 * das {@code KC_SMTP_*} do Keycloak no Railway.</p>
 */
@Getter
@Setter
@Component
@Validated
@ConfigurationProperties(prefix = "app.email")
public class EmailProperties {

    /** Endereço do remetente. */
    @NotBlank
    private String from = "nao-responda@menthoros.com";

    /** Nome exibido do remetente. */
    @NotBlank
    private String fromName = "Menthoros";

    /** Diretório onde o {@code FileEmailSender} grava os {@code .eml} (só {@code local}/{@code test}). */
    @NotBlank
    private String outboxDir = "target/outbox";
}
