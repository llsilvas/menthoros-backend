package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.dto.output.CoachAttentionItemOutputDto;
import br.com.menthoros.backend.dto.output.CoachAttentionItemOutputDto.Evidencia;
import br.com.menthoros.backend.dto.output.RecommendationExplanation;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.MetricasDiarias;
import br.com.menthoros.backend.enums.ExplanationConfidence;
import br.com.menthoros.backend.entity.PlanoMetaDados;
import br.com.menthoros.backend.entity.TreinoRealizado;
import br.com.menthoros.backend.enums.AtletaStatus;
import br.com.menthoros.backend.enums.Severidade;
import br.com.menthoros.backend.enums.TreinoExecucaoStatus;
import br.com.menthoros.backend.multitenancy.TenantContext;
import br.com.menthoros.backend.repository.AtletaRepository;
import br.com.menthoros.backend.repository.MetricasDiariasRepository;
import br.com.menthoros.backend.repository.PlanoMetadadosRepository;
import br.com.menthoros.backend.repository.TreinoPlanejadoRepository;
import br.com.menthoros.backend.repository.TreinoRealizadoRepository;
import br.com.menthoros.backend.services.CoachAttentionQueueService;
import br.com.menthoros.backend.services.helper.CoachAttentionSignalEvaluator;
import br.com.menthoros.backend.services.helper.SinalAtencao;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Implementação read-only/on-demand da fila de atenção do treinador. Agrega no escopo do tenant
 * (via {@link TenantContext}); não recebe resource-id (por isso não usa {@code @RequireTenant}).
 * Não persiste nada nem recalcula sinais: lê sinais existentes e os consolida/prioriza.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CoachAttentionQueueServiceImpl implements CoachAttentionQueueService {

    /** Janela (dias) dos sinais de aderência. */
    static final int JANELA_ADERENCIA_DIAS = 14;

    /** Cap de segurança de itens por tenant na resposta. */
    static final int MAX_ITENS = 20;

    /** Corte de exibição da v1: apenas itens com severidade ≥ ALTA. */
    private static final int CORTE_SEVERIDADE = Severidade.ALTA.getPeso();

    private static final Comparator<CoachAttentionItemOutputDto> ORDENACAO =
            Comparator.comparingInt((CoachAttentionItemOutputDto i) -> i.severity().getPeso()).reversed()
                    .thenComparing(Comparator.comparingInt(CoachAttentionItemOutputDto::priorityScore).reversed())
                    .thenComparing(CoachAttentionItemOutputDto::athleteName,
                            Comparator.nullsLast(Comparator.naturalOrder()));

    private final AtletaRepository atletaRepository;
    private final MetricasDiariasRepository metricasDiariasRepository;
    private final PlanoMetadadosRepository planoMetadadosRepository;
    private final TreinoPlanejadoRepository treinoPlanejadoRepository;
    private final TreinoRealizadoRepository treinoRealizadoRepository;
    private final CoachAttentionSignalEvaluator evaluator;
    private final Clock clock;

    /**
     * Idempotent: YES — leitura pura, sem mutação de estado.
     * Side Effects: NONE.
     * Tenant-aware: YES — roster restrito ao tenant do contexto.
     */
    @Override
    @Transactional(readOnly = true)
    public List<CoachAttentionItemOutputDto> getAttentionQueue() {
        UUID tenantId = TenantContext.getRequiredTenantId();
        LocalDate hoje = LocalDate.now(clock);
        LocalDate inicioJanela = hoje.minusDays(JANELA_ADERENCIA_DIAS);
        Instant geradoEm = clock.instant();

        List<CoachAttentionItemOutputDto> fila = atletaRepository.findAllByTenantIdOrderByNome(tenantId).stream()
                .filter(atleta -> atleta.getAtivo() != AtletaStatus.INATIVO)
                .map(atleta -> montarItem(atleta, hoje, inicioJanela, geradoEm))
                .flatMap(Optional::stream)
                .filter(item -> item.severity().getPeso() >= CORTE_SEVERIDADE)
                .sorted(ORDENACAO)
                .limit(MAX_ITENS)
                .toList();

        log.info("Fila de atenção gerada: tenant={}, itens={}", tenantId, fila.size());
        return fila;
    }

    /**
     * Idempotent: YES. Side Effects: NONE. Tenant-aware: YES.
     */
    @Override
    @Transactional(readOnly = true)
    public List<CoachAttentionItemOutputDto> getSinaisParaAtleta(UUID atletaId, int limite) {
        UUID tenantId = TenantContext.getRequiredTenantId();
        LocalDate hoje = LocalDate.now(clock);
        LocalDate inicioJanela = hoje.minusDays(JANELA_ADERENCIA_DIAS);
        Instant geradoEm = clock.instant();

        return atletaRepository.findByIdAndTenantId(atletaId, tenantId)
                .flatMap(atleta -> montarItem(atleta, hoje, inicioJanela, geradoEm))
                .stream()
                .filter(item -> item.severity().getPeso() >= CORTE_SEVERIDADE)
                .sorted(ORDENACAO)
                .limit(limite)
                .toList();
    }

    /** Deriva todos os sinais do atleta e consolida em um único item (motivo principal = maior severidade). */
    private Optional<CoachAttentionItemOutputDto> montarItem(Atleta atleta, LocalDate hoje,
                                                            LocalDate inicioJanela, Instant geradoEm) {
        UUID atletaId = atleta.getId();

        Double tsb = metricasDiariasRepository.findLatestByAtletaId(atletaId)
                .map(MetricasDiarias::getTsb).orElse(null);
        PlanoMetaDados plano = planoMetadadosRepository.findByAtletaId(atletaId).orElse(null);
        long perdidos = contarNaoCumpridos(atletaId, inicioJanela, hoje);
        Long diasInativos = diasDesdeUltimaAtividade(atletaId, hoje);

        List<SinalAtencao> sinais = new ArrayList<>();
        evaluator.avaliarFadiga(tsb).ifPresent(sinais::add);
        evaluator.avaliarSemPlano(plano != null).ifPresent(sinais::add);
        if (plano != null) {
            evaluator.avaliarSobrecarga(
                    Boolean.TRUE.equals(plano.getAlertaSobrecarga()),
                    Boolean.TRUE.equals(plano.getAlertaNecessitaDescanso()),
                    Boolean.TRUE.equals(plano.getAlertaRampAlto()),
                    Boolean.TRUE.equals(plano.getAlertaDiasConsecutivos()),
                    plano.getDiasConsecutivosTreino()
            ).ifPresent(sinais::add);
        }
        evaluator.avaliarAderencia(perdidos).ifPresent(sinais::add);
        evaluator.avaliarInatividade(diasInativos).ifPresent(sinais::add);
        evaluator.avaliarZonasVencidas(atleta.precisaAtualizarTestes()).ifPresent(sinais::add);

        if (sinais.isEmpty()) {
            return Optional.empty();
        }

        SinalAtencao principal = sinais.stream()
                .max(Comparator.comparingInt((SinalAtencao s) -> s.severidade().getPeso())
                        .thenComparingInt(s -> s.motivo().getPeso()))
                .orElseThrow();

        List<Evidencia> evidencias = consolidarEvidencias(sinais, principal);
        int priorityScore = principal.severidade().getPeso() * 100 + principal.motivo().getPeso();
        RecommendationExplanation explanation = new RecommendationExplanation(
                principal.rationale(), principal.sourceRules(), ExplanationConfidence.HIGH);

        return Optional.of(new CoachAttentionItemOutputDto(
                atletaId,
                nomeCompleto(atleta),
                principal.severidade(),
                priorityScore,
                principal.motivo(),
                principal.motivo().getSuggestedAction(),
                geradoEm,
                evidencias,
                explanation
        ));
    }

    /** Evidências do motivo principal primeiro, depois as dos demais sinais (consolidação). */
    private List<Evidencia> consolidarEvidencias(List<SinalAtencao> sinais, SinalAtencao principal) {
        List<Evidencia> evidencias = new ArrayList<>(principal.evidencias());
        sinais.stream()
                .filter(s -> s != principal)
                .forEach(s -> evidencias.addAll(s.evidencias()));
        return List.copyOf(evidencias);
    }

    private long contarNaoCumpridos(UUID atletaId, LocalDate inicio, LocalDate fim) {
        return treinoPlanejadoRepository.findByAtletaIdAndDataBetween(atletaId, inicio, fim).stream()
                .map(tp -> tp.getStatusTreino())
                .filter(s -> s == TreinoExecucaoStatus.PERDIDO || s == TreinoExecucaoStatus.PARCIAL)
                .count();
    }

    private Long diasDesdeUltimaAtividade(UUID atletaId, LocalDate hoje) {
        return treinoRealizadoRepository.findTopByAtletaIdOrderByDataTreinoDesc(atletaId)
                .map(TreinoRealizado::getDataTreino)
                .map(data -> ChronoUnit.DAYS.between(data, hoje))
                .orElse(null);
    }

    private String nomeCompleto(Atleta atleta) {
        String nome = atleta.getNome() != null ? atleta.getNome() : "";
        return atleta.getSobrenome() != null ? (nome + " " + atleta.getSobrenome()).trim() : nome;
    }
}
