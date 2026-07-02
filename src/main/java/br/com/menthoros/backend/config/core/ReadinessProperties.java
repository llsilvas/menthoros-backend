package br.com.menthoros.backend.config.core;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Pesos de ponderação do {@code readinessScore} e flag de rollback do motor de readiness.
 *
 * <p>{@code enabled=false} desabilita a leitura de readiness no portão de elegibilidade de
 * intervalado sem remover dados — suporte a rollback documentado na V46/V47 (ver
 * {@code add-daily-readiness-checkin}).</p>
 */
@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "app.readiness")
public class ReadinessProperties {

    private boolean enabled = true;

    private double pesoSono = 0.35;
    private double pesoEnergia = 0.25;
    private double pesoHumor = 0.20;
    private double pesoDores = 0.15;
    private double pesoEstresse = 0.05;
}
