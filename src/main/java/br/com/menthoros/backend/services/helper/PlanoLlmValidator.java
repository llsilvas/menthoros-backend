package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.ai.ledger.Violacao;
import br.com.menthoros.backend.domain.planner.SessionSlot;
import br.com.menthoros.backend.domain.planner.WeekPlanSkeleton;
import br.com.menthoros.backend.dto.llm.PlanoSemanalLlmDto;
import br.com.menthoros.backend.dto.llm.TreinoPlanejadoLlmDto;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.enums.DiaSemana;
import br.com.menthoros.backend.enums.TipoTreino;
import br.com.menthoros.backend.exception.LLMException;
import br.com.menthoros.backend.exception.PlanoNaoConformeException;
import br.com.menthoros.backend.services.helper.ZonaTreinoService.ZonaFC;
import br.com.menthoros.backend.services.prompt.PaceHistoricoFormatter;
import br.com.menthoros.backend.util.Utils;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Validação e normalização do <b>plano semanal</b> gerado pela LLM: pré-computa o contexto do
 * atleta (tetos/pisos de pace, zonas de FC), entrega cada treino planejado à
 * {@link NormalizacaoDeTreino} (a receita por família de treino) e fecha com a distribuição de
 * carga semanal. Dois níveis de agregação, dois modules (decisão Q4 do design de
 * pipeline-normalizacao-treino): aqui o plano, lá o treino.
 *
 * <p>Idempotent: YES — mesma entrada sempre produz a mesma saída. Side Effects: NONE (leitura via
 * {@code TreinoHistoricoProvider}/{@code ZonaTreinoService}, já resolvidos pelo chamador; nenhuma
 * escrita). Tenant-aware: NO — recebe o {@code Atleta} já resolvido pelo tenant corrente.</p>
 */
@Slf4j
@Component
public class PlanoLlmValidator {

    private final TreinoHistoricoProvider treinoHistoricoProvider;
    private final PaceHistoricoFormatter paceHistoricoFormatter;
    private final ZonaTreinoService zonaTreinoService;
    private final NormalizacaoDeTreino normalizacaoDeTreino;

    public PlanoLlmValidator(TreinoHistoricoProvider treinoHistoricoProvider,
                             PaceHistoricoFormatter paceHistoricoFormatter,
                             ZonaTreinoService zonaTreinoService,
                             NormalizacaoDeTreino normalizacaoDeTreino) {
        this.treinoHistoricoProvider = treinoHistoricoProvider;
        this.paceHistoricoFormatter = paceHistoricoFormatter;
        this.zonaTreinoService = zonaTreinoService;
        this.normalizacaoDeTreino = normalizacaoDeTreino;
    }

    /** Ponto único de entrada: prepara contexto → normaliza cada treino → valida carga semanal. */
    public PlanoSemanalLlmDto validarENormalizarPlano(PlanoSemanalLlmDto plano, Atleta atleta, UUID atletaId) {
        if (plano == null || plano.treinosPlanejados() == null) {
            throw new LLMException("Plano gerado está nulo ou sem treinos");
        }

        ContextoNormalizacao ctx = contexto(atleta, atletaId);

        // Percorre TODOS os treinos antes de decidir — não aborta no primeiro inválido (achado do
        // pré-mortem de plan-generation-repair-turn: um .stream().map().toList() aqui escondia um
        // 2º treino malformado, e com só 2 tentativas de reparo isso podia esgotar o orçamento sem
        // o modelo nunca ver o 2º problema). NormalizacaoDeTreino continua abortando na 1ª violação
        // DENTRO de um treino (F2.5, não reaberto).
        List<TreinoPlanejadoLlmDto> treinosNormalizados = new ArrayList<>();
        List<Violacao> violacoesEstruturais = new ArrayList<>();
        for (TreinoPlanejadoLlmDto treino : plano.treinosPlanejados()) {
            try {
                treinosNormalizados.add(normalizacaoDeTreino.normalizar(treino, ctx));
            } catch (LLMException e) {
                String dia = treino.diaSemana() != null ? treino.diaSemana() : "DIA_DESCONHECIDO";
                violacoesEstruturais.add(new Violacao("NORMALIZACAO_" + dia, e.getMessage()));
            }
        }
        if (!violacoesEstruturais.isEmpty()) {
            throw new PlanoNaoConformeException(
                    "Plano gerado com " + violacoesEstruturais.size() + " treino(s) inválido(s): "
                            + violacoesEstruturais.stream().map(Violacao::mensagem).collect(Collectors.joining("; ")),
                    violacoesEstruturais);
        }

        validarDistribuicaoCargaSemanal(treinosNormalizados);

        return new PlanoSemanalLlmDto(
                plano.volumePlanejadoKm(),
                plano.volumeAlvoKm(),
                plano.tsbInicio(),
                plano.tsbFim(),
                plano.status(),
                plano.objetivoSemanal(),
                treinosNormalizados
        );
    }

    /**
     * Equivalente v2 (semantic-session-schema) de {@link #validarENormalizarPlano} — substitui,
     * não complementa: o plano já chega aqui **resolvido** pelo {@code SessionResolver} (dentro de
     * `gerar`, fora do escopo de retry), então não há passos de correção aritmética a rodar, só
     * validação estrutural + TSS do slot, dentro de `validar` (protegido pelo retry F3).
     *
     * <p>Percorre TODOS os treinos antes de decidir — mesma garantia de
     * {@link #validarENormalizarPlano} (F3, plan-generation-repair-turn).</p>
     */
    public PlanoSemanalLlmDto validarPlanoV2(PlanoSemanalLlmDto plano, Atleta atleta, UUID atletaId,
                                              @Nullable WeekPlanSkeleton skeleton) {
        if (plano == null || plano.treinosPlanejados() == null) {
            throw new LLMException("Plano gerado está nulo ou sem treinos");
        }

        ContextoNormalizacao ctx = contexto(atleta, atletaId);
        List<Violacao> violacoesEstruturais = new ArrayList<>();
        for (TreinoPlanejadoLlmDto treino : plano.treinosPlanejados()) {
            try {
                normalizacaoDeTreino.validarEstruturaV2(treino, ctx);
                SessionSlot slot = encontrarSlot(skeleton, treino.diaSemana());
                if (slot != null) {
                    normalizacaoDeTreino.validarTssSlotV2(treino, slot, ctx);
                }
            } catch (LLMException e) {
                String dia = treino.diaSemana() != null ? treino.diaSemana() : "DIA_DESCONHECIDO";
                violacoesEstruturais.add(new Violacao("NORMALIZACAO_V2_" + dia, e.getMessage()));
            }
        }
        if (!violacoesEstruturais.isEmpty()) {
            throw new PlanoNaoConformeException(
                    "Plano v2 gerado com " + violacoesEstruturais.size() + " treino(s) inválido(s): "
                            + violacoesEstruturais.stream().map(Violacao::mensagem).collect(Collectors.joining("; ")),
                    violacoesEstruturais);
        }
        return plano;
    }

    /** {@code null} quando não há skeleton (flag `planner-engine.enabled` off) ou o dia não está no skeleton. */
    private @Nullable SessionSlot encontrarSlot(@Nullable WeekPlanSkeleton skeleton, @Nullable String diaSemana) {
        if (skeleton == null || diaSemana == null) {
            return null;
        }
        var dayOfWeek = Utils.converterParaDayOfWeek(DiaSemana.valueOf(diaSemana));
        return skeleton.sessions().stream()
                .filter(slot -> slot.day() == dayOfWeek)
                .findFirst()
                .orElse(null);
    }

    private ContextoNormalizacao contexto(Atleta atleta, UUID atletaId) {
        var historico = treinoHistoricoProvider.prepararContexto(atleta);
        Map<TipoTreino, BigDecimal> tetoPorTipo = paceHistoricoFormatter.calcularTetoPorTipo(historico.treinosUltimas4Semanas());
        Map<TipoTreino, BigDecimal> pisoPorTipo = paceHistoricoFormatter.calcularPisoPorTipo(historico.treinosUltimas4Semanas());

        // Zonas de FC (LTHR) só com dado fisiológico — null desliga corrigir-fc-zona na receita
        List<ZonaFC> zonasFC = (atleta.getFcLimiar() != null || atleta.getFcMaxima() != null)
                ? zonaTreinoService.calcularZonasFC(atleta.getFcMaximaCalculada(), atleta.getFcLimiarCalculada())
                : null;

        return new ContextoNormalizacao(atleta, atletaId, zonasFC, tetoPorTipo, pisoPorTipo);
    }

    /**
     * Treinos "duros" (INTERVALADO, TIRO, TEMPO_RUN, LONGO) em dias consecutivos → WARN. Não rejeita
     * o plano — apenas alerta.
     */
    public void validarDistribuicaoCargaSemanal(List<TreinoPlanejadoLlmDto> treinos) {
        if (treinos == null || treinos.size() < 2) return;

        Set<String> tiposDuros = Set.of("INTERVALADO", "TIRO", "TEMPO_RUN", "LONGO");

        Map<Integer, String> ordemParaTipo = new TreeMap<>();
        for (TreinoPlanejadoLlmDto treino : treinos) {
            if (treino.diaSemana() == null || treino.tipoTreino() == null) continue;
            try {
                DiaSemana dia = DiaSemana.valueOf(treino.diaSemana().toUpperCase());
                ordemParaTipo.put(dia.getOrder(), treino.tipoTreino());
            } catch (IllegalArgumentException ignored) {}
        }

        List<Map.Entry<Integer, String>> entradas = new ArrayList<>(ordemParaTipo.entrySet());
        for (int i = 0; i < entradas.size() - 1; i++) {
            var atual = entradas.get(i);
            var proximo = entradas.get(i + 1);
            if ((proximo.getKey() - atual.getKey()) == 1
                    && tiposDuros.contains(atual.getValue())
                    && tiposDuros.contains(proximo.getValue())) {
                DiaSemana diaAtual   = diaPorOrdem(atual.getKey());
                DiaSemana diaProximo = diaPorOrdem(proximo.getKey());
                log.warn("CARGA SEMANAL: treinos duros em dias consecutivos — {} ({}) e {} ({})",
                        diaAtual   != null ? diaAtual.getLabel()   : atual.getKey(),   atual.getValue(),
                        diaProximo != null ? diaProximo.getLabel() : proximo.getKey(), proximo.getValue());
            }
        }
    }

    private DiaSemana diaPorOrdem(int order) {
        for (DiaSemana d : DiaSemana.values()) {
            if (d.getOrder() == order) return d;
        }
        return null;
    }
}
