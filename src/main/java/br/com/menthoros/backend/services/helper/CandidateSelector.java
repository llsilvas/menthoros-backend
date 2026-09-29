package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.entity.TreinoPlanejado;
import br.com.menthoros.backend.entity.TreinoRealizado;
import br.com.menthoros.backend.repository.TreinoPlanejadoRepository;
import br.com.menthoros.backend.repository.TreinoRealizadoRepository;
import br.com.menthoros.backend.services.ActivityTypeCompatibilityMatrix;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
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
    private final TreinoRealizadoRepository treinoRealizadoRepository;

    /**
     * Busca candidatos na janela [data-1, data+1] da data do {@code realizado}, filtrados por
     * compatibilidade de tipo, por tenant e por ocupação: planejado já vinculado a OUTRO realizado
     * não concorre — senão o treino da véspera, já feito, empata com o do dia e força
     * {@code TIE_BREAK} (fix-reconciliation-exclude-realized-candidates).
     */
    public List<TreinoPlanejado> buscarCandidatos(TreinoRealizado realizado, UUID tenantId) {
        LocalDate data = realizado.getDataTreino();
        LocalDate windowStart = data.minusDays(1);
        LocalDate windowEnd = data.plusDays(1);

        List<TreinoPlanejado> candidatos = treinoPlanejadoRepository
                .findByAtletaIdAndDataBetween(realizado.getAtleta().getId(), windowStart, windowEnd);

        List<TreinoPlanejado> doTenant = filterCompatibleCandidatos(realizado, candidatos).stream()
                .filter(c -> {
                    boolean mesmoTenant = c.getAtleta().getAssessoria().getId().equals(tenantId);
                    if (!mesmoTenant) {
                        log.error("SECURITY: Candidate {} belongs to different tenant. Expected: {}, Found: {}",
                                c.getId(), tenantId, c.getAtleta().getAssessoria().getId());
                    }
                    return mesmoTenant;
                })
                .toList();

        return descartarOcupados(realizado, doTenant);
    }

    // Critério é o vínculo, não status_treino: o status é efeito colateral do vínculo e pode divergir dele.
    private List<TreinoPlanejado> descartarOcupados(TreinoRealizado realizado, List<TreinoPlanejado> candidatos) {
        if (candidatos.isEmpty()) {
            return candidatos;
        }
        // Com id nulo, "tr.id <> :realizadoId" vira UNKNOWN em SQL e a query diria "nada ocupado" em silêncio.
        if (realizado.getId() == null) {
            throw new IllegalArgumentException("TreinoRealizado precisa estar persistido para buscar candidatos de reconciliação");
        }

        List<UUID> ids = candidatos.stream().map(TreinoPlanejado::getId).toList();
        Set<UUID> ocupados = treinoRealizadoRepository.findPlanejadoIdsVinculadosAOutroRealizado(ids, realizado.getId());
        if (ocupados.isEmpty()) {
            return candidatos;
        }

        log.info("Reconciliação do treino {}: {} candidato(s) descartado(s) por já estarem vinculados: {}",
                realizado.getId(), ocupados.size(), ocupados);
        return candidatos.stream()
                .filter(c -> !ocupados.contains(c.getId()))
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
