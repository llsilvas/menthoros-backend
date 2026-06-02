package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.enums.TipoTreino;

/**
 * Sugestão de reclassificação de tipo de treino com base na análise estrutural das etapas.
 *
 * <p>Este record é imutável e produzido pelo {@link TipoTreinoConsistenciaValidator}
 * quando a distribuição de esforço observada nas etapas diverge do tipo declarado pelo coach.
 *
 * <p>A sugestão é informativa — nunca altera o tipo automaticamente.
 *
 * @param tipoSugerido tipo de treino sugerido com base na análise do IF por etapa
 * @param confianca    valor entre 0.0 e 1.0 indicando a confiança da sugestão
 * @param motivo       descrição legível do motivo da sugestão
 */
public record SugestaoReclassificacao(
        TipoTreino tipoSugerido,
        double confianca,
        String motivo
) {}
