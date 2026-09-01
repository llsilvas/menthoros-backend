package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.AbstractIntegrationTest;
import br.com.menthoros.backend.dto.input.BatchGeracaoPlanoInputDto;
import br.com.menthoros.backend.dto.llm.PlanoSemanalLlmDto;
import br.com.menthoros.backend.dto.llm.TreinoPlanejadoLlmDto;
import br.com.menthoros.backend.dto.output.BatchJobStatusOutputDto;
import br.com.menthoros.backend.entity.Assessoria;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.enums.AtletaStatus;
import br.com.menthoros.backend.enums.BatchJobStatus;
import br.com.menthoros.backend.enums.DiaSemana;
import br.com.menthoros.backend.enums.ModoGeracaoPlano;
import br.com.menthoros.backend.enums.NivelExperiencia;
import br.com.menthoros.backend.enums.PlanoAssessoria;
import br.com.menthoros.backend.multitenancy.TenantContext;
import br.com.menthoros.backend.repository.AssessoriaRepository;
import br.com.menthoros.backend.repository.AtletaRepository;
import br.com.menthoros.backend.repository.PlanoSemanalRepository;
import br.com.menthoros.backend.services.BatchPlanService;
import br.com.menthoros.backend.services.IaService;
import br.com.menthoros.backend.services.PlanoService;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Prova de carga de refactor-llm-call-outside-transaction (CA1 + CA2) no cenário das assessorias
 * fundadoras (design.md D0): <b>10 assessorias × 10 atletas = 100 gerações</b> disparadas na mesma
 * janela, pelo lote real ({@code iniciarLote} → {@code @Async processarLote} →
 * {@code LlmConcurrencyLimiter}).
 *
 * <p>Condições deliberadamente hostis: pool do Hikari com <b>2</b> conexões e
 * {@code llm-concorrencia} = <b>10</b>. Antes desta change cada chamada ao LLM segurava uma
 * conexão pela duração inteira — com 10 em voo e pool 2, o lote travava no
 * {@code connection-timeout} e derrubava o app junto. Depois dela, o LLM roda sem conexão em
 * posse e o pool volta a ser transiente.
 *
 * <p>O LLM é um stub com latência fixa que também instrumenta: quantas chamadas estão em voo,
 * se há transação ativa na thread (CA1) e quantas conexões o pool tem ativas naquele instante.
 */
@DisplayName("Lote das fundadoras — 10 assessorias × 10 atletas com pool 2 e concorrência 10")
@TestPropertySource(properties = {
        "spring.datasource.hikari.maximum-pool-size=2",
        "spring.datasource.hikari.connection-timeout=3000",
        "app.batch-plan.llm-concorrencia=10",
        "onboarding.migrate-existing.enabled=false"
})
class LotePlanosFundadorasIT extends AbstractIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(LotePlanosFundadorasIT.class);

    private static final int ASSESSORIAS = 10;
    private static final int ATLETAS_POR_ASSESSORIA = 10;
    private static final int POOL = 2;
    private static final int LLM_CONCORRENCIA = 10;
    private static final long LATENCIA_LLM_MS = 250;

    @MockitoBean private IaService iaService;

    @Autowired private BatchPlanService batchPlanService;
    @Autowired private PlanoService planoService;
    @Autowired private AssessoriaRepository assessoriaRepository;
    @Autowired private AtletaRepository atletaRepository;
    @Autowired private PlanoSemanalRepository planoSemanalRepository;
    @Autowired private TransactionTemplate transactionTemplate;
    @Autowired private DataSource dataSource;

    private final AtomicInteger llmEmVoo = new AtomicInteger();
    private final AtomicInteger picoLlmEmVoo = new AtomicInteger();
    private final AtomicInteger chamadasComTransacaoAtiva = new AtomicInteger();
    private final AtomicInteger picoConexoesAtivasDuranteLlm = new AtomicInteger();
    private final AtomicInteger totalChamadasLlm = new AtomicInteger();

    @BeforeEach
    void instrumentarLlm() {
        when(iaService.geraPlanoSemanalAvancado(any(), any(), any(), any(), any(), any(), any()))
                .thenAnswer(inv -> {
                    int emVoo = llmEmVoo.incrementAndGet();
                    picoLlmEmVoo.accumulateAndGet(emVoo, Math::max);
                    totalChamadasLlm.incrementAndGet();
                    if (TransactionSynchronizationManager.isActualTransactionActive()) {
                        chamadasComTransacaoAtiva.incrementAndGet();
                    }
                    picoConexoesAtivasDuranteLlm.accumulateAndGet(conexoesAtivas(), Math::max);
                    try {
                        Thread.sleep(LATENCIA_LLM_MS);
                        return planoDeUmTreino();
                    } finally {
                        llmEmVoo.decrementAndGet();
                    }
                });
    }

    @AfterEach
    void limpar() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("100 planos em 10 lotes simultâneos: tudo persiste, 10 LLM em voo, zero transação dentro do LLM")
    void dezAssessoriasDezAtletas() throws Exception {
        Map<UUID, List<UUID>> atletasPorAssessoria = new LinkedHashMap<>();
        for (int i = 1; i <= ASSESSORIAS; i++) {
            Assessoria assessoria = criarAssessoria("Fundadora " + i);
            List<UUID> ids = new ArrayList<>();
            for (int j = 1; j <= ATLETAS_POR_ASSESSORIA; j++) {
                ids.add(criarAtleta(assessoria, "Atleta " + i + "." + j).getId());
            }
            atletasPorAssessoria.put(assessoria.getId(), ids);
        }

        // Todos os treinadores clicam "gerar lote" na mesma janela (domingo à noite).
        Map<UUID, UUID> jobPorAssessoria = new LinkedHashMap<>();
        Instant inicio = Instant.now();
        for (Map.Entry<UUID, List<UUID>> e : atletasPorAssessoria.entrySet()) {
            UUID tenantId = e.getKey();
            TenantContext.setTenantId(tenantId);
            jobPorAssessoria.put(tenantId, batchPlanService.iniciarLote(
                    new BatchGeracaoPlanoInputDto(e.getValue(), ModoGeracaoPlano.PROXIMA_SEMANA), tenantId).jobId());
            TenantContext.clear();
        }

        Map<UUID, BatchJobStatusOutputDto> statusFinal = new LinkedHashMap<>();
        Map<UUID, Duration> duracaoPorAssessoria = new LinkedHashMap<>();
        Instant limite = inicio.plusSeconds(120);
        while (statusFinal.size() < ASSESSORIAS && Instant.now().isBefore(limite)) {
            for (Map.Entry<UUID, UUID> e : jobPorAssessoria.entrySet()) {
                if (statusFinal.containsKey(e.getKey())) {
                    continue;
                }
                BatchJobStatusOutputDto status = batchPlanService.consultarStatus(e.getValue(), e.getKey());
                if (status.status() == BatchJobStatus.CONCLUIDO || status.status() == BatchJobStatus.CONCLUIDO_COM_ERROS) {
                    statusFinal.put(e.getKey(), status);
                    duracaoPorAssessoria.put(e.getKey(), Duration.between(inicio, Instant.now()));
                }
            }
            Thread.sleep(100);
        }
        Duration total = Duration.between(inicio, Instant.now());

        // --- relatório (lido pelo founder na task 5.1) ---
        long teorico = (long) ASSESSORIAS * ATLETAS_POR_ASSESSORIA * LATENCIA_LLM_MS / LLM_CONCORRENCIA;
        log.info("[fundadoras] 100 planos em {} ms (teórico com concorrência {}: {} ms); pico LLM em voo = {}; " +
                        "chamadas com transação ativa = {}; pico de conexões ativas durante o LLM = {} (pool {})",
                total.toMillis(), LLM_CONCORRENCIA, teorico, picoLlmEmVoo.get(),
                chamadasComTransacaoAtiva.get(), picoConexoesAtivasDuranteLlm.get(), POOL);
        int n = 0;
        for (Map.Entry<UUID, Duration> e : duracaoPorAssessoria.entrySet()) {
            BatchJobStatusOutputDto s = statusFinal.get(e.getKey());
            log.info("[fundadoras] assessoria #{} terminou em {} ms — status {}, gerados {}, erros {}",
                    ++n, e.getValue().toMillis(), s.status(), s.gerados(), s.erros());
        }

        // --- asserções ---
        assertThat(statusFinal).as("todos os 10 lotes terminaram dentro do limite").hasSize(ASSESSORIAS);
        assertThat(statusFinal.values())
                .as("nenhum lote terminou com erro — com pool 2 e 10 LLM em voo, antes da change isso era connection-timeout")
                .allSatisfy(s -> {
                    assertThat(s.status()).isEqualTo(BatchJobStatus.CONCLUIDO);
                    assertThat(s.gerados()).isEqualTo(ATLETAS_POR_ASSESSORIA);
                    assertThat(s.erros()).isZero();
                });
        for (Map.Entry<UUID, List<UUID>> e : atletasPorAssessoria.entrySet()) {
            for (UUID atletaId : e.getValue()) {
                assertThat(planoSemanalRepository.findAtivosPorAtleta(atletaId, e.getKey()))
                        .as("um plano ativo por atleta").hasSize(1);
            }
        }
        assertThat(totalChamadasLlm.get()).as("uma chamada de LLM por atleta").isEqualTo(ASSESSORIAS * ATLETAS_POR_ASSESSORIA);
        assertThat(chamadasComTransacaoAtiva.get()).as("CA1: nenhuma chamada ao LLM dentro de transação").isZero();
        assertThat(picoLlmEmVoo.get())
                .as("CA2: a concorrência do LLM é limitada pelo semáforo, não pelo pool")
                .isEqualTo(LLM_CONCORRENCIA);
        assertThat(picoConexoesAtivasDuranteLlm.get())
                .as("as conexões ativas nunca passam do pool — e o pool é menor que a concorrência do LLM")
                .isLessThanOrEqualTo(POOL);
    }

    @Test
    @DisplayName("A/B do acoplamento: com o LLM dentro da transação só `pool` gerações chegam ao LLM juntas; fora, `llm-concorrencia`")
    void acoplamentoAntesEDepois() throws Exception {
        int chegamDentro = quantasChegamAoLlmAoMesmoTempo(true);
        int chegamFora = quantasChegamAoLlmAoMesmoTempo(false);

        log.info("[fundadoras] gerações que chegam ao LLM ao mesmo tempo — dentro da transação (antes da change): {}; fora (depois): {}",
                chegamDentro, chegamFora);

        assertThat(chegamDentro)
                .as("simulando o código antigo, cada geração segura uma conexão e o pool de %d é o teto", POOL)
                .isLessThanOrEqualTo(POOL);
        assertThat(chegamFora)
                .as("com a fronteira aberta, todas as %d gerações estão no LLM ao mesmo tempo com o mesmo pool", LLM_CONCORRENCIA)
                .isEqualTo(LLM_CONCORRENCIA);
    }

    /**
     * Dispara {@code LLM_CONCORRENCIA} gerações simultâneas de um tenant e conta quantas estão
     * dentro do stub do LLM ao mesmo tempo. Com {@code dentroDaTransacao}, cada geração é
     * envolvida por um {@code TransactionTemplate} — o que o antigo {@code @Transactional} de
     * {@code gerarPlanoTreino} fazia — e as que não conseguem conexão ficam presas no
     * {@code connection-timeout} de 3s enquanto as outras seguram o pool.
     */
    private int quantasChegamAoLlmAoMesmoTempo(boolean dentroDaTransacao) throws Exception {
        Assessoria assessoria = criarAssessoria(dentroDaTransacao ? "A/B dentro" : "A/B fora");
        List<UUID> atletas = new ArrayList<>();
        for (int j = 1; j <= LLM_CONCORRENCIA; j++) {
            atletas.add(criarAtleta(assessoria, "Atleta A/B " + j).getId());
        }
        UUID tenantId = assessoria.getId();

        CountDownLatch segura = new CountDownLatch(1);
        AtomicInteger noLlm = new AtomicInteger();
        when(iaService.geraPlanoSemanalAvancado(any(), any(), any(), any(), any(), any(), any()))
                .thenAnswer(inv -> {
                    noLlm.incrementAndGet();
                    segura.await(10, TimeUnit.SECONDS);
                    return planoDeUmTreino();
                });

        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        List<Future<?>> futuros = new ArrayList<>();
        try {
            for (UUID atletaId : atletas) {
                futuros.add(executor.submit(() -> {
                    TenantContext.setTenantId(tenantId);
                    try {
                        if (dentroDaTransacao) {
                            transactionTemplate.execute(s -> planoService.gerarPlanoTreino(atletaId, ModoGeracaoPlano.PROXIMA_SEMANA));
                        } else {
                            planoService.gerarPlanoTreino(atletaId, ModoGeracaoPlano.PROXIMA_SEMANA);
                        }
                    } catch (Exception e) {
                        // No modo "dentro", as gerações sem conexão morrem no connection-timeout — é o
                        // comportamento antigo que se quer expor, não um erro do teste.
                        log.debug("[fundadoras] geração falhou ({}): {}", dentroDaTransacao ? "dentro" : "fora", e.toString());
                    } finally {
                        TenantContext.clear();
                    }
                    return null;
                }));
            }

            // Janela generosa para todas chegarem ao stub; no modo "dentro" as que não têm conexão
            // não chegam nunca — é isso que a contagem mede.
            long fim = System.currentTimeMillis() + 2000;
            while (noLlm.get() < LLM_CONCORRENCIA && System.currentTimeMillis() < fim) {
                Thread.sleep(50);
            }
            int chegaram = noLlm.get();
            segura.countDown();
            for (Future<?> f : futuros) {
                f.get(30, TimeUnit.SECONDS);
            }
            return chegaram;
        } finally {
            executor.shutdownNow();
        }
    }

    private int conexoesAtivas() {
        if (dataSource instanceof HikariDataSource hikari && hikari.getHikariPoolMXBean() != null) {
            return hikari.getHikariPoolMXBean().getActiveConnections();
        }
        return -1;
    }

    private Assessoria criarAssessoria(String nome) {
        Assessoria assessoria = new Assessoria();
        assessoria.setNome(nome);
        assessoria.setDominio("fund-" + UUID.randomUUID());
        assessoria.setPlano(PlanoAssessoria.BASIC);
        return assessoriaRepository.save(assessoria);
    }

    private Atleta criarAtleta(Assessoria assessoria, String nome) {
        Atleta atleta = new Atleta();
        atleta.setNome(nome);
        atleta.setObjetivo("Terminar os 10k");
        atleta.setNivelExperiencia(NivelExperiencia.INICIANTE);
        atleta.setAtivo(AtletaStatus.ATIVO);
        atleta.setDiasDisponiveis(List.of(DiaSemana.SEGUNDA, DiaSemana.QUARTA, DiaSemana.SABADO));
        atleta.setDiaPreferidoLongo(DiaSemana.SABADO);
        atleta.setAssessoria(assessoria);
        return atletaRepository.save(atleta);
    }

    private static PlanoSemanalLlmDto planoDeUmTreino() {
        TreinoPlanejadoLlmDto treino = new TreinoPlanejadoLlmDto(
                "SEGUNDA", "FACIL", "130-140 bpm", 40, 1.0, 4,
                "Base aeróbica", "45", 8.0, "5:40", List.of());
        return new PlanoSemanalLlmDto(8.0, 8.0, null, null, "PLANEJADO", "Semana de base", List.of(treino));
    }
}
