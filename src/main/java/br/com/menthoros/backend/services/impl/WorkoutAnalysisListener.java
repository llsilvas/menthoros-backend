package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.dto.llm.AnaliseWorkoutRawDto;
import br.com.menthoros.backend.entity.AnaliseWorkout;
import br.com.menthoros.backend.entity.PlanoMetaDados;
import br.com.menthoros.backend.entity.TreinoPlanejado;
import br.com.menthoros.backend.entity.TreinoRealizado;
import br.com.menthoros.backend.enums.AnaliseStatus;
import br.com.menthoros.backend.events.TreinoRegistradoEvent;
import br.com.menthoros.backend.repository.AiWorkoutAnalysisRepository;
import br.com.menthoros.backend.repository.PlanoMetadadosRepository;
import br.com.menthoros.backend.repository.TreinoRealizadoRepository;
import br.com.menthoros.backend.routing.ModelRouter;
import br.com.menthoros.backend.routing.TaskComplexity;
import br.com.menthoros.backend.services.WorkoutAnalysisTranslator;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.HashMap;
import java.util.Map;
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
    private final PlanoMetadadosRepository planoMetadadosRepository;
    private final AiWorkoutAnalysisRepository analiseRepository;
    private final ModelRouter modelRouter;
    private final WorkoutAnalysisTranslator translator;
    private final ResourceLoader resourceLoader;
    private final ObjectMapper objectMapper;

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

        if (treino.getPercepcaoEsforco() == null) {
            log.debug("TreinoRealizado {} sem RPE, análise ignorada", treinoId);
            return;
        }

        AnaliseWorkout analise = createPending(treinoId, tenantId);

        try {
            String skillContent = cachedSkillContent;
            String promptData = buildPromptData(treino);

            ChatClient sonnet = modelRouter.route(TaskComplexity.COMPLEX);
            String rawJson = callWithRetry(() -> sonnet.prompt()
                    .system(skillContent)
                    .user("Analyze this workout and respond with valid JSON only:\n" + promptData)
                    .call()
                    .content(), 3);

            AnaliseWorkoutRawDto raw = objectMapper.readValue(rawJson, AnaliseWorkoutRawDto.class);

            AnaliseWorkoutRawDto translated;
            boolean translationFailed = false;
            try {
                translated = translator.translate(raw);
            } catch (Exception e) {
                log.warn("Falha na tradução para treinoId={}, persistindo em inglês: {}", treinoId, e.getMessage());
                translated = raw;
                translationFailed = true;
            }

            applyResult(analise, translated, translationFailed);
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

    // Only numeric/enum fields are included — never include free-text fields (feedbackAtleta, etc.)
    // to prevent prompt injection from user-supplied content.
    private String buildPromptData(TreinoRealizado treino) {
        Map<String, Object> data = new HashMap<>();

        Map<String, Object> planned = new HashMap<>();
        TreinoPlanejado planejado = treino.getTreinoPlanejado();
        if (planejado != null) {
            planned.put("type", planejado.getTipoTreino());
            planned.put("distance_km", planejado.getDistanciaKm());
            planned.put("expected_rpe", planejado.getPercepcaoEsforcoEsperada());
        }

        Map<String, Object> actual = new HashMap<>();
        actual.put("distance_km", treino.getDistanciaKm());
        actual.put("rpe", treino.getPercepcaoEsforco());
        if (treino.getFcMedia() != null) actual.put("avg_hr", treino.getFcMedia());

        Map<String, Object> context = new HashMap<>();
        Optional<PlanoMetaDados> metaDados = treino.getAtleta() != null
                ? planoMetadadosRepository.findByAtletaId(treino.getAtleta().getId())
                : Optional.empty();
        metaDados.ifPresent(m -> {
            context.put("tsb", m.getTsbAtual());
            context.put("ctl", m.getCtlAtual());
        });

        data.put("planned", planned);
        data.put("actual", actual);
        data.put("athlete_context", context);

        try {
            return objectMapper.writeValueAsString(data);
        } catch (Exception e) {
            throw new RuntimeException("Falha ao serializar dados do treino", e);
        }
    }

    private void applyResult(AnaliseWorkout analise, AnaliseWorkoutRawDto dto, boolean translationFailed) {
        analise.setStatus(AnaliseStatus.COMPLETED);
        analise.setSummaryPt(dto.summary());
        analise.setTechnicalInterpretationPt(dto.technicalInterpretation());
        analise.setPrimaryCause(dto.primaryCause());
        analise.setRecommendationPt(dto.recommendation());
        analise.setTags(dto.tags());
        analise.setExecutionScore(dto.executionScore());
        analise.setRationalePt(dto.rationale());
        analise.setTranslationFailed(translationFailed);
        analise.setAnalyzedAt(Instant.now());
        analiseRepository.save(analise);
    }

    // maxAttempts = total number of attempts (1 initial + (maxAttempts-1) retries)
    private <T> T callWithRetry(java.util.function.Supplier<T> supplier, int maxAttempts) {
        int attempts = 0;
        while (true) {
            try {
                return supplier.get();
            } catch (Exception ex) {
                attempts++;
                if (attempts >= maxAttempts) {
                    throw ex;
                }
                long backoffMs = (long) Math.pow(2, attempts) * 500L;
                log.warn("LLM call failed (attempt {}/{}), retrying in {}ms: {}", attempts, maxAttempts, backoffMs, ex.getMessage());
                try {
                    Thread.sleep(backoffMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw ex;
                }
            }
        }
    }
}
