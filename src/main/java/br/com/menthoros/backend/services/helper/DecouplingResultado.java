package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.enums.MotivoNullDecoupling;

/**
 * Resultado do decoupling nas duas intensidades, cada uma com seu motivo de null — as métricas
 * têm pipelines de elegibilidade independentes (design D3 de fit-lap-derived-metrics), então um
 * lado pode ser calculado com o outro null.
 *
 * @param percentual          Pa:HR (%), ou {@code null} quando não aplicável
 * @param motivoNull          motivo do Pa:HR null; {@code null} quando calculado
 * @param potenciaPercentual  Pw:HR (%), ou {@code null} quando não aplicável
 * @param motivoNullPotencia  motivo do Pw:HR null; {@code null} quando calculado
 */
public record DecouplingResultado(
        Double percentual,
        MotivoNullDecoupling motivoNull,
        Double potenciaPercentual,
        MotivoNullDecoupling motivoNullPotencia
) {

    static DecouplingResultado ambosNull(MotivoNullDecoupling motivo) {
        return new DecouplingResultado(null, motivo, null, motivo);
    }
}
