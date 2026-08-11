package br.com.menthoros.backend.exception;

/**
 * Limite de auto-cadastro atingido.
 *
 * <p>Distinta de {@code StravaRateLimitException} e {@code IntervalsIcuRateLimitException}: aquelas
 * são limites que <em>nós</em> batemos em terceiros; esta é limite que impomos a quem nos chama.</p>
 */
public class SignupRateLimitException extends RuntimeException {

    public SignupRateLimitException(String message) {
        super(message);
    }
}
