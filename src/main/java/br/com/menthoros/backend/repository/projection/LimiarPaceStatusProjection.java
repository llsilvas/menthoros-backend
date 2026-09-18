package br.com.menthoros.backend.repository.projection;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Status de limiar de pace de um atleta, sem carregar o agregado {@code Atleta} inteiro —
 * refactor-threshold-call-outside-transaction, design.md D1.
 */
public interface LimiarPaceStatusProjection {
    UUID getAssessoriaId();
    BigDecimal getPaceLimiar();
    LocalDate getDataUltimoTestePace();
}
