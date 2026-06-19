package br.com.menthoros.backend.jobs;

import br.com.menthoros.backend.dto.output.CoachAttentionItemOutputDto;
import br.com.menthoros.backend.dto.output.RecommendationExplanation;
import br.com.menthoros.backend.entity.SugestaoCoach;
import br.com.menthoros.backend.enums.MotivoAtencao;
import br.com.menthoros.backend.enums.Severidade;
import br.com.menthoros.backend.enums.StatusSugestao;
import br.com.menthoros.backend.enums.TipoSugestao;
import br.com.menthoros.backend.multitenancy.TenantContext;
import br.com.menthoros.backend.repository.AssessoriaRepository;
import br.com.menthoros.backend.repository.AtletaRepository;
import br.com.menthoros.backend.repository.SugestaoCoachRepository;
import br.com.menthoros.backend.services.CoachAttentionQueueService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Job diário que converte sinais elegíveis da fila de atenção em sugestões persistentes
 * para revisão do treinador.
 *
 * <p>Executa às 06:00 UTC. Itera todos os tenants ativos e, para cada um, chama
 * {@link CoachAttentionQueueService#getAttentionQueue()} filtrando sinais com
 * severidade CRITICA ou ALTA. Sinais MEDIA são descartados (threshold da v1).
 *
 * <p>Idempotência garantida pelo índice parcial {@code uk_sugestao_pending}:
 * {@link DataIntegrityViolationException} em INSERT duplicado é capturada silenciosamente.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SugestaoCoachGeneratorJob {

    private final AssessoriaRepository assessoriaRepository;
    private final CoachAttentionQueueService coachAttentionQueueService;
    private final SugestaoCoachRepository sugestaoCoachRepository;
    private final AtletaRepository atletaRepository;
    private final ObjectMapper objectMapper;

    private static final Map<MotivoAtencao, TipoSugestao> MOTIVO_TIPO = Map.of(
            MotivoAtencao.FADIGA,        TipoSugestao.RECOVERY,
            MotivoAtencao.SOBRECARGA,    TipoSugestao.RECOVERY,
            MotivoAtencao.INATIVIDADE,   TipoSugestao.RECOVERY,
            MotivoAtencao.SEM_PLANO,     TipoSugestao.NEW_PLAN,
            MotivoAtencao.ADERENCIA,     TipoSugestao.PLAN_ADJUST,
            MotivoAtencao.ZONAS_VENCIDAS, TipoSugestao.PLAN_ADJUST
    );

    @Scheduled(cron = "0 0 6 * * *")
    public void gerarSugestoes() {
        List<UUID> tenantIds = assessoriaRepository.findByAtivoTrue()
                .stream().map(a -> a.getId()).toList();

        log.info("SugestaoCoachGeneratorJob: iniciando para {} tenant(s)", tenantIds.size());

        for (UUID tenantId : tenantIds) {
            try {
                TenantContext.setTenantId(tenantId);
                gerarPorTenant(tenantId);
            } catch (Exception e) {
                log.error("SugestaoCoachGeneratorJob: erro ao processar tenant={}: {}", tenantId, e.getMessage(), e);
            } finally {
                TenantContext.clear();
            }
        }
    }

    private void gerarPorTenant(UUID tenantId) {
        List<CoachAttentionItemOutputDto> itens = coachAttentionQueueService.getAttentionQueue();

        List<CoachAttentionItemOutputDto> elegiveis = itens.stream()
                .filter(i -> i.severity() == Severidade.CRITICA || i.severity() == Severidade.ALTA)
                .toList();

        int criadas = 0;
        int ignoradas = 0;

        for (CoachAttentionItemOutputDto item : elegiveis) {
            TipoSugestao tipo = MOTIVO_TIPO.get(item.primaryReason());
            if (tipo == null) {
                log.warn("SugestaoCoachGeneratorJob: MotivoAtencao {} sem mapeamento, ignorado", item.primaryReason());
                ignoradas++;
                continue;
            }

            var atletaOpt = atletaRepository.findById(item.atletaId());
            if (atletaOpt.isEmpty()) {
                log.warn("SugestaoCoachGeneratorJob: atleta {} não encontrado, ignorado", item.atletaId());
                ignoradas++;
                continue;
            }

            try {
                Instant agora = Instant.now();
                SugestaoCoach sugestao = SugestaoCoach.builder()
                        .tenantId(tenantId)
                        .atleta(atletaOpt.get())
                        .tipo(tipo)
                        .status(StatusSugestao.PENDING)
                        .confidence(mapConfidence(item.severity()))
                        .summary(item.suggestedAction())
                        .reasoningJson(serializarReasoning(item.explanation()))
                        .createdAt(agora)
                        .expiresAt(agora.plus(7, ChronoUnit.DAYS))
                        .build();

                sugestaoCoachRepository.save(sugestao);
                criadas++;

            } catch (DataIntegrityViolationException e) {
                log.debug("SugestaoCoachGeneratorJob: sugestão já pending para atleta={} tipo={}, ignorada",
                        item.atletaId(), tipo);
                ignoradas++;
            }
        }

        log.info("SugestaoCoachGeneratorJob: tenant={} — {} elegíveis, {} criadas, {} ignoradas",
                tenantId, elegiveis.size(), criadas, ignoradas);
    }

    private String mapConfidence(Severidade severidade) {
        return switch (severidade) {
            case CRITICA -> "HIGH";
            case ALTA    -> "MEDIUM";
            default      -> "LOW";
        };
    }

    private String serializarReasoning(RecommendationExplanation explanation) {
        if (explanation == null) return null;
        try {
            return objectMapper.writeValueAsString(explanation);
        } catch (JsonProcessingException e) {
            log.warn("SugestaoCoachGeneratorJob: falha ao serializar reasoning: {}", e.getMessage());
            return null;
        }
    }
}
