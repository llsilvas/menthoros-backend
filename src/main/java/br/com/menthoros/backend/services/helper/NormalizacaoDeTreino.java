package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.dto.llm.EtapaTreinoLlmDto;
import br.com.menthoros.backend.dto.llm.TreinoPlanejadoLlmDto;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.enums.TipoTreino;
import br.com.menthoros.backend.exception.LLMException;
import br.com.menthoros.backend.services.helper.ZonaTreinoService.ZonaFC;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Normaliza e valida UM treino planejado gerado pela LLM, executando a receita de normalização da
 * sua família (ver {@code CONTEXT.md}, "Receita de normalização"). A ordem dos passos é dado — cada
 * família tem sua {@link Receita}, inspecionável por {@link #receita(FamiliaTreino)} e congelada
 * pelo golden {@code FamiliaTreinoTest}.
 *
 * <p>Extraído de {@code PlanoLlmValidator.normalizarTreino} (pipeline-normalizacao-treino, seção 2)
 * com <b>ordem idêntica</b>: os dois bugs de ordem achados no {@code /qa} da
 * refactor-iaservice-decomposition (gate de contagem mascarado pelo padding; duração dos tiros não
 * rechecada após o IA-05) eram invisíveis aos testes das peças porque a regra morava na composição
 * — aqui ela mora na lista. {@code TreinoNormalizador}, {@code EtapaFcValidator},
 * {@code PlanoEstruturaReparador} e {@code PaceValidator} são internal seams: nada mais os chama.</p>
 *
 * <p>Idempotent: YES — mesma entrada, mesma saída; sem estado entre chamadas. Side Effects: NONE
 * além de log e do contador {@code plano_violacao_estrutural}. Tenant-aware: NO — recebe o
 * {@code Atleta} já resolvido pelo tenant no {@link ContextoNormalizacao}.</p>
 */
@Slf4j
@Component
public class NormalizacaoDeTreino {

    private static final Pattern DURACAO_MM_SS = Pattern.compile("^(\\d{1,3}):(\\d{2})$");

    private final TreinoNormalizador treinoNormalizador;
    private final EtapaFcValidator etapaFcValidator;
    private final PlanoEstruturaReparador estruturaReparador;
    private final PaceValidator paceValidator;
    private final MeterRegistry meterRegistry;
    private final Map<FamiliaTreino, Receita> receitas;

    public NormalizacaoDeTreino(TreinoNormalizador treinoNormalizador,
                                EtapaFcValidator etapaFcValidator,
                                PlanoEstruturaReparador estruturaReparador,
                                PaceValidator paceValidator,
                                MeterRegistry meterRegistry) {
        this.treinoNormalizador = treinoNormalizador;
        this.etapaFcValidator = etapaFcValidator;
        this.estruturaReparador = estruturaReparador;
        this.paceValidator = paceValidator;
        this.meterRegistry = meterRegistry;
        this.receitas = montarReceitas();
    }

    /**
     * Ponto único de entrada: resolve a família do treino e executa a receita, passo a passo.
     * Um passo de validação lança {@link LLMException} e interrompe; o chamador decide o retry.
     */
    public TreinoPlanejadoLlmDto normalizar(TreinoPlanejadoLlmDto bruto, ContextoNormalizacao ctx) {
        FamiliaTreino familia = FamiliaTreino.de(bruto.tipoTreino());
        TreinoPlanejadoLlmDto treino = bruto;
        for (Passo passo : receitas.get(familia).passos()) {
            TreinoPlanejadoLlmDto antes = treino;
            treino = passo.fn().aplicar(treino, ctx);
            // equals, não identidade: o normalizador constrói record novo mesmo sem mudança (DoR, Codex)
            log.debug("NORMALIZAÇÃO [Atleta {}] familia={} passo={} alterou={}",
                    ctx.atletaId(), familia, passo.nome(), !antes.equals(treino));
        }
        return treino;
    }

    /** A receita de uma família — o test surface do golden da ordem. */
    public Receita receita(FamiliaTreino familia) {
        return receitas.get(familia);
    }

    // ======================================================================================
    // Receitas — a ordem aqui é a regra (design.md, "Forma do module")
    // ======================================================================================

    private Map<FamiliaTreino, Receita> montarReceitas() {
        List<Passo> caudaComum = List.of(
                gate("validar-repeticoes", this::validarRepeticoes),
                new Passo("corrigir-fc-zona", this::corrigirFcZona),
                new Passo("corrigir-pace-teto-piso", this::corrigirPaceTetoPiso),
                new Passo("recalcular-duracao", this::recalcularDuracao),
                new Passo("garantir-distancia-continuo",
                        (t, c) -> treinoNormalizador.garantirDistanciaContinuo(t, c.atleta().getPaceLimiar())),
                gate("validar-triangulo", (t, c) -> validarTrianguloPaceDuracaoDistancia(t)));

        Passo corrigirTemporais = new Passo("corrigir-temporais",
                (t, c) -> t.comEtapas(treinoNormalizador.corrigirDistanciasEtapasTemporais(t.etapas(), c.atleta().getPaceLimiar())));
        Passo expandir = new Passo("expandir",
                (t, c) -> treinoNormalizador.expandirEtapasAgregadas(t, c.zonasFC()));
        Passo reconciliarDistancia = new Passo("reconciliar-distancia",
                (t, c) -> treinoNormalizador.reconciliarDistanciaComEtapas(t));
        Passo gateDuracaoTiros = gate("gate-duracao-tiros", this::validarDuracaoTiros);

        Map<FamiliaTreino, Receita> mapa = new EnumMap<>(FamiliaTreino.class);

        mapa.put(FamiliaTreino.INTERVALADO_TIRO, receita(List.of(
                corrigirTemporais,
                expandir,
                // ── validarTreinoIntervalado, item a item, ANTES de normalizar: normalizar-intervalado
                //    sintetiza pares tiro+recuperação — validar depois mascararia um treino de 4 etapas
                gate("gate-existencia", this::gateExistencia),
                gate("gate-contagem", this::gateContagem),
                gate("gate-presenca-aquec-desaq", this::gatePresencaAquecDesaq),
                gate("gate-ordem-aquec-desaq", this::gateOrdemAquecDesaq),
                gate("alerta-poucos-tiros", this::alertaPoucosTiros),
                gate("gate-balanceamento", this::gateBalanceamento),
                gate("gate-sequencia", this::gateSequencia),
                gate("alerta-distancias", this::alertaDistancias),
                gateDuracaoTiros,
                gate("log-validacao-ok", this::logValidacaoOk),
                new Passo("normalizar-intervalado",
                        (t, c) -> treinoNormalizador.normalizarTreinoIntervalado(t, c.atleta().getNivelExperiencia(), c.zonasFC())),
                reconciliarDistancia,
                // 2ª vez (IA-05): a normalização recalcula duracaoMin ao ajustar distanciaKm — um tiro
                // válido na 1ª checagem pode passar do teto de 10min aqui
                gateDuracaoTiros
        ), caudaComum));

        mapa.put(FamiliaTreino.FARTLEK, receita(List.of(
                corrigirTemporais,
                expandir,
                reconciliarDistancia
        ), caudaComum));

        mapa.put(FamiliaTreino.TRES_ETAPAS, receita(List.of(
                // reparar é identidade fora de TIPOS_3_ETAPAS (PlanoEstruturaReparador:43-46) — por isso
                // mora só aqui, uma vez, e não na cauda
                new Passo("reparar-3-etapas", (t, c) -> estruturaReparador.reparar(t, t.tipoTreino())),
                gate("validar-por-tipo", this::validarPorTipo)
        ), caudaComum));

        mapa.put(FamiliaTreino.PADRAO, receita(List.of(), caudaComum));

        return mapa;
    }

    private static Receita receita(List<Passo> cabeca, List<Passo> cauda) {
        List<Passo> todos = new ArrayList<>(cabeca);
        todos.addAll(cauda);
        return new Receita(todos);
    }

    /** Passo de validação: executa a checagem (que lança em violação) e devolve o treino intacto. */
    private static Passo gate(String nome, Validacao validacao) {
        return new Passo(nome, (t, c) -> {
            validacao.validar(t, c);
            return t;
        });
    }

    @FunctionalInterface
    private interface Validacao {
        void validar(TreinoPlanejadoLlmDto treino, ContextoNormalizacao ctx);
    }

    // ======================================================================================
    // Gates de INTERVALADO/TIRO — itens 1-8 do antigo validarTreinoIntervalado, na mesma ordem
    // ======================================================================================

    private void gateExistencia(TreinoPlanejadoLlmDto treino, ContextoNormalizacao ctx) {
        var etapas = treino.etapas();
        if (etapas == null || etapas.isEmpty()) {
            log.error("VALIDAÇÃO FALHOU [Atleta {}]: Treino {} sem etapas", ctx.atletaId(), treino.tipoTreino());
            throw new LLMException(String.format("Treino %s inválido: não foram geradas etapas", treino.tipoTreino()));
        }
    }

    private void gateContagem(TreinoPlanejadoLlmDto treino, ContextoNormalizacao ctx) {
        var etapas = treino.etapas();
        if (etapas.size() < 6) {
            log.error("VALIDAÇÃO FALHOU [Atleta {}]: Treino {} tem apenas {} etapas (mínimo 6)",
                    ctx.atletaId(), treino.tipoTreino(), etapas.size());
            throw new LLMException(String.format(
                    "Treino %s inválido: gerou apenas %d etapas (mínimo 6 para intervalados)",
                    treino.tipoTreino(), etapas.size()));
        }
    }

    private void gatePresencaAquecDesaq(TreinoPlanejadoLlmDto treino, ContextoNormalizacao ctx) {
        var etapas = treino.etapas();
        if (etapas.stream().noneMatch(e -> "AQUECIMENTO".equals(e.tipoEtapa()))) {
            log.error("VALIDAÇÃO FALHOU [Atleta {}]: Treino {} não possui etapa de aquecimento",
                    ctx.atletaId(), treino.tipoTreino());
            throw new LLMException(String.format("Treino %s inválido: não possui etapa de aquecimento", treino.tipoTreino()));
        }
        if (etapas.stream().noneMatch(e -> "DESAQUECIMENTO".equals(e.tipoEtapa()))) {
            log.error("VALIDAÇÃO FALHOU [Atleta {}]: Treino {} não possui etapa de desaquecimento",
                    ctx.atletaId(), treino.tipoTreino());
            throw new LLMException(String.format("Treino %s inválido: não possui etapa de desaquecimento", treino.tipoTreino()));
        }
    }

    private void gateOrdemAquecDesaq(TreinoPlanejadoLlmDto treino, ContextoNormalizacao ctx) {
        var etapas = treino.etapas();
        var primeira = etapas.get(0);
        var ultima = etapas.get(etapas.size() - 1);
        if (!"AQUECIMENTO".equals(primeira.tipoEtapa())) {
            log.error("VALIDAÇÃO FALHOU [Atleta {}]: Treino {} não inicia com aquecimento (inicia com {})",
                    ctx.atletaId(), treino.tipoTreino(), primeira.tipoEtapa());
            throw new LLMException(String.format("Treino %s inválido: deve iniciar com aquecimento", treino.tipoTreino()));
        }
        if (!"DESAQUECIMENTO".equals(ultima.tipoEtapa())) {
            log.error("VALIDAÇÃO FALHOU [Atleta {}]: Treino {} não termina com desaquecimento (termina com {})",
                    ctx.atletaId(), treino.tipoTreino(), ultima.tipoEtapa());
            throw new LLMException(String.format("Treino %s inválido: deve terminar com desaquecimento", treino.tipoTreino()));
        }
    }

    private void alertaPoucosTiros(TreinoPlanejadoLlmDto treino, ContextoNormalizacao ctx) {
        long numTiros = contar(treino.etapas(), "INTERVALADO");
        if (numTiros < 3) {
            log.warn("VALIDAÇÃO ALERTA [Atleta {}]: Treino {} tem apenas {} tiros (recomendado: 3+)",
                    ctx.atletaId(), treino.tipoTreino(), numTiros);
        }
    }

    private void gateBalanceamento(TreinoPlanejadoLlmDto treino, ContextoNormalizacao ctx) {
        long numTiros = contar(treino.etapas(), "INTERVALADO");
        long numRecuperacoes = contar(treino.etapas(), "RECUPERACAO");
        // pode ter 1 rec a menos se o último tiro não tiver rec
        if (Math.abs(numTiros - numRecuperacoes) > 1) {
            log.error("VALIDAÇÃO FALHOU [Atleta {}]: Treino {} desbalanceado: {} tiros vs {} recuperações",
                    ctx.atletaId(), treino.tipoTreino(), numTiros, numRecuperacoes);
            throw new LLMException(String.format(
                    "Treino %s inválido: %d tiros mas %d recuperações (devem ser iguais ou diferença de 1)",
                    treino.tipoTreino(), numTiros, numRecuperacoes));
        }
    }

    private void gateSequencia(TreinoPlanejadoLlmDto treino, ContextoNormalizacao ctx) {
        boolean ultimoFoiTiro = false;
        boolean jaTeveTiro = false;
        int recuperacoesInvalidas = 0;

        for (var etapa : treino.etapas()) {
            String tipo = etapa.tipoEtapa();
            if ("INTERVALADO".equals(tipo)) {
                jaTeveTiro = true;
                ultimoFoiTiro = true;
            } else if ("RECUPERACAO".equals(tipo)) {
                if (!ultimoFoiTiro) {
                    recuperacoesInvalidas++;
                }
                ultimoFoiTiro = false;
            } else if ("AQUECIMENTO".equals(tipo)) {
                if (jaTeveTiro) {
                    log.warn("VALIDAÇÃO ALERTA [Atleta {}]: Aquecimento após tiro detectado em treino {}",
                            ctx.atletaId(), treino.tipoTreino());
                }
                ultimoFoiTiro = false;
            } else if ("DESAQUECIMENTO".equals(tipo)) {
                if (!jaTeveTiro) {
                    log.warn("VALIDAÇÃO ALERTA [Atleta {}]: Desaquecimento antes de qualquer tiro em treino {}",
                            ctx.atletaId(), treino.tipoTreino());
                }
                ultimoFoiTiro = false;
            } else {
                ultimoFoiTiro = false;
            }
        }

        if (recuperacoesInvalidas > 0) {
            log.error("VALIDAÇÃO FALHOU [Atleta {}]: Treino {} possui {} recuperações sem tiro anterior",
                    ctx.atletaId(), treino.tipoTreino(), recuperacoesInvalidas);
            throw new LLMException(String.format(
                    "Treino %s inválido: existem recuperações sem tiro imediatamente anterior", treino.tipoTreino()));
        }
    }

    private void alertaDistancias(TreinoPlanejadoLlmDto treino, ContextoNormalizacao ctx) {
        var etapas = treino.etapas();
        double somaDistancias = somarDistancias(etapas, null);
        double distanciaPlanejada = treino.distanciaKm() != null ? treino.distanciaKm() : 0.0;

        double diferenca = Math.abs(somaDistancias - distanciaPlanejada);
        if (diferenca > 0.5) { // tolerância de 500m
            log.warn("VALIDAÇÃO ALERTA [Atleta {}]: Soma das etapas ({} km) difere da distância planejada ({} km) em {} km",
                    ctx.atletaId(), somaDistancias, distanciaPlanejada, diferenca);
        }

        if (distanciaPlanejada > 0.0) {
            double distanciaTiros = somarDistancias(etapas, "INTERVALADO");
            double distanciaRecuperacoes = somarDistancias(etapas, "RECUPERACAO");
            if (distanciaTiros < distanciaPlanejada * 0.20) {
                log.warn("VALIDAÇÃO ALERTA [Atleta {}]: Distância em tiros ({}) muito baixa para total de {} km no treino {}",
                        ctx.atletaId(), distanciaTiros, distanciaPlanejada, treino.tipoTreino());
            }
            if (distanciaRecuperacoes > distanciaPlanejada * 0.65) {
                log.warn("VALIDAÇÃO ALERTA [Atleta {}]: Distância em recuperação ({}) muito alta para total de {} km no treino {}",
                        ctx.atletaId(), distanciaRecuperacoes, distanciaPlanejada, treino.tipoTreino());
            }
        }
    }

    /**
     * Coerência fisiológica da duração dos tiros: entre ~18s (0.3 min) e 10 min; {@code null} conta
     * como inválido. Aparece duas vezes na receita INTERVALADO_TIRO — antes e depois de
     * normalizar-intervalado (IA-05).
     */
    private void validarDuracaoTiros(TreinoPlanejadoLlmDto treino, ContextoNormalizacao ctx) {
        var etapas = treino.etapas();
        if (etapas == null) return;

        long tirosInvalidos = etapas.stream()
                .filter(e -> "INTERVALADO".equals(e.tipoEtapa()))
                .filter(e -> {
                    if (e.duracaoMin() == null) return true;
                    double duracao = e.duracaoMin();
                    return duracao < 0.3 || duracao > 10.0;
                })
                .count();

        if (tirosInvalidos > 0) {
            log.error("VALIDAÇÃO FALHOU [Atleta {}]: Treino {} possui {} tiros com duração incoerente",
                    ctx.atletaId(), treino.tipoTreino(), tirosInvalidos);
            throw new LLMException(String.format(
                    "Treino %s inválido: existem tiros com duração incoerente (muito curtos ou muito longos)",
                    treino.tipoTreino()));
        }
    }

    /** Na posição de hoje (pré-normalização): as contagens/distâncias logadas são as do que a LLM gerou. */
    private void logValidacaoOk(TreinoPlanejadoLlmDto treino, ContextoNormalizacao ctx) {
        var etapas = treino.etapas();
        log.info("VALIDAÇÃO OK [Atleta {}]: Treino {} - {} etapas ({} tiros, {} recuperações, {} km - tiros: {} km, rec: {} km)",
                ctx.atletaId(), treino.tipoTreino(), etapas.size(),
                contar(etapas, "INTERVALADO"), contar(etapas, "RECUPERACAO"),
                somarDistancias(etapas, null), somarDistancias(etapas, "INTERVALADO"), somarDistancias(etapas, "RECUPERACAO"));
    }

    private static long contar(List<EtapaTreinoLlmDto> etapas, String tipoEtapa) {
        return etapas.stream().filter(e -> tipoEtapa.equals(e.tipoEtapa())).count();
    }

    private static double somarDistancias(List<EtapaTreinoLlmDto> etapas, String tipoEtapaOuNull) {
        return etapas.stream()
                .filter(e -> tipoEtapaOuNull == null || tipoEtapaOuNull.equals(e.tipoEtapa()))
                .mapToDouble(e -> e.distanciaKm() != null ? e.distanciaKm() : 0.0)
                .sum();
    }

    // ======================================================================================
    // TRES_ETAPAS — validação por tipo (REGENERATIVO / CONTINUO / TEMPO_RUN / LONGO)
    // ======================================================================================

    private void validarPorTipo(TreinoPlanejadoLlmDto treino, ContextoNormalizacao ctx) {
        String tipo = treino.tipoTreino();
        UUID atletaId = ctx.atletaId();
        if ("LONGO".equals(tipo)) validarTreinoLongo(treino, atletaId);
        if ("REGENERATIVO".equals(tipo)) validarTreinoRegenerativo(treino, atletaId);
        if ("CONTINUO".equals(tipo)) validarTreinoContinuo(treino, atletaId);
        if ("TEMPO_RUN".equals(tipo)) validarTreinoTempoRun(treino, atletaId, ctx.atleta());
    }

    /**
     * Validação estrutural compartilhada de treinos "3 etapas" (AQUECIMENTO → PRINCIPAL → DESAQUECIMENTO).
     * Hard-fail (lança {@link LLMException}) em: número de etapas ≠ 3; etapa central que não é
     * PRINCIPAL (fix IA-04, review.md 2026-09-05 — incondicional, independente de
     * {@code validarOrdem}: {@code PlanoEstruturaReparador} trata REGENERATIVO/CONTINUO/TEMPO_RUN/
     * LONGO identicamente, sem razão de domínio pra LONGO aceitar etapa central diferente); e —
     * quando {@code validarOrdem} — AQUECIMENTO/DESAQUECIMENTO fora de posição (LONGO não valida
     * a posição de aquec/desaq, só a contagem e a etapa central).
     */
    private void validarEstrutura3Etapas(TreinoPlanejadoLlmDto treino, String tipo, UUID atletaId, boolean validarOrdem) {
        var etapas = treino.etapas();
        if (etapas == null || etapas.size() != 3) {
            log.error("VALIDAÇÃO FALHOU [Atleta {}]: Treino {} tem {} etapas (esperado: 3)",
                    atletaId, tipo, etapas != null ? etapas.size() : 0);
            contarViolacaoEstrutural(tipo);
            throw new LLMException(String.format(
                    "Treino %s inválido: gerou %d etapas (esperado 3: aquec, principal, desaq)",
                    tipo, etapas != null ? etapas.size() : 0));
        }
        if (!"PRINCIPAL".equals(etapas.get(1).tipoEtapa())) {
            log.error("VALIDAÇÃO FALHOU [Atleta {}]: Treino {} sem etapa PRINCIPAL no meio: [{}→{}→{}]",
                    atletaId, tipo, etapas.get(0).tipoEtapa(), etapas.get(1).tipoEtapa(), etapas.get(2).tipoEtapa());
            contarViolacaoEstrutural(tipo);
            throw new LLMException(String.format("Treino %s inválido: etapa central deve ser PRINCIPAL", tipo));
        }
        if (validarOrdem
                && (!"AQUECIMENTO".equals(etapas.get(0).tipoEtapa()) || !"DESAQUECIMENTO".equals(etapas.get(2).tipoEtapa()))) {
            log.error("VALIDAÇÃO FALHOU [Atleta {}]: Treino {} fora de ordem: [{}→{}→{}]",
                    atletaId, tipo, etapas.get(0).tipoEtapa(), etapas.get(1).tipoEtapa(), etapas.get(2).tipoEtapa());
            contarViolacaoEstrutural(tipo);
            throw new LLMException(String.format(
                    "Treino %s inválido: deve ser AQUECIMENTO → PRINCIPAL → DESAQUECIMENTO", tipo));
        }
    }

    /** Telemetria: violação estrutural residual (não reparada) por tipo de treino. */
    private void contarViolacaoEstrutural(String tipo) {
        Counter.builder("plano_violacao_estrutural").tag("tipo", tipo).register(meterRegistry).increment();
    }

    private void validarTreinoLongo(TreinoPlanejadoLlmDto treino, UUID atletaId) {
        validarEstrutura3Etapas(treino, "LONGO", atletaId, false);
        log.info("VALIDAÇÃO OK [Atleta {}]: Treino LONGO - 3 etapas conforme esperado", atletaId);
    }

    /** REGENERATIVO: 3 etapas, duração 20–45 min (WARN fora da faixa). */
    private void validarTreinoRegenerativo(TreinoPlanejadoLlmDto treino, UUID atletaId) {
        validarEstrutura3Etapas(treino, "REGENERATIVO", atletaId, true);

        if (treino.duracaoMin() != null) {
            var m = DURACAO_MM_SS.matcher(treino.duracaoMin().trim());
            if (m.matches()) {
                try {
                    int minutos = Integer.parseInt(m.group(1));
                    if (minutos > 45) {
                        log.warn("VALIDAÇÃO ALERTA [Atleta {}]: Treino REGENERATIVO com {} min (máximo recomendado: 45 min)",
                                atletaId, minutos);
                    } else if (minutos < 20) {
                        log.warn("VALIDAÇÃO ALERTA [Atleta {}]: Treino REGENERATIVO com {} min (mínimo recomendado: 20 min)",
                                atletaId, minutos);
                    }
                } catch (NumberFormatException ignored) {}
            }
        }

        log.info("VALIDAÇÃO OK [Atleta {}]: Treino REGENERATIVO - 3 etapas conforme esperado", atletaId);
    }

    /** CONTINUO: 3 etapas, distância mínima de 5 km (WARN). */
    private void validarTreinoContinuo(TreinoPlanejadoLlmDto treino, UUID atletaId) {
        validarEstrutura3Etapas(treino, "CONTINUO", atletaId, true);

        if (treino.distanciaKm() != null && treino.distanciaKm() < 5.0) {
            log.warn("VALIDAÇÃO ALERTA [Atleta {}]: Treino CONTINUO com {} km (mínimo recomendado: 5 km)",
                    atletaId, treino.distanciaKm());
        }

        log.info("VALIDAÇÃO OK [Atleta {}]: Treino CONTINUO - 3 etapas conforme esperado", atletaId);
    }

    /** TEMPO_RUN: 3 etapas, PRINCIPAL ≥ 15 min e ritmoAlvo do PRINCIPAL dentro de ±10% do paceLimiar (WARNs). */
    private void validarTreinoTempoRun(TreinoPlanejadoLlmDto treino, UUID atletaId, Atleta atleta) {
        validarEstrutura3Etapas(treino, "TEMPO_RUN", atletaId, true);

        var etapaPrincipal = treino.etapas().get(1);

        if (etapaPrincipal.duracaoMin() != null && etapaPrincipal.duracaoMin() < 15) {
            log.warn("VALIDAÇÃO ALERTA [Atleta {}]: TEMPO_RUN principal com {} min (mínimo para indução de limiar: 15 min)",
                    atletaId, etapaPrincipal.duracaoMin());
        }

        if (atleta.getPaceLimiar() != null && etapaPrincipal.ritmoAlvo() != null) {
            var paceMediaOpt = paceValidator.calcularPaceMedia(etapaPrincipal.ritmoAlvo());
            if (paceMediaOpt.isPresent()) {
                double paceMedia = paceMediaOpt.getAsDouble();
                double limiar = atleta.getPaceLimiar().doubleValue();
                double tolerancia = limiar * 0.10;
                if (paceMedia < limiar - tolerancia || paceMedia > limiar + tolerancia) {
                    log.warn("VALIDAÇÃO ALERTA [Atleta {}]: TEMPO_RUN principal ritmoAlvo='{}' (média={} min/km) fora da faixa limiar ±10% [{}-{} min/km]",
                            atletaId, etapaPrincipal.ritmoAlvo(),
                            String.format("%.2f", paceMedia),
                            String.format("%.2f", limiar - tolerancia),
                            String.format("%.2f", limiar + tolerancia));
                }
            }
        }

        log.info("VALIDAÇÃO OK [Atleta {}]: Treino TEMPO_RUN - 3 etapas conforme esperado", atletaId);
    }

    // ======================================================================================
    // Cauda comum — todas as famílias, nesta ordem
    // ======================================================================================

    /** Todas as etapas com repeticoes = 1; {@code etapas == null} é no-op e {@code repeticoes == null} é aceito. */
    private void validarRepeticoes(TreinoPlanejadoLlmDto treino, ContextoNormalizacao ctx) {
        var etapas = treino.etapas();
        if (etapas == null) return;

        etapas.forEach(etapa -> {
            if (etapa.repeticoes() != null && etapa.repeticoes() != 1) {
                log.error("VALIDAÇÃO FALHOU [Atleta {}]: Etapa '{}' tem repeticoes={} (deve ser sempre 1)",
                        ctx.atletaId(), etapa.descricaoEtapa(), etapa.repeticoes());
                throw new LLMException(String.format(
                        "Etapa '%s' inválida: repeticoes=%d (deve ser sempre 1 - expandir etapas individualmente)",
                        etapa.descricaoEtapa(), etapa.repeticoes()));
            }
        });
    }

    /** FC das etapas contra as zonas LTHR — só quando há zonas e etapas (guarda faz parte do passo). */
    private TreinoPlanejadoLlmDto corrigirFcZona(TreinoPlanejadoLlmDto treino, ContextoNormalizacao ctx) {
        List<ZonaFC> zonas = ctx.zonasFC();
        if (zonas == null || treino.etapas() == null) return treino;
        final String tipoTreino = treino.tipoTreino();
        return treino.comEtapas(treino.etapas().stream()
                .map(etapa -> etapaFcValidator.validarFcEtapa(etapa, tipoTreino, zonas))
                .collect(Collectors.toList()));
    }

    /** ritmoAlvo contra teto e piso de pace do histórico; só reconstrói se mudou. */
    private TreinoPlanejadoLlmDto corrigirPaceTetoPiso(TreinoPlanejadoLlmDto treino, ContextoNormalizacao ctx) {
        BigDecimal teto = null;
        BigDecimal piso = null;
        try {
            TipoTreino tipoEnum = TipoTreino.valueOf(treino.tipoTreino());
            teto = ctx.tetoPorTipo().get(tipoEnum);
            piso = ctx.pisoPorTipo().get(tipoEnum);
        } catch (IllegalArgumentException ignored) {}
        String ritmoValidado = paceValidator.validar(treino.ritmoAlvo(), teto, piso);
        return Objects.equals(ritmoValidado, treino.ritmoAlvo()) ? treino : treino.comRitmo(ritmoValidado);
    }

    /** Duração total = soma das etapas (override do valor da LLM) — só com etapas e soma > 0. */
    private TreinoPlanejadoLlmDto recalcularDuracao(TreinoPlanejadoLlmDto treino, ContextoNormalizacao ctx) {
        if (treino.etapas() == null || treino.etapas().isEmpty()) return treino;
        int totalMinEtapas = treinoNormalizador.somarDuracoesMin(treino.etapas());
        if (totalMinEtapas <= 0) return treino;

        String duracaoAtual = treino.duracaoMin();
        TreinoPlanejadoLlmDto recalculado = treinoNormalizador.recalcularDuracaoTreino(treino, treino.etapas());
        if (!Objects.equals(duracaoAtual, recalculado.duracaoMin())) {
            log.info("DURAÇÃO RECALCULADA [{}]: '{}' → '{}' (baseado nas {} etapas)",
                    treino.tipoTreino(), duracaoAtual, recalculado.duracaoMin(), recalculado.etapas().size());
        }
        return recalculado;
    }

    /**
     * Consistência entre ritmoAlvo, distanciaKm e duracaoMin (identidade física). Desvio > 20% → WARN.
     * Não corrige: os três são prescrições da LLM e nenhum tem precedência clara.
     */
    private void validarTrianguloPaceDuracaoDistancia(TreinoPlanejadoLlmDto treino) {
        if (treino.ritmoAlvo() == null || treino.distanciaKm() == null || treino.duracaoMin() == null) return;

        var paceMediaOpt = paceValidator.calcularPaceMedia(treino.ritmoAlvo());
        if (paceMediaOpt.isEmpty()) return;

        double distanciaKm = treino.distanciaKm();
        if (distanciaKm <= 0) return;

        var mDuracao = DURACAO_MM_SS.matcher(treino.duracaoMin().trim());
        if (!mDuracao.matches()) return;
        double duracaoMin;
        try {
            duracaoMin = Integer.parseInt(mDuracao.group(1)) + Integer.parseInt(mDuracao.group(2)) / 60.0;
        } catch (NumberFormatException e) {
            return;
        }
        if (duracaoMin <= 0) return;

        double paceMedia = paceMediaOpt.getAsDouble();
        double duracaoEsperada = paceMedia * distanciaKm;
        double desvio = Math.abs(duracaoEsperada - duracaoMin) / duracaoEsperada;

        if (desvio > 0.20) {
            log.warn("TRIÂNGULO pace×dist×dur [{}]: ritmoAlvo='{}', dist={} km, duracao={} min → esperado {} min (desvio {}%)",
                    treino.tipoTreino(), treino.ritmoAlvo(), distanciaKm, duracaoMin,
                    String.format("%.1f", duracaoEsperada), String.format("%.0f", desvio * 100));
        }
    }
}
