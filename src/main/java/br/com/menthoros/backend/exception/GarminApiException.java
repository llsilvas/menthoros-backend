package br.com.menthoros.backend.exception;

import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatusCode;

/**
 * Erro da API do Garmin. {@code status} nulo significa falha de transporte (timeout/IO) antes de
 * qualquer resposta HTTP. A mensagem NUNCA contém token, code ou verifier.
 */
public class GarminApiException extends RuntimeException {

    private final transient @Nullable HttpStatusCode status;

    public GarminApiException(@Nullable HttpStatusCode status, String message) {
        super(message);
        this.status = status;
    }

    public GarminApiException(String message, Throwable cause) {
        super(message, cause);
        this.status = null;
    }

    public @Nullable HttpStatusCode getStatus() {
        return status;
    }
}
