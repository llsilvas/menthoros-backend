package br.com.menthoros.backend.events;

import java.util.UUID;

/**
 * Publicado quando um atleta permanece em {@code TrainingPhase.CALIBRATION} alem da semana 4
 * (design.md Decisao 5, athlete-onboarding-baseline) — sinal para o treinador revisar
 * manualmente, ja que a saida automatica esperava ate 4 semanas.
 *
 * <p>Nenhum consumidor nesta change (backend-only); integracao com a fila de atencao do coach
 * fica para change futura, se o founder decidir que e necessario.
 *
 * @param atletaId atleta preso em calibracao
 * @param tenantId assessoria (tenant) do atleta
 * @param semanasEmCalibracao quantas semanas o atleta ja esta em CALIBRATION
 */
public record AtletaPresoEmCalibracaoEvent(UUID atletaId, UUID tenantId, int semanasEmCalibracao) {
}
