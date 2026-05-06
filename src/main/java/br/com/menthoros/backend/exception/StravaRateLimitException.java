package br.com.menthoros.backend.exception;

public class StravaRateLimitException extends RuntimeException {
    public StravaRateLimitException(String message) {
        super(message);
    }

    public StravaRateLimitException(String message, Throwable cause) {
        super(message, cause);
    }
}
