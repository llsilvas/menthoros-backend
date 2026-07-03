package br.com.menthoros.backend.config.core;

import jakarta.validation.constraints.AssertTrue;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

/**
 * Pesos de ponderação do {@code readinessScore} e flag de rollback do motor de readiness.
 *
 * <p>{@code enabled=false} desabilita a leitura de readiness no portão de elegibilidade de
 * intervalado sem remover dados — suporte a rollback documentado na V46/V47 (ver
 * {@code add-daily-readiness-checkin}).</p>
 */
@Getter
@Setter
@Validated
@Configuration
@ConfigurationProperties(prefix = "app.readiness")
public class ReadinessProperties {

    private static final double EPSILON = 1e-6;

    private boolean enabled = true;

    private double pesoSono = 0.35;
    private double pesoEnergia = 0.25;
    private double pesoHumor = 0.20;
    private double pesoDores = 0.15;
    private double pesoEstresse = 0.05;

    /**
     * Falha rápido no boot se os pesos configurados não somarem 1.0 — evita que
     * {@code readinessScore} saia do intervalo [0,1] documentado (e viole o CHECK constraint
     * de {@code tb_checkin_prontidao}) por um erro de configuração silencioso.
     */
    @AssertTrue(message = "A soma dos pesos de readiness (pesoSono+pesoEnergia+pesoHumor+pesoDores+pesoEstresse) deve ser 1.0")
    public boolean isPesosValidos() {
        double soma = pesoSono + pesoEnergia + pesoHumor + pesoDores + pesoEstresse;
        return Math.abs(soma - 1.0) < EPSILON;
    }
}
