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

    /**
     * Recalcula de {@code inicio} até {@code fim} (ambos inclusive), com {@code fim} explícito —
     * nunca o relógio do servidor. {@code fim} não pode ser posterior ao "hoje" do atleta no fuso
     * dele. Metadados (limiares, pace) só são gravados quando {@code fim} alcança o hoje do atleta;
     * um bloco intermediário de um atraso longo não os toca.
     *
     * Idempotent: YES — recalcular o mesmo intervalo produz o mesmo resultado.
     * Side Effects: Database insert/update das métricas de cada dia do intervalo e, quando
     * {@code fim} é o hoje do atleta, do PlanoMetaDados.
     * Tenant-aware: NO
     *
     * @throws IllegalArgumentException se algum argumento for nulo, {@code inicio > fim} ou
     *         {@code fim} for posterior ao hoje do atleta
     */
    void recalcularDesde(UUID atletaId, LocalDate inicio, LocalDate fim);
}
