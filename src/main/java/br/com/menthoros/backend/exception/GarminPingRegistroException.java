package br.com.menthoros.backend.exception;

/**
 * Falha ao gravar um ping {@code activityFiles} do Garmin (garmin-activity-ingestion, D2).
 *
 * <p>Deliberadamente <b>sem causa</b>: a exceção original do banco pode trazer a linha inteira
 * ("Failing row contains ..."), com o {@code callback_url} que carrega o token de download. Cai no
 * handler genérico (500), e o Garmin reenvia o lote; o dedup por {@code summary_id} absorve o que
 * já tinha sido gravado.
 */
public class GarminPingRegistroException extends RuntimeException {
    public GarminPingRegistroException(String message) {
        super(message);
    }
}
