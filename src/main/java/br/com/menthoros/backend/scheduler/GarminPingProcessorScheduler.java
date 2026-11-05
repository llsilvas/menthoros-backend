package br.com.menthoros.backend.scheduler;

import br.com.menthoros.backend.dto.fit.FitImportResultado;
import br.com.menthoros.backend.dto.fit.FitSessionData;
import br.com.menthoros.backend.entity.GarminPing;
import br.com.menthoros.backend.entity.IntegracaoExterna;
import br.com.menthoros.backend.enums.FonteDados;
import br.com.menthoros.backend.enums.GarminPingStatus;
import br.com.menthoros.backend.exception.FitParseException;
import br.com.menthoros.backend.exception.GarminApiException;
import br.com.menthoros.backend.multitenancy.TenantContext;
import br.com.menthoros.backend.repository.GarminPingRepository;
import br.com.menthoros.backend.repository.IntegracaoExternaRepository;
import br.com.menthoros.backend.services.FitParseService;
import br.com.menthoros.backend.services.GarminClient;
import br.com.menthoros.backend.services.helper.FitTreinoPersister;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatusCode;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.ByteArrayInputStream;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Processa a fila de pings {@code activityFiles} do Garmin (garmin-activity-ingestion, D3): baixa o
 * FIT pelo {@code callbackURL}, parseia e persiste o treino realizado.
 *
 * <p>Ordem por ciclo: reclaim de {@code SYNCING} preso, expiração da janela de 24 h, lote de
 * {@link #LOTE} pendentes mais antigos primeiro. Download e parse ficam fora de transação; o claim e
 * a persistência têm transações próprias e curtas. Erro num ping não aborta o lote.
 *
 * <p>shortcut: 20 pings/min por instância e claim sem lock — premissa de uma instância só; com
 * mais de uma réplica, trocar o claim por update condicional antes de escalar.
 *
 * <p>{@code menthoros.garmin-ping-processor.enabled=false} desliga o job (ITs: os contextos
 * cacheados compartilham o Postgres e um scheduler alheio roubaria o ping do teste).
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "menthoros.garmin-ping-processor.enabled", havingValue = "true", matchIfMissing = true)
public class GarminPingProcessorScheduler {

    static final int LOTE = 20;
    static final Duration JANELA_DOWNLOAD = Duration.ofHours(24);
    static final Duration RECLAIM_APOS = Duration.ofMinutes(10);
    static final long BACKOFF_MAX_MIN = 60;

    private static final Set<Integer> LINK_CONSUMIDO = Set.of(404, 410);
    private static final Set<Integer> SEM_PERMISSAO = Set.of(401, 403, 412);

    private final GarminPingRepository pingRepository;
    private final IntegracaoExternaRepository integracaoRepository;
    private final GarminClient garminClient;
    private final FitParseService fitParseService;
    private final FitTreinoPersister fitTreinoPersister;
    private final TransactionTemplate tx;
    private final Clock clock;

    public GarminPingProcessorScheduler(GarminPingRepository pingRepository,
                                        IntegracaoExternaRepository integracaoRepository,
                                        GarminClient garminClient,
                                        FitParseService fitParseService,
                                        FitTreinoPersister fitTreinoPersister,
                                        PlatformTransactionManager transactionManager,
                                        Clock clock) {
        this.pingRepository = pingRepository;
        this.integracaoRepository = integracaoRepository;
        this.garminClient = garminClient;
        this.fitParseService = fitParseService;
        this.fitTreinoPersister = fitTreinoPersister;
        this.tx = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    /**
     * Um ciclo da fila.
     *
     * <p><b>Idempotent:</b> NO — cada download consome o link do Garmin; o dedup do treino e o
     * claim por status impedem duplicar o resultado.
     * <p><b>Side Effects:</b> External API call (download do FIT) + Database update (ping, treino,
     * {@code IntegracaoExterna.ultimaSincronizacao}).
     * <p><b>Tenant-aware:</b> YES — job de sistema; instala o {@code TenantContext} do próprio ping
     * e o limpa em {@code finally}.
     */
    @Scheduled(fixedDelayString = "PT1M", initialDelayString = "PT1M")
    public void processar() {
        Instant agora = clock.instant();
        reclamarPresos(agora);
        expirar(agora);
        List<GarminPing> lote = pingRepository.findPendingOldestFirst(
                agora, agora.minus(JANELA_DOWNLOAD), PageRequest.of(0, LOTE));
        for (GarminPing ping : lote) {
            try {
                TenantContext.setTenantId(ping.getTenantId());
                processarPing(ping);
            } catch (Exception e) {
                // Só o tipo: a mensagem pode carregar o callbackURL com o token.
                log.error("Erro inesperado no ping Garmin {}: {}", ping.getId(), e.getClass().getSimpleName());
            } finally {
                TenantContext.clear();
            }
        }
        if (!lote.isEmpty()) {
            log.info("Fila Garmin: {} ping(s) processado(s) no ciclo", lote.size());
        }
    }

    private void reclamarPresos(Instant agora) {
        for (GarminPing ping : pingRepository.findStuckSyncing(agora.minus(RECLAIM_APOS))) {
            isolado(ping, () -> {
                ping.setStatus(GarminPingStatus.PENDING);
                ping.setAttempts(ping.getAttempts() + 1);
                ping.setNextAttemptAt(agora);
                ping.setSyncingSince(null);
                pingRepository.save(ping);
                log.warn("Ping Garmin {} preso em SYNCING; devolvido à fila", ping.getId());
            });
        }
    }

    private void expirar(Instant agora) {
        for (GarminPing ping : pingRepository.findExpired(agora.minus(JANELA_DOWNLOAD))) {
            isolado(ping, () -> finalizar(ping, GarminPingStatus.ERROR, "Expirado: link de download vale 24 h", agora));
        }
    }

    /** Erro num ping da limpeza não pode abortar o ciclo: a mesma consulta o traria de volta a cada minuto. */
    private static void isolado(GarminPing ping, Runnable acao) {
        try {
            acao.run();
        } catch (RuntimeException e) {
            log.error("Erro na limpeza do ping Garmin {}: {}", ping.getId(), e.getClass().getSimpleName());
        }
    }

    private void processarPing(GarminPing ping) {
        Instant agora = clock.instant();
        ping.setStatus(GarminPingStatus.SYNCING);
        ping.setSyncingSince(agora);
        GarminPing claimed = pingRepository.save(ping);

        Optional<IntegracaoExterna> conexao = integracaoRepository.findActiveByAtletaIdAndPlataformaAndTenantId(
                claimed.getAthleteId(), FonteDados.GARMIN, claimed.getTenantId());
        if (conexao.isEmpty()) {
            finalizar(claimed, GarminPingStatus.DISCARDED, "Conexão Garmin inativa", agora);
            return;
        }

        FitSessionData dados;
        try {
            byte[] arquivo = garminClient.baixarArquivo(claimed.getCallbackUrl());
            dados = fitParseService.parse(new ByteArrayInputStream(arquivo));
        } catch (IllegalArgumentException e) {
            finalizar(claimed, GarminPingStatus.ERROR, "callbackURL inválido", agora);
            return;
        } catch (FitParseException e) {
            finalizar(claimed, GarminPingStatus.ERROR, "Arquivo FIT inválido", agora);
            return;
        } catch (GarminApiException e) {
            tratarFalhaDownload(claimed, e.getStatus(), agora);
            return;
        }

        // Treino, ping e ultimaSincronizacao na mesma TX: sem PROCESSED sem treino, nem o contrário.
        UUID conexaoId = conexao.get().getId();
        try {
            tx.executeWithoutResult(status -> {
                FitImportResultado resultado = fitTreinoPersister.persistir(
                        claimed.getAthleteId(), dados, claimed.getDeviceName());
                claimed.setCompletedWorkoutId(resultado.treino().id());
                Instant fim = clock.instant();
                finalizar(claimed, GarminPingStatus.PROCESSED, null, fim);
                integracaoRepository.atualizarUltimaSincronizacao(conexaoId, claimed.getTenantId(), fim);
            });
        } catch (RuntimeException e) {
            // O link já foi consumido: deixar em SYNCING levaria a um 410 no reclaim, com o motivo errado.
            log.error("Falha ao persistir o treino do ping Garmin {}: {}", claimed.getId(), e.getClass().getSimpleName());
            claimed.setCompletedWorkoutId(null);
            finalizar(claimed, GarminPingStatus.ERROR, "Falha ao persistir o treino", agora);
        }
    }

    private void tratarFalhaDownload(GarminPing ping, HttpStatusCode status, Instant agora) {
        int codigo = status == null ? 0 : status.value();
        if (LINK_CONSUMIDO.contains(codigo)) {
            finalizar(ping, GarminPingStatus.ERROR, "Link de download consumido ou inexistente (HTTP " + codigo + ")", agora);
        } else if (SEM_PERMISSAO.contains(codigo)) {
            finalizar(ping, GarminPingStatus.ERROR, "Sem permissão para baixar a atividade (HTTP " + codigo + ")", agora);
        } else if (status == null || codigo == 429 || status.is5xxServerError()) {
            // Sem teto de tentativas: o limite é a janela de 24 h, e uma queda do Garmin não pode queimar o arquivo.
            int tentativas = ping.getAttempts() + 1;
            ping.setAttempts(tentativas);
            ping.setStatus(GarminPingStatus.PENDING);
            ping.setSyncingSince(null);
            ping.setNextAttemptAt(agora.plus(Duration.ofMinutes(backoffMinutos(tentativas))));
            ping.setReason(status == null ? "Falha de transporte" : "HTTP " + codigo);
            pingRepository.save(ping);
        } else {
            finalizar(ping, GarminPingStatus.ERROR, "Download recusado (HTTP " + codigo + ")", agora);
        }
    }

    static long backoffMinutos(int tentativas) {
        return Math.min(1L << Math.min(tentativas, 6), BACKOFF_MAX_MIN);
    }

    private void finalizar(GarminPing ping, GarminPingStatus status, String motivo, Instant agora) {
        ping.setStatus(status);
        ping.setReason(motivo);
        ping.setSyncingSince(null);
        ping.setProcessedAt(agora);
        pingRepository.save(ping);
        log.info("Ping Garmin {} finalizado: status={}, motivo={}", ping.getId(), status, motivo);
    }
}
