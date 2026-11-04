package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.FaixaZonaManual;
import br.com.menthoros.backend.enums.ModoZonas;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.util.List;

/**
 * Dados fisiológicos do atleta necessários para {@link SessionResolver} resolver zonas em
 * absolutos. {@code fcMaxima}/{@code fcLimiar} devem vir de
 * {@code Atleta.getFcMaximaCalculada()}/{@code getFcLimiarCalculada()} — nunca {@code null}.
 * {@code paceLimiar} é o campo cru de {@code Atleta} — pode ser {@code null} de verdade
 * ({@link ZoneResolver#pace} cai no fallback sintético nesse caso).
 * {@code fcManual}/{@code paceManual}: as faixas do eixo quando ele está em {@code MANUAL}; {@code null}
 * em {@code LIMIAR} (coach-zonas-manuais, D2).
 */
public record AthleteZones(Integer fcMaxima, Integer fcLimiar, @Nullable BigDecimal paceLimiar,
                           @Nullable List<FaixaZonaManual> fcManual, @Nullable List<FaixaZonaManual> paceManual) {

    /** Atleta sem faixas manuais — eval e testes que só conhecem as âncoras. */
    public AthleteZones(Integer fcMaxima, Integer fcLimiar, @Nullable BigDecimal paceLimiar) {
        this(fcMaxima, fcLimiar, paceLimiar, null, null);
    }

    /** Único ponto que lê o modo por eixo do atleta. */
    public static AthleteZones de(Atleta atleta) {
        return new AthleteZones(atleta.getFcMaximaCalculada(), atleta.getFcLimiarCalculada(), atleta.getPaceLimiar(),
                manuais(atleta.getModoZonasFc(), atleta.getZonasManuaisFc()),
                manuais(atleta.getModoZonasPace(), atleta.getZonasManuaisPace()));
    }

    static @Nullable List<FaixaZonaManual> manuais(ModoZonas modo, @Nullable List<FaixaZonaManual> faixas) {
        return modo == ModoZonas.MANUAL ? faixas : null;
    }
}
