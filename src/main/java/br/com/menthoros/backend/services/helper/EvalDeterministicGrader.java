package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.domain.compliance.PlannerViolation;
import br.com.menthoros.backend.domain.planner.WeekPlanSkeleton;
import br.com.menthoros.backend.dto.llm.EtapaTreinoLlmDto;
import br.com.menthoros.backend.dto.llm.PlanoSemanalLlmDto;
import br.com.menthoros.backend.dto.llm.TreinoPlanejadoLlmDto;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.Prova;
import br.com.menthoros.backend.enums.DiaSemana;
import br.com.menthoros.backend.mapper.TreinoMapperImpl;
import br.com.menthoros.backend.services.plano.ProvaNoPlanoService;
import br.com.menthoros.backend.util.Utils;
import br.com.menthoros.backend.services.prompt.constraint.Constraint;
import br.com.menthoros.backend.services.prompt.constraint.ConstraintKey;
import br.com.menthoros.backend.services.quality.PlanQualityChecker;
import br.com.menthoros.backend.services.quality.ViolacaoQualidade;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.jspecify.annotations.Nullable;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Grader determinístico do eval set (plan-generation-eval-set, fatia 1) — roda **só em modo
 * candidato** (design.md "Correção de escopo", rodada 2 de DoR): fixtures de auditoria não têm
 * `constraintsAtivas`/`skeleton` retroativos, então este grader nunca as recebe delas.
 *
 * <p>Despacha por {@code schemaVersion}: {@code schema-v2} passa por {@link SessionResolver}
 * antes de checar (mesmo caminho de {@code IaServiceImpl.gerarChamadaLlmV2}); qualquer outro valor
 * (ou ausente) desserializa direto para {@link PlanoSemanalLlmDto}.
 *
 * <p>Usa um {@link SimpleMeterRegistry} descartável para {@link PlanQualityChecker} — nunca o
 * registry de produção, para não poluir métricas com execuções de eval (design.md §1.4).
 *
 * <p>Idempotent: YES — leitura pura. Side Effects: NONE (métrica só no registry descartável
 * interno). Tenant-aware: NÃO — opera sobre dados de fixture/candidato, não de tenant real.
 *
 * <p>Deliberadamente sem {@code @Component} — nunca instanciada pelo Spring (achado do /qa).
 *
 * <p>Avalia o plano **depois** da {@link NormalizacaoDeTreino} de produção (add-etapa-pace-validator):
 * a resposta bruta da LLM não é o que o coach recebe, e gradá-la mede bugs que a normalização já corrige
 * e esconde os que ela cria. Treino reprovado por gate (em produção vai ao turno de reparo) é avaliado cru.
 */
public class EvalDeterministicGrader {

    private final ObjectMapper objectMapper;
    private final SessionResolver sessionResolver;
    private final PlannerShadowService plannerShadowService;
    private final PlanQualityChecker planQualityChecker;
    private final NormalizacaoDeTreino normalizacao = new NormalizacaoDeTreino(
            new TreinoNormalizador(new PaceValidator()), new EtapaFcValidator(),
            new PlanoEstruturaReparador(new SimpleMeterRegistry()), new PaceValidator(), new SimpleMeterRegistry());
    private final PaceValidator paceValidator = new PaceValidator();
    // Só construirTreinoProva é usado: não toca repositório nem os colaboradores do mapper (só durationToString).
    private final ProvaNoPlanoService provaNoPlano =
            new ProvaNoPlanoService(null, new TreinoMapperImpl(null, null), null, null);

    /** Tolerância do pace implícito (duração ÷ distância) em torno da faixa do ritmoAlvo — decisão do coach. */
    static final double TOLERANCIA_PACE = 0.10;

    public EvalDeterministicGrader(ObjectMapper objectMapper, SessionResolver sessionResolver,
                                    PlannerShadowService plannerShadowService) {
        this.objectMapper = objectMapper;
        this.sessionResolver = sessionResolver;
        this.plannerShadowService = plannerShadowService;
        this.planQualityChecker = new PlanQualityChecker(new SimpleMeterRegistry());
    }

    /** Violações dos dois checkers reaproveitados — tipos distintos, sem conversão forçada. */
    public record Resultado(List<ViolacaoQualidade> violacoesQualidade,
                             List<PlannerViolation> violacoesCompliance,
                             PlanoSemanalLlmDto planoAvaliado) {
    }

    /**
     * Avalia uma resposta candidata (JSON bruto do LLM, v1 ou v2) contra as regras/skeleton da
     * fixture de candidato. Idempotent: YES. Side Effects: NONE. Tenant-aware: NÃO.
     */
    public Resultado avaliar(String respostaJson, @Nullable String schemaVersion, List<Constraint> regras,
                              @Nullable WeekPlanSkeleton skeleton, AthleteZones zonasAtleta,
                              Atleta atleta, LocalDate semanaInicio) {
        return avaliar(respostaJson, schemaVersion, regras, skeleton, zonasAtleta, atleta, semanaInicio, null);
    }

    /**
     * Como {@link #avaliar(String, String, List, WeekPlanSkeleton, AthleteZones, Atleta, LocalDate)}, aplicando
     * também o que o {@code PlanGenerationPersister} faz depois da normalização (migrate-openai-retired-models,
     * revisão cega rodada 2): o treino do dia da prova vira o {@code PROVA} determinístico de
     * {@link ProvaNoPlanoService#construirTreinoProva} e o volume planejado é recalculado pela soma dos treinos.
     */
    public Resultado avaliar(String respostaJson, @Nullable String schemaVersion, List<Constraint> regras,
                              @Nullable WeekPlanSkeleton skeleton, AthleteZones zonasAtleta,
                              Atleta atleta, LocalDate semanaInicio, @Nullable Prova prova) {
        PlanoSemanalLlmDto plano = EvalPlanoJsonParser.parsear(objectMapper, sessionResolver, respostaJson,
                schemaVersion, zonasAtleta);
        plano = normalizar(plano, atleta, atleta != null ? atleta.getId() : null);
        plano = comoPersistido(plano, atleta, semanaInicio, prova);
        List<ViolacaoQualidade> violacoesQualidade = new ArrayList<>(planQualityChecker.check(plano, regras));
        violacoesQualidade.addAll(paceImplicito(plano));
        List<PlannerViolation> violacoesCompliance = skeleton != null
                ? plannerShadowService.checkPreRedistribution(plano, skeleton, atleta, semanaInicio)
                : List.of();
        return new Resultado(violacoesQualidade, violacoesCompliance, plano);
    }

    // shortcut: sem zonas de FC nem teto/piso históricos no contexto (eval não tem banco); corrigir-fc-zona
    // e o teto/piso viram no-op. Suficiente para distância × duração × ritmo, que não dependem deles.
    private PlanoSemanalLlmDto normalizar(PlanoSemanalLlmDto plano, Atleta atleta, java.util.@Nullable UUID atletaId) {
        if (plano.treinosPlanejados() == null || atleta == null) return plano;
        var ctx = new ContextoNormalizacao(atleta, atletaId, null, Map.of(), Map.of());
        List<TreinoPlanejadoLlmDto> treinos = plano.treinosPlanejados().stream().map(t -> {
            try {
                return normalizacao.normalizar(t, ctx);
            } catch (RuntimeException e) {
                return t;
            }
        }).toList();
        return new PlanoSemanalLlmDto(plano.volumePlanejadoKm(), plano.volumeAlvoKm(), plano.tsbInicio(),
                plano.tsbFim(), plano.status(), plano.objetivoSemanal(), treinos, plano.restDays());
    }

    private PlanoSemanalLlmDto comoPersistido(PlanoSemanalLlmDto plano, Atleta atleta, LocalDate semanaInicio,
                                               @Nullable Prova prova) {
        if (plano.treinosPlanejados() == null) return plano;
        List<TreinoPlanejadoLlmDto> treinos = plano.treinosPlanejados();
        if (prova != null && prova.getDataProva() != null && atleta != null
                && !prova.getDataProva().isBefore(semanaInicio) && !prova.getDataProva().isAfter(semanaInicio.plusDays(6))) {
            DiaSemana dia = Utils.converterDayOfWeekParaDiaSemana(prova.getDataProva().getDayOfWeek());
            treinos = new ArrayList<>(treinos.stream().filter(t -> !dia.name().equals(t.diaSemana())).toList());
            treinos.add(provaNoPlano.construirTreinoProva(prova, atleta));
        }
        double volume = treinos.stream().mapToDouble(t -> t.distanciaKm() != null ? t.distanciaKm() : 0.0).sum();
        // garantir-cobertura-sete-dias-plano: o persist completa o dia sem declaração como descanso
        var declarados = new java.util.HashSet<String>();
        treinos.forEach(t -> { if (t.diaSemana() != null) declarados.add(t.diaSemana().trim().toUpperCase()); });
        List<br.com.menthoros.backend.dto.llm.RestDayLlmDto> descansos = new ArrayList<>(plano.restDays());
        descansos.forEach(d -> { if (d.dayOfWeek() != null) declarados.add(d.dayOfWeek().trim().toUpperCase()); });
        for (int i = 0; i < 7; i++) {
            String dia = Utils.converterDayOfWeekParaDiaSemana(semanaInicio.plusDays(i).getDayOfWeek()).name();
            if (!declarados.contains(dia)) {
                descansos.add(new br.com.menthoros.backend.dto.llm.RestDayLlmDto(dia, PlanGenerationPersister.MOTIVO_SEM_PRESCRICAO));
            }
        }
        return new PlanoSemanalLlmDto(Math.round(volume * 100) / 100.0, plano.volumeAlvoKm(), plano.tsbInicio(),
                plano.tsbFim(), plano.status(), plano.objetivoSemanal(), treinos, descansos);
    }

    /** Etapa com duração e ritmo legível: sem distância, ou com pace implícito fora da faixa ±10%. */
    List<ViolacaoQualidade> paceImplicito(PlanoSemanalLlmDto plano) {
        List<ViolacaoQualidade> violacoes = new ArrayList<>();
        if (plano.treinosPlanejados() == null) return violacoes;
        for (TreinoPlanejadoLlmDto t : plano.treinosPlanejados()) {
            if (t.etapas() == null) continue;
            for (EtapaTreinoLlmDto e : t.etapas()) {
                if (e.duracaoMin() == null || e.duracaoMin() <= 0) continue;
                var faixa = paceValidator.parsear(e.ritmoAlvo());
                if (faixa == null) continue;
                if (e.distanciaKm() == null || e.distanciaKm() <= 0) {
                    violacoes.add(new ViolacaoQualidade(ConstraintKey.PACE_IMPLICITO, String.format(
                            "Etapa %s de %s em %s sem distância (%d min, ritmo %s)",
                            e.tipoEtapa(), t.tipoTreino(), t.diaSemana(), e.duracaoMin(), e.ritmoAlvo())));
                    continue;
                }
                double pace = e.duracaoMin() / e.distanciaKm();
                if (pace < faixa.paceMin().doubleValue() * (1 - TOLERANCIA_PACE)
                        || pace > faixa.paceMax().doubleValue() * (1 + TOLERANCIA_PACE)) {
                    violacoes.add(new ViolacaoQualidade(ConstraintKey.PACE_IMPLICITO, String.format(
                            "Etapa %s de %s em %s: %.2f km em %d min = %.2f min/km, fora do ritmo %s",
                            e.tipoEtapa(), t.tipoTreino(), t.diaSemana(), e.distanciaKm(), e.duracaoMin(), pace,
                            e.ritmoAlvo())));
                }
            }
        }
        return violacoes;
    }
}
