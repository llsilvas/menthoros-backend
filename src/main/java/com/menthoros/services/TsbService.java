package com.menthoros.services;

import com.menthoros.dto.output.MetricasSemanaisMedias;
import com.menthoros.dto.output.PadroesTreino;

import java.time.LocalDate;
import java.util.UUID;

public interface TsbService {
    void recalcularHistoricoCompleto(UUID id);

    /**
     * Atualiza TSB para um dia específico baseado nos treinos realizados
     *
     * @param atletaId ID do atleta
     * @param data Data para atualizar
     */
    void atualizarTsbDia(UUID atletaId, LocalDate data);

    /**
     * Calcula métricas semanais médias baseadas no histórico de treinos realizados
     *
     * @param atletaId ID do atleta
     * @param numSemanas Número de semanas para calcular a média (padrão: 4-6 semanas)
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
