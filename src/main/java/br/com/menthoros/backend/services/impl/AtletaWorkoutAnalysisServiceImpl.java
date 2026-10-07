package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.config.core.WorkoutAnalysisProperties;
import br.com.menthoros.backend.dto.output.AthleteWorkoutAnalysisOutputDto;
import br.com.menthoros.backend.entity.AnaliseWorkout;
import br.com.menthoros.backend.entity.TreinoPlanejado;
import br.com.menthoros.backend.entity.TreinoRealizado;
import br.com.menthoros.backend.enums.AnaliseStatus;
import br.com.menthoros.backend.enums.WorkoutPlanVerdict;
import br.com.menthoros.backend.exception.DomainNotFoundException;
import br.com.menthoros.backend.repository.AiWorkoutAnalysisRepository;
import br.com.menthoros.backend.repository.TreinoRealizadoRepository;
import br.com.menthoros.backend.services.AtletaWorkoutAnalysisService;
import br.com.menthoros.backend.services.WorkoutAnalysisEligibility;
import br.com.menthoros.backend.services.helper.WorkoutPlanVerdictCalculator;
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
 *
 * <p><b>{@code veredito} é "live" (add-athlete-workout-verdict-chip, Codex #importante):</b>
 * diferente dos quatro textos da IA — escritos uma única vez em {@code tb_analise_workout} e
 * congelados a partir daí —, o veredito é recalculado em toda chamada a partir dos números
 * <em>atuais</em> de {@code TreinoRealizado}. Se o atleta editar duração/distância/RPE depois de
 * a análise já estar {@code COMPLETED} (edição manual ou re-sync do Strava), ou se o planejado
 * vinculado mudar, o veredito pode divergir do texto já escrito pela IA — nenhum dos dois
 * fluxos de edição invalida ou reprocessa a análise. Aceito nesta versão (fora de escopo
 * reprocessar a IA); se a divergência se mostrar incômoda em produção, considerar invalidar
 * {@code AnaliseWorkout} quando os campos relevantes de {@code TreinoRealizado} mudarem.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AtletaWorkoutAnalysisServiceImpl implements AtletaWorkoutAnalysisService {

    private static final String SEM_VEREDITO = "sem_planejado";

    private final TreinoRealizadoRepository treinoRealizadoRepository;
    private final AiWorkoutAnalysisRepository analiseRepository;
    private final WorkoutAnalysisEligibility eligibility;
    private final WorkoutAnalysisProperties properties;
    private final MeterRegistry meterRegistry;
    private final Clock clock;
    private final WorkoutPlanVerdictCalculator verdictCalculator;

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

        AthleteWorkoutAnalysisOutputDto dto = dtoCompleto(treino, pronta);
        registrarPrimeiraVisualizacao(pronta, dto.veredito());
        return Optional.of(dto);
    }

    /** Carimba e conta UMA vez por análise (Codex #6) — o polling do front não infla a métrica. */
    private void registrarPrimeiraVisualizacao(AnaliseWorkout analise, WorkoutPlanVerdict veredito) {
        if (analise.getAtletaPrimeiraVisualizacaoEm() != null) {
            return;
        }
        // Update condicional atômico (QA/Codex): só quem transiciona null → agora conta.
        Instant agora = Instant.now(clock);
        if (analiseRepository.marcarPrimeiraVisualizacao(analise.getId(), agora) != 1) {
            return;
        }
        analise.setAtletaPrimeiraVisualizacaoEm(agora);
        Counter.builder("atleta_analise_visualizada_total")
                .description("Análises pós-treino abertas pelo atleta (primeira visualização por análise)")
                .register(meterRegistry)
                .increment();
        Counter.builder("atleta_treino_veredito_total")
                .description("Veredito de aderência ao plano (add-athlete-workout-verdict-chip), por primeira visualização")
                .tag("veredito", veredito != null ? veredito.name() : SEM_VEREDITO)
                .register(meterRegistry)
                .increment();
    }

    private AthleteWorkoutAnalysisOutputDto dtoPendente(TreinoRealizado treino) {
        AthleteWorkoutAnalysisOutputDto.Executado executado = executado(treino);
        AthleteWorkoutAnalysisOutputDto.Planejado planejado = planejado(treino);
        return new AthleteWorkoutAnalysisOutputDto(AnaliseStatus.PENDING, null,
                null, null, null, null, executado, planejado,
                verdictCalculator.calcular(executado, planejado));
    }

    private AthleteWorkoutAnalysisOutputDto dtoCompleto(TreinoRealizado treino, AnaliseWorkout analise) {
        AthleteWorkoutAnalysisOutputDto.Executado executado = executado(treino);
        AthleteWorkoutAnalysisOutputDto.Planejado planejado = planejado(treino);
        return new AthleteWorkoutAnalysisOutputDto(
                AnaliseStatus.COMPLETED,
                analise.getAnalyzedAt(),
                analise.getAtletaReconhecimento(),
                analise.getAtletaComoFoi(),
                analise.getAtletaEsforco(),
                analise.getAtletaProximoTreino(),
                executado,
                planejado,
                verdictCalculator.calcular(executado, planejado));
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
