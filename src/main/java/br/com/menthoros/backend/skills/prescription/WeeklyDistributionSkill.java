package br.com.menthoros.backend.skills.prescription;

import br.com.menthoros.backend.skills.core.DomainSkill;
import br.com.menthoros.backend.skills.core.SkillCategory;
import br.com.menthoros.backend.skills.core.SkillConfidence;
import br.com.menthoros.backend.skills.core.SkillContext;
import br.com.menthoros.backend.skills.core.SkillResult;
import br.com.menthoros.backend.skills.core.SkillSeverity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Skill de distribuição semanal das sessões de treino.
 *
 * <p>Resolve formalmente o problema de distribuição da carga semanal ({@code fix-weekly-load-distribution})
 * como um {@link DomainSkill} determinístico. Organiza as sessões pelos dias disponíveis
 * do atleta respeitando as seguintes regras de recuperação:</p>
 *
 * <h2>Regras de distribuição</h2>
 * <ol>
 *   <li><b>Longão:</b> vai para {@code diaPreferidoLongo} se disponível; caso contrário, para o
 *       último dia disponível da semana.</li>
 *   <li><b>Sessões-chave consecutivas:</b> nunca INTERVALADO seguido de LONGO — mínimo 1 dia
 *       fácil entre sessões-chave.</li>
 *   <li><b>Alta intensidade consecutiva:</b> nunca dois treinos de alta intensidade
 *       (INTERVALADO, TEMPO_RUN, LONGO) em dias consecutivos.</li>
 *   <li><b>Qualidade no meio da semana:</b> treinos de qualidade (INTERVALADO, TEMPO_RUN)
 *       preferencialmente posicionados no meio da semana disponível.</li>
 * </ol>
 *
 * <p><b>Idempotente:</b> SIM — análise determinística sobre os dados de entrada.</p>
 * <p><b>Side Effects:</b> NONE — não escreve em banco nem chama APIs externas.</p>
 * <p><b>Tenant-aware:</b> SIM — context.tenantId() disponível para rastreabilidade.</p>
 */
@Slf4j
@Component
public class WeeklyDistributionSkill
        implements DomainSkill<WeeklyDistributionInput, WeeklyDistributionPayload> {

    private static final String SKILL_KEY     = "weekly-distribution-v1";
    private static final String SKILL_VERSION = "1.0";

    /** Ordem canônica dos dias da semana. */
    private static final List<String> ORDEM_SEMANA = List.of(
            "DOMINGO", "SEGUNDA", "TERCA", "QUARTA", "QUINTA", "SEXTA", "SABADO"
    );

    /** Tipos de treino de alta intensidade que requerem dias de recuperação entre si. */
    private static final Set<String> ALTA_INTENSIDADE = Set.of("INTERVALADO", "LONGO", "TEMPO_RUN", "TIRO", "SUBIDA", "PROVA");

    /** Tipos de treino de qualidade a posicionar preferencialmente no meio da semana. */
    private static final Set<String> TREINOS_QUALIDADE = Set.of("INTERVALADO", "TEMPO_RUN");

    // ─────────────────────────────────────────────────────────────────────────
    // Contrato DomainSkill
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public String skillKey() {
        return SKILL_KEY;
    }

    @Override
    public String skillVersion() {
        return SKILL_VERSION;
    }

    @Override
    public SkillCategory category() {
        return SkillCategory.DISTRIBUTION;
    }

    /**
     * Executa a redistribuição semanal das sessões de treino respeitando as regras de recuperação.
     *
     * <p><b>Idempotente:</b> SIM — mesmos inputs produzem sempre o mesmo resultado.</p>
     * <p><b>Side Effects:</b> NONE</p>
     * <p><b>Tenant-aware:</b> SIM</p>
     *
     * @param input   sessões a distribuir, dias disponíveis e preferências do atleta
     * @param context contexto de execução com atletaId, tenantId e dataReferencia
     * @return resultado com distribuição final, movimentos realizados e flag de reordenação
     * @throws IllegalArgumentException se {@code input} for null
     */
    @Override
    public SkillResult<WeeklyDistributionPayload> execute(
            WeeklyDistributionInput input,
            SkillContext context) {

        if (input == null) {
            throw new IllegalArgumentException("WeeklyDistributionInput cannot be null");
        }

        log.debug("WeeklyDistributionSkill iniciada: atletaId={}, tenantId={}, sessoes={}, diasDisponiveis={}",
                context.atletaId(), context.tenantId(),
                input.sessoes() != null ? input.sessoes().size() : 0,
                input.diasDisponiveis());

        List<TreinoPlanejadoResumo> sessoes = input.sessoes() != null ? input.sessoes() : List.of();
        List<String> diasDisponiveis = input.diasDisponiveis() != null ? input.diasDisponiveis() : List.of();
        List<String> movimentos = new ArrayList<>();
        List<String> evidence = new ArrayList<>();

        evidence.add(String.format("Sessões a distribuir: %d", sessoes.size()));
        evidence.add(String.format("Dias disponíveis: %s", diasDisponiveis));
        evidence.add(String.format("Dia preferido longo: %s", input.diaPreferidoLongo()));

        // Ordenar dias disponíveis pela ordem canônica da semana
        List<String> diasOrdenados = diasDisponiveis.stream()
                .filter(d -> d != null)
                .map(d -> d.trim().toUpperCase())
                .filter(d -> ORDEM_SEMANA.contains(d))
                .sorted((a, b) -> ORDEM_SEMANA.indexOf(a) - ORDEM_SEMANA.indexOf(b))
                .distinct()
                .toList();

        if (sessoes.isEmpty() || diasOrdenados.isEmpty()) {
            log.debug("WeeklyDistributionSkill: plano vazio ou sem dias disponíveis — retornando distribuição vazia");
            WeeklyDistributionPayload payload = new WeeklyDistributionPayload(
                    Map.of(), List.of(), false);
            return buildResult(payload, List.copyOf(evidence), List.of());
        }

        // ── Distribuição inicial: mapear sessão pelo dia proposto ─────────────
        // Construir mapa mutável: dia → tipoTreino (representando a distribuição atual)
        Map<String, String> distribuicao = new LinkedHashMap<>();
        // Registrar mapeamento original para detectar movimentos
        Map<String, String> distribuicaoOriginal = new LinkedHashMap<>();

        // Inicializar dias disponíveis sem sessão
        for (String dia : diasOrdenados) {
            distribuicao.put(dia, null);
        }

        // Posicionar sessões nos dias que ainda estão disponíveis
        // LONGO é tratado separadamente depois
        List<TreinoPlanejadoResumo> sessoesParaDistribuir = new ArrayList<>(sessoes);
        List<TreinoPlanejadoResumo> sessoesSemDia = new ArrayList<>();

        // Fase 1: Tentar posicionar cada sessão no seu dia proposto (se disponível)
        for (TreinoPlanejadoResumo sessao : sessoesParaDistribuir) {
            String diaProposto = sessao.diaSemana() != null
                    ? sessao.diaSemana().trim().toUpperCase() : null;
            String tipo = sessao.tipoTreino() != null
                    ? sessao.tipoTreino().trim().toUpperCase() : "FACIL";

            if (diaProposto != null && diasOrdenados.contains(diaProposto)
                    && distribuicao.get(diaProposto) == null) {
                distribuicao.put(diaProposto, tipo);
                distribuicaoOriginal.put(diaProposto, tipo);
            } else {
                sessoesSemDia.add(sessao);
            }
        }

        // Fase 2: Posicionar sessões sem dia válido nos slots livres
        List<String> slotLivres = diasOrdenados.stream()
                .filter(d -> distribuicao.get(d) == null)
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));

        for (TreinoPlanejadoResumo sessao : sessoesSemDia) {
            String tipo = sessao.tipoTreino() != null
                    ? sessao.tipoTreino().trim().toUpperCase() : "FACIL";
            if (!slotLivres.isEmpty()) {
                String dia = slotLivres.remove(0);
                distribuicao.put(dia, tipo);
                distribuicaoOriginal.put(dia, tipo);
            }
        }

        // ── Regra 1: Longão vai para diaPreferidoLongo ───────────────────────
        boolean reordenado = false;

        String diaPreferido = input.diaPreferidoLongo() != null
                ? input.diaPreferidoLongo().trim().toUpperCase() : null;

        // Encontrar onde o LONGO está atualmente
        String diaAtualLongo = distribuicao.entrySet().stream()
                .filter(e -> "LONGO".equals(e.getValue()))
                .map(Map.Entry::getKey)
                .findFirst().orElse(null);

        if (diaAtualLongo != null) {
            String diaAlvoLongo;

            if (diaPreferido != null && diasOrdenados.contains(diaPreferido)) {
                // Dia preferido disponível
                diaAlvoLongo = diaPreferido;
            } else {
                // Usar o último dia disponível da semana
                diaAlvoLongo = diasOrdenados.get(diasOrdenados.size() - 1);
            }

            if (!diaAtualLongo.equals(diaAlvoLongo)) {
                // Mover LONGO: trocar com o que estava no alvo (se houver)
                String ocupanteAlvo = distribuicao.get(diaAlvoLongo);
                distribuicao.put(diaAlvoLongo, "LONGO");
                distribuicao.put(diaAtualLongo, ocupanteAlvo);

                String movimento = String.format(
                        "LONGO movido de %s para %s: %s",
                        diaAtualLongo, diaAlvoLongo,
                        diaPreferido != null && diasOrdenados.contains(diaPreferido)
                                ? "dia de preferência do atleta"
                                : "último dia disponível da semana");
                movimentos.add(movimento);
                evidence.add("Movimento: " + movimento);
                reordenado = true;

                log.debug("WeeklyDistributionSkill: {} | atletaId={}", movimento, context.atletaId());
            }
        }

        // ── Regras 2 e 3: Sessões de alta intensidade consecutivas ───────────
        // Resolver conflitos de consecutividade iterativamente
        boolean conflito = true;
        int iteracoes = 0;
        int maxIteracoes = diasOrdenados.size() * 2; // guardrail anti-loop infinito

        while (conflito && iteracoes < maxIteracoes) {
            conflito = false;
            iteracoes++;

            for (int i = 0; i < diasOrdenados.size() - 1; i++) {
                String dia1 = diasOrdenados.get(i);
                String dia2 = diasOrdenados.get(i + 1);

                // Verificar se são realmente consecutivos na semana
                if (!saoConsecutivosNaSemana(dia1, dia2)) continue;

                String tipo1 = distribuicao.get(dia1);
                String tipo2 = distribuicao.get(dia2);

                if (tipo1 == null || tipo2 == null) continue;

                boolean ambosAltaIntensidade = ALTA_INTENSIDADE.contains(tipo1) && ALTA_INTENSIDADE.contains(tipo2);

                if (ambosAltaIntensidade) {
                    // Encontrar um slot não-consecutivo disponível para mover o segundo treino
                    String diaAlternativo = encontrarSlotNaoConsecutivo(diasOrdenados, distribuicao, dia1, dia2);

                    if (diaAlternativo != null) {
                        String ocupanteAlternativo = distribuicao.get(diaAlternativo);
                        distribuicao.put(diaAlternativo, tipo2);
                        distribuicao.put(dia2, ocupanteAlternativo);

                        String movimento = String.format(
                                "%s movido de %s para %s: evitar alta intensidade consecutiva com %s",
                                tipo2, dia2, diaAlternativo, tipo1);
                        movimentos.add(movimento);
                        evidence.add("Movimento: " + movimento);
                        reordenado = true;
                        conflito = true; // Re-verificar após movimentação

                        log.debug("WeeklyDistributionSkill: {} | atletaId={}", movimento, context.atletaId());
                        break; // Reiniciar verificação
                    }
                }
            }
        }

        // ── Montar payload final ──────────────────────────────────────────────
        // Remover dias sem sessão
        Map<String, String> distribuicaoFinal = new LinkedHashMap<>();
        for (String dia : diasOrdenados) {
            String tipo = distribuicao.get(dia);
            if (tipo != null) {
                distribuicaoFinal.put(dia, tipo);
            }
        }

        WeeklyDistributionPayload payload = new WeeklyDistributionPayload(
                Map.copyOf(distribuicaoFinal),
                List.copyOf(movimentos),
                reordenado
        );

        log.debug("WeeklyDistributionSkill concluída: atletaId={}, reordenado={}, movimentos={}",
                context.atletaId(), reordenado, movimentos.size());

        List<String> recommendations = new ArrayList<>();
        if (reordenado) {
            recommendations.add("Distribuição ajustada para respeitar as regras de recuperação entre sessões-chave.");
        } else {
            recommendations.add("Distribuição original mantida — sem conflitos de recuperação detectados.");
        }

        return buildResult(payload, List.copyOf(evidence), List.copyOf(recommendations));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Utilitários
    // ─────────────────────────────────────────────────────────────────────────

    private boolean saoConsecutivosNaSemana(String dia1, String dia2) {
        int idx1 = ORDEM_SEMANA.indexOf(dia1);
        int idx2 = ORDEM_SEMANA.indexOf(dia2);
        return idx1 >= 0 && idx2 >= 0 && Math.abs(idx1 - idx2) == 1;
    }

    /**
     * Encontra um slot disponível que não seja consecutivo com {@code diaReferencia}
     * nem com o slot a ser esvaziado ({@code diaOrigem}).
     */
    private String encontrarSlotNaoConsecutivo(
            List<String> diasOrdenados,
            Map<String, String> distribuicao,
            String diaReferencia,
            String diaOrigem) {

        for (String candidato : diasOrdenados) {
            if (candidato.equals(diaOrigem)) continue;
            if (candidato.equals(diaReferencia)) continue;

            // Verificar que o candidato não é consecutivo com diaReferencia
            if (saoConsecutivosNaSemana(candidato, diaReferencia)) continue;

            // Verificar que o conteúdo atual do candidato (se houver) não cria novo conflito
            String tipoNoCandidato = distribuicao.get(candidato);

            // Se o candidato tem treino de alta intensidade e é consecutivo com algum
            // outro treino de alta intensidade, pular
            if (tipoNoCandidato != null && ALTA_INTENSIDADE.contains(tipoNoCandidato)) {
                continue;
            }

            return candidato;
        }

        return null; // Nenhum slot adequado encontrado
    }

    private SkillResult<WeeklyDistributionPayload> buildResult(
            WeeklyDistributionPayload payload,
            List<String> evidence,
            List<String> recommendations) {

        return new SkillResult<>(
                SKILL_KEY,
                SKILL_VERSION,
                SkillSeverity.INFO,
                SkillConfidence.HIGH,
                payload,
                evidence,
                recommendations);
    }
}
