package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.config.core.ReadinessProperties;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.PlanoMetaDados;
import br.com.menthoros.backend.entity.TreinoRealizado;
import br.com.menthoros.backend.enums.CategoriaIntervalado;
import br.com.menthoros.backend.enums.FasePeriodizacao;
import br.com.menthoros.backend.enums.NivelExperiencia;
import br.com.menthoros.backend.enums.NivelProntidao;
import br.com.menthoros.backend.enums.TipoTreino;
import br.com.menthoros.backend.skills.core.SkillContext;
import br.com.menthoros.backend.skills.core.SkillResult;
import br.com.menthoros.backend.skills.eligibility.IntervaladoElegibilidadeInput;
import br.com.menthoros.backend.skills.eligibility.IntervaladoElegibilidadePayload;
import br.com.menthoros.backend.skills.eligibility.IntervaladoElegibilidadeSkill;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;

/**
 * Motor de elegibilidade determinístico para treinos INTERVALADOS.
 *
 * <p>Avalia os dados reais do atleta em 5 portões sequenciais antes da chamada ao LLM,
 * produzindo uma instrução mandatória que é injetada no prompt pelo
 * {@code PlanoTreinoPromptBuilder}. O LLM não pode contrariar essa decisão.</p>
 *
 * <p>Não possui dependências de repositório — opera apenas sobre dados já carregados
 * pelo {@code TreinoHistoricoProvider}, seguindo o princípio de separação de concerns
 * do projeto.</p>
 *
 * <h2>Portões de decisão</h2>
 * <ol>
 *   <li><b>Contraindicações absolutas</b> — lesão ativa, fadiga extrema (TSB &lt; -30),
 *       alerta de dias consecutivos</li>
 *   <li><b>Prontidão fisiológica por nível</b> — TSB abaixo do limiar do nível,
 *       RPE médio ≥ 7.5 nos últimos 7 dias</li>
 *   <li><b>Recuperação desde último intensivo</b> — janela mínima em horas por nível
 *       após INTERVALADO ou TIRO</li>
 *   <li><b>Base aeróbica mínima</b> — CTL mínimo por nível</li>
 *   <li><b>Seleção de categoria</b> — A–E por fase de periodização e histórico</li>
 * </ol>
 */
@Slf4j
@Component
public class IntervaladoElegibilidadeService {

    /**
     * Skill de elegibilidade formalizada — invocada em paralelo para logging e rastreabilidade.
     * A lógica principal do serviço permanece inalterada (delegação sem remoção — D6).
     */
    private final IntervaladoElegibilidadeSkill eligibilidadeSkill;
    private final ReadinessProperties readinessProperties;

    public IntervaladoElegibilidadeService(IntervaladoElegibilidadeSkill eligibilidadeSkill,
                                            ReadinessProperties readinessProperties) {
        this.eligibilidadeSkill = eligibilidadeSkill;
        this.readinessProperties = readinessProperties;
    }

    // ── Portão 2: limiares de TSB por nível ──────────────────────────────────
    private static final Map<NivelExperiencia, Double> TSB_THRESHOLD = Map.of(
            NivelExperiencia.INICIANTE,     -10.0,
            NivelExperiencia.INTERMEDIARIO, -15.0,
            NivelExperiencia.AVANCADO,      -20.0,
            NivelExperiencia.ELITE,         -25.0
    );

    // ── Portão 3: horas mínimas de recuperação por nível ─────────────────────
    private static final Map<NivelExperiencia, Long> MIN_HORAS_RECUPERACAO = Map.of(
            NivelExperiencia.INICIANTE,     72L,
            NivelExperiencia.INTERMEDIARIO, 60L,
            NivelExperiencia.AVANCADO,      48L,
            NivelExperiencia.ELITE,         48L
    );

    // ── Portão 4: CTL mínimo por nível ───────────────────────────────────────
    private static final Map<NivelExperiencia, Double> CTL_MINIMO = Map.of(
            NivelExperiencia.INICIANTE,     15.0,
            NivelExperiencia.INTERMEDIARIO, 25.0,
            NivelExperiencia.AVANCADO,      40.0,
            NivelExperiencia.ELITE,         55.0
    );

    private static final double RPE_THRESHOLD      = 7.5;
    private static final double TSB_ABSOLUTE_BLOCK = -30.0;

    /**
     * Ponto de entrada. Avalia os 5 portões sequencialmente e retorna a recomendação.
     *
     * @param atleta                   entidade do atleta ({@code temLesao}, {@code nivelExperiencia})
     * @param metaDados                metadados de treino ({@code tsbProntidaoAtual} — TSB pré-treino usado
     *                                 nos gates fisiológicos, {@code ctlAtual},
     *                                 {@code alertaDiasConsecutivos}, {@code fasePeriodizacao})
     * @param treinosUltimas4Semanas   treinos pré-carregados via {@code ContextoTreino}
     * @param dataReferencia           data de referência da semana (ex: {@code ctx.dataReferencia()})
     * @return {@link RecomendacaoIntervalado} — uma das 3 variantes seladas
     */
    public RecomendacaoIntervalado avaliar(
            Atleta atleta,
            PlanoMetaDados metaDados,
            List<TreinoRealizado> treinosUltimas4Semanas,
            LocalDate dataReferencia) {
        return avaliar(atleta, metaDados, treinosUltimas4Semanas, dataReferencia, null);
    }

    /**
     * Sobrecarga que adiciona o 6º portão — readiness subjetiva diária (checkin de sono, humor,
     * dores, energia, estresse). {@code DESCANSAR} bloqueia intervalado de forma absoluta, mesmo
     * que os 5 portões fisiológicos permitam. {@code CAUTELOSO} permite, mas atenua a instrução
     * final com recomendação de redução de volume (20–30%). Sem checkin do dia ({@code null}),
     * opera exatamente como a sobrecarga de 4 parâmetros (fallback documentado — spec Scenario
     * "Sem checkin do dia"). A flag {@code app.readiness.enabled=false} desliga o portão inteiro
     * sem remover dados (suporte a rollback).
     *
     * @param nivelProntidaoHoje nível de prontidão do dia de referência, ou {@code null} se não houver checkin
     */
    public RecomendacaoIntervalado avaliar(
            Atleta atleta,
            PlanoMetaDados metaDados,
            List<TreinoRealizado> treinosUltimas4Semanas,
            LocalDate dataReferencia,
            NivelProntidao nivelProntidaoHoje) {

        boolean readinessAtivo = readinessProperties.isEnabled();

        // ── PORTÃO READINESS (6º portão): bloqueio absoluto por DESCANSAR ────
        if (!readinessAtivo) {
            log.debug("IntervaladoElegibilidade: readiness desabilitado via flag app.readiness.enabled — motor opera sem o portao");
        } else if (nivelProntidaoHoje == null) {
            log.warn("IntervaladoElegibilidade: sem checkin de prontidao para {} — motor opera sem readiness", dataReferencia);
        } else if (nivelProntidaoHoje == NivelProntidao.DESCANSAR) {
            return criarSubstituido(
                    TipoTreino.REGENERATIVO,
                    "Readiness do dia = DESCANSAR",
                    "INTERVALADO PROIBIDO — readiness subjetiva do dia classificada como DESCANSAR. "
                    + "Prescrever REGENERATIVO obrigatoriamente. Priorizar recuperacao esta sessao.");
        }

        RecomendacaoIntervalado decisao = avaliarPortoesFisiologicos(
                atleta, metaDados, treinosUltimas4Semanas, dataReferencia);

        if (readinessAtivo && nivelProntidaoHoje == NivelProntidao.CAUTELOSO) {
            decisao = atenuarPorReadinessCauteloso(decisao);
        }

        return decisao;
    }

    /**
     * Portões 1–5 (fisiológicos) — lógica original, inalterada, apenas extraída para método
     * próprio de forma que o portão de readiness (6º) possa envolvê-la.
     */
    private RecomendacaoIntervalado avaliarPortoesFisiologicos(
            Atleta atleta,
            PlanoMetaDados metaDados,
            List<TreinoRealizado> treinosUltimas4Semanas,
            LocalDate dataReferencia) {

        NivelExperiencia nivel = atleta != null && atleta.getNivelExperiencia() != null
                ? atleta.getNivelExperiencia()
                : NivelExperiencia.INTERMEDIARIO;

        // Usa tsbProntidaoAtual (pré-treino) para decisões fisiológicas.
        // Este valor representa o estado do atleta ANTES da carga do dia —
        // é a métrica correta para decidir se o atleta está apto a treinar.
        Double tsb      = metaDados != null ? metaDados.getTsbProntidaoAtual() : null;
        Double ctl      = metaDados != null ? metaDados.getCtlAtual()  : null;
        Boolean alertaDias = metaDados != null ? metaDados.getAlertaDiasConsecutivos() : null;
        FasePeriodizacao fase = metaDados != null ? metaDados.getFasePeriodizacao() : null;

        // ── DELEGAÇÃO PARALELA para IntervaladoElegibilidadeSkill (D6) ───────
        // A skill é invocada em paralelo à lógica legada para rastreabilidade e transição
        // gradual. NÃO altera o resultado do método — apenas loga o resultado da skill.
        try {
            IntervaladoElegibilidadeInput skillInput = buildSkillInput(
                    atleta, metaDados, treinosUltimas4Semanas, dataReferencia);
            SkillContext skillContext = SkillContext.of(
                    UUID.randomUUID(),  // atletaId anônimo — Atleta não expõe UUID aqui
                    UUID.randomUUID(),  // tenantId anônimo — não disponível neste contexto legado
                    dataReferencia);
            SkillResult<IntervaladoElegibilidadePayload> skillResult =
                    eligibilidadeSkill.execute(skillInput, skillContext);
            log.debug("IntervaladoElegibilidadeSkill (delegação paralela): severity={} elegivel={} — {}",
                    skillResult.severity(),
                    skillResult.payload().elegivel(),
                    skillResult.payload().motivo());
        } catch (Exception ex) {
            // Nunca propagar exceção da skill — a lógica legada é a fonte de verdade
            log.warn("IntervaladoElegibilidadeSkill (delegação paralela) falhou — ignorando: {}",
                    ex.getMessage());
        }
        // ── FIM DA DELEGAÇÃO PARALELA ─────────────────────────────────────────

        // ── PORTÃO 1: Contraindicações absolutas ─────────────────────────────

        if (atleta != null && Boolean.TRUE.equals(atleta.getTemLesao())) {
            String desc = sanitizarDescricaoLesao(atleta.getDescricaoLesao());
            return criarSubstituido(
                    TipoTreino.REGENERATIVO,
                    "Lesao ativa: " + desc,
                    "INTERVALADO PROIBIDO — atleta com lesao ativa (" + desc + "). "
                    + "Prescrever apenas REGENERATIVO ou CONTINUO leve em Z1-Z2.");
        }

        if (tsb != null && tsb < TSB_ABSOLUTE_BLOCK) {
            String tsbStr = String.format("%.1f", tsb);
            return criarSubstituido(
                    TipoTreino.REGENERATIVO,
                    "Fadiga extrema TSB=" + tsbStr,
                    "INTERVALADO PROIBIDO — TSB=" + tsbStr + " (abaixo de -30). "
                    + "Prescrever REGENERATIVO obrigatoriamente. Sem treinos intensos esta semana.");
        }

        if (Boolean.TRUE.equals(alertaDias)) {
            int dias = metaDados.getDiasConsecutivosTreino() != null
                    ? metaDados.getDiasConsecutivosTreino() : 0;
            return criarSubstituido(
                    TipoTreino.CONTINUO,
                    "Dias consecutivos excessivos (" + dias + " dias sem descanso)",
                    "INTERVALADO PROIBIDO — alerta de dias consecutivos ativo (" + dias + " dias). "
                    + "Incluir descanso ou CONTINUO leve em Z2 nesta semana.");
        }

        // ── PORTÃO 2: Prontidão fisiológica por nível ────────────────────────

        double tsbLimiar = TSB_THRESHOLD.getOrDefault(nivel, -15.0);
        if (tsb != null && tsb < tsbLimiar) {
            String tsbStr = String.format("%.1f", tsb);
            return criarDegradado(
                    CategoriaIntervalado.D,
                    "TSB=" + tsbStr + " abaixo do limiar para " + nivel.getLabel() + " (limiar: " + tsbLimiar + ")",
                    "INTERVALADO DEGRADADO para Categoria D — TSB=" + tsbStr
                    + " abaixo do limiar de " + tsbLimiar + " para atleta " + nivel.getLabel() + ". "
                    + CategoriaIntervalado.D.getInstrucaoPadrao());
        }

        double rpeMedia = calcularRpeMedia7Dias(treinosUltimas4Semanas, dataReferencia);
        if (rpeMedia >= RPE_THRESHOLD) {
            String rpeStr = String.format("%.1f", rpeMedia);
            return criarDegradado(
                    CategoriaIntervalado.C,
                    "RPE medio nos ultimos 7 dias=" + rpeStr + " (limiar: 7.5)",
                    "INTERVALADO DEGRADADO para Categoria C — RPE medio=" + rpeStr
                    + " elevado. Reducao de intensidade recomendada. "
                    + CategoriaIntervalado.C.getInstrucaoPadrao());
        }

        // ── PORTÃO 3: Recuperação desde último treino intensivo ──────────────

        long minHoras = MIN_HORAS_RECUPERACAO.getOrDefault(nivel, 60L);
        Optional<TreinoRealizado> ultimoIntensivo = encontrarUltimoTreinoIntensivo(
                treinosUltimas4Semanas, dataReferencia);

        if (ultimoIntensivo.isPresent()) {
            long horasDesde = ChronoUnit.HOURS.between(
                    ultimoIntensivo.get().getDataTreino().atStartOfDay(),
                    dataReferencia.atStartOfDay());
            if (horasDesde < minHoras) {
                return criarDegradado(
                        CategoriaIntervalado.D,
                        "Apenas " + horasDesde + "h desde ultimo intensivo (minimo: "
                        + minHoras + "h para " + nivel.getLabel() + ")",
                        "INTERVALADO DEGRADADO para Categoria D — recuperacao insuficiente: "
                        + horasDesde + "h desde ultimo treino intensivo (minimo " + minHoras
                        + "h para " + nivel.getLabel() + "). "
                        + CategoriaIntervalado.D.getInstrucaoPadrao());
            }
        }

        // ── PORTÃO 4: Base aeróbica mínima ───────────────────────────────────

        double ctlMinimo = CTL_MINIMO.getOrDefault(nivel, 25.0);
        if (ctl != null && ctl < ctlMinimo) {
            String ctlStr = String.format("%.1f", ctl);
            return criarDegradado(
                    CategoriaIntervalado.D,
                    "CTL=" + ctlStr + " abaixo do minimo (" + ctlMinimo + ") para " + nivel.getLabel(),
                    "INTERVALADO DEGRADADO para Categoria D — base aerobica insuficiente: CTL="
                    + ctlStr + " (minimo " + ctlMinimo + " para " + nivel.getLabel() + "). "
                    + CategoriaIntervalado.D.getInstrucaoPadrao());
        }

        // ── PORTÃO 5: Seleção de categoria (atleta elegível) ─────────────────

        CategoriaIntervalado categoria = selecionarCategoria(fase, treinosUltimas4Semanas, dataReferencia);
        String faseLabel = fase != null ? fase.getLabel() : "Desenvolvimento Geral";
        String motivo = "Todos os portoes passados. Fase: " + faseLabel
                + ". Categoria selecionada por periodizacao e historico.";
        String instrucao = "INTERVALADO AUTORIZADO — Categoria " + categoria.name()
                + " (" + categoria.getNome() + "). " + categoria.getInstrucaoPadrao();

        log.info("IntervaladoElegibilidade: elegivel para Categoria {} | TSB={} CTL={} Fase={}",
                categoria, tsb, ctl, fase);

        return new RecomendacaoIntervalado.Elegivel(categoria, motivo, instrucao);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Delegação para skill — builder de input
    // ─────────────────────────────────────────────────────────────────────────

    private IntervaladoElegibilidadeInput buildSkillInput(
            Atleta atleta,
            PlanoMetaDados metaDados,
            List<TreinoRealizado> treinos,
            LocalDate dataReferencia) {

        double tsb        = metaDados != null && metaDados.getTsbProntidaoAtual() != null
                ? metaDados.getTsbProntidaoAtual() : 0.0;
        double ctlVal     = metaDados != null && metaDados.getCtlAtual() != null
                ? metaDados.getCtlAtual() : 0.0;
        double rampRate   = metaDados != null && metaDados.getRampRateAtual() != null
                ? metaDados.getRampRateAtual() : 0.0;
        boolean temLesao  = atleta != null && Boolean.TRUE.equals(atleta.getTemLesao());
        String faseStr    = metaDados != null && metaDados.getFasePeriodizacao() != null
                ? metaDados.getFasePeriodizacao().name() : "";

        // Calcula dias desde o último treino intensivo para a skill
        int diasDesdeUltimo = calcularDiasDesdeUltimoIntervalado(treinos, dataReferencia);

        return new IntervaladoElegibilidadeInput(tsb, ctlVal, rampRate, temLesao, diasDesdeUltimo, faseStr);
    }

    private int calcularDiasDesdeUltimoIntervalado(
            List<TreinoRealizado> treinos, LocalDate dataReferencia) {
        if (treinos == null || treinos.isEmpty()) return Integer.MAX_VALUE;
        OptionalLong minDias = treinos.stream()
                .filter(t -> t.getTipoTreino() != null
                        && (t.getTipoTreino() == TipoTreino.INTERVALADO
                            || t.getTipoTreino() == TipoTreino.TIRO))
                .filter(t -> t.getDataTreino() != null
                        && t.getDataTreino().isBefore(dataReferencia))
                .mapToLong(t -> ChronoUnit.DAYS.between(t.getDataTreino(), dataReferencia))
                .min();
        return minDias.isPresent() ? (int) minDias.getAsLong() : Integer.MAX_VALUE;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Portão 2 — helpers
    // ─────────────────────────────────────────────────────────────────────────

    private double calcularRpeMedia7Dias(List<TreinoRealizado> treinos, LocalDate dataReferencia) {
        if (treinos == null || treinos.isEmpty()) return 0.0;
        LocalDate limite = dataReferencia.minusDays(7);
        return treinos.stream()
                .filter(t -> t.getDataTreino() != null && !t.getDataTreino().isBefore(limite))
                .filter(t -> t.getPercepcaoEsforco() != null)
                .mapToInt(TreinoRealizado::getPercepcaoEsforco)
                .average()
                .orElse(0.0);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Portão 3 — helpers
    // ─────────────────────────────────────────────────────────────────────────

    private Optional<TreinoRealizado> encontrarUltimoTreinoIntensivo(
            List<TreinoRealizado> treinos, LocalDate dataReferencia) {
        if (treinos == null || treinos.isEmpty()) return Optional.empty();
        return treinos.stream()
                .filter(t -> t.getTipoTreino() != null
                        && (t.getTipoTreino() == TipoTreino.INTERVALADO
                            || t.getTipoTreino() == TipoTreino.TIRO))
                .filter(t -> t.getDataTreino() != null
                        && t.getDataTreino().isBefore(dataReferencia))
                .max(Comparator.comparing(TreinoRealizado::getDataTreino));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Portão 5 — seleção de categoria
    // ─────────────────────────────────────────────────────────────────────────

    private CategoriaIntervalado selecionarCategoria(
            FasePeriodizacao fase,
            List<TreinoRealizado> treinos,
            LocalDate dataReferencia) {

        if (fase == null) {
            return selecionarCategoriaRotacao(treinos, dataReferencia);
        }

        return switch (fase) {
            case BASE                  -> selecionarEntreAeB(treinos, dataReferencia);
            case BUILD                 -> selecionarEntreBoeC(treinos, dataReferencia);
            case ESPECIFICO            -> selecionarEntreCeE(treinos, dataReferencia);
            case TAPER, SEMANA_PROVA   -> CategoriaIntervalado.D;
            case POS_PROVA             -> CategoriaIntervalado.D;
            case DESENVOLVIMENTO_GERAL -> selecionarCategoriaRotacao(treinos, dataReferencia);
        };
    }

    private CategoriaIntervalado selecionarEntreAeB(
            List<TreinoRealizado> treinos, LocalDate dataReferencia) {
        CategoriaIntervalado ultima = identificarUltimaCategoriaUsada(treinos, dataReferencia);
        return ultima == CategoriaIntervalado.A ? CategoriaIntervalado.B : CategoriaIntervalado.A;
    }

    private CategoriaIntervalado selecionarEntreBoeC(
            List<TreinoRealizado> treinos, LocalDate dataReferencia) {
        CategoriaIntervalado ultima = identificarUltimaCategoriaUsada(treinos, dataReferencia);
        return ultima == CategoriaIntervalado.B ? CategoriaIntervalado.C : CategoriaIntervalado.B;
    }

    private CategoriaIntervalado selecionarEntreCeE(
            List<TreinoRealizado> treinos, LocalDate dataReferencia) {
        CategoriaIntervalado ultima = identificarUltimaCategoriaUsada(treinos, dataReferencia);
        return ultima == CategoriaIntervalado.C ? CategoriaIntervalado.E : CategoriaIntervalado.C;
    }

    private CategoriaIntervalado selecionarCategoriaRotacao(
            List<TreinoRealizado> treinos, LocalDate dataReferencia) {
        CategoriaIntervalado ultima = identificarUltimaCategoriaUsada(treinos, dataReferencia);
        if (ultima == null) return CategoriaIntervalado.A;
        return switch (ultima) {
            case A -> CategoriaIntervalado.B;
            case B -> CategoriaIntervalado.C;
            case C -> CategoriaIntervalado.D;
            case D -> CategoriaIntervalado.E;
            case E -> CategoriaIntervalado.A;
        };
    }

    /**
     * Detecta a última categoria de intervalado usada pelo atleta a partir
     * das observações e descrições dos treinos de alta intensidade.
     * Usa o mesmo padrão de palavras-chave que o {@code VariabilidadePromptFormatter}.
     */
    private CategoriaIntervalado identificarUltimaCategoriaUsada(
            List<TreinoRealizado> treinos, LocalDate dataReferencia) {
        if (treinos == null || treinos.isEmpty()) return null;

        return treinos.stream()
                .filter(t -> t.getDataTreino() != null
                        && !t.getDataTreino().isAfter(dataReferencia))
                .filter(t -> t.getTipoTreino() == TipoTreino.INTERVALADO
                          || t.getTipoTreino() == TipoTreino.TIRO
                          || t.getTipoTreino() == TipoTreino.FARTLEK)
                .max(Comparator.comparing(TreinoRealizado::getDataTreino))
                .map(this::detectarCategoriaDoTreino)
                .orElse(null);
    }

    private CategoriaIntervalado detectarCategoriaDoTreino(TreinoRealizado treino) {
        String obs  = treino.getObservacao() != null ? treino.getObservacao().toUpperCase() : "";
        String desc = treino.getDescricao()  != null ? treino.getDescricao().toUpperCase()  : "";
        String tipo = treino.getTipoTreino() != null ? treino.getTipoTreino().name()         : "";
        String texto = obs + " " + desc;

        if (texto.contains("200M") || texto.contains("400M") || texto.contains("600M")
                || texto.contains("CURTO") || texto.contains("VO2MAX CURTO")) {
            return CategoriaIntervalado.A;
        }
        if (texto.contains("3MIN") || texto.contains("4MIN") || texto.contains("5MIN")
                || texto.contains("VO2MAX LONGO") || texto.contains("REPETICOES LONGAS")) {
            return CategoriaIntervalado.B;
        }
        if (texto.contains("THRESHOLD") || texto.contains("LIMIAR") || texto.contains("4-6 MIN")
                || texto.contains("BLOCOS")) {
            return CategoriaIntervalado.C;
        }
        if (tipo.contains("FARTLEK") || texto.contains("FARTLEK") || texto.contains("VARIADO")) {
            return CategoriaIntervalado.E;
        }
        // D é o default seguro quando o padrão textual não é conclusivo
        return CategoriaIntervalado.D;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Factories com logging
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Aplica a atenuação de readiness CAUTELOSO sobre a decisão dos portões fisiológicos.
     * {@code Elegivel} vira {@code Degradado} (mesma categoria, instrução de atenuação de volume);
     * {@code Degradado} recebe a nota de atenuação adicional; {@code Substituido} não é alterado
     * (já é o bloqueio máximo).
     */
    private RecomendacaoIntervalado atenuarPorReadinessCauteloso(RecomendacaoIntervalado decisao) {
        String nota = " Readiness do dia CAUTELOSO — atenuar volume da sessao intervalada em 20-30%.";
        return switch (decisao) {
            case RecomendacaoIntervalado.Elegivel el -> {
                log.info("IntervaladoElegibilidade: readiness CAUTELOSO — atenuando volume para Categoria {}", el.categoria());
                yield new RecomendacaoIntervalado.Degradado(
                        el.categoria(),
                        el.motivo() + nota,
                        el.instrucaoParaLlm() + nota);
            }
            case RecomendacaoIntervalado.Degradado deg -> new RecomendacaoIntervalado.Degradado(
                    deg.categoriaSegura(),
                    deg.motivo() + nota,
                    deg.instrucaoParaLlm() + nota);
            case RecomendacaoIntervalado.Substituido sub -> sub;
        };
    }

    private RecomendacaoIntervalado criarSubstituido(
            TipoTreino tipoFallback, String motivo, String instrucao) {
        log.warn("IntervaladoElegibilidade: SUBSTITUIDO por {} — {}", tipoFallback, motivo);
        return new RecomendacaoIntervalado.Substituido(tipoFallback, motivo, instrucao);
    }

    private RecomendacaoIntervalado criarDegradado(
            CategoriaIntervalado categoria, String motivo, String instrucao) {
        log.warn("IntervaladoElegibilidade: DEGRADADO para Categoria {} — {}", categoria, motivo);
        return new RecomendacaoIntervalado.Degradado(categoria, motivo, instrucao);
    }

    private String sanitizarDescricaoLesao(String descricao) {
        if (descricao == null || descricao.isBlank()) return "sem descricao";
        return descricao.substring(0, Math.min(80, descricao.length()));
    }
}
