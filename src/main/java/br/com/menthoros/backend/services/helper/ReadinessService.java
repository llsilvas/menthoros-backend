package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.config.core.ReadinessProperties;
import br.com.menthoros.backend.entity.CheckinProntidao;
import br.com.menthoros.backend.enums.NivelProntidao;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Calculadora determinística de prontidão subjetiva diária.
 *
 * <p>Converte os 5 sinais subjetivos do {@link CheckinProntidao} (sono, humor, dores,
 * energia, estresse — cada um em sua escala 0–10 ou 1–10) num {@code readinessScore}
 * normalizado (0–1) via média ponderada, e classifica o resultado em {@link NivelProntidao}.</p>
 */
@Component
public class ReadinessService {

    private static final BigDecimal LIMIAR_PRONTO = new BigDecimal("0.75");
    private static final BigDecimal LIMIAR_CAUTELOSO = new BigDecimal("0.50");

    private final ReadinessProperties properties;

    public ReadinessService(ReadinessProperties properties) {
        this.properties = properties;
    }

    /**
     * Calcula o score de prontidão (0–1) a partir dos sinais subjetivos do checkin.
     *
     * Idempotent: YES — função pura, mesmo input sempre produz o mesmo output.
     * Side Effects: NONE
     * Tenant-aware: NO — não depende de contexto de requisição.
     *
     * @param checkin checkin com os 5 sinais subjetivos preenchidos
     * @return score normalizado entre 0 e 1
     * @throws IllegalArgumentException se checkin for null
     */
    public BigDecimal calcularScore(CheckinProntidao checkin) {
        if (checkin == null) {
            throw new IllegalArgumentException("checkin não pode ser null");
        }

        double sonoNorm = normalizarPositivo(checkin.getQualidadeSono(), 1, 10);
        double humorNorm = normalizarPositivo(checkin.getHumor(), 1, 10);
        double energiaNorm = normalizarPositivo(checkin.getNivelEnergia(), 1, 10);
        double doresNorm = normalizarInvertido(checkin.getDoresMusculares(), 0, 10);
        double estresseNorm = normalizarInvertido(checkin.getEstresse(), 0, 10);

        double score = properties.getPesoSono() * sonoNorm
                + properties.getPesoEnergia() * energiaNorm
                + properties.getPesoHumor() * humorNorm
                + properties.getPesoDores() * doresNorm
                + properties.getPesoEstresse() * estresseNorm;

        return BigDecimal.valueOf(score).setScale(3, RoundingMode.HALF_UP);
    }

    /**
     * Classifica o score de prontidão em {@link NivelProntidao}.
     * {@code >= 0.75} PRONTO, {@code 0.50–0.74} CAUTELOSO, {@code < 0.50} DESCANSAR.
     *
     * Idempotent: YES
     * Side Effects: NONE
     * Tenant-aware: NO
     *
     * @param score score de prontidão (0–1)
     * @return nível de prontidão classificado
     * @throws IllegalArgumentException se score for null
     */
    public NivelProntidao classificarNivel(BigDecimal score) {
        if (score == null) {
            throw new IllegalArgumentException("score não pode ser null");
        }
        if (score.compareTo(LIMIAR_PRONTO) >= 0) {
            return NivelProntidao.PRONTO;
        }
        if (score.compareTo(LIMIAR_CAUTELOSO) >= 0) {
            return NivelProntidao.CAUTELOSO;
        }
        return NivelProntidao.DESCANSAR;
    }

    /** Normaliza um sinal onde valor MAIOR = melhor (sono, humor, energia). */
    private double normalizarPositivo(Integer valor, int min, int max) {
        return (valor - min) / (double) (max - min);
    }

    /** Normaliza um sinal onde valor MAIOR = pior (dores, estresse) — inverte a escala. */
    private double normalizarInvertido(Integer valor, int min, int max) {
        return (max - valor) / (double) (max - min);
    }
}
