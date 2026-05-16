package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.dto.output.AnaliseWorkoutOutputDto;
import br.com.menthoros.backend.entity.AnaliseWorkout;
import br.com.menthoros.backend.enums.AnaliseStatus;
import br.com.menthoros.backend.exception.DomainNotFoundException;
import br.com.menthoros.backend.repository.AiWorkoutAnalysisRepository;
import br.com.menthoros.backend.repository.TreinoRealizadoRepository;
import br.com.menthoros.backend.services.AnaliseWorkoutService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnaliseWorkoutServiceImpl implements AnaliseWorkoutService {

    private final AiWorkoutAnalysisRepository analiseRepository;
    private final TreinoRealizadoRepository treinoRealizadoRepository;

    /**
     * Idempotent: YES — Read-only.
     * Side Effects: NONE
     * Tenant-aware: YES
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<AnaliseWorkoutOutputDto> buscarAnalise(UUID treinoRealizadoId, UUID tenantId) {
        log.info("Buscando análise: treinoRealizadoId={}, tenantId={}", treinoRealizadoId, tenantId);

        treinoRealizadoRepository.findById(treinoRealizadoId)
                .filter(t -> tenantId.equals(t.getTenantId()))
                .orElseThrow(() -> new DomainNotFoundException("Treino não encontrado"));

        var result = analiseRepository
                .findByTreinoRealizadoIdAndTenantId(treinoRealizadoId, tenantId)
                .filter(a -> a.getStatus() != AnaliseStatus.PENDING)
                .map(this::toDto);

        log.info("Análise encontrada: treinoRealizadoId={}, present={}", treinoRealizadoId, result.isPresent());
        return result;
    }

    private AnaliseWorkoutOutputDto toDto(AnaliseWorkout a) {
        return new AnaliseWorkoutOutputDto(
                a.getId(),
                a.getTreinoRealizadoId(),
                a.getStatus(),
                a.getSummaryPt(),
                a.getTechnicalInterpretationPt(),
                a.getPrimaryCause(),
                a.getRecommendationPt(),
                a.getTags(),
                a.getExecutionScore(),
                a.getRationalePt(),
                a.getAnalyzedAt()
        );
    }
}
