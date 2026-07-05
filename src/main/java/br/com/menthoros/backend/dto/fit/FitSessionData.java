package br.com.menthoros.backend.dto.fit;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

/**
 * Dados de sessão extraídos de um arquivo .fit — POJO interno, não é DTO de API.
 *
 * @param serialNumber   serial do dispositivo (FileIdMesg) — usado para compor o externalId (D0.2)
 * @param corrida        {@code true} quando {@code Session.Sport == RUNNING}; qualquer outro
 *                       esporte usa {@code tipoTreino = CONTINUO} e o nome do esporte é anotado
 *                       em {@code descricao} pelo chamador (D0.6) — este record só carrega o fato
 *                       bruto, a decisão de mapeamento fica no service de persistência.
 */
public record FitSessionData(
        Long serialNumber,
        LocalDate dataTreino,
        long startTimeEpochSeconds,
        Duration duracao,
        Double distanciaKm,
        Integer fcMedia,
        Integer fcMax,
        Integer tssCalculado,
        boolean corrida,
        String esporteDetectado,
        List<FitLapData> laps
) {}
