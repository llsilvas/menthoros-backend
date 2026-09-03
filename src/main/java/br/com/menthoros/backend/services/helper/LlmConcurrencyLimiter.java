package br.com.menthoros.backend.services.helper;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.function.Supplier;

/**
 * Limita quantas chamadas <em>reais</em> ao LLM ficam em voo simultaneamente, em três faixas
 * (fair-llm-concurrency-per-tenant):
 *
 * <ul>
 *   <li><b>Global</b> ({@code app.batch-plan.llm-concorrencia}): teto contra o provedor, vale
 *       para lote e interativo somados.</li>
 *   <li><b>Por assessoria no lote</b> ({@code app.batch-plan.llm-concorrencia-por-tenant}): com
 *       vários lotes disparados na mesma janela, nenhuma assessoria monopoliza a fila — todas
 *       progridem a até {@code cap} gerações por vez.</li>
 *   <li><b>Reserva interativa</b> ({@code app.batch-plan.llm-reserva-interativa}): permits do
 *       global que o lote não pode ocupar, para o coach que clica "gerar" durante um lote não
 *       esperar a fila drenar. O interativo pode usar a capacidade ociosa do lote; o contrário,
 *       nunca.</li>
 * </ul>
 *
 * <p><b>Ordem de aquisição do lote, sempre esta:</b> tenant → capacidade do lote
 * ({@code global − reserva}) → global; release inverso. O interativo adquire só o global. Sem
 * ciclo de espera possível: o interativo nunca segura tenant/capacidade, e o lote nunca adquire
 * fora da ordem.
 *
 * <p>Os três semáforos são <b>justos</b> (FIFO): o cap por tenant é o que distribui a vazão entre
 * assessorias; a justiça do semáforo só elimina o barging — sem ela, um waiter poderia perder a
 * corrida pelo permit indefinidamente sob carga contínua. Com ~10 permits o custo é desprezível.
 *
 * <p><b>Reentrante por thread:</b> uma geração vinda do lote já segura permits quando passa pela
 * fase 2 do {@code PlanoServiceImpl}; o wrap interativo detecta isso por {@link ThreadLocal} e
 * vira no-op — sem dupla aquisição, sem deadlock com global pequeno.
 *
 * <p><b>Risco aceito (proposal, Open Questions):</b> a faixa interativa não tem cap por tenant —
 * é um humano clicando; abuso do endpoint é problema de rate-limiting HTTP, não deste limiter.
 * Muitos cliques interativos de um mesmo tenant podem ocupar o global e atrasar o lote.
 *
 * <p>Os semáforos são por instância da JVM (não distribuídos): com escala horizontal, o teto real
 * vira {@code permits × nº de instâncias}. O mapa por tenant não é limpo — cresce com o nº de
 * assessorias vivas na JVM, o que é irrelevante nessa escala (registrado na change).
 */
@Component
public class LlmConcurrencyLimiter {

    private static final boolean FAIR = true;

    private final Semaphore global;
    private final Semaphore capacidadeLote;
    private final int capPorTenant;
    private final ConcurrentHashMap<UUID, Semaphore> porTenant = new ConcurrentHashMap<>();
    private final ThreadLocal<Boolean> permitsDoLoteEmPosse = ThreadLocal.withInitial(() -> Boolean.FALSE);

    public LlmConcurrencyLimiter(
            @Value("${app.batch-plan.llm-concorrencia:4}") int permits,
            @Value("${app.batch-plan.llm-concorrencia-por-tenant:2}") int capPorTenant,
            @Value("${app.batch-plan.llm-reserva-interativa:1}") int reservaInterativa) {
        if (permits < 1) {
            throw new IllegalArgumentException("llm-concorrencia deve ser >= 1 (recebido: " + permits + ")");
        }
        if (capPorTenant < 1) {
            throw new IllegalArgumentException("llm-concorrencia-por-tenant deve ser >= 1 (recebido: " + capPorTenant + ")");
        }
        if (reservaInterativa < 0 || reservaInterativa >= permits) {
            throw new IllegalArgumentException("llm-reserva-interativa deve estar em [0, llm-concorrencia): recebido "
                    + reservaInterativa + " com llm-concorrencia " + permits);
        }
        this.global = new Semaphore(permits, FAIR);
        this.capacidadeLote = new Semaphore(permits - reservaInterativa, FAIR);
        this.capPorTenant = capPorTenant;
    }

    /**
     * Faixa do lote: chamada ao LLM de uma geração em lote, limitada pelo cap da assessoria, pela
     * capacidade do lote e pelo global — nesta ordem.
     *
     * Idempotent: NÃO — cada invocação executa a chamada (billable) recebida.
     * Side Effects: os do {@code Supplier} (chamada externa ao LLM); o limiter em si só bloqueia.
     * Tenant-aware: YES — o cap é do {@code tenantId} recebido, explícito.
     *
     * @throws InterruptedException se a thread for interrompida enquanto aguarda um permit
     */
    public <T> T executarLote(UUID tenantId, Supplier<T> chamadaLlm) throws InterruptedException {
        Semaphore tenant = porTenant.computeIfAbsent(tenantId, id -> new Semaphore(capPorTenant, FAIR));
        tenant.acquire();
        try {
            capacidadeLote.acquire();
            try {
                global.acquire();
                permitsDoLoteEmPosse.set(Boolean.TRUE);
                try {
                    return chamadaLlm.get();
                } finally {
                    permitsDoLoteEmPosse.remove();
                    global.release();
                }
            } finally {
                capacidadeLote.release();
            }
        } finally {
            tenant.release();
        }
    }

    /**
     * Faixa interativa: chamada ao LLM disparada por um clique do treinador. Só o global — pode
     * usar a capacidade ociosa do lote, e a reserva garante que nunca espera o lote drenar.
     * No-op quando a thread já segura permits do lote (reentrância).
     *
     * Idempotent: NÃO — cada invocação executa a chamada (billable) recebida.
     * Side Effects: os do {@code Supplier} (chamada externa ao LLM); o limiter em si só bloqueia.
     * Tenant-aware: NO — sem cap por tenant nesta faixa (risco aceito; ver JavaDoc da classe).
     *
     * @throws InterruptedException se a thread for interrompida enquanto aguarda um permit
     */
    public <T> T executarInterativo(Supplier<T> chamadaLlm) throws InterruptedException {
        if (permitsDoLoteEmPosse.get()) {
            return chamadaLlm.get();
        }
        global.acquire();
        try {
            return chamadaLlm.get();
        } finally {
            global.release();
        }
    }
}
