package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.entity.IntegracaoExterna;
import br.com.menthoros.backend.entity.PlanoSemanal;
import br.com.menthoros.backend.entity.TreinoPlanejado;
import br.com.menthoros.backend.enums.StatusSincronizacao;
import br.com.menthoros.backend.events.PlanoAprovadoEvent;
import br.com.menthoros.backend.repository.IntegracaoExternaRepository;
import br.com.menthoros.backend.repository.PlanoSemanalRepository;
import br.com.menthoros.backend.repository.TreinoPlanejadoRepository;
import br.com.menthoros.backend.services.IntervalsIcuConnectionService;
import br.com.menthoros.backend.services.WorkoutChannel;
import br.com.menthoros.backend.services.impl.IntervalsIcuPushProcessor.ProcessamentoResultado;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Escuta {@link PlanoAprovadoEvent} e empurra os treinos exportáveis do plano para o intervals.icu,
 * um treino por vez, com claim atômico e reconciliação de eventos órfãos ao final.
 *
 * <p>Roda fora da transação de aprovação (AFTER_COMMIT + REQUIRES_NEW): a aprovação do plano nunca
 * falha por causa do push (ver {@code PlanoReviewServiceImplTest#naoInterageComWorkoutChannelSincronamente}).
 *
 * <p>O claim atômico e a marcação de resultado por treino vivem em {@link IntervalsIcuPushProcessor},
 * compartilhado com {@link IntervalsIcuRetrySchedulerImpl}. Este listener resolve o que é específico
 * do gatilho de aprovação: recarregar plano/treino frescos, validar o tenant contra o evento e
 * reconciliar órfãos ao final.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IntervalsIcuPushListener {

    private final IntervalsIcuConnectionService connectionService;
    private final IntervalsIcuPushProcessor pushProcessor;
    private final WorkoutChannel workoutChannel;
    private final PlanoSemanalRepository planoSemanalRepository;
    private final TreinoPlanejadoRepository treinoPlanejadoRepository;
    private final IntegracaoExternaRepository integracaoExternaRepository;

    /**
     * Processa o push de todos os treinos exportáveis do plano aprovado para o intervals.icu.
     *
     * <p><b>Idempotente:</b> YES — cada execução reclama (claim) apenas treinos ainda não
     * sincronizados por outro worker e re-envia o mesmo {@code externalId} determinístico do
     * treino estruturado; re-executar após falha parcial converge para o mesmo estado final
     * (upsert no canal externo).
     * <p><b>Side Effects:</b> chamada HTTP externa (push e remoção de órfãos no intervals.icu) +
     * atualização de {@link TreinoPlanejado} (status/tentativas/externalId) e, quando houver ao
     * menos um push bem-sucedido no lote, de {@link IntegracaoExterna#getUltimaSincronizacao()};
     * em erro de autenticação, de {@link IntegracaoExterna#getLastSyncError()}.
     * <p><b>Tenant-aware:</b> YES — todas as queries usam {@code event.tenantId()}; um treino cujo
     * tenant não bate com o evento é ignorado com log de segurança.
     *
     * @param event evento de aprovação do plano
     */
    @Async("intervalsIcuPushExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onPlanoAprovado(PlanoAprovadoEvent event) {
        UUID planoId = event.planoId();
        UUID atletaId = event.atletaId();
        UUID tenantId = event.tenantId();

        Optional<IntegracaoExterna> conexaoOpt = connectionService.conexaoAtiva(atletaId, tenantId);
        if (conexaoOpt.isEmpty()) {
            log.info("Push intervals.icu ignorado: sem conexão ativa. planoId={}, atletaId={}", planoId, atletaId);
            return;
        }
        IntegracaoExterna conexao = conexaoOpt.get();

        // Regra 2: plano e treinos são sempre recarregados frescos aqui — nunca usar a instância
        // da transação pai que publicou o evento (o @Version só protege entidade fresca).
        PlanoSemanal plano = planoSemanalRepository.findByIdAndTenantId(planoId, tenantId).orElse(null);
        if (plano == null) {
            log.warn("Push intervals.icu abortado: plano {} não encontrado no tenant {}", planoId, tenantId);
            return;
        }
        List<TreinoPlanejado> treinos = plano.getTreinosPlanejados();
        if (treinos == null || treinos.isEmpty()) {
            return;
        }

        Set<String> externalIdsAtuais = new HashSet<>();
        boolean[] autenticacaoFalhou = {false};
        boolean[] algumPushComSucesso = {false};

        for (TreinoPlanejado treinoOrigem : treinos) {
            try {
                processarTreino(treinoOrigem, tenantId, conexao, externalIdsAtuais, autenticacaoFalhou,
                        algumPushComSucesso);
            } catch (Exception e) {
                // Regra 6: erro em um treino não pode abortar o processamento dos demais.
                log.error("Erro inesperado ao processar push do treino {}: {}",
                        treinoOrigem.getId(), e.getMessage(), e);
            }
        }

        boolean precisaSalvarConexao = false;
        if (algumPushComSucesso[0]) {
            conexao.setUltimaSincronizacao(Instant.now());
            precisaSalvarConexao = true;
        }
        if (autenticacaoFalhou[0]) {
            conexao.setLastSyncError("Falha de autenticação intervals.icu ao sincronizar plano " + planoId);
            precisaSalvarConexao = true;
        }
        if (precisaSalvarConexao) {
            integracaoExternaRepository.save(conexao);
        }

        workoutChannel.removerOrfaos(conexao, plano.getSemanaInicio(), plano.getSemanaFim(), externalIdsAtuais);
    }

    private void processarTreino(TreinoPlanejado treinoOrigem, UUID tenantId, IntegracaoExterna conexao,
                                  Set<String> externalIdsAtuais, boolean[] autenticacaoFalhou,
                                  boolean[] algumPushComSucesso) {
        UUID treinoId = treinoOrigem.getId();

        // Regra 9: tenant do treino (herdado do plano) deve bater com o tenant do evento.
        if (!tenantId.equals(treinoOrigem.getTenantId())) {
            log.error("SECURITY: treino {} pertence a tenant diferente do evento. esperado={}, encontrado={}",
                    treinoId, tenantId, treinoOrigem.getTenantId());
            return;
        }

        TreinoPlanejado treino = treinoPlanejadoRepository.findByIdAndTenantId(treinoId, tenantId).orElse(null);
        if (treino == null) {
            return;
        }

        ProcessamentoResultado resultado = pushProcessor.processar(treino, conexao);

        // Regra 4: treino não exportável (NAO_EXPORTAVEL) é pulado sem qualquer mutação de estado
        // e sem entrar no conjunto de externalIds atuais — o antigo evento, se houver, vira órfão
        // (ex.: coach transformou o treino em DESCANSO e o evento antigo precisa ser reconciliado).
        if (resultado == ProcessamentoResultado.NAO_EXPORTAVEL) {
            return;
        }

        if (resultado == ProcessamentoResultado.PROCESSADO_ERRO_AUTENTICACAO) {
            autenticacaoFalhou[0] = true;
        }

        if (treino.getStatusSincronizacao() == StatusSincronizacao.SINCRONIZADO) {
            algumPushComSucesso[0] = true;
        }

        // Regra 7: o external_id do evento no intervals.icu é SEMPRE "menthoros-<treinoId>" — o
        // adapter (IntervalsIcuAdapter#removerOrfaos) compara contra esse valor canônico, nunca
        // contra treino.getExternalId() (que após um push bem-sucedido vira o id numérico do
        // evento). TODO treino exportável entra no set — inclusive claim perdido e erro no push:
        // o evento antigo dele continua válido e não pode ser reconciliado como órfão.
        externalIdsAtuais.add("menthoros-" + treinoId);
    }
}
