package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.dto.intervalsicu.IcuActivityDto;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.TreinoPlanejado;
import br.com.menthoros.backend.entity.TreinoRealizado;
import br.com.menthoros.backend.events.TreinoRegistradoEvent;
import br.com.menthoros.backend.exception.DomainConflictException;
import br.com.menthoros.backend.services.IntervalsIcuConnectionService;
import br.com.menthoros.backend.services.TsbService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Persiste e reconcilia o {@link TreinoRealizado} importado do intervals.icu — colaborador
 * transacional do {@link br.com.menthoros.backend.services.impl.IntervalsIcuActivityIngestionServiceImpl}
 * (design.md D3, passos 6-9), mesmo padrão de separação de {@code IntervalsIcuPushProcessor}: a
 * chamada HTTP já aconteceu no orquestrador, fora de qualquer transação.
 *
 * <p>Idempotent: YES — {@link TreinoDedupHelper#saveIdempotent} garante que uma corrida de
 * concorrência não duplica o insert.
 * <p>Side Effects: Database insert (TreinoRealizado, quando novo), update de TSB do dia,
 * reconciliação (via {@link CandidateSelector}/{@link ReconciliationDecisionExecutor}),
 * publicação de {@link TreinoRegistradoEvent}.
 * <p>Tenant-aware: YES — {@code tenantId} recebido explicitamente por parâmetro.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IntervalsIcuActivityPersister {

    private final IntervalsIcuConnectionService intervalsIcuConnectionService;
    private final IntervalsIcuActivityMapper intervalsIcuActivityMapper;
    private final TreinoDedupHelper treinoDedupHelper;
    private final TssCalculatorService tssCalculatorService;
    private final TsbService tsbService;
    private final CandidateSelector candidateSelector;
    private final ReconciliationDecisionExecutor reconciliationDecisionExecutor;
    private final ApplicationEventPublisher eventPublisher;
    private final MeterRegistry meterRegistry;

    @Transactional
    public TreinoRealizado persistir(IcuActivityDto dto, Atleta atleta, UUID tenantId, String externalId) {
        // Passo 6 (TOCTOU, pre-mortem #9): a conexão pode ter sido desativada entre a leitura
        // inicial (orquestrador) e este ponto — recarrega dentro da TX antes de persistir.
        intervalsIcuConnectionService.conexaoAtiva(atleta.getId(), tenantId)
                .orElseThrow(() -> new DomainConflictException("Conexão intervals.icu não está mais ativa"));

        TreinoRealizado treino = intervalsIcuActivityMapper.map(dto, atleta);

        TreinoDedupHelper.SaveResult resultado = treinoDedupHelper.saveIdempotent(treino, externalId, atleta.getId());
        TreinoRealizado salvo = resultado.treino();

        if (resultado.inserted()) {
            salvo.setTssCalculado(tssCalculatorService.calcularTss(salvo));
            tsbService.atualizarTsbDia(atleta.getId(), salvo.getDataTreino());

            List<TreinoPlanejado> candidatos = candidateSelector.buscarCandidatos(salvo, tenantId);
            reconciliationDecisionExecutor.executar(salvo, candidatos, atleta);

            eventPublisher.publishEvent(new TreinoRegistradoEvent(salvo.getId(), tenantId));
            registrarCoberturaDeEtapas(salvo, tenantId);
            log.info("Activity intervals.icu importada: treinoId={}, atletaId={}, externalId={}, etapas={}",
                    salvo.getId(), atleta.getId(), externalId, salvo.getEtapasRealizadas().size());
        } else {
            log.info("Corrida de concorrência no import intervals.icu — registro já persistido por outra requisição: treinoId={}, atletaId={}, externalId={}",
                    salvo.getId(), atleta.getId(), externalId);
        }

        return salvo;
    }

    /**
     * Cobertura de etapas por assessoria — a métrica de sucesso da change
     * {@code intervals-icu-activity-laps}. É o instrumento que torna a lacuna observável: sem ele,
     * o treinador não teria como saber que deveria reclamar, porque não tem com o que comparar.
     *
     * <p>Só conta import NOVO: numa corrida de concorrência quem inseriu já contou.
     *
     * <p>A tag {@code tenant} tem cardinalidade igual ao número de assessorias. É aceitável no
     * piloto e foi um pedido explícito do product review; se a base crescer para milhares de
     * tenants, trocar por uma métrica agregada com breakdown fora do Prometheus.
     */
    private void registrarCoberturaDeEtapas(TreinoRealizado salvo, UUID tenantId) {
        String resultado = salvo.getEtapasRealizadas().isEmpty() ? "sem_etapas" : "com_etapas";
        Counter.builder("intervals_icu.import.etapas")
                .tag("tenant", tenantId.toString())
                .tag("resultado", resultado)
                .description("Imports do intervals.icu por cobertura de etapas, segmentado por assessoria")
                .register(meterRegistry)
                .increment();
    }
}
