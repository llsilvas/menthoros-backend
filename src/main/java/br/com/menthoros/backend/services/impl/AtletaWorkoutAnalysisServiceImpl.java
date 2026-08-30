package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.config.core.WorkoutAnalysisProperties;
import br.com.menthoros.backend.dto.output.AthleteWorkoutAnalysisOutputDto;
import br.com.menthoros.backend.entity.AnaliseWorkout;
import br.com.menthoros.backend.entity.TreinoPlanejado;
import br.com.menthoros.backend.entity.TreinoRealizado;
import br.com.menthoros.backend.enums.AnaliseStatus;
import br.com.menthoros.backend.exception.DomainNotFoundException;
import br.com.menthoros.backend.repository.AiWorkoutAnalysisRepository;
import br.com.menthoros.backend.repository.TreinoRealizadoRepository;
import br.com.menthoros.backend.services.AtletaWorkoutAnalysisService;
import br.com.menthoros.backend.services.WorkoutAnalysisEligibility;
import br.com.menthoros.backend.multitenancy.TenantContext;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Análise pós-treino na visão do ATLETA (analise-ia-treino-atleta, D4).
 *
 * <p><b>Isolamento:</b> mesmo gate do feedback — o realizado é buscado por {@code id + tenantId}
 * e confirmado como do atleta autenticado; fora disso, {@code 404}.
 *
 * <p><b>{@code PENDING} por elegibilidade (Codex #2):</b> o listener é assíncrono; logo após o
 * registro a linha de {@code AnaliseWorkout} ainda não existe. Realizado elegível (mesma regra
 * do listener, via {@link WorkoutAnalysisEligibility}) sem linha ou com linha {@code PENDING}
 * devolve {@code 200 PENDING} — senão o card do atleta sumiria exatamente no fluxo de registro.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AtletaWorkoutAnalysisServiceImpl implements AtletaWorkoutAnalysisService {

    private final TreinoRealizadoRepository treinoRealizadoRepository;
    private final AiWorkoutAnalysisRepository analiseRepository;
    private final WorkoutAnalysisEligibility eligibility;
    private final WorkoutAnalysisProperties properties;
    private final MeterRegistry meterRegistry;
    private final Clock clock;

    /**
     * Idempotent: quase — a primeira chamada com COMPLETED carimba a visualização; as demais só leem.
     * Side Effects: UPDATE em tb_analise_workout (carimbo) e incremento de métrica, uma vez por análise.
     * Tenant-aware: YES.
     */
    @Override
    @Transactional
    public Optional<AthleteWorkoutAnalysisOutputDto> buscarAnalise(UUID atletaId, UUID treinoRealizadoId) {
        UUID tenantId = TenantContext.getRequiredTenantId();
        TreinoRealizado treino = treinoRealizadoRepository.findByIdAndTenantId(treinoRealizadoId, tenantId)
                .filter(tr -> tr.getAtleta() != null && atletaId.equals(tr.getAtleta().getId()))
                .orElseThrow(() -> new DomainNotFoundException("Treino realizado não encontrado"));

        if (!properties.getAthleteMessage().isEnabled() || !eligibility.elegivel(treino)) {
            return Optional.empty();
        }

        Optional<AnaliseWorkout> analise = analiseRepository
                .findByTreinoRealizadoIdAndTenantId(treinoRealizadoId, tenantId);

        if (analise.isEmpty() || analise.get().getStatus() == AnaliseStatus.PENDING) {
            return Optional.of(dtoPendente(treino));
        }

        AnaliseWorkout pronta = analise.get();
        if (pronta.getStatus() != AnaliseStatus.COMPLETED || pronta.getAtletaComoFoi() == null) {
            // FAILED, bloco bloqueado pelo validador ou análise anterior à change: sem card.
            return Optional.empty();
        }

        registrarPrimeiraVisualizacao(pronta);
        return Optional.of(dtoCompleto(treino, pronta));
    }

    /** Carimba e conta UMA vez por análise (Codex #6) — o polling do front não infla a métrica. */
    private void registrarPrimeiraVisualizacao(AnaliseWorkout analise) {
        if (analise.getAtletaPrimeiraVisualizacaoEm() != null) {
            return;
        }
        analise.setAtletaPrimeiraVisualizacaoEm(Instant.now(clock));
        analiseRepository.save(analise);
        Counter.builder("atleta_analise_visualizada_total")
                .description("Análises pós-treino abertas pelo atleta (primeira visualização por análise)")
                .register(meterRegistry)
                .increment();
    }

    private AthleteWorkoutAnalysisOutputDto dtoPendente(TreinoRealizado treino) {
        return new AthleteWorkoutAnalysisOutputDto(AnaliseStatus.PENDING, null,
                null, null, null, null, executado(treino), planejado(treino));
    }

    private AthleteWorkoutAnalysisOutputDto dtoCompleto(TreinoRealizado treino, AnaliseWorkout analise) {
        return new AthleteWorkoutAnalysisOutputDto(
                AnaliseStatus.COMPLETED,
                analise.getAnalyzedAt(),
                analise.getAtletaReconhecimento(),
                analise.getAtletaComoFoi(),
                analise.getAtletaEsforco(),
                analise.getAtletaProximoTreino(),
                executado(treino),
                planejado(treino));
    }

    private static AthleteWorkoutAnalysisOutputDto.Executado executado(TreinoRealizado treino) {
        return new AthleteWorkoutAnalysisOutputDto.Executado(
                minutos(treino.getDuracaoMin()), treino.getDistanciaKm(), treino.getPercepcaoEsforco());
    }

    private static AthleteWorkoutAnalysisOutputDto.Planejado planejado(TreinoRealizado treino) {
        TreinoPlanejado planejado = treino.getTreinoPlanejado();
        if (planejado == null) {
            return null;
        }
        return new AthleteWorkoutAnalysisOutputDto.Planejado(
                minutos(planejado.getDuracaoMin()), planejado.getDistanciaKm(),
                planejado.getPercepcaoEsforcoEsperada());
    }

    private static Long minutos(Duration duracao) {
        return duracao == null ? null : duracao.toMinutes();
    }
}
