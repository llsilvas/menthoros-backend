package br.com.menthoros.backend.exception;

/**
 * Canal Garmin desligado ({@code app.garmin.habilitado=false}) ou sem client ID/secret.
 *
 * <p>Mapeada para {@code 503}, não {@code 409}: não é conflito de estado do atleta, é o canal que
 * não está disponível neste ambiente. O callback (redirect de navegador) não devolve 503 — captura
 * esta exceção e redireciona com {@code garmin=error} (D11).
 */
public class GarminCanalIndisponivelException extends RuntimeException {
    public GarminCanalIndisponivelException(String message) {
        super(message);
    }
}
