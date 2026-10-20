package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.repository.MetricasDiariasRepository;
import br.com.menthoros.backend.services.helper.PaceLimiarResolvido;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Fase transacional do recálculo de um intervalo de TSB — bean à parte de {@link TsbServiceImpl}
 * pelo mesmo motivo de {@link TsbDiaPersister}: o {@code @Transactional} só vale quando a chamada
 * passa pelo proxy de outro bean (fix-tsb-rest-days-materialization, design.md "Mecânica
 * transacional").
 *
 * <p>Ordem fixa dentro da transação: <b>lock do atleta → métricas → laço do persister</b>. O pace é
 * resolvido antes, fora de qualquer transação, por {@link TsbServiceImpl} (chamada externa ao
 * intervals.icu não pode segurar conexão). Na ingestão, onde o chamador já tem transação, este
 * método participa dela: o lock vale até o commit do treino. No job e no reparo abre a própria.
 *
 * <p>Atomicidade: o intervalo inteiro grava ou nada grava. Uma falha no meio reverte todos os dias,
 * nunca deixa uma série densa com seeds errados.
 *
 * Idempotent: YES — recalcular o mesmo intervalo produz o mesmo resultado.
 * Side Effects: Database insert/update das métricas de cada dia e, no último, do PlanoMetaDados.
 * Tenant-aware: NO
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TsbRecalculoTransacional {

    private final MetricasDiariasRepository metricasDiariasRepository;
    private final TsbDiaPersister tsbDiaPersister;

    /**
     * @param inicio         primeiro dia pedido; recua para {@code ultimaMetrica + 1} quando há um
     *                       buraco entre a última métrica e ele (os dias de descanso nascem com
     *                       TSS 0 e estado carregado). Treino retroativo (inicio dentro da série
     *                       existente) não recua.
     * @param fim            último dia, inclusive
     * @param gravaMetadados {@code true} só quando {@code fim} alcança o hoje do atleta; o último
     *                       dia grava então os metadados com {@code paceResolvido}
     */
    @Transactional
    public void recalcularIntervalo(UUID atletaId, LocalDate inicio, LocalDate fim,
                                    boolean gravaMetadados, PaceLimiarResolvido paceResolvido) {
        metricasDiariasRepository.lockAtleta(atletaId);

        LocalDate ultimoDia = metricasDiariasRepository.findDataUltimaMetrica(atletaId);
        LocalDate inicioEfetivo = (ultimoDia != null && ultimoDia.plusDays(1).isBefore(inicio))
                ? ultimoDia.plusDays(1)
                : inicio;
        if (inicioEfetivo.isBefore(inicio)) {
            log.info("Fechando buraco do atleta {}: {} até {} (última métrica em {})",
                    atletaId, inicioEfetivo, inicio.minusDays(1), ultimoDia);
        }

        for (LocalDate dia = inicioEfetivo; !dia.isAfter(fim); dia = dia.plusDays(1)) {
            boolean ultimoDiaDoLaco = gravaMetadados && dia.equals(fim);
            tsbDiaPersister.atualizarDiaTransacional(
                    atletaId, dia, ultimoDiaDoLaco, ultimoDiaDoLaco ? paceResolvido : null);
        }
    }
}
