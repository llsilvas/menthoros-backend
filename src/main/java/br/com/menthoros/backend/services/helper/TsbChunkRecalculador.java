package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.repository.MetricasDiariasRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;
import java.util.function.BiConsumer;

/**
 * Fronteira transacional de um bloco do recálculo histórico de TSB.
 *
 * <p><b>Por que é um bean separado, e não um método privado.</b> {@code REQUIRES_NEW} só tem efeito
 * quando a chamada atravessa o proxy do Spring. Um método privado — ou público chamado de dentro da
 * própria classe — é auto-invocação: não passa pelo proxy e a anotação vira decoração. É exatamente o
 * defeito que já existe em {@code TsbServiceImpl.atualizarTsbDia}, cujo {@code @Transactional} nunca
 * vale no fluxo de recálculo. Repetir o padrão aqui faria a change parecer entregue sem mudar nada.</p>
 *
 * <p><b>Ordem sequencial é obrigatória.</b> O CTL/ATL de um dia é calculado a partir do dia anterior,
 * lido do banco. Um bloco só produz o valor certo se o bloco anterior já tiver comitado. Paralelizar
 * os blocos é a otimização óbvia e <b>quebra o cálculo</b> — não faça.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TsbChunkRecalculador {

    private final MetricasDiariasRepository metricasDiariasRepository;

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * Apaga e reconstrói um intervalo fechado, numa transação própria.
     *
     * <p>O delete acontece <b>dentro da mesma transação</b> que reconstrói o intervalo. Assim, para
     * qualquer bloco, ou o intervalo está reconstruído ou continua com o dado antigo — nunca vazio.
     * É o que elimina a janela de "apagado e não reconstruído".</p>
     *
     * <p>Ao final faz {@code flush}/{@code clear} para liberar o contexto de persistência: sem isso,
     * um recálculo de 400+ dias acumularia todas as entidades numa única sessão.</p>
     *
     * Idempotent: YES — reprocessar o mesmo intervalo produz o mesmo resultado.
     * Side Effects: Database delete + insert/update das métricas do intervalo. Comita ao retornar,
     *   independentemente da transação do chamador.
     * Tenant-aware: NO — o caller resolve o atleta antes.
     *
     * @param atletaId      atleta cujo intervalo será reconstruído
     * @param inicio        primeiro dia do bloco (inclusive)
     * @param fim           último dia do bloco (inclusive)
     * @param recalcularDia aplica o cálculo de um dia; roda dentro desta transação
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recalcularBloco(UUID atletaId, LocalDate inicio, LocalDate fim,
                                BiConsumer<UUID, LocalDate> recalcularDia) {
        int apagadas = metricasDiariasRepository.deleteByAtletaIdAndDataBetween(atletaId, inicio, fim);
        // O delete é JPQL bulk: não passa pelo contexto de persistência, então entidades já
        // carregadas ficariam obsoletas. O clear garante que a reconstrução parta do banco.
        entityManager.flush();
        entityManager.clear();

        LocalDate dataAtual = inicio;
        while (!dataAtual.isAfter(fim)) {
            recalcularDia.accept(atletaId, dataAtual);
            dataAtual = dataAtual.plusDays(1);
        }

        entityManager.flush();
        entityManager.clear();

        log.debug("Bloco {} até {} reconstruído para atleta {} ({} métricas antigas removidas)",
                inicio, fim, atletaId, apagadas);
    }

    /**
     * Executa a fase de consolidação pós-blocos numa transação própria.
     *
     * <p>Depois que os blocos comitam, {@code atualizarMetaDados} e {@code recalcularSemanasProgressao}
     * ainda precisam de uma sessão Hibernate ativa — eles navegam associações lazy (por exemplo
     * {@code metricas.getAtleta()}). Sem esta fronteira, rodariam fora de transação e lançariam
     * {@code LazyInitializationException}.</p>
     *
     * <p>Se esta fase falhar, os blocos já comitados <b>permanecem</b> e os metadados ficam no estado
     * anterior — explicitamente stale, não silenciosamente dessincronizados. O caminho de recuperação
     * é re-disparar o recálculo, que é idempotente.</p>
     *
     * Idempotent: depende da fase recebida; as usadas hoje são idempotentes.
     * Side Effects: Database update. Comita ao retornar.
     * Tenant-aware: NO
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void consolidar(Runnable fase) {
        fase.run();
    }
}
