package br.com.menthoros.backend.enums;

/**
 * Marca de relógio predominante entre os atletas do inscrito na waitlist. Hoje o Menthoros só lê
 * dados do {@link #GARMIN} — as demais existem para medir demanda, não porque a integração exista.
 */
public enum WatchBrand {
    GARMIN,
    COROS,
    POLAR,
    APPLE,
    OTHER,
    UNKNOWN
}
