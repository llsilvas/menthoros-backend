package br.com.menthoros.backend.exception;

/**
 * Rate limit ou falha transitória (5xx, timeout) do intervals.icu — mapeado para HTTP 429,
 * consistente com {@link StravaRateLimitException}. Reservado exclusivamente para instabilidade
 * do provedor; nunca reaproveitado para conflitos de estado do domínio (ver
 * {@link DomainConflictException}).
 */
public class IntervalsIcuRateLimitException extends RuntimeException {
    public IntervalsIcuRateLimitException(String message) {
        super(message);
    }
}
