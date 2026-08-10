package br.com.menthoros.backend.config.signup;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * Propriedades do auto-cadastro público de assessoria.
 *
 * <p>Segue o padrão de {@code LgpdProperties}: {@code @Component} para garantir o bean
 * independentemente de {@code @ConfigurationPropertiesScan}.
 *
 * <p><b>{@code @Validated} não é enfeite</b> — e aqui a direção da falha é a que importa. No LGPD, um
 * typo derrubaria o enforcement em silêncio. Aqui, um valor inválido que caísse no default
 * <em>ligaria</em> um endpoint público de provisionamento sem ninguém decidir isso. Melhor derrubar
 * o boot do que subir com a porta aberta.
 */
@Getter
@Setter
@Component
@Validated
@ConfigurationProperties(prefix = "app.coach-signup")
public class CoachSignupProperties {

    /**
     * Liga o endpoint de auto-cadastro. Default {@code false}: <b>o deploy nunca liga sozinho</b>.
     *
     * <p>É também o kill switch. Um endpoint anônimo que provisiona no Keycloak e cria tenant
     * precisa poder ser desligado por variável de ambiente, em minutos — sem reverter código, que é
     * o caminho lento justamente quando há pressa.</p>
     */
    private boolean enabled = false;

    /**
     * Tamanho máximo do corpo aceito, em bytes.
     *
     * <p>A validação do DTO só roda <em>depois</em> de o corpo ser lido e desserializado. Sem um
     * teto, um POST de vários megabytes é trabalho de parsing que o servidor faz antes de ter a
     * chance de recusar — e numa rota anônima isso é gratuito para quem ataca.</p>
     */
    @NotNull
    @Min(512)
    private Integer maxRequestBytes = 8_192;
}
