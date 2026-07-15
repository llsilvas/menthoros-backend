package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.domain.workout.StructuredWorkout;
import br.com.menthoros.backend.entity.IntegracaoExterna;
import br.com.menthoros.backend.entity.TreinoPlanejado;
import br.com.menthoros.backend.enums.FonteDados;
import br.com.menthoros.backend.enums.StatusSincronizacao;
import br.com.menthoros.backend.repository.TreinoPlanejadoRepository;
import br.com.menthoros.backend.services.PushResult;
import br.com.menthoros.backend.services.WorkoutChannel;
import br.com.menthoros.backend.services.helper.IntervalsIcuWorkoutConverter;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Processa o push de um único {@link TreinoPlanejado} para o intervals.icu: conversão para
 * {@link StructuredWorkout}, claim atômico (transição condicional + {@code @Version}), chamada ao
 * {@link WorkoutChannel} e marcação do resultado.
 *
 * <p><b>Fronteira transacional (hardening):</b> cada treino é processado em transações PRÓPRIAS e
 * curtas — TX-A (reload fresco + claim) e TX-B (marcação do resultado) via
 * {@link TransactionTemplate} com {@code REQUIRES_NEW}; a chamada HTTP acontece FORA de qualquer
 * transação (nenhuma conexão de banco fica presa durante a rede). Um claim perdido
 * ({@link OptimisticLockingFailureException}) descarta apenas a TX-A daquele treino — as marcações
 * dos demais treinos do lote nunca são arrastadas por rollback alheio (CA1 da change
 * intervals-icu-push-hardening; achado convergente Claude+Codex na change-mãe).
 *
 * <p>Colaborador compartilhado entre {@link IntervalsIcuPushListener} (gatilho na aprovação do
 * plano) e {@link IntervalsIcuRetrySchedulerImpl} (retry periódico). Um throw inesperado do channel
 * após o claim nunca pode deixar o treino preso em {@code SINCRONIZANDO} — degrada para
 * {@code ERRO_TEMPORARIO} na TX-B.
 */
@Slf4j
@Component
public class IntervalsIcuPushProcessor {

    private static final String PLATAFORMA = FonteDados.INTERVALS_ICU.name();

    private final IntervalsIcuWorkoutConverter converter;
    private final WorkoutChannel workoutChannel;
    private final TreinoPlanejadoRepository treinoPlanejadoRepository;
    private final TransactionTemplate txPropria;

    public IntervalsIcuPushProcessor(IntervalsIcuWorkoutConverter converter,
                                     WorkoutChannel workoutChannel,
                                     TreinoPlanejadoRepository treinoPlanejadoRepository,
                                     PlatformTransactionManager transactionManager) {
        this.converter = converter;
        this.workoutChannel = workoutChannel;
        this.treinoPlanejadoRepository = treinoPlanejadoRepository;
        this.txPropria = new TransactionTemplate(transactionManager);
        this.txPropria.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * Valida que o tenant encontrado para o treino bate com o esperado — ponto único da validação
     * compartilhada entre {@link IntervalsIcuPushListener} (tenant do evento × tenant do treino) e
     * {@link IntervalsIcuRetrySchedulerImpl} (tenant do treino × assessoria do atleta).
     *
     * <p><b>Idempotente:</b> YES — leitura pura.
     * <p><b>Side Effects:</b> log de segurança (padrão SECURITY) quando há mismatch.
     * <p><b>Tenant-aware:</b> YES — é a própria defesa de isolamento multi-tenant.
     *
     * @return {@code true} se os tenants batem; {@code false} quando o chamador deve pular o
     *         treino sem qualquer mutação de estado
     */
    public static boolean tenantValido(UUID treinoId, UUID tenantEsperado, UUID tenantEncontrado) {
        if (Objects.equals(tenantEsperado, tenantEncontrado)) {
            return true;
        }
        log.error("SECURITY: treino {} associado a tenant divergente. esperado={}, encontrado={}",
                treinoId, tenantEsperado, tenantEncontrado);
        return false;
    }

    /**
     * Processa o push de um treino, recarregando-o fresco DENTRO da própria fronteira
     * transacional (o chamador nunca passa a entidade — só o id, já validado no tenant).
     *
     * <p><b>Idempotente:</b> YES — reclama (claim) o treino apenas se ainda não foi assumido por
     * outro worker e reenvia o mesmo {@code externalId} determinístico; reexecutar após falha
     * parcial converge para o mesmo estado final (upsert no canal externo).
     * <p><b>Side Effects:</b> chamada HTTP externa (push, fora de TX) + atualização do treino em
     * duas TXs próprias e curtas (claim; marcação de resultado). Treino sincronizado que virou
     * não exportável tem o vínculo resetado na TX-A (o evento externo vira órfão e é reconciliado
     * pelo chamador).
     * <p><b>Tenant-aware:</b> YES — reload por {@code findByIdAndTenantId} com o tenant informado.
     *
     * @param treinoId id do treino a processar
     * @param tenantId tenant dono do treino (do evento, no listener; do próprio registro, no retry)
     * @param conexao  conexão ativa do atleta com o intervals.icu
     * @return desfecho + metadados do push (evento criado × atualizado, id do evento)
     */
    public ResultadoPush processar(UUID treinoId, UUID tenantId, IntegracaoExterna conexao) {
        ClaimResultado claim;
        try {
            claim = txPropria.execute(status -> claimNaTx(treinoId, tenantId));
        } catch (OptimisticLockingFailureException e) {
            log.info("Claim de sincronização perdido para o treino {}: outro worker assumiu", treinoId);
            return ResultadoPush.simples(ProcessamentoResultado.CLAIM_PERDIDO);
        }
        if (claim.tipo != null) {
            return ResultadoPush.simples(claim.tipo);
        }

        // Rede FORA de transação — nenhuma conexão de banco presa durante o HTTP. O channel
        // promete nunca lançar; violação de contrato degrada para ERRO_TEMPORARIO na TX-B.
        PushResult resultado;
        try {
            resultado = workoutChannel.push(conexao, claim.workout, claim.eventIdArmazenado);
        } catch (Exception e) {
            log.error("Erro inesperado no push do treino {} (violação de contrato do channel): {}",
                    treinoId, e.getMessage(), e);
            resultado = PushResult.erro(StatusSincronizacao.ERRO_TEMPORARIO,
                    "Erro inesperado no push intervals.icu: " + e.getMessage());
        }

        final PushResult resultadoFinal = resultado;
        Boolean autenticacaoFalhou = txPropria.execute(status -> marcarNaTx(treinoId, tenantId, resultadoFinal));

        ProcessamentoResultado tipo;
        if (Boolean.TRUE.equals(autenticacaoFalhou)) {
            tipo = ProcessamentoResultado.PROCESSADO_ERRO_AUTENTICACAO;
        } else {
            tipo = resultadoFinal.sucesso()
                    ? ProcessamentoResultado.PROCESSADO_SUCESSO
                    : ProcessamentoResultado.PROCESSADO_ERRO;
        }
        return new ResultadoPush(tipo, resultadoFinal.sucesso() && resultadoFinal.criadoNovo(),
                resultadoFinal.eventId());
    }

    /**
     * TX-A: reload fresco, exportabilidade (com reset de vínculo stale) e claim atômico.
     * Lança {@link OptimisticLockingFailureException} para fora do template em claim perdido.
     */
    private ClaimResultado claimNaTx(UUID treinoId, UUID tenantId) {
        TreinoPlanejado treino = treinoPlanejadoRepository.findByIdAndTenantId(treinoId, tenantId).orElse(null);
        if (treino == null) {
            log.warn("Push intervals.icu pulado: treino {} não encontrado no tenant {}", treinoId, tenantId);
            return ClaimResultado.encerrado(ProcessamentoResultado.NAO_ENCONTRADO);
        }

        Optional<StructuredWorkout> workoutOpt = converter.converter(treino);
        if (workoutOpt.isEmpty()) {
            // Treino que já esteve sincronizado e virou não exportável (ex.: coach o transformou
            // em DESCANSO): o evento externo vira órfão (o chamador o reconcilia) e o estado
            // local acompanha — reset completo. Treino nunca sincronizado sai sem mutação.
            if (treino.getExternalId() != null
                    || (treino.getStatusSincronizacao() != null
                            && treino.getStatusSincronizacao().estaSincronizado())) {
                treino.resetarSincronizacao();
                treinoPlanejadoRepository.save(treino);
            }
            return ClaimResultado.encerrado(ProcessamentoResultado.NAO_EXPORTAVEL);
        }

        Long eventIdArmazenado = parseEventId(treino.getExternalId());
        treino.registrarTentativaSincronizacao();
        treino.setStatusSincronizacao(StatusSincronizacao.SINCRONIZANDO);
        treinoPlanejadoRepository.saveAndFlush(treino);
        return ClaimResultado.reclamado(workoutOpt.get(), eventIdArmazenado);
    }

    /**
     * TX-B: reload pós-claim e marcação do resultado. Ninguém mais toca treino em
     * {@code SINCRONIZANDO} (scheduler não varre; novo claim perderia no {@code @Version}).
     *
     * @return true quando o erro marcado foi especificamente de autenticação
     */
    private boolean marcarNaTx(UUID treinoId, UUID tenantId, PushResult resultado) {
        TreinoPlanejado treino = treinoPlanejadoRepository.findByIdAndTenantId(treinoId, tenantId).orElse(null);
        if (treino == null) {
            log.warn("Treino {} sumiu entre o claim e a marcação (deleção concorrente) — resultado descartado",
                    treinoId);
            return false;
        }

        boolean autenticacaoFalhou = false;
        if (resultado.sucesso()) {
            treino.marcarComoSincronizado(PLATAFORMA);
            treino.setExternalId(String.valueOf(resultado.eventId()));
        } else {
            treino.marcarErroSincronizacao(resultado.statusErro(), resultado.mensagem());
            if (treino.atingiuLimiteTentativas()) {
                treino.setStatusSincronizacao(StatusSincronizacao.ERRO_PERMANENTE);
            }
            autenticacaoFalhou = resultado.statusErro() == StatusSincronizacao.ERRO_AUTENTICACAO;
        }
        treinoPlanejadoRepository.save(treino);
        return autenticacaoFalhou;
    }

    private @Nullable Long parseEventId(@Nullable String externalId) {
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

    /** Estado interno entre a TX-A e a fase de rede. */
    private record ClaimResultado(@Nullable ProcessamentoResultado tipo,
                                  @Nullable StructuredWorkout workout,
                                  @Nullable Long eventIdArmazenado) {
        static ClaimResultado encerrado(ProcessamentoResultado tipo) {
            return new ClaimResultado(tipo, null, null);
        }

        static ClaimResultado reclamado(StructuredWorkout workout, @Nullable Long eventIdArmazenado) {
            return new ClaimResultado(null, workout, eventIdArmazenado);
        }
    }

    /**
     * Desfecho do processamento + metadados para o chamador decidir passos adicionais
     * (reconciliação de órfãos, nudge anti-debounce, agregação por lote).
     *
     * @param tipo       desfecho do processamento
     * @param criadoNovo true quando o push CRIOU um evento novo (POST) — false em atualização
     *                   (PUT), erro ou desfecho sem rede
     * @param eventId    id do evento no intervals.icu quando o push teve sucesso; null nos demais
     */
    public record ResultadoPush(ProcessamentoResultado tipo, boolean criadoNovo, @Nullable Long eventId) {
        static ResultadoPush simples(ProcessamentoResultado tipo) {
            return new ResultadoPush(tipo, false, null);
        }
    }

    /** Desfecho do processamento de um treino. */
    public enum ProcessamentoResultado {
        /** Treino não encontrado no tenant (deleção concorrente) — nenhuma mutação. */
        NAO_ENCONTRADO,
        /** Treino não é exportável (ex.: virou DESCANSO) — vínculo stale resetado se existia. */
        NAO_EXPORTAVEL,
        /** Outro worker já reclamou o treino nesta janela — desistência silenciosa. */
        CLAIM_PERDIDO,
        /** Push bem-sucedido, resultado persistido no treino. */
        PROCESSADO_SUCESSO,
        /** Push com erro (não-autenticação), resultado persistido no treino. */
        PROCESSADO_ERRO,
        /** Como {@link #PROCESSADO_ERRO}, mas o erro foi especificamente de autenticação. */
        PROCESSADO_ERRO_AUTENTICACAO
    }
}
