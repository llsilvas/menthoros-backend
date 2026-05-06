package br.com.menthoros.backend.exception;

public class LLMException extends RuntimeException {
    
    public LLMException(String message) {
        super(message);
    }
    
    public LLMException(String message, Throwable cause) {
        super(message, cause);
    }
}