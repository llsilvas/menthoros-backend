package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.config.core.WorkoutAnalysisProperties;
import br.com.menthoros.backend.dto.llm.AnaliseWorkoutRawDto;
import br.com.menthoros.backend.dto.llm.AthleteMessageDto;
import br.com.menthoros.backend.entity.AnaliseWorkout;
import br.com.menthoros.backend.entity.TreinoRealizado;
import br.com.menthoros.backend.enums.AnaliseStatus;
import br.com.menthoros.backend.events.TreinoRegistradoEvent;
import br.com.menthoros.backend.repository.AiWorkoutAnalysisRepository;
import br.com.menthoros.backend.repository.TreinoRealizadoRepository;
import br.com.menthoros.backend.routing.ModelRouter;
import br.com.menthoros.backend.routing.TaskComplexity;
import br.com.menthoros.backend.services.AthleteMessageGenerator;
import br.com.menthoros.backend.services.AthleteMessageValidator;
import br.com.menthoros.backend.services.WorkoutAnalysisEligibility;
import br.com.menthoros.backend.services.WorkoutAnalysisPromptDataBuilder;
import br.com.menthoros.backend.services.WorkoutAnalysisTranslator;
import br.com.menthoros.backend.services.prompt.PromptTemplateLoader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.core.io.ResourceLoader;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import jakarta.annotation.PostConstruct;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Escuta TreinoRegistradoEvent e dispara análise pós-treino assíncrona via workout-analyzer skill.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorkoutAnalysisListener {

    private final TreinoRealizadoRepository treinoRealizadoRepository;
    private final AiWorkoutAnalysisRepository analiseRepository;
    private final ModelRouter modelRouter;
    private final WorkoutAnalysisTranslator translator;
    private final ResourceLoader resourceLoader;
    private final PromptTemplateLoader templateLoader;
    private final WorkoutAnalysisProperties workoutAnalysisProperties;
    private final WorkoutAnalysisEligibility eligibility;
    private final WorkoutAnalysisPromptDataBuilder promptDataBuilder;
    private final AthleteMessageGenerator athleteMessageGenerator;
    private final AthleteMessageValidator athleteMessageValidator;

    private static final String SKILL_PATH = "classpath:skills/analise/workout-analyzer/SKILL.md";
    private String cachedSkillContent;

    @PostConstruct
    void initSkillContent() {
        this.cachedSkillContent = loadSkill();
    }

    /**
     * Processa análise pós-treino de forma assíncrona após commit da transação de save.
     *
     * Idempotent: YES — ignora se análise COMPLETED já existe para o treino.
     * Side Effects: Database insert (AnaliseWorkout), External API call (Claude Sonnet + Haiku)
     * Tenant-aware: YES
     */
    @Async("workoutAnalysisExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onTreinoRegistrado(TreinoRegistradoEvent event) {
        UUID treinoId = event.treinoRealizadoId();
        UUID tenantId = event.tenantId();
        log.info("Iniciando análise de treino: treinoRealizadoId={}, tenantId={}", treinoId, tenantId);

        if (analiseRepository.existsByTreinoRealizadoIdAndStatus(treinoId, AnaliseStatus.COMPLETED)) {
            log.debug("Análise COMPLETED já existe para treinoRealizadoId={}, ignorando", treinoId);
            return;
        }

        TreinoRealizado treino = treinoRealizadoRepository.findById(treinoId).orElse(null);
        if (treino == null) {
            log.warn("TreinoRealizado não encontrado: {}", treinoId);
            return;
        }

        // Elegibilidade compartilhada com o endpoint do atleta (Codex #2): sem RPE não há o que
        // analisar, e o guard de custo (ingestao-treino-realizado, D5) corta atividade histórica —
        // registrar publica evento em toda inserção, e a carga inicial de um atleta recém
        // conectado dispararia uma chamada de LLM por atividade.
        if (!eligibility.elegivel(treino)) {
            log.debug("TreinoRealizado {} não elegível para análise (sem RPE ou mais antigo que {} dias)",
                    treinoId, workoutAnalysisProperties.getMaxIdadeDias());
            return;
        }

        AnaliseWorkout analise = createPending(treinoId, tenantId);

        try {
            String skillContent = cachedSkillContent;
            String promptData = promptDataBuilder.build(treino);
            String userPrompt = templateLoader.loadAndFormat("workout-analysis-user-prompt.txt", promptData);

            ChatClient sonnet = modelRouter.route(TaskComplexity.COMPLEX);
            AnaliseWorkoutRawDto raw = sonnet.prompt()
                    .system(skillContent)
                    .user(userPrompt)
                    .call()
                    .entity(AnaliseWorkoutRawDto.class);

            AnaliseWorkoutRawDto translated;
            boolean translationFailed = false;
            try {
                translated = translator.translate(raw);
            } catch (Exception e) {
                log.warn("Falha na tradução para treinoId={}, persistindo em inglês: {}", treinoId, e.getMessage());
                translated = raw;
                translationFailed = true;
            }

            // Chamada 2 (D2): bloco do atleta em PT-BR, com o primary_cause resultante da
            // chamada 1. Falha vira Optional.empty() dentro do gerador — o COMPLETED do coach
            // nunca depende dela.
            Optional<AthleteMessageDto> bloco = athleteMessageGenerator.gerar(promptData, raw.primaryCause());

            applyResult(analise, translated, translationFailed, bloco);
            log.info("Análise concluída: treinoRealizadoId={}, score={}", treinoId, analise.getExecutionScore());

        } catch (Exception e) {
            log.error("Falha na análise de treino: treinoRealizadoId={}, tenantId={}: {}", treinoId, tenantId, e.getMessage(), e);
            // Reset all completed fields to avoid partially-set state persisting alongside FAILED status
            analise.setStatus(AnaliseStatus.FAILED);
            analise.setSummaryPt(null);
            analise.setTechnicalInterpretationPt(null);
            analise.setPrimaryCause(null);
            analise.setRecommendationPt(null);
            analise.setTags(null);
            analise.setExecutionScore(null);
            analise.setRationalePt(null);
            analise.setAtletaReconhecimento(null);
            analise.setAtletaComoFoi(null);
            analise.setAtletaEsforco(null);
            analise.setAtletaProximoTreino(null);
            analise.setAtletaBloqueadoMotivo(null);
            analise.setTranslationFailed(false);
            analise.setErrorMessage(e.getMessage());
            analise.setAnalyzedAt(Instant.now());
            analiseRepository.save(analise);
        }
    }

    private AnaliseWorkout createPending(UUID treinoId, UUID tenantId) {
        AnaliseWorkout analise = analiseRepository
                .findByTreinoRealizadoIdAndTenantId(treinoId, tenantId)
                .orElseGet(() -> {
                    AnaliseWorkout novo = new AnaliseWorkout();
                    novo.setTreinoRealizadoId(treinoId);
                    novo.setTenantId(tenantId);
                    return novo;
                });
        analise.setStatus(AnaliseStatus.PENDING);
        return analiseRepository.save(analise);
    }

    private String loadSkill() {
        try {
            var resource = resourceLoader.getResource(SKILL_PATH);
            return new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Falha ao carregar SKILL.md: " + e.getMessage(), e);
        }
    }

    private void applyResult(AnaliseWorkout analise, AnaliseWorkoutRawDto dto, boolean translationFailed,
                             Optional<AthleteMessageDto> bloco) {
        analise.setStatus(AnaliseStatus.COMPLETED);
        analise.setSummaryPt(dto.summary());
        analise.setTechnicalInterpretationPt(dto.technicalInterpretation());
        analise.setPrimaryCause(dto.primaryCause());
        analise.setRecommendationPt(dto.recommendation());
        analise.setTags(dto.tags());
        analise.setExecutionScore(dto.executionScore());
        analise.setRationalePt(dto.rationale());
        analise.setTranslationFailed(translationFailed);
        aplicarBlocoAtleta(analise, bloco);
        analise.setAnalyzedAt(Instant.now());
        analiseRepository.save(analise);
    }

    /** Bloco ausente/incompleto → campos nulos sem motivo; bloqueado pelo validador → nulos com motivo. */
    private void aplicarBlocoAtleta(AnaliseWorkout analise, Optional<AthleteMessageDto> bloco) {
        analise.setAtletaReconhecimento(null);
        analise.setAtletaComoFoi(null);
        analise.setAtletaEsforco(null);
        analise.setAtletaProximoTreino(null);
        analise.setAtletaBloqueadoMotivo(null);

        if (bloco.isEmpty() || !athleteMessageValidator.completo(bloco.get())) {
            return;
        }
        AthleteMessageDto dto = bloco.get();
        Optional<String> motivo = athleteMessageValidator.validar(dto);
        if (motivo.isPresent()) {
            log.warn("Bloco do atleta bloqueado pelo validador ({}): treinoRealizadoId={}",
                    motivo.get(), analise.getTreinoRealizadoId());
            analise.setAtletaBloqueadoMotivo(motivo.get());
            return;
        }
        analise.setAtletaReconhecimento(dto.recognition());
        analise.setAtletaComoFoi(dto.howItWent());
        analise.setAtletaEsforco(dto.effortReading());
        analise.setAtletaProximoTreino(dto.nextWorkoutTip());
    }
}
