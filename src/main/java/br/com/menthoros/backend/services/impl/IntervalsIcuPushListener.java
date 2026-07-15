package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.domain.workout.StructuredWorkout;
import br.com.menthoros.backend.entity.IntegracaoExterna;
import br.com.menthoros.backend.entity.PlanoSemanal;
import br.com.menthoros.backend.entity.TreinoPlanejado;
import br.com.menthoros.backend.enums.FonteDados;
import br.com.menthoros.backend.enums.StatusSincronizacao;
import br.com.menthoros.backend.events.PlanoAprovadoEvent;
import br.com.menthoros.backend.repository.IntegracaoExternaRepository;
import br.com.menthoros.backend.repository.PlanoSemanalRepository;
import br.com.menthoros.backend.repository.TreinoPlanejadoRepository;
import br.com.menthoros.backend.services.IntervalsIcuConnectionService;
import br.com.menthoros.backend.services.PushResult;
import br.com.menthoros.backend.services.WorkoutChannel;
import br.com.menthoros.backend.services.helper.IntervalsIcuWorkoutConverter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

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
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IntervalsIcuPushListener {

    private static final String PLATAFORMA = FonteDados.INTERVALS_ICU.name();

    private final IntervalsIcuConnectionService connectionService;
    private final IntervalsIcuWorkoutConverter converter;
    private final WorkoutChannel workoutChannel;
    private final PlanoSemanalRepository planoSemanalRepository;
    private final TreinoPlanejadoRepository treinoPlanejadoRepository;
    private final IntegracaoExternaRepository integracaoExternaRepository;

    /**
     * Processa o push de todos os treinos exportáveis do plano aprovado para o intervals.icu.
     *
     * <p><b>Idempotente:</b> YES — cada execução reclama (claim) apenas treinos ainda não
     * sincronizados por outro worker e re-envia o mesmo {@code externalId} determinístico do
     * {@link StructuredWorkout}; re-executar após falha parcial converge para o mesmo estado final
     * (upsert no canal externo).
     * <p><b>Side Effects:</b> chamada HTTP externa (push e remoção de órfãos no intervals.icu) +
     * atualização de {@link TreinoPlanejado} (status/tentativas/externalId) e, em erro de
     * autenticação, de {@link IntegracaoExterna#getLastSyncError()}.
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

        for (TreinoPlanejado treinoOrigem : treinos) {
            try {
                processarTreino(treinoOrigem, tenantId, conexao, externalIdsAtuais, autenticacaoFalhou);
            } catch (Exception e) {
                // Regra 6: erro em um treino não pode abortar o processamento dos demais.
                log.error("Erro inesperado ao processar push do treino {}: {}",
                        treinoOrigem.getId(), e.getMessage(), e);
            }
        }

        if (autenticacaoFalhou[0]) {
            conexao.setLastSyncError("Falha de autenticação intervals.icu ao sincronizar plano " + planoId);
            integracaoExternaRepository.save(conexao);
        }

        workoutChannel.removerOrfaos(conexao, plano.getSemanaInicio(), plano.getSemanaFim(), externalIdsAtuais);
    }

    private void processarTreino(TreinoPlanejado treinoOrigem, UUID tenantId, IntegracaoExterna conexao,
                                  Set<String> externalIdsAtuais, boolean[] autenticacaoFalhou) {
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

        // Regra 4: treino não exportável é pulado sem erro e sem qualquer mutação de estado.
        Optional<StructuredWorkout> workoutOpt = converter.converter(treino);
        if (workoutOpt.isEmpty()) {
            return;
        }

        // Regra 3: claim atômico — transição para SINCRONIZANDO persistida ANTES da chamada de rede.
        treino.registrarTentativaSincronizacao();
        treino.setStatusSincronizacao(StatusSincronizacao.SINCRONIZANDO);
        try {
            treinoPlanejadoRepository.saveAndFlush(treino);
        } catch (OptimisticLockingFailureException e) {
            log.info("Claim de sincronização perdido para o treino {}: outro worker assumiu", treinoId);
            if (treino.getExternalId() != null) {
                externalIdsAtuais.add(treino.getExternalId());
            }
            return;
        }

        PushResult resultado = workoutChannel.push(conexao, workoutOpt.get(), parseEventId(treino.getExternalId()));

        // Regra 5: sucesso marca sincronizado + externalId; falha grava o erro e, ao atingir o
        // limite de tentativas, escala para ERRO_PERMANENTE (não reprocessável automaticamente).
        if (resultado.sucesso()) {
            treino.marcarComoSincronizado(PLATAFORMA);
            treino.setExternalId(String.valueOf(resultado.eventId()));
        } else {
            treino.marcarErroSincronizacao(resultado.statusErro(), resultado.mensagem());
            if (treino.atingiuLimiteTentativas()) {
                treino.setStatusSincronizacao(StatusSincronizacao.ERRO_PERMANENTE);
            }
            if (resultado.statusErro() == StatusSincronizacao.ERRO_AUTENTICACAO) {
                autenticacaoFalhou[0] = true;
            }
        }
        treinoPlanejadoRepository.save(treino);

        if (treino.getExternalId() != null) {
            externalIdsAtuais.add(treino.getExternalId());
        }
    }

    private Long parseEventId(String externalId) {
        if (externalId == null) {
            return null;
        }
        try {
            return Long.valueOf(externalId);
        } catch (NumberFormatException e) {
            log.warn("externalId '{}' não é um eventId numérico válido, tratando como primeiro push", externalId);
            return null;
        }
    }
}
