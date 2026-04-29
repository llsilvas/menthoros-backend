package com.menthoros.exception;

public class StravaRateLimitException extends RuntimeException {
    public StravaRateLimitException(String message) {
        super(message);
    }

    public StravaRateLimitException(String message, Throwable cause) {
        super(message, cause);
    }
}
