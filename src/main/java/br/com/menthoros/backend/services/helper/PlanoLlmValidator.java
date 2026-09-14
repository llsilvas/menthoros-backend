package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.dto.llm.TreinoPlanejadoLlmDto;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.enums.DiaSemana;
import br.com.menthoros.backend.exception.LLMException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * Validações estruturais e de coerência física do plano gerado pela LLM, por tipo de treino.
 * Extraído de {@code IaServiceImpl} (refactor-iaservice-decomposition, seção 5). Nunca corrige o
 * plano — hard-fail ({@link LLMException}) nas violações estruturais, WARN nas fisiológicas/de
 * coerência (não bloqueiam a geração).
 */
@Slf4j
@Component
public class PlanoLlmValidator {

    private final MeterRegistry meterRegistry;
    private final PaceValidator paceValidator;

    public PlanoLlmValidator(MeterRegistry meterRegistry, PaceValidator paceValidator) {
        this.meterRegistry = meterRegistry;
        this.paceValidator = paceValidator;
    }

    public void validarTreinoIntervalado(TreinoPlanejadoLlmDto treino, Object atletaId) {
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
        long tirosInvalidos = etapas.stream()
                .filter(e -> "INTERVALADO".equals(e.tipoEtapa()))
                .filter(e -> {
                    if (e.duracaoMin() == null) return true;
                    double duracao = e.duracaoMin();
                    // mínimo ~18s (0.3 min) e máximo 10 min
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
     * Validação estrutural compartilhada de treinos "3 etapas" (AQUECIMENTO → PRINCIPAL → DESAQUECIMENTO).
     * Hard-fail (lança {@link LLMException}) em: número de etapas ≠ 3; etapa central que não é
     * PRINCIPAL (fix IA-04, review.md 2026-09-05 — incondicional, independente de
     * {@code validarOrdem}: {@code PlanoEstruturaReparador} trata REGENERATIVO/CONTINUO/TEMPO_RUN/
     * LONGO identicamente, sem razão de domínio pra LONGO aceitar etapa central diferente); e —
     * quando {@code validarOrdem} — AQUECIMENTO/DESAQUECIMENTO fora de posição (LONGO não valida
     * a posição de aquec/desaq, só a contagem e a etapa central).
     */
    public void validarEstrutura3Etapas(TreinoPlanejadoLlmDto treino, String tipo, Object atletaId, boolean validarOrdem) {
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

    public void validarTreinoLongo(TreinoPlanejadoLlmDto treino, Object atletaId) {
        validarEstrutura3Etapas(treino, "LONGO", atletaId, false);
        log.info("VALIDAÇÃO OK [Atleta {}]: Treino LONGO - 3 etapas conforme esperado", atletaId);
    }

    /**
     * Valida que todas as etapas têm repeticoes = 1
     */
    public void validarRepeticoes(TreinoPlanejadoLlmDto treino, Object atletaId) {
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
    public void validarTreinoRegenerativo(TreinoPlanejadoLlmDto treino, Object atletaId) {
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
    public void validarTreinoContinuo(TreinoPlanejadoLlmDto treino, Object atletaId) {
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
    public void validarTreinoTempoRun(TreinoPlanejadoLlmDto treino, Object atletaId, Atleta atleta) {
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
