package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.services.ReconciliacaoPendentesService;
import br.com.menthoros.backend.services.MatchingScoreCalculator;
import br.com.menthoros.backend.dto.output.CandidateMatchDto;
import br.com.menthoros.backend.dto.output.TreinoRealizadoPendenteOutputDto;
import br.com.menthoros.backend.dto.MatchingScoreResult;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.TreinoRealizado;
import br.com.menthoros.backend.entity.TreinoPlanejado;
import br.com.menthoros.backend.enums.ReconciliationStatus;
import br.com.menthoros.backend.repository.AtletaRepository;
import br.com.menthoros.backend.repository.TreinoPlanejadoRepository;
import br.com.menthoros.backend.repository.TreinoRealizadoRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ReconciliacaoPendentesServiceImpl implements ReconciliacaoPendentesService {

    private final TreinoRealizadoRepository treinoRealizadoRepository;
    private final TreinoPlanejadoRepository treinoPlanejadoRepository;
    private final AtletaRepository atletaRepository;
    private final MatchingScoreCalculator matchingScoreCalculator;

    public ReconciliacaoPendentesServiceImpl(
            TreinoRealizadoRepository treinoRealizadoRepository,
            TreinoPlanejadoRepository treinoPlanejadoRepository,
            AtletaRepository atletaRepository,
            MatchingScoreCalculator matchingScoreCalculator) {
        this.treinoRealizadoRepository = treinoRealizadoRepository;
        this.treinoPlanejadoRepository = treinoPlanejadoRepository;
        this.atletaRepository = atletaRepository;
        this.matchingScoreCalculator = matchingScoreCalculator;
    }

    @Transactional(readOnly = true)
    public Page<TreinoRealizadoPendenteOutputDto> listarPendentes(
            UUID atletaId,
            UUID tenantId,
            List<ReconciliationStatus> statuses,
            LocalDate dataInicio,
            LocalDate dataFim,
            Pageable pageable) {

        if (statuses == null || statuses.isEmpty()) {
            statuses = List.of(ReconciliationStatus.AMBIGUO, ReconciliationStatus.NAO_PLANEJADO);
        }

        Page<TreinoRealizado> page = treinoRealizadoRepository.findPendentesParaRevisao(
                tenantId, atletaId, statuses, dataInicio, dataFim, pageable);

        Atleta atleta = atletaRepository.findByIdAndTenantId(atletaId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Atleta não encontrado ou não pertence a este tenant"));

        List<TreinoRealizadoPendenteOutputDto> dtos = page.getContent().stream()
                .map(tr -> toDto(tr, atleta.getNome()))
                .collect(Collectors.toList());

        return new PageImpl<>(dtos, pageable, page.getTotalElements());
    }

    @Transactional(readOnly = true)
    public List<CandidateMatchDto> listarCandidatos(UUID treinoRealizadoId, UUID tenantId) {
        TreinoRealizado realizado = treinoRealizadoRepository.findById(treinoRealizadoId)
                .orElseThrow(() -> new IllegalArgumentException("TreinoRealizado não encontrado: " + treinoRealizadoId));

        if (!realizado.getTenantId().equals(tenantId)) {
            throw new IllegalArgumentException("Tenant mismatch para TreinoRealizado");
        }

        UUID atletaId = realizado.getAtleta().getId();
        LocalDate dataTreino = realizado.getDataTreino();

        List<TreinoPlanejado> candidatos = treinoPlanejadoRepository.findByAtletaIdAndDataBetween(
                atletaId,
                dataTreino.minusDays(1),
                dataTreino.plusDays(1));

        Atleta atleta = realizado.getAtleta();

        return candidatos.stream()
                .map(planejado -> {
                    MatchingScoreResult scoreResult = matchingScoreCalculator.calculate(realizado, planejado, atleta);
                    double overall = scoreResult.getOverallScore() != null ? scoreResult.getOverallScore().doubleValue() : 0;
                    double temporal = scoreResult.getTemporalScore() != null ? scoreResult.getTemporalScore().doubleValue() : 0;
                    double duration = scoreResult.getDurationScore() != null ? scoreResult.getDurationScore().doubleValue() : 0;
                    double distance = scoreResult.getDistanceScore() != null ? scoreResult.getDistanceScore().doubleValue() : 0;
                    return new CandidateMatchDto(
                            planejado.getId(),
                            planejado.getDataTreino(),
                            planejado.getTipoTreino(),
                            planejado.getDistanciaKm(),
                            planejado.getDuracaoMin(),
                            overall,
                            temporal,
                            duration,
                            distance
                    );
                })
                .filter(dto -> dto.score() > 0)
                .sorted((a, b) -> Double.compare(b.score(), a.score()))
                .collect(Collectors.toList());
    }

    private TreinoRealizadoPendenteOutputDto toDto(TreinoRealizado tr, String atletaNome) {
        return new TreinoRealizadoPendenteOutputDto(
                tr.getId(),
                tr.getExternalId(),
                tr.getAtleta().getId(),
                atletaNome,
                tr.getDataTreino(),
                tr.getTipoTreino(),
                tr.getDistanciaKm(),
                tr.getDuracaoMin(),
                tr.getReconciliationStatus(),
                tr.getReconciliationScore() != null ? tr.getReconciliationScore().doubleValue() : null,
                tr.getReconciledAt(),
                tr.getReconciledBy(),
                tr.getFonteDados()
        );
    }
}
