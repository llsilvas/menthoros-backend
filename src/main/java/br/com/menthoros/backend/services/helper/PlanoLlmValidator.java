package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.dto.llm.EtapaTreinoLlmDto;
import br.com.menthoros.backend.dto.llm.PlanoSemanalLlmDto;
import br.com.menthoros.backend.dto.llm.TreinoPlanejadoLlmDto;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.enums.DiaSemana;
import br.com.menthoros.backend.enums.TipoTreino;
import br.com.menthoros.backend.exception.LLMException;
import br.com.menthoros.backend.services.helper.ZonaTreinoService.ZonaFC;
import br.com.menthoros.backend.services.prompt.PaceHistoricoFormatter;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Validação e normalização completa do plano gerado pela LLM: pré-computa contexto (tetos/pisos
 * de pace, zonas de FC), dispara os normalizadores por tipo de treino e os validadores estruturais
 * (LLMException em violação estrutural; WARN nas fisiológicas/de coerência, que não bloqueiam a
 * geração). Extraído de {@code IaServiceImpl} (refactor-iaservice-decomposition, seções 5-6) — o
 * ponto único de entrada é {@link #validarENormalizarPlano}; os demais métodos públicos existem
 * pra teste direto de cada regra.
 *
 * <p>Idempotent: YES — mesma entrada sempre produz a mesma saída (nenhum estado mutável entre
 * chamadas). Side Effects: NONE (apenas leitura via {@code TreinoHistoricoProvider}/
 * {@code ZonaTreinoService}, já resolvidos pelo chamador; nenhuma escrita). Tenant-aware: NO —
 * recebe o {@code Atleta} já resolvido pelo tenant corrente; não consulta {@code TenantContext}.</p>
 */
@Slf4j
@Component
public class PlanoLlmValidator {

    private final MeterRegistry meterRegistry;
    private final PaceValidator paceValidator;
    private final TreinoHistoricoProvider treinoHistoricoProvider;
    private final PaceHistoricoFormatter paceHistoricoFormatter;
    private final ZonaTreinoService zonaTreinoService;
    private final TreinoNormalizador treinoNormalizador;
    private final EtapaFcValidator etapaFcValidator;
    private final PlanoEstruturaReparador estruturaReparador;

    public PlanoLlmValidator(MeterRegistry meterRegistry, PaceValidator paceValidator,
                             TreinoHistoricoProvider treinoHistoricoProvider,
                             PaceHistoricoFormatter paceHistoricoFormatter,
                             ZonaTreinoService zonaTreinoService,
                             TreinoNormalizador treinoNormalizador,
                             EtapaFcValidator etapaFcValidator,
                             PlanoEstruturaReparador estruturaReparador) {
        this.meterRegistry = meterRegistry;
        this.paceValidator = paceValidator;
        this.treinoHistoricoProvider = treinoHistoricoProvider;
        this.paceHistoricoFormatter = paceHistoricoFormatter;
        this.zonaTreinoService = zonaTreinoService;
        this.treinoNormalizador = treinoNormalizador;
        this.etapaFcValidator = etapaFcValidator;
        this.estruturaReparador = estruturaReparador;
    }

    /**
     * Ponto único de entrada: valida e normaliza o plano gerado pela LLM para um atleta —
     * expansão/normalização por tipo de treino, validação estrutural, FC por zona, pace (teto/piso
     * + triângulo pace×distância×duração) e distribuição de carga semanal.
     */
    public PlanoSemanalLlmDto validarENormalizarPlano(PlanoSemanalLlmDto plano, Atleta atleta, UUID atletaId) {
        if (plano == null || plano.treinosPlanejados() == null) {
            throw new LLMException("Plano gerado está nulo ou sem treinos");
        }

        // Pré-computar tetos e pisos de pace para validação
        var ctx = treinoHistoricoProvider.prepararContexto(atleta);
        Map<TipoTreino, BigDecimal> tetoPorTipo = paceHistoricoFormatter.calcularTetoPorTipo(ctx.treinosUltimas4Semanas());
        Map<TipoTreino, BigDecimal> pisoPorTipo = paceHistoricoFormatter.calcularPisoPorTipo(ctx.treinosUltimas4Semanas());

        // Pré-computar zonas de FC para validação de etapas (LTHR) — null se sem dados fisiológicos
        final List<ZonaFC> zonasParaValidacao;
        if (atleta.getFcLimiar() != null || atleta.getFcMaxima() != null) {
            zonasParaValidacao = zonaTreinoService.calcularZonasFC(
                    atleta.getFcMaximaCalculada(), atleta.getFcLimiarCalculada());
        } else {
            zonasParaValidacao = null;
        }

        List<TreinoPlanejadoLlmDto> treinosNormalizados = plano.treinosPlanejados().stream()
                .map(treino -> normalizarTreino(treino, atleta, atletaId, zonasParaValidacao, tetoPorTipo, pisoPorTipo))
                .collect(Collectors.toList());

        // Validar distribuição de carga semanal (dias consecutivos intensos)
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
     * Expande/normaliza (por tipo de treino) → valida estrutura/repeticoes/FC/pace → recalcula
     * duração/distância → valida triângulo pace×distância×duração. Um treino por chamada; extraído
     * do corpo do `.map()` de {@link #validarENormalizarPlano} pra manter o método de entrada
     * legível como "prepara contexto → normaliza cada treino → valida carga semanal".
     */
    private TreinoPlanejadoLlmDto normalizarTreino(TreinoPlanejadoLlmDto treino, Atleta atleta, UUID atletaId,
                                                    List<ZonaFC> zonasParaValidacao,
                                                    Map<TipoTreino, BigDecimal> tetoPorTipo,
                                                    Map<TipoTreino, BigDecimal> pisoPorTipo) {
        String tipoTreino = treino.tipoTreino();

        // Validar treinos INTERVALADO ou TIRO
        if ("INTERVALADO".equals(tipoTreino) || "TIRO".equals(tipoTreino)) {
            // (Passo 0) corrige distâncias de etapas temporais antes de expandir e normalizar
            treino = treino.comEtapas(
                    treinoNormalizador.corrigirDistanciasEtapasTemporais(treino.etapas(), atleta.getPaceLimiar()));
            // Expansão ANTES da validação: corrige alucinação de compressão "NxDist"
            treino = treinoNormalizador.expandirEtapasAgregadas(treino, zonasParaValidacao);
            // Gate estrutural sobre o que a LLM gerou, ANTES da normalização: normalizarTreinoIntervalado
            // sintetiza pares tiro+recuperação (adicionarTiroERecuperacao) quando falta distância —
            // validar depois deixaria um treino de 4 etapas passar completado pelo normalizador em
            // vez de cair no retry com feedback (achado do code-reviewer, /qa 2ª rodada).
            validarTreinoIntervalado(treino, atletaId);
            treino = treinoNormalizador.normalizarTreinoIntervalado(treino, atleta.getNivelExperiencia(), zonasParaValidacao);
            treino = treinoNormalizador.reconciliarDistanciaComEtapas(treino);
            // Só a duração dos tiros é rechecada DEPOIS (achado do Codex, adversarial review
            // 2026-09-14): desde o fix IA-05 a normalização recalcula duracaoMin a partir do
            // ritmoAlvo ao ajustar distanciaKm — um tiro válido antes pode passar do teto de 10min.
            validarDuracaoTiros(treino, atletaId);
        }

        // Fartlek: expande alucinações "Nx (AccelMin + RecovMin)" e reconcilia distância
        if ("FARTLEK".equals(tipoTreino)) {
            treino = treino.comEtapas(
                    treinoNormalizador.corrigirDistanciasEtapasTemporais(treino.etapas(), atleta.getPaceLimiar()));
            treino = treinoNormalizador.expandirEtapasAgregadas(treino, zonasParaValidacao);
            treino = treinoNormalizador.reconciliarDistanciaComEtapas(treino);
        }

        // Reparo determinístico de estrutura "3 etapas" ANTES da validação (não-op p/ outros tipos):
        // sintetiza aquecimento/desaquecimento faltante ou reordena, evitando derrubar o plano por
        // violação trivial. Falta-PRINCIPAL/ambíguo não é reparado → cai na validação (retry).
        treino = estruturaReparador.reparar(treino, tipoTreino);

        // Validar treino LONGO
        if ("LONGO".equals(tipoTreino)) {
            validarTreinoLongo(treino, atletaId);
        }

        // Validar estrutura de treinos REGENERATIVO, CONTINUO e TEMPO_RUN
        if ("REGENERATIVO".equals(tipoTreino)) {
            validarTreinoRegenerativo(treino, atletaId);
        }
        if ("CONTINUO".equals(tipoTreino)) {
            validarTreinoContinuo(treino, atletaId);
        }
        if ("TEMPO_RUN".equals(tipoTreino)) {
            validarTreinoTempoRun(treino, atletaId, atleta);
        }

        // Validar repeticoes = 1 em todas as etapas
        validarRepeticoes(treino, atletaId);

        // Validar FC das etapas contra zonas fisiológicas LTHR
        if (zonasParaValidacao != null && treino.etapas() != null) {
            final String tipoTreinoFinal = treino.tipoTreino();
            treino = treino.comEtapas(treino.etapas().stream()
                    .map(etapa -> etapaFcValidator.validarFcEtapa(etapa, tipoTreinoFinal, zonasParaValidacao))
                    .collect(Collectors.toList()));
        }

        // Validar ritmoAlvo contra teto e piso de pace
        BigDecimal teto = null;
        BigDecimal piso = null;
        try {
            TipoTreino tipoEnum = TipoTreino.valueOf(tipoTreino);
            teto = tetoPorTipo.get(tipoEnum);
            piso = pisoPorTipo.get(tipoEnum);
        } catch (IllegalArgumentException ignored) {}
        String ritmoValidado = paceValidator.validar(treino.ritmoAlvo(), teto, piso);
        if (!Objects.equals(ritmoValidado, treino.ritmoAlvo())) {
            treino = treino.comRitmo(ritmoValidado);
        }

        // Recalcular duração total com base na soma das etapas (override do valor gerado pelo LLM)
        if (treino.etapas() != null && !treino.etapas().isEmpty()) {
            int totalMinEtapas = treinoNormalizador.somarDuracoesMin(treino.etapas());
            if (totalMinEtapas > 0) {
                String duracaoAtual = treino.duracaoMin();
                treino = treinoNormalizador.recalcularDuracaoTreino(treino, treino.etapas());
                if (!Objects.equals(duracaoAtual, treino.duracaoMin())) {
                    log.info("DURAÇÃO RECALCULADA [{}]: '{}' → '{}' (baseado nas {} etapas)",
                            tipoTreino, duracaoAtual, treino.duracaoMin(), treino.etapas().size());
                }
            }
        }

        // Distância zerada em treino contínuo (ex.: REGENERATIVO sintetizado pelo reparo estrutural /
        // substituição por lesão): as etapas nascem só com duração, e corrigirDistanciasEtapasTemporais
        // não deriva a etapa PRINCIPAL. Aqui derivamos de duração×pace e reconciliamos o total — sem
        // sobrescrever distância válida já existente.
        treino = treinoNormalizador.garantirDistanciaContinuo(treino, atleta.getPaceLimiar());

        // Validar triângulo pace × distância × duração (após recálculo)
        validarTrianguloPaceDuracaoDistancia(treino);

        return treino;
    }

    public void validarTreinoIntervalado(TreinoPlanejadoLlmDto treino, UUID atletaId) {
        var etapas = treino.etapas();

        // 1) Existência e quantidade mínima de etapas
        if (etapas == null || etapas.isEmpty()) {
            log.error("VALIDAÇÃO FALHOU [Atleta {}]: Treino {} sem etapas",
                    atletaId, treino.tipoTreino());
            throw new LLMException(String.format(
                    "Treino %s inválido: não foram geradas etapas",
                    treino.tipoTreino()
            ));
        }

        if (etapas.size() < 6) {
            log.error("VALIDAÇÃO FALHOU [Atleta {}]: Treino {} tem apenas {} etapas (mínimo 8)",
                    atletaId, treino.tipoTreino(), etapas.size());
            throw new LLMException(String.format(
                    "Treino %s inválido: gerou apenas %d etapas (mínimo 6 para intervalados)",
                    treino.tipoTreino(), etapas.size()
            ));
        }

        // 2) Tipos básicos de etapa
        boolean temAquecimento = etapas.stream()
                .anyMatch(e -> "AQUECIMENTO".equals(e.tipoEtapa()));
        boolean temDesaquecimento = etapas.stream()
                .anyMatch(e -> "DESAQUECIMENTO".equals(e.tipoEtapa()));

        if (!temAquecimento) {
            log.error("VALIDAÇÃO FALHOU [Atleta {}]: Treino {} não possui etapa de aquecimento",
                    atletaId, treino.tipoTreino());
            throw new LLMException(String.format(
                    "Treino %s inválido: não possui etapa de aquecimento",
                    treino.tipoTreino()
            ));
        }

        if (!temDesaquecimento) {
            log.error("VALIDAÇÃO FALHOU [Atleta {}]: Treino {} não possui etapa de desaquecimento",
                    atletaId, treino.tipoTreino());
            throw new LLMException(String.format(
                    "Treino %s inválido: não possui etapa de desaquecimento",
                    treino.tipoTreino()
            ));
        }

        // 3) Ordem lógica: primeiro AQUECIMENTO, último DESAQUECIMENTO
        var primeiraEtapa = etapas.get(0);
        var ultimaEtapa   = etapas.get(etapas.size() - 1);

        if (!"AQUECIMENTO".equals(primeiraEtapa.tipoEtapa())) {
            log.error("VALIDAÇÃO FALHOU [Atleta {}]: Treino {} não inicia com aquecimento (inicia com {})",
                    atletaId, treino.tipoTreino(), primeiraEtapa.tipoEtapa());
            throw new LLMException(String.format(
                    "Treino %s inválido: deve iniciar com aquecimento",
                    treino.tipoTreino()
            ));
        }

        if (!"DESAQUECIMENTO".equals(ultimaEtapa.tipoEtapa())) {
            log.error("VALIDAÇÃO FALHOU [Atleta {}]: Treino {} não termina com desaquecimento (termina com {})",
                    atletaId, treino.tipoTreino(), ultimaEtapa.tipoEtapa());
            throw new LLMException(String.format(
                    "Treino %s inválido: deve terminar com desaquecimento",
                    treino.tipoTreino()
            ));
        }

        // 4) Contar tiros e recuperações
        long numTiros = etapas.stream()
                .filter(e -> "INTERVALADO".equals(e.tipoEtapa()))
                .count();
        long numRecuperacoes = etapas.stream()
                .filter(e -> "RECUPERACAO".equals(e.tipoEtapa()))
                .count();

        if (numTiros < 3) {
            log.warn("VALIDAÇÃO ALERTA [Atleta {}]: Treino {} tem apenas {} tiros (recomendado: 3+)",
                    atletaId, treino.tipoTreino(), numTiros);
        }

        // Balanceamento tiros x recuperações (pode ter 1 rec a menos se o último tiro não tiver rec)
        if (Math.abs(numTiros - numRecuperacoes) > 1) {
            log.error("VALIDAÇÃO FALHOU [Atleta {}]: Treino {} desbalanceado: {} tiros vs {} recuperações",
                    atletaId, treino.tipoTreino(), numTiros, numRecuperacoes);
            throw new LLMException(String.format(
                    "Treino %s inválido: %d tiros mas %d recuperações (devem ser iguais ou diferença de 1)",
                    treino.tipoTreino(), numTiros, numRecuperacoes
            ));
        }

        // 5) Validar sequência: recuperação só pode vir após tiro
        boolean ultimoFoiTiro = false;
        boolean jaTeveTiro = false;
        int recuperacoesInvalidas = 0;

        for (var etapa : etapas) {
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
                // Se aquecimento for gerado depois de tiro, é estranho (mas vamos só logar)
                if (jaTeveTiro) {
                    log.warn("VALIDAÇÃO ALERTA [Atleta {}]: Aquecimento após tiro detectado em treino {}",
                            atletaId, treino.tipoTreino());
                }
                ultimoFoiTiro = false;
            } else if ("DESAQUECIMENTO".equals(tipo)) {
                // Desaquecimento antes de qualquer tiro também é estranho
                if (!jaTeveTiro) {
                    log.warn("VALIDAÇÃO ALERTA [Atleta {}]: Desaquecimento antes de qualquer tiro em treino {}",
                            atletaId, treino.tipoTreino());
                }
                ultimoFoiTiro = false;
            } else {
                // Outros tipos, se existirem
                ultimoFoiTiro = false;
            }
        }

        if (recuperacoesInvalidas > 0) {
            log.error("VALIDAÇÃO FALHOU [Atleta {}]: Treino {} possui {} recuperações sem tiro anterior",
                    atletaId, treino.tipoTreino(), recuperacoesInvalidas);
            throw new LLMException(String.format(
                    "Treino %s inválido: existem recuperações sem tiro imediatamente anterior",
                    treino.tipoTreino()
            ));
        }

        // 6) Distâncias: soma total e proporções
        double somaDistancias = etapas.stream()
                .mapToDouble(e -> e.distanciaKm() != null ? e.distanciaKm() : 0.0)
                .sum();
        double distanciaPlanejada = treino.distanciaKm() != null ? treino.distanciaKm() : 0.0;

        double diferenca = Math.abs(somaDistancias - distanciaPlanejada);
        if (diferenca > 0.5) { // Tolerância de 500m
            log.warn("VALIDAÇÃO ALERTA [Atleta {}]: Soma das etapas ({} km) difere da distância planejada ({} km) em {} km",
                    atletaId, somaDistancias, distanciaPlanejada, diferenca);
        }

        double distanciaTiros = etapas.stream()
                .filter(e -> "INTERVALADO".equals(e.tipoEtapa()))
                .mapToDouble(e -> e.distanciaKm() != null ? e.distanciaKm() : 0.0)
                .sum();

        double distanciaRecuperacoes = etapas.stream()
                .filter(e -> "RECUPERACAO".equals(e.tipoEtapa()))
                .mapToDouble(e -> e.distanciaKm() != null ? e.distanciaKm() : 0.0)
                .sum();

        if (distanciaPlanejada > 0.0) {
            // Pelo menos 20% da distância em tiros (garante estímulo mínimo)
            if (distanciaTiros < distanciaPlanejada * 0.20) {
                log.warn("VALIDAÇÃO ALERTA [Atleta {}]: Distância em tiros ({}) muito baixa para total de {} km no treino {}",
                        atletaId, distanciaTiros, distanciaPlanejada, treino.tipoTreino());
            }

            // Recuperação não deve ser a maior parte do treino
            if (distanciaRecuperacoes > distanciaPlanejada * 0.65) {
                log.warn("VALIDAÇÃO ALERTA [Atleta {}]: Distância em recuperação ({}) muito alta para total de {} km no treino {}",
                        atletaId, distanciaRecuperacoes, distanciaPlanejada, treino.tipoTreino());
            }
        }

        // 7) Duração dos tiros (coerência fisiológica geral)
        validarDuracaoTiros(treino, atletaId);

        // 8) Log final de sucesso
        log.info("VALIDAÇÃO OK [Atleta {}]: Treino {} - {} etapas ({} tiros, {} recuperações, {} km - tiros: {} km, rec: {} km)",
                atletaId,
                treino.tipoTreino(),
                etapas.size(),
                numTiros,
                numRecuperacoes,
                somaDistancias,
                distanciaTiros,
                distanciaRecuperacoes
        );
    }

    /**
     * Coerência fisiológica da duração dos tiros: entre ~18s (0.3 min) e 10 min. Chamada duas vezes
     * no fluxo INTERVALADO/TIRO — dentro de {@link #validarTreinoIntervalado} (sobre o que a LLM
     * gerou) e de novo depois de {@code normalizarTreinoIntervalado}, porque desde o fix IA-05 a
     * normalização recalcula {@code duracaoMin} a partir do {@code ritmoAlvo} quando ajusta
     * {@code distanciaKm} — um tiro válido na primeira checagem pode passar do teto na segunda.
     */
    public void validarDuracaoTiros(TreinoPlanejadoLlmDto treino, UUID atletaId) {
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
                    atletaId, treino.tipoTreino(), tirosInvalidos);
            throw new LLMException(String.format(
                    "Treino %s inválido: existem tiros com duração incoerente (muito curtos ou muito longos)",
                    treino.tipoTreino()
            ));
        }
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
    public void validarEstrutura3Etapas(TreinoPlanejadoLlmDto treino, String tipo, UUID atletaId, boolean validarOrdem) {
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
            throw new LLMException(String.format(
                    "Treino %s inválido: etapa central deve ser PRINCIPAL", tipo));
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

    public void validarTreinoLongo(TreinoPlanejadoLlmDto treino, UUID atletaId) {
        validarEstrutura3Etapas(treino, "LONGO", atletaId, false);
        log.info("VALIDAÇÃO OK [Atleta {}]: Treino LONGO - 3 etapas conforme esperado", atletaId);
    }

    /**
     * Valida que todas as etapas têm repeticoes = 1
     */
    public void validarRepeticoes(TreinoPlanejadoLlmDto treino, UUID atletaId) {
        var etapas = treino.etapas();
        if (etapas == null) return;

        etapas.forEach(etapa -> {
            if (etapa.repeticoes() != null && etapa.repeticoes() != 1) {
                log.error("VALIDAÇÃO FALHOU [Atleta {}]: Etapa '{}' tem repeticoes={} (deve ser sempre 1)",
                        atletaId, etapa.descricaoEtapa(), etapa.repeticoes());
                throw new LLMException(String.format(
                        "Etapa '%s' inválida: repeticoes=%d (deve ser sempre 1 - expandir etapas individualmente)",
                        etapa.descricaoEtapa(), etapa.repeticoes()
                ));
            }
        });
    }

    // ======================== P2-B — TRIÂNGULO pace × distância × duração ========================

    /**
     * Valida a consistência entre ritmoAlvo, distanciaKm e duracaoMin (identidade física).
     * Se desvio > 20%, registra WARN. Não corrige: os três valores são prescrições do LLM e
     * nenhum deles tem precedência clara sobre os outros.
     */
    public void validarTrianguloPaceDuracaoDistancia(TreinoPlanejadoLlmDto treino) {
        if (treino.ritmoAlvo() == null || treino.distanciaKm() == null || treino.duracaoMin() == null) return;

        var paceMediaOpt = paceValidator.calcularPaceMedia(treino.ritmoAlvo());
        if (paceMediaOpt.isEmpty()) return;

        double distanciaKm = treino.distanciaKm();
        if (distanciaKm <= 0) return;

        var mDuracao = Pattern.compile("^(\\d{1,3}):(\\d{2})$").matcher(treino.duracaoMin().trim());
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

    // ======================== P3-A — VALIDAÇÃO ESTRUTURAL POR TIPO ========================

    /**
     * Valida treino REGENERATIVO: 3 etapas (AQUECIMENTO → PRINCIPAL → DESAQUECIMENTO),
     * duração 20–45 min.
     */
    public void validarTreinoRegenerativo(TreinoPlanejadoLlmDto treino, UUID atletaId) {
        validarEstrutura3Etapas(treino, "REGENERATIVO", atletaId, true);

        if (treino.duracaoMin() != null) {
            var m = Pattern.compile("^(\\d{1,3}):(\\d{2})$").matcher(treino.duracaoMin().trim());
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

    /**
     * Valida treino CONTINUO: 3 etapas (AQUECIMENTO → PRINCIPAL → DESAQUECIMENTO),
     * distância mínima de 5 km.
     */
    public void validarTreinoContinuo(TreinoPlanejadoLlmDto treino, UUID atletaId) {
        validarEstrutura3Etapas(treino, "CONTINUO", atletaId, true);

        if (treino.distanciaKm() != null && treino.distanciaKm() < 5.0) {
            log.warn("VALIDAÇÃO ALERTA [Atleta {}]: Treino CONTINUO com {} km (mínimo recomendado: 5 km)",
                    atletaId, treino.distanciaKm());
        }

        log.info("VALIDAÇÃO OK [Atleta {}]: Treino CONTINUO - 3 etapas conforme esperado", atletaId);
    }

    /**
     * Valida treino TEMPO_RUN: 3 etapas (AQUECIMENTO → PRINCIPAL → DESAQUECIMENTO),
     * PRINCIPAL mínimo 15 min, ritmoAlvo do PRINCIPAL dentro de ±10% do paceLimiar do atleta.
     */
    public void validarTreinoTempoRun(TreinoPlanejadoLlmDto treino, UUID atletaId, Atleta atleta) {
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

    // ======================== P3-B — DISTRIBUIÇÃO DE CARGA SEMANAL ========================

    /**
     * Verifica se existem treinos "duros" (INTERVALADO, TIRO, TEMPO_RUN) em dias consecutivos
     * e registra WARN. Não rejeita o plano — apenas alerta.
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
