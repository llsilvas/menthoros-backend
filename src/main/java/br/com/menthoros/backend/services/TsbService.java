package br.com.menthoros.backend.services;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Serviço de cálculo de TSB (Training Stress Balance).
 *
 * <p>Responsável por calcular CTL, ATL, TSB e Ramp Rate
 * usando médias móveis exponenciais.
 */
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
     * Recalcula o TSB a partir de {@code data} até o último dia com {@link
     * br.com.menthoros.backend.entity.MetricasDiarias} materializado (ou até hoje, se não houver
     * dia posterior).
     *
     * <p>Por que existe (D13): {@link #atualizarTsbDia} deriva CTL/ATL do dia anterior — mudar o
     * TSS de um dia passado invalida todos os dias seguintes, não só o dia alterado. Todo caminho
     * de ingestão retroativo (import de laps, edição de data, cancelamento) precisa deste método
     * em vez de {@code atualizarTsbDia} isolado.</p>
     *
     * Idempotent: YES — recalcular o mesmo intervalo produz o mesmo resultado.
     * Side Effects: Database insert/update das métricas de cada dia do intervalo e do
     * PlanoMetaDados, no último dia.
     * Tenant-aware: NO
     *
     * @param atletaId ID do atleta
     * @param data primeiro dia a recalcular
     */
    void recalcularDesde(UUID atletaId, LocalDate data);
}
