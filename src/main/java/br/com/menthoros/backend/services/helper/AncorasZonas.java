package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.dto.input.AthleteZoneAnchorsInputDto;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.FaixaZonaManual;
import br.com.menthoros.backend.enums.FonteLimiar;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

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
        Double tsbProntidao,
        List<FaixaZonaManual> fcManual,
        List<FaixaZonaManual> paceManual
) {

    public static AncorasZonas de(Atleta atleta, Double tsbProntidao) {
        if (atleta == null) {
            throw new IllegalArgumentException("Atleta cannot be null");
        }
        return new AncorasZonas(atleta.getPaceLimiar(), atleta.getDataUltimoTestePace(), atleta.getFonteLimiarPace(),
                atleta.getFcLimiar(), atleta.getFcMaxima(), atleta.getFcMaximaCalculada(),
                atleta.getFcMaximaComFallback(), atleta.getDataUltimoTesteFc(), atleta.getFonteLimiarFc(),
                tsbProntidao, AthleteZones.manuais(atleta.getModoZonasFc(), atleta.getZonasManuaisFc()),
                AthleteZones.manuais(atleta.getModoZonasPace(), atleta.getZonasManuaisPace()));
    }

    /** Conversão única do pace de limiar (s/km do corpo) para a coluna {@code numeric(5,2)} em min/km. */
    public static BigDecimal paceEmMinutos(int segundosPorKm) {
        return BigDecimal.valueOf(segundosPorKm).divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
    }

    /**
     * O que {@code Atleta} devolveria depois de gravar {@code input}: grupo ausente mantém o eixo;
     * máxima vazia mantém a gravada e os fallbacks. Não toca em entidade — é a simulação.
     */
    public AncorasZonas com(AthleteZoneAnchorsInputDto input) {
        AncorasZonas r = this;
        if (input.fc() != null) {
            var fc = input.fc();
            boolean novaMaxima = fc.maxima() != null;
            r = new AncorasZonas(r.paceLimiar, r.dataTestePace, r.fontePace,
                    fc.limiar(),
                    novaMaxima ? fc.maxima() : r.fcMaximaGravada,
                    novaMaxima ? fc.maxima() : r.fcMaximaCalculada,
                    novaMaxima ? fc.maxima() : r.fcMaximaComFallback,
                    fc.dataTeste(), fc.origem(), r.tsbProntidao, r.fcManual, r.paceManual);
        }
        if (input.pace() != null) {
            var pace = input.pace();
            r = new AncorasZonas(paceEmMinutos(pace.limiarSegKm()), pace.dataTeste(), pace.origem(),
                    r.fcLimiar, r.fcMaximaGravada, r.fcMaximaCalculada, r.fcMaximaComFallback,
                    r.dataTesteFc, r.fonteFc, r.tsbProntidao, r.fcManual, r.paceManual);
        }
        return r;
    }

    public boolean temPace() {
        return paceLimiar != null && paceLimiar.signum() > 0;
    }

    /** Faixas efetivas: as manuais do eixo em MANUAL, senão o cálculo sobre estas âncoras. */
    public AthleteZones zonas() {
        return new AthleteZones(fcMaximaCalculada, fcLimiarCalculada(), paceLimiar, fcManual, paceManual);
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
