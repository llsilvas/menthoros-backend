package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.domain.workout.HrTarget;
import br.com.menthoros.backend.entity.Atleta;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Resolve o alvo de FC do plano para bpm absoluto, contra o atleta.
 *
 * <p>É aqui que a base do domínio (%LTHR, Friel) é aplicada — e é a única aplicação dela neste
 * caminho. A faixa de uma zona vem do {@link ZonaTreinoService}, nunca recalculada: duas expressões
 * da mesma grandeza, cada uma correta isoladamente, foi exatamente como o BUG-CONF-001 nasceu.</p>
 */
@Slf4j
@Component
public class IntervalsIcuFcAlvoResolver {

    private final ZonaTreinoService zonaTreinoService;

    public IntervalsIcuFcAlvoResolver(ZonaTreinoService zonaTreinoService) {
        this.zonaTreinoService = zonaTreinoService;
    }

    /**
     * Desfecho da resolução. {@code alvo} nulo com {@code descartadoPorFaltaDeDado} verdadeiro é o
     * caso que o treinador precisa ver: ele prescreveu FC e o treino vai sair sem meta.
     */
    public record Resolucao(HrTarget alvo, boolean descartadoPorFaltaDeDado) {

        static Resolucao resolvido(HrTarget alvo) {
            return new Resolucao(alvo, false);
        }

        static Resolucao descartado() {
            return new Resolucao(null, true);
        }
    }

    /**
     * Converte o alvo bruto em bpm absoluto.
     *
     * <p><b>Idempotente:</b> YES — cálculo puro.
     * <p><b>Side Effects:</b> NONE.
     * <p><b>Tenant-aware:</b> NO — opera sobre o atleta que o chamador já resolveu.
     *
     * @param bruto  alvo como o plano o escreveu; nulo significa "etapa sem alvo de FC"
     * @param atleta atleta do treino
     * @return o alvo em bpm, ou descarte quando falta FC de limiar medida
     */
    public Resolucao resolver(IntervalsIcuTargetParser.FcAlvoBruto bruto, Atleta atleta) {
        if (bruto == null) {
            return new Resolucao(null, false);
        }
        if (bruto.base() == IntervalsIcuTargetParser.FcAlvoBruto.Base.BPM) {
            return Resolucao.resolvido(new HrTarget(bruto.inicio(), bruto.fim()));
        }

        // Deliberadamente getFcLimiar(), não getFcLimiarCalculada(): o getter "calculada" cai em
        // 0,85 × FCmax, que por sua vez cai em 220 - idade (ou 180). São estimativas aceitáveis para
        // EXIBIR uma faixa, e inaceitáveis como número que o atleta vai perseguir no relógio — a
        // fórmula etária erra dezenas de bpm entre indivíduos. Sem limiar medido, não há meta.
        Integer fcLimiar = atleta != null ? atleta.getFcLimiar() : null;
        if (fcLimiar == null || fcLimiar <= 0) {
            return Resolucao.descartado();
        }

        return switch (bruto.base()) {
            case PERCENT -> {
                HrTarget alvo = new HrTarget(
                        percentualDoLimiar(bruto.inicio(), fcLimiar),
                        percentualDoLimiar(bruto.fim(), fcLimiar));
                // Registrado, não silencioso: o mesmo texto do plano passa a significar outro bpm.
                // DEBUG, não INFO: a linha carrega FC de limiar e bpm — dado fisiológico do atleta,
                // que não deve ficar em nível de rotina num pipeline de log centralizado.
                log.debug("Alvo percentual {}-{}% interpretado como %LTHR (limiar {}) => {}-{} bpm",
                        bruto.inicio(), bruto.fim(), fcLimiar, alvo.startBpm(), alvo.endBpm());
                yield Resolucao.resolvido(alvo);
            }
            case ZONE -> Resolucao.resolvido(faixaDaZona(bruto.inicio(), atleta, fcLimiar));
            case BPM -> throw new IllegalStateException("BPM já tratado acima");
        };
    }

    /**
     * O percentual do plano é lido na base do domínio (%LTHR). O mesmo texto
     * {@code "90-95% FCmax"} gravado por planos antigos passa a significar outro bpm — é
     * reinterpretação deliberada, e o rótulo "FCmax" no texto legado sempre foi o engano: o prompt
     * entregava zonas em bpm derivadas do limiar, e o modelo copiou o sufixo do exemplo.
     */
    private int percentualDoLimiar(int percentual, int fcLimiar) {
        return (int) Math.round(fcLimiar * (percentual / 100.0));
    }

    private HrTarget faixaDaZona(int numeroZona, Atleta atleta, int fcLimiar) {
        ZonaTreinoService.ZonaFC zona = zonaTreinoService
                .calcularZonasFC(atleta.getFcMaxima(), fcLimiar)
                .get(numeroZona - 1);
        return new HrTarget(zona.fcMin(), zona.fcMax());
    }
}
