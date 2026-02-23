package com.menthoros.services;

import com.menthoros.dto.output.MetricasSemanaisMedias;
import com.menthoros.dto.output.PadroesTreino;

import java.util.UUID;

/**
 * Serviço de métricas agregadas: estatísticas semanais e padrões de treino.
 */
public interface MetricasAgregadasService {

    /**
     * Calcula métricas semanais médias baseadas no histórico de treinos realizados
     *
     * @param atletaId ID do atleta
     * @param numSemanas Número de semanas para calcular a média (recomendado: 4-6)
     * @return Métricas semanais médias (volume, TSS, frequência)
     */
    MetricasSemanaisMedias calcularMetricasSemanais(UUID atletaId, int numSemanas);

    /**
     * Calcula padrões de treino (dias consecutivos e desde último descanso)
     *
     * @param atletaId ID do atleta
     * @return Padrões de treino identificados
     */
    PadroesTreino calcularPadroesTreino(UUID atletaId);
}
