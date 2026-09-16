package br.com.menthoros.backend.services.helper;

import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;

/**
 * Dados fisiológicos do atleta necessários para {@link SessionResolver} resolver zonas em
 * absolutos. {@code fcMaxima}/{@code fcLimiar} devem vir de
 * {@code Atleta.getFcMaximaCalculada()}/{@code getFcLimiarCalculada()} — nunca {@code null}.
 * {@code paceLimiar} é o campo cru de {@code Atleta} — pode ser {@code null} de verdade
 * ({@link ZoneResolver#pace} cai no fallback sintético nesse caso).
 */
public record AthleteZones(Integer fcMaxima, Integer fcLimiar, @Nullable BigDecimal paceLimiar) {
}
