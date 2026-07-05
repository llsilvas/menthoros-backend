package br.com.menthoros.backend.exception;

/**
 * Lançada quando um arquivo .fit está corrompido, não é um FIT válido, ou não contém uma
 * mensagem Session (dado mínimo necessário para importar um treino).
 */
public class FitParseException extends RuntimeException {
    public FitParseException(String message) {
        super(message);
    }

    public FitParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
