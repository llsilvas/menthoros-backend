package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.entity.IntegracaoExterna;
import br.com.menthoros.backend.events.WorkoutEditedAfterApprovalEvent;
import br.com.menthoros.backend.repository.IntegracaoExternaRepository;
import br.com.menthoros.backend.services.GarminConnectionService;
import br.com.menthoros.backend.services.impl.IntervalsIcuPushProcessor.ProcessamentoResultado;
import br.com.menthoros.backend.services.impl.IntervalsIcuPushProcessor.ResultadoPush;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Instant;
import java.util.Optional;

/**
 * Empurra ao canal só o treino editado em plano aprovado (coach-editar-treino-aprovado, D2). Não
 * republica {@code PlanoAprovadoEvent}: isso reprocessaria a semana inteira, a reconciliação de
 * órfãos e o nudge anti-debounce por causa de um treino.
 *
 * <p>Como o {@link IntervalsIcuPushListener}, é orquestrador sem transação própria: o claim e a
 * marcação acontecem em TXs curtas dentro do {@link IntervalsIcuPushProcessor}. Qualquer falha aqui
 * deixa o treino em {@code AGUARDANDO_RETRY}, que o scheduler de retry recupera.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorkoutEditedPushListener {

    private final GarminConnectionService connectionService;
    private final IntervalsIcuPushProcessor pushProcessor;
    private final IntegracaoExternaRepository integracaoExternaRepository;

    /**
     * <p><b>Idempotente:</b> YES — o claim do processor é condicional e o push é upsert pelo
     * {@code externalId}.
     * <p><b>Side Effects:</b> chamada HTTP externa (push) + atualização do treino (via processor) e
     * da conexão (última sincronização ou erro de autenticação).
     * <p><b>Tenant-aware:</b> YES — conexão e treino resolvidos com {@code event.tenantId()}.
     */
    @Async("intervalsIcuPushExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onWorkoutEdited(WorkoutEditedAfterApprovalEvent event) {
        // Canal resolvido de novo: a conexão pode ter caído entre o commit do PATCH e agora.
        Optional<IntegracaoExterna> conexaoOpt = connectionService.conexaoDeCanal(event.atletaId(), event.tenantId());
        if (conexaoOpt.isEmpty()) {
            log.info("Push do treino editado ignorado: sem conexão ativa. treinoId={}, tenantId={}",
                    event.treinoId(), event.tenantId());
            return;
        }
        IntegracaoExterna conexao = conexaoOpt.get();

        ResultadoPush resultado;
        try {
            resultado = pushProcessor.processar(event.treinoId(), event.tenantId(), conexao);
        } catch (Exception e) {
            log.error("Erro inesperado no push do treino editado {}: {}", event.treinoId(), e.getMessage(), e);
            return;
        }

        if (resultado.tipo() == ProcessamentoResultado.PROCESSADO_SUCESSO) {
            conexao.setUltimaSincronizacao(Instant.now());
            integracaoExternaRepository.save(conexao);
        } else if (resultado.tipo() == ProcessamentoResultado.PROCESSADO_ERRO_AUTENTICACAO) {
            conexao.setLastSyncError("Falha de autenticação " + conexao.getPlataforma().getLabel()
                    + " ao sincronizar o treino " + event.treinoId());
            integracaoExternaRepository.save(conexao);
        }
        log.info("Push do treino editado: treinoId={}, tenantId={}, resultado={}",
                event.treinoId(), event.tenantId(), resultado.tipo());
    }
}
