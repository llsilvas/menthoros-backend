package br.com.menthoros.backend.dto.fit;

import org.jspecify.annotations.Nullable;

/** Uma mensagem {@code record} do .fit reduzida ao que o tempo em zonas usa: timestamp (s) e FC, que pode faltar. */
public record FitAmostraFc(long timestampSeg, @Nullable Integer fc) {
}
