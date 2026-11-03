package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.enums.FonteLimiar;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Tudo que o cálculo das zonas lê do atleta, já com os fallbacks de FC máxima resolvidos pela entidade.
 * Os dois fallbacks divergem de propósito (ver {@code Atleta}): {@code fcMaximaCalculada} alimenta as
 * zonas e a LTHR estimada; {@code fcMaximaComFallback} é o que o card exibe.
 */
public record AncorasZonas(
        BigDecimal paceLimiar,
        LocalDate dataTestePace,
        FonteLimiar fontePace,
        Integer fcLimiar,
        Integer fcMaximaGravada,
        int fcMaximaCalculada,
        int fcMaximaComFallback,
        LocalDate dataTesteFc,
        FonteLimiar fonteFc,
        Double tsbProntidao
) {

    public static AncorasZonas de(Atleta atleta, Double tsbProntidao) {
        if (atleta == null) {
            throw new IllegalArgumentException("Atleta cannot be null");
        }
        return new AncorasZonas(atleta.getPaceLimiar(), atleta.getDataUltimoTestePace(), atleta.getFonteLimiarPace(),
                atleta.getFcLimiar(), atleta.getFcMaxima(), atleta.getFcMaximaCalculada(),
                atleta.getFcMaximaComFallback(), atleta.getDataUltimoTesteFc(), atleta.getFonteLimiarFc(),
                tsbProntidao);
    }

    public boolean temPace() {
        return paceLimiar != null && paceLimiar.signum() > 0;
    }

    public boolean fcLimiarEstimada() {
        return fcLimiar == null;
    }

    public boolean fcMaximaEstimada() {
        return fcMaximaGravada == null || fcMaximaGravada <= 0;
    }

    /** Mesma regra de {@code Atleta.getFcLimiarCalculada()}. */
    public int fcLimiarCalculada() {
        return fcLimiar != null ? fcLimiar : (int) (0.85 * fcMaximaCalculada);
    }
}
