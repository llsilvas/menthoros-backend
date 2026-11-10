package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.events.FitWorkoutImportedEvent;
import br.com.menthoros.backend.services.helper.FitWorkoutReconciler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.UnexpectedRollbackException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FitWorkoutReconciliationListenerTest {

    @Mock private FitWorkoutReconciler reconciler;
    @InjectMocks private FitWorkoutReconciliationListener listener;

    private final UUID treinoId = UUID.randomUUID();
    private final UUID tenantId = UUID.randomUUID();

    @Nested
    @DisplayName("onFitWorkoutImported")
    class OnFitWorkoutImported {

        @Test
        @DisplayName("delega a conciliação com o id e o tenant do evento")
        void delegaConciliacao() {
            listener.onFitWorkoutImported(new FitWorkoutImportedEvent(treinoId, tenantId));

            verify(reconciler).reconcile(treinoId, tenantId);
        }

        @Test
        @DisplayName("falha da conciliação não propaga (CA5)")
        void falhaNaoPropaga() {
            doThrow(new IllegalStateException("boom")).when(reconciler).reconcile(treinoId, tenantId);

            assertThatCode(() -> listener.onFitWorkoutImported(new FitWorkoutImportedEvent(treinoId, tenantId)))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("falha no commit da transação da conciliação também não propaga (CA5)")
        void falhaNoCommitNaoPropaga() {
            doThrow(new UnexpectedRollbackException("rollback-only")).when(reconciler).reconcile(treinoId, tenantId);

            assertThatCode(() -> listener.onFitWorkoutImported(new FitWorkoutImportedEvent(treinoId, tenantId)))
                    .doesNotThrowAnyException();
        }
    }
}
