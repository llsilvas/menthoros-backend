package br.com.menthoros.backend.entity;

/**
 * Uma faixa de zona informada pelo treinador. FC em bpm; pace em segundos por km, com {@code min} = limite
 * mais rápido e {@code max} = o mais lento (mesma convenção do {@code PaceFaixa} do GET de zonas).
 */
public record FaixaZonaManual(int zona, int min, int max) {
}
