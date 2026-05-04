package br.com.menthoros.backend.services;

import br.com.menthoros.backend.dto.output.CandidateMatchDto;
import br.com.menthoros.backend.dto.output.TreinoRealizadoPendenteOutputDto;
import br.com.menthoros.backend.enums.ReconciliationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Query service for pending reconciliation activities.
 * Lists realized activities awaiting manual or auto reconciliation.
 */
public interface ReconciliacaoPendentesService {
    Page<TreinoRealizadoPendenteOutputDto> listarPendentes(
            UUID atletaId, UUID tenantId, List<ReconciliationStatus> statuses,
            LocalDate dataInicio, LocalDate dataFim, Pageable pageable);
    List<CandidateMatchDto> listarCandidatos(UUID treinoRealizadoId, UUID tenantId);
}
