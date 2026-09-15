package br.com.menthoros.backend.services.helper;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Constrói fixtures de auditoria (plan-generation-eval-set, fatia 1) a partir de gerações reais do
 * ledger — amostragem estratificada + redação de PII. Não é um serviço de runtime: roda uma vez,
 * manualmente, contra o ledger real (task 1.3), fora do fluxo de produção e fora do CI (não há
 * endpoint — leitura analítica do ledger é por design "sem endpoint", ver
 * {@code LlmCallRepository}).
 *
 * <p>Deliberadamente sem constraints/skeleton na fixture: nenhum dos dois é recuperável
 * retroativamente de uma geração já ocorrida (ver design.md "Correção de escopo", rodada 2 de DoR)
 * — só o que o ledger de fato retém.
 *
 * <p>Idempotent: YES — leitura pura sobre a amostra recebida, sem mutação de estado.
 * Side Effects: NONE (a gravação em disco das fixtures é responsabilidade do chamador da task 1.3).
 * Tenant-aware: NÃO — opera sobre uma amostra já resolvida pelo chamador.
 */
@Component
public class EvalFixtureExtractor {

    private final EvalPiiRedactor piiRedactor;

    public EvalFixtureExtractor(EvalPiiRedactor piiRedactor) {
        this.piiRedactor = piiRedactor;
    }

    /**
     * Um caso candidato a virar fixture de auditoria, já resolvido pelo chamador (join
     * {@code tb_llm_call}/{@code tb_plano_semanal} feito fora desta classe — ver Javadoc da classe).
     */
    public record CandidatoAmostra(
            UUID generationRequestId,
            String respostaHistoricaJson,
            @Nullable String schemaVersion,
            @Nullable String promptVersion,
            String planoFinalPersistidoJson,
            @Nullable Integer fcMaxima,
            @Nullable Integer fcLimiar,
            @Nullable BigDecimal paceLimiar,
            String arquetipo,
            boolean coldStart,
            String veredito,
            EvalPiiRedactor.PiiAlvo piiAlvo
    ) {
    }

    /** Fixture de auditoria final, já redigida — pronta para serialização em disco (task 1.3). */
    public record FixtureAuditoria(
            UUID generationRequestId,
            String respostaHistoricaJson,
            @Nullable String schemaVersion,
            @Nullable String promptVersion,
            String planoFinalPersistidoJson,
            AthleteZones zonasAtleta,
            String arquetipo,
            boolean coldStart,
            String veredito
    ) {
    }

    /**
     * Estratifica por (arquétipo × cold-start × veredito) e redige PII, até {@code alvoTotal}
     * fixtures — distribuindo a amostra o mais uniformemente possível entre os grupos observados
     * (round-robin por grupo, preservando a ordem de chegada dentro de cada um).
     *
     * <p>Idempotent: YES. Side Effects: NONE. Tenant-aware: NÃO.
     */
    public List<FixtureAuditoria> estratificarERedigir(List<CandidatoAmostra> candidatos, int alvoTotal) {
        if (candidatos == null || candidatos.isEmpty() || alvoTotal <= 0) {
            return List.of();
        }

        Map<String, List<CandidatoAmostra>> porGrupo = new LinkedHashMap<>();
        for (CandidatoAmostra c : candidatos) {
            porGrupo.computeIfAbsent(chaveGrupo(c), k -> new ArrayList<>()).add(c);
        }

        List<FixtureAuditoria> selecionadas = new ArrayList<>();
        List<List<CandidatoAmostra>> filas = new ArrayList<>(porGrupo.values());
        int indiceFila = 0;
        while (selecionadas.size() < alvoTotal && filas.stream().anyMatch(f -> !f.isEmpty())) {
            List<CandidatoAmostra> fila = filas.get(indiceFila % filas.size());
            if (!fila.isEmpty()) {
                selecionadas.add(redigir(fila.remove(0)));
            }
            indiceFila++;
        }
        return selecionadas;
    }

    private String chaveGrupo(CandidatoAmostra c) {
        return c.arquetipo() + "|" + c.coldStart() + "|" + c.veredito();
    }

    private FixtureAuditoria redigir(CandidatoAmostra c) {
        String respostaRedigida = piiRedactor.redigir(c.respostaHistoricaJson(), c.piiAlvo());
        String planoRedigido = piiRedactor.redigir(c.planoFinalPersistidoJson(), c.piiAlvo());
        return new FixtureAuditoria(
                c.generationRequestId(), respostaRedigida, c.schemaVersion(), c.promptVersion(),
                planoRedigido, new AthleteZones(c.fcMaxima(), c.fcLimiar(), c.paceLimiar()),
                c.arquetipo(), c.coldStart(), c.veredito());
    }
}
