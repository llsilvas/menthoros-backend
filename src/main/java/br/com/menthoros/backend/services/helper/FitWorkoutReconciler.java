package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.entity.TreinoPlanejado;
import br.com.menthoros.backend.entity.TreinoRealizado;
import br.com.menthoros.backend.repository.TreinoRealizadoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Concilia com o planejado um realizado recém-importado de .fit (fix-garmin-realizado-pos-ingestao),
 * numa transação própria, depois do commit da ingestão.
 *
 * <p>Separado do listener de propósito: o {@code try/catch} do listener fica fora desta fronteira
 * transacional e pega também a falha do commit ({@code UnexpectedRollbackException}). Uma exceção no
 * meio faz rollback inteiro da conciliação — sem vínculo parcial nem auditoria órfã.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FitWorkoutReconciler {

    private final TreinoRealizadoRepository treinoRealizadoRepository;
    private final CandidateSelector candidateSelector;
    private final ReconciliationDecisionExecutor reconciliationDecisionExecutor;

    /**
     * Busca candidatos na janela ±1 dia e decide/persiste o vínculo.
     *
     * <p><b>Idempotent:</b> YES — realizado já conciliado (status não nulo) é ignorado.
     * <p><b>Side Effects:</b> Database update do realizado e do planejado vinculado; insert em
     * {@code tb_treino_reconciliacao}.
     * <p><b>Tenant-aware:</b> YES — carrega o realizado por (id, tenant) explícitos, sem depender do
     * {@code TenantContext}.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void reconcile(UUID treinoRealizadoId, UUID tenantId) {
        TreinoRealizado realizado = treinoRealizadoRepository.findByIdAndTenantId(treinoRealizadoId, tenantId)
                .orElse(null);
        if (realizado == null) {
            log.warn("Conciliação do FIT ignorada: realizado {} não encontrado no tenant {}", treinoRealizadoId, tenantId);
            return;
        }
        if (realizado.getReconciliationStatus() != null) {
            log.debug("Conciliação do FIT ignorada: realizado {} já conciliado ({})",
                    treinoRealizadoId, realizado.getReconciliationStatus());
            return;
        }

        List<TreinoPlanejado> candidatos = candidateSelector.buscarCandidatos(realizado, tenantId);
        var decisao = reconciliationDecisionExecutor.executar(realizado, candidatos, realizado.getAtleta());
        log.info("Realizado do FIT conciliado: treinoId={}, candidatos={}, status={}",
                treinoRealizadoId, candidatos.size(), decisao.getStatus());
    }
}
