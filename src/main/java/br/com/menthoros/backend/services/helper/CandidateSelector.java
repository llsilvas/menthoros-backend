package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.entity.TreinoPlanejado;
import br.com.menthoros.backend.entity.TreinoRealizado;
import br.com.menthoros.backend.repository.TreinoPlanejadoRepository;
import br.com.menthoros.backend.services.ActivityTypeCompatibilityMatrix;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Seleciona candidatos {@link TreinoPlanejado} para reconciliação de um {@link TreinoRealizado}
 * — extraído do {@code DailyActivitySyncSchedulerImpl} (design.md D4) para ser reutilizado tanto
 * pelo scheduler batch quanto pelo import inline do intervals.icu, com a MESMA janela e o MESMO
 * pré-filtro (não uma nova regra).
 *
 * <p>Idempotent: YES — leitura pura.
 * <p>Side Effects: NONE.
 * <p>Tenant-aware: YES — tenant recebido explicitamente por parâmetro.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CandidateSelector {

    private final TreinoPlanejadoRepository treinoPlanejadoRepository;
    private final ActivityTypeCompatibilityMatrix activityTypeCompatibilityMatrix;

    /**
     * Busca candidatos na janela [data-1, data+1] da data do {@code realizado}, filtrados por
     * compatibilidade de tipo e por tenant.
     */
    public List<TreinoPlanejado> buscarCandidatos(TreinoRealizado realizado, UUID tenantId) {
        LocalDate data = realizado.getDataTreino();
        LocalDate windowStart = data.minusDays(1);
        LocalDate windowEnd = data.plusDays(1);

        List<TreinoPlanejado> candidatos = treinoPlanejadoRepository
                .findByAtletaIdAndDataBetween(realizado.getAtleta().getId(), windowStart, windowEnd);

        return filterCompatibleCandidatos(realizado, candidatos).stream()
                .filter(c -> {
                    boolean mesmoTenant = c.getAtleta().getAssessoria().getId().equals(tenantId);
                    if (!mesmoTenant) {
                        log.error("SECURITY: Candidate {} belongs to different tenant. Expected: {}, Found: {}",
                                c.getId(), tenantId, c.getAtleta().getAssessoria().getId());
                    }
                    return mesmoTenant;
                })
                .toList();
    }

    /**
     * Filtra candidatos por compatibilidade de tipo de treino (pré-filtro obrigatório) — mesma
     * regra do {@code DailyActivitySyncSchedulerImpl} original.
     */
    private List<TreinoPlanejado> filterCompatibleCandidatos(TreinoRealizado activity, List<TreinoPlanejado> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return candidates;
        }

        return candidates.stream()
                .filter(planned -> activityTypeCompatibilityMatrix.isCompatible(activity.getTipoTreino(), planned.getTipoTreino()))
                .toList();
    }
}
