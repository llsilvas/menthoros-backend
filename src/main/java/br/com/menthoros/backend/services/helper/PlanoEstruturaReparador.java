package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.dto.llm.EtapaTreinoLlmDto;
import br.com.menthoros.backend.dto.llm.TreinoPlanejadoLlmDto;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * Reparo determinístico de violações estruturais triviais e inequívocas em treinos "3 etapas"
 * (AQUECIMENTO → PRINCIPAL → DESAQUECIMENTO), aplicado ANTES da validação para evitar derrubar o
 * plano inteiro por um único treino malformado.
 *
 * <p><b>Conservador por design:</b> só repara o que é formulaico e não-decisório — sintetizar
 * aquecimento/desaquecimento faltante (segmento leve, não é o estímulo) e reordenar quando os 3 tipos
 * já estão presentes. Exige <b>exatamente 1 etapa PRINCIPAL</b>: se o estímulo principal falta ou é
 * ambíguo, NÃO repara (a validação falha e o fluxo cai no retry). Reparo nunca silencioso: logado e
 * contado na telemetria.</p>
 *
 * <p>Idempotent: YES — transformação pura sobre o DTO; reaplicar sobre um treino já canônico é no-op.
 * Side Effects: incrementa contador Micrometer {@code plano_reparo_aplicado{tipo,acao}}.
 * Tenant-aware: NO — opera só sobre o DTO.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PlanoEstruturaReparador {

    private static final Set<String> TIPOS_3_ETAPAS =
            Set.of("REGENERATIVO", "CONTINUO", "TEMPO_RUN", "LONGO");

    private final MeterRegistry meterRegistry;

    /**
     * Tenta reparar a estrutura de etapas de um treino "3 etapas". Retorna o treino reparado, ou o
     * original quando não há reparo seguro a fazer (a validação subsequente decide passar ou exigir retry).
     */
    public TreinoPlanejadoLlmDto reparar(TreinoPlanejadoLlmDto treino, String tipo) {
        if (treino == null || !TIPOS_3_ETAPAS.contains(tipo) || treino.etapas() == null) {
            return treino;
        }
        List<EtapaTreinoLlmDto> etapas = treino.etapas();

        long principais = etapas.stream().filter(e -> "PRINCIPAL".equals(e.tipoEtapa())).count();
        if (principais != 1) {
            return treino; // estímulo ausente/ambíguo — não reparável (vai para retry)
        }
        EtapaTreinoLlmDto principal = etapas.stream().filter(e -> "PRINCIPAL".equals(e.tipoEtapa())).findFirst().orElseThrow();
        EtapaTreinoLlmDto aquecimento = primeiroDoTipo(etapas, "AQUECIMENTO");
        EtapaTreinoLlmDto desaquecimento = primeiroDoTipo(etapas, "DESAQUECIMENTO");

        boolean foraDeOrdem = etapas.size() == 3
                && (!"AQUECIMENTO".equals(etapas.get(0).tipoEtapa()) || !"DESAQUECIMENTO".equals(etapas.get(2).tipoEtapa()));
        boolean faltaAquec = aquecimento == null;
        boolean faltaDesaq = desaquecimento == null;

        if (!faltaAquec && !faltaDesaq && !foraDeOrdem) {
            return treino; // já canônico — nada a reparar
        }

        if (faltaAquec) {
            aquecimento = sintetizar("AQUECIMENTO", "Aquecimento leve em Z1-Z2 (gerado pelo sistema)", 10);
            contar(tipo, "aquecimento_sintetizado");
        }
        if (faltaDesaq) {
            desaquecimento = sintetizar("DESAQUECIMENTO", "Desaquecimento leve em Z1 (gerado pelo sistema)", 5);
            contar(tipo, "desaquecimento_sintetizado");
        }
        if (!faltaAquec && !faltaDesaq && foraDeOrdem) {
            contar(tipo, "reordenado");
        }

        List<EtapaTreinoLlmDto> reparadas = List.of(
                comOrdem(aquecimento, 1), comOrdem(principal, 2), comOrdem(desaquecimento, 3));
        log.info("REPARO ESTRUTURAL [{}]: etapas normalizadas para AQUECIMENTO→PRINCIPAL→DESAQUECIMENTO "
                + "(faltaAquec={}, faltaDesaq={}, foraDeOrdem={})", tipo, faltaAquec, faltaDesaq, foraDeOrdem);
        return comEtapas(treino, reparadas);
    }

    private static EtapaTreinoLlmDto primeiroDoTipo(List<EtapaTreinoLlmDto> etapas, String tipoEtapa) {
        return etapas.stream().filter(e -> tipoEtapa.equals(e.tipoEtapa())).findFirst().orElse(null);
    }

    private static EtapaTreinoLlmDto sintetizar(String tipoEtapa, String descricao, int duracaoMin) {
        return new EtapaTreinoLlmDto(null, tipoEtapa, descricao, duracaoMin, null, null, 1, null);
    }

    private static EtapaTreinoLlmDto comOrdem(EtapaTreinoLlmDto e, int ordem) {
        return new EtapaTreinoLlmDto(ordem, e.tipoEtapa(), e.descricaoEtapa(), e.duracaoMin(),
                e.distanciaKm(), e.fcAlvoEtapa(), e.repeticoes(), e.ritmoAlvo());
    }

    private static TreinoPlanejadoLlmDto comEtapas(TreinoPlanejadoLlmDto t, List<EtapaTreinoLlmDto> etapas) {
        return new TreinoPlanejadoLlmDto(t.diaSemana(), t.tipoTreino(), t.fcAlvo(), t.tssPlanejado(),
                t.intensidadePlanejada(), t.percepcaoEsforcoEsperada(), t.justificativaIa(),
                t.duracaoMin(), t.distanciaKm(), t.ritmoAlvo(), etapas,
                t.descricao(), t.zonaAlvo(), t.provaId());
    }

    private void contar(String tipo, String acao) {
        Counter.builder("plano_reparo_aplicado").tag("tipo", tipo).tag("acao", acao).register(meterRegistry).increment();
    }
}
