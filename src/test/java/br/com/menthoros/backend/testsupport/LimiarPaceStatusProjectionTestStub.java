package br.com.menthoros.backend.testsupport;

import br.com.menthoros.backend.repository.projection.LimiarPaceStatusProjection;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Stub mínimo de {@link LimiarPaceStatusProjection} para testes de {@code TsbServiceImpl}/
 * {@code TsbDiaPersister} que não exercitam o fluxo de resolução de fonte de pace
 * (refactor-threshold-call-outside-transaction) — {@link #naoDesatualizado} devolve um status
 * "recém-testado" (`dataUltimoTestePace = hoje`), fazendo {@code isPaceLimiarDesatualizado} curto-
 * circuitar em {@code false} sem precisar estubar treinos/provas.
 */
public final class LimiarPaceStatusProjectionTestStub {

    private LimiarPaceStatusProjectionTestStub() {
    }

    public static LimiarPaceStatusProjection naoDesatualizado(UUID assessoriaId, LocalDate hoje) {
        return projection(assessoriaId, new BigDecimal("4.5000"), hoje);
    }

    public static LimiarPaceStatusProjection projection(UUID assessoriaId, BigDecimal paceLimiar,
                                                          LocalDate dataUltimoTestePace) {
        return new LimiarPaceStatusProjection() {
            @Override
            public UUID getAssessoriaId() {
                return assessoriaId;
            }

            @Override
            public BigDecimal getPaceLimiar() {
                return paceLimiar;
            }

            @Override
            public LocalDate getDataUltimoTestePace() {
                return dataUltimoTestePace;
            }
        };
    }
}
