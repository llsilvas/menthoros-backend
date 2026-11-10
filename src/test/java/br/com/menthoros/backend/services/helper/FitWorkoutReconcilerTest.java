package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.dto.MatchingDecision;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.TreinoPlanejado;
import br.com.menthoros.backend.entity.TreinoRealizado;
import br.com.menthoros.backend.enums.ReconciliationStatus;
import br.com.menthoros.backend.repository.TreinoRealizadoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FitWorkoutReconcilerTest {

    @Mock private TreinoRealizadoRepository treinoRealizadoRepository;
    @Mock private CandidateSelector candidateSelector;
    @Mock private ReconciliationDecisionExecutor reconciliationDecisionExecutor;
    @InjectMocks private FitWorkoutReconciler reconciler;

    private UUID treinoId;
    private UUID tenantId;
    private Atleta atleta;

    @BeforeEach
    void setUp() {
        treinoId = UUID.randomUUID();
        tenantId = UUID.randomUUID();
        atleta = new Atleta();
    }

    @Nested
    @DisplayName("reconcile")
    class Reconcile {

        @Test
        @DisplayName("busca candidatos e decide com o realizado recarregado do tenant")
        void conciliaRealizadoRecarregado() {
            TreinoRealizado realizado = realizado(null);
            List<TreinoPlanejado> candidatos = List.of(new TreinoPlanejado());
            when(treinoRealizadoRepository.findByIdAndTenantId(treinoId, tenantId)).thenReturn(Optional.of(realizado));
            when(candidateSelector.buscarCandidatos(realizado, tenantId)).thenReturn(candidatos);
            when(reconciliationDecisionExecutor.executar(realizado, candidatos, atleta)).thenReturn(
                    new MatchingDecision(ReconciliationStatus.NAO_PLANEJADO, null, List.of(), "SEM_CANDIDATO", "teste"));

            reconciler.reconcile(treinoId, tenantId);

            verify(reconciliationDecisionExecutor).executar(realizado, candidatos, atleta);
        }

        @Test
        @DisplayName("realizado já conciliado não é conciliado de novo")
        void jaConciliadoNaoReprocessa() {
            when(treinoRealizadoRepository.findByIdAndTenantId(treinoId, tenantId))
                    .thenReturn(Optional.of(realizado(ReconciliationStatus.VINCULADO_MANUAL)));

            reconciler.reconcile(treinoId, tenantId);

            verifyNoInteractions(candidateSelector, reconciliationDecisionExecutor);
        }

        @Test
        @DisplayName("realizado ausente no tenant não concilia")
        void ausenteNaoConcilia() {
            when(treinoRealizadoRepository.findByIdAndTenantId(treinoId, tenantId)).thenReturn(Optional.empty());

            reconciler.reconcile(treinoId, tenantId);

            verifyNoInteractions(candidateSelector, reconciliationDecisionExecutor);
        }
    }

    private TreinoRealizado realizado(ReconciliationStatus status) {
        TreinoRealizado r = new TreinoRealizado();
        r.setId(treinoId);
        r.setAtleta(atleta);
        r.setReconciliationStatus(status);
        return r;
    }
}
