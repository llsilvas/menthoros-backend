package br.com.menthoros.backend.enums;

/**
 * Quem definiu os alvos de uma semana da temporada (glossário: Origem do valor).
 * <ul>
 *   <li>{@code ENGINE} — calculado pelo motor.</li>
 *   <li>{@code COACH} — ajustado pelo treinador.</li>
 *   <li>{@code AI_ACCEPTED} — sugestão da IA aceita pelo treinador.</li>
 * </ul>
 */
public enum WeekValueSource {
    ENGINE,
    COACH,
    AI_ACCEPTED
}
