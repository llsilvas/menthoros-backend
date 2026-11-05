package br.com.menthoros.backend.entity;

import java.util.List;

/**
 * Tempo do treino em cada zona de FC, com os limites vigentes no import (coach-ver-treino-realizado,
 * D6). Guardar os limites junto é o que mantém o histórico estável quando as zonas do atleta mudam.
 * {@code bpmMax} nulo na zona mais alta: ela não tem teto.
 */
public record TempoEmZonas(String fonte, List<Zona> zonas) {

    public static final String FONTE_FIT_RECORDS = "FIT_RECORDS";

    public record Zona(String zona, Integer bpmMin, Integer bpmMax, int segundos) {
    }
}
