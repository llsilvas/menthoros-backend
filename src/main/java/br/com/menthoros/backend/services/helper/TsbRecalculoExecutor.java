package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.repository.MetricasDiariasRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;
import java.util.function.BiConsumer;

/**
 * Infraestrutura do recálculo histórico de TSB: fronteiras transacionais, invalidação de cache e
 * telemetria.
 *
 * <p>Reúne o que o recálculo precisa que aconteça <b>fora</b> do cálculo em si — o
 * {@code TsbServiceImpl} orquestra o algoritmo, este bean cuida das bordas: cada bloco numa
 * transação própria, a consolidação numa transação própria, o cache de metadados invalidado ao
 * fim, e o contador de abortos incrementado quando algo falha.</p>
 *
 * <h2>Fronteira transacional de um bloco</h2>
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
public class TsbRecalculoExecutor {

    private final MetricasDiariasRepository metricasDiariasRepository;
    private final CacheManager cacheManager;
    private final MeterRegistry meterRegistry;

    /** Cache de metadados servido por {@code PlanoMetadadosServiceImpl.buscarOuCriarMetadados}. */
    private static final String CACHE_METADADOS = "metadados-atleta";

    /** Contador de recálculos abortados, tagueado pela fase em que pararam. */
    private static final String METRICA_ABORTADO = "tsb.recalculo.abortado";

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

    /**
     * Invalida o cache de metadados do atleta ao fim do recálculo.
     *
     * <p>O recálculo escreve {@code PlanoMetaDados} direto pelo repository, enquanto a geração de
     * plano lê pelo serviço cacheado. Sem esta invalidação, o plano sairia com o CTL/TSB anteriores
     * ao recálculo.</p>
     *
     * <p>A chave replica a do {@code @Cacheable}: {@code atletaId + '_' + tenantId}. O tenant é a
     * assessoria do atleta — que é o mesmo valor que o {@code TenantContext} carrega quando a
     * entrada foi cacheada (o {@code @Cacheable} só grava quando há tenant no contexto).</p>
     *
     * Idempotent: YES — invalidar duas vezes é inócuo.
     * Side Effects: remoção de entrada de cache.
     * Tenant-aware: YES — recebe o tenant explicitamente.
     */
    public void invalidarCacheMetadados(UUID atletaId, UUID tenantId) {
        if (tenantId == null) {
            return;
        }
        Cache cache = cacheManager.getCache(CACHE_METADADOS);
        if (cache != null) {
            cache.evict(atletaId + "_" + tenantId);
        }
    }

    /**
     * Registra que um recálculo foi abortado, para que o estado deixe de ser silencioso.
     *
     * <p>A métrica de sucesso da change é "zero atletas com histórico parcial silencioso". Sem este
     * contador, esse zero seria uma afirmação que ninguém consegue verificar depois do deploy — a
     * leitura correta é o contador em zero <b>com</b> recálculos ocorrendo.</p>
     *
     * Idempotent: NO — cada chamada incrementa.
     * Side Effects: incremento de contador Micrometer.
     * Tenant-aware: NO
     *
     * @param fase onde o recálculo parou: {@code blocos} ou {@code metadados}
     */
    public void registrarAborto(String fase) {
        Counter.builder(METRICA_ABORTADO)
                .description("Recálculos de histórico TSB que abortaram antes de concluir")
                .tag("fase", fase)
                .register(meterRegistry)
                .increment();
    }
}
