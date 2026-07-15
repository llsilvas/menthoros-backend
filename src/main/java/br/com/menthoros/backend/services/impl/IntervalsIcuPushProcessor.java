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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Processa o push de um único {@link TreinoPlanejado} para o intervals.icu: conversão para
 * {@link StructuredWorkout}, claim atômico (transição condicional + {@code @Version}), chamada ao
 * {@link WorkoutChannel} e marcação do resultado.
 *
 * <p>Colaborador compartilhado entre {@link IntervalsIcuPushListener} (gatilho na aprovação do
 * plano) e {@link IntervalsIcuRetrySchedulerImpl} (retry periódico) — os dois pontos de entrada
 * precisam da mesma lógica de claim e da mesma degradação de erro (um throw inesperado do channel
 * após o claim nunca pode deixar o treino preso em {@code SINCRONIZANDO}; ver guard-rail coberto
 * em {@code IntervalsIcuPushListenerTest#throwInesperadoAposClaimNaoDeixaSincronizandoOrfao}).
 *
 * <p>Não resolve conexão nem valida tenant — o chamador já localizou a {@link IntegracaoExterna}
 * ativa e garantiu que {@code treino} pertence ao tenant correto antes de invocar
 * {@link #processar(TreinoPlanejado, IntegracaoExterna)}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IntervalsIcuPushProcessor {

    private static final String PLATAFORMA = FonteDados.INTERVALS_ICU.name();

    private final IntervalsIcuWorkoutConverter converter;
    private final WorkoutChannel workoutChannel;
    private final TreinoPlanejadoRepository treinoPlanejadoRepository;

    /**
     * Processa o push de um treino já resolvido pelo chamador (tenant validado, conexão ativa).
     *
     * <p><b>Idempotente:</b> YES — reclama (claim) o treino apenas se ainda não foi assumido por
     * outro worker e reenvia o mesmo {@code externalId} determinístico do
     * {@link StructuredWorkout}; reexecutar após falha parcial converge para o mesmo estado final
     * (upsert no canal externo).
     * <p><b>Side Effects:</b> chamada HTTP externa (push) + atualização de {@code treino}
     * (status/tentativas/externalId).
     * <p><b>Tenant-aware:</b> NO diretamente — o chamador já resolveu {@code treino}/{@code conexao}
     * dentro do tenant correto antes de invocar este método.
     *
     * @param treino  treino a processar; mutado em memória e persistido por este método
     * @param conexao conexão ativa do atleta com o intervals.icu
     * @return o desfecho do processamento — o chamador usa {@code treino.getExternalId()} e
     *         {@code treino.getStatusSincronizacao()} (já atualizados) para decidir os próximos
     *         passos (ex.: reconciliação de órfãos, agregação de erro de autenticação)
     */
    public ProcessamentoResultado processar(TreinoPlanejado treino, IntegracaoExterna conexao) {
        UUID treinoId = treino.getId();

        // Regra 4 (listener): treino não exportável é pulado sem erro e sem qualquer mutação de
        // estado — nem sequer registra tentativa de sincronização.
        Optional<StructuredWorkout> workoutOpt = converter.converter(treino);
        if (workoutOpt.isEmpty()) {
            return ProcessamentoResultado.NAO_EXPORTAVEL;
        }

        // Claim atômico — transição para SINCRONIZANDO persistida ANTES da chamada de rede.
        treino.registrarTentativaSincronizacao();
        treino.setStatusSincronizacao(StatusSincronizacao.SINCRONIZANDO);
        try {
            treinoPlanejadoRepository.saveAndFlush(treino);
        } catch (OptimisticLockingFailureException e) {
            log.info("Claim de sincronização perdido para o treino {}: outro worker assumiu", treinoId);
            return ProcessamentoResultado.CLAIM_PERDIDO;
        }

        // Após o claim, NENHUM throw pode escapar sem resolver o estado: um treino preso em
        // SINCRONIZANDO fica órfão (o retry scheduler não varre esse estado). O channel promete
        // nunca lançar, mas uma violação de contrato aqui degrada para ERRO_TEMPORARIO.
        boolean autenticacaoFalhou = false;
        try {
            PushResult resultado = workoutChannel.push(conexao, workoutOpt.get(), parseEventId(treino.getExternalId()));

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
        } catch (Exception e) {
            log.error("Erro inesperado no push do treino {} (violação de contrato do channel): {}",
                    treinoId, e.getMessage(), e);
            treino.marcarErroSincronizacao(StatusSincronizacao.ERRO_TEMPORARIO,
                    "Erro inesperado no push intervals.icu: " + e.getMessage());
        }
        treinoPlanejadoRepository.save(treino);

        return autenticacaoFalhou
                ? ProcessamentoResultado.PROCESSADO_ERRO_AUTENTICACAO
                : ProcessamentoResultado.PROCESSADO;
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

    /**
     * Desfecho do processamento de um treino, para o chamador decidir passos adicionais
     * (reconciliação de órfãos, agregação de erro de autenticação por plano).
     */
    public enum ProcessamentoResultado {
        /** Treino não é exportável (ex.: virou DESCANSO) — nenhuma mutação de estado ocorreu. */
        NAO_EXPORTAVEL,
        /** Outro worker já reclamou o treino nesta janela — desistência silenciosa. */
        CLAIM_PERDIDO,
        /** Push tentado e resultado (sucesso ou erro) já marcado e persistido no treino. */
        PROCESSADO,
        /** Como {@link #PROCESSADO}, mas o erro retornado foi especificamente de autenticação. */
        PROCESSADO_ERRO_AUTENTICACAO
    }
}
