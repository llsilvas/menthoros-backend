package br.com.menthoros.backend.enums;

/**
 * De onde veio o volume de partida da temporada ({@code PlanoTreino.initialVolumeSource}).
 * <ul>
 *   <li>{@code MEDIAN} — mediana dos km das 8 semanas completas antes da geração.</li>
 *   <li>{@code ONBOARDING} — sem treino na janela: o volume semanal informado no onboarding.</li>
 *   <li>{@code COACH} — informado pelo treinador num recálculo.</li>
 * </ul>
 */
public enum StartVolumeSource {
    MEDIAN,
    ONBOARDING,
    COACH
}
