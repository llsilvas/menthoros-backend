package br.com.menthoros.backend.exception;

import org.springframework.http.HttpStatusCode;
import org.jspecify.annotations.Nullable;

/**
 * Erro da API do intervals.icu. {@code status} nulo significa falha de transporte
 * (timeout/IO) antes de qualquer resposta HTTP. A mensagem NUNCA contém a API key.
 */
public class IntervalsIcuApiException extends RuntimeException {

    private final transient @Nullable HttpStatusCode status;

    public IntervalsIcuApiException(@Nullable HttpStatusCode status, String message) {
        super(message);
        this.status = status;
    }

    public IntervalsIcuApiException(String message, Throwable cause) {
        super(message, cause);
        this.status = null;
    }

    public @Nullable HttpStatusCode getStatus() {
        return status;
    }
}
