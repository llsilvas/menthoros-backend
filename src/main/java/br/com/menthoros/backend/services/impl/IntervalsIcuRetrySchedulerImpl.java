package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.IntegracaoExterna;
import br.com.menthoros.backend.entity.TreinoPlanejado;
import br.com.menthoros.backend.enums.StatusSincronizacao;
import br.com.menthoros.backend.repository.TreinoPlanejadoRepository;
import br.com.menthoros.backend.services.IntervalsIcuConnectionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reprocessa periodicamente treinos planejados que ficaram em estado de retry do push para o
 * intervals.icu ({@code AGUARDANDO_RETRY}, {@code ERRO_TEMPORARIO}, {@code ERRO_LIMITE_RATE}) —
 * padrão de {@link DailyActivitySyncSchedulerImpl}: erro por treino não aborta o batch, validação
 * de tenant com log de segurança, log estruturado.
 *
 * <p>NUNCA varre {@code SINCRONIZANDO} (treino sendo processado agora pelo
 * {@link IntervalsIcuPushListener} ou por outro worker deste scheduler) nem
 * {@code PENDENTE}/{@code NAO_SINCRONIZADO} (treino recém-aprovado ainda não tocado pelo
 * listener) — a precedência do listener sobre o retry é uma invariante de segurança (spec
 * 3.3 + 8.2): tocar esses estados aqui duplicaria o processamento de um treino que outro worker
 * já reclamou ou ainda nem começou.
 *
 * <p>Quando o atleta não tem mais conexão ativa com o intervals.icu (desconectou depois do push
 * original), o treino é pulado sem qualquer mutação de estado — decisão deliberada: o retry nunca
 * vai suceder sem reconexão, mas se o atleta reconectar depois, o próximo ciclo deste scheduler
 * retoma o retry automaticamente a partir do mesmo estado. Escalar para {@code ERRO_AUTENTICACAO}
 * ou {@code DESABILITADO} aqui destruiria essa recuperação automática e exigiria reprocessamento
 * manual do coach.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IntervalsIcuRetrySchedulerImpl {

    private final IntervalsIcuConnectionService connectionService;
    private final IntervalsIcuPushProcessor pushProcessor;
    private final TreinoPlanejadoRepository treinoPlanejadoRepository;

    /**
     * Varre e reprocessa treinos planejados nos estados de retry do push intervals.icu.
     *
     * <p><b>Idempotente:</b> YES — mesmo claim atômico do {@link IntervalsIcuPushProcessor}
     * (transição condicional + {@code @Version}); reexecutar sobre o mesmo treino converge para o
     * mesmo estado final.
     * <p><b>Side Effects:</b> chamada HTTP externa (push) + atualização de {@link TreinoPlanejado}.
     * <p><b>Tenant-aware:</b> YES — job de sistema sem contexto de requisição HTTP; cada treino usa
     * o próprio {@code tenantId} ({@code treino.getTenantId()}) nas operações, nunca
     * {@code TenantContext}. Mismatch entre o tenant do treino e a assessoria do atleta é logado
     * como violação de segurança e o treino é pulado.
     */
    @Scheduled(fixedDelayString = "PT15M", initialDelayString = "PT5M")
    @Transactional
    public void reprocessarPendentes() {
        List<TreinoPlanejado> candidatos = treinoPlanejadoRepository.findAllAguardandoRetryIntervalsIcu();
        log.info("Retry intervals.icu: {} treinos candidatos a reprocessamento", candidatos.size());

        int processados = 0;
        for (TreinoPlanejado treino : candidatos) {
            try {
                if (processarRetry(treino)) {
                    processados++;
                }
            } catch (Exception e) {
                // Erro em um treino não pode abortar o processamento dos demais.
                log.error("Erro inesperado no retry do treino {}: {}", treino.getId(), e.getMessage(), e);
            }
        }
        log.info("Retry intervals.icu concluído: {} de {} treinos processados", processados, candidatos.size());
    }

    /**
     * @return true se o treino chegou a ser processado (push tentado via
     *         {@link IntervalsIcuPushProcessor}), false se pulado sem mutação de estado.
     */
    private boolean processarRetry(TreinoPlanejado treino) {
        UUID treinoId = treino.getId();

        // Limite de tentativas já esgotado finaliza como estado final sem nova chamada de rede —
        // independente da janela de retry ter passado ou não.
        if (treino.atingiuLimiteTentativas()) {
            treino.setStatusSincronizacao(StatusSincronizacao.ERRO_PERMANENTE);
            treinoPlanejadoRepository.save(treino);
            log.info("Retry intervals.icu: treino {} atingiu limite de tentativas, escalado para ERRO_PERMANENTE",
                    treinoId);
            return false;
        }

        // Janela de 5 minutos entre tentativas ainda não vencida — pula sem mutação.
        if (!treino.podeRetentarSincronizacao()) {
            return false;
        }

        UUID atletaId = resolverAtletaTenantValidado(treino);
        if (atletaId == null) {
            return false;
        }
        UUID tenantId = treino.getTenantId();

        Optional<IntegracaoExterna> conexaoOpt = connectionService.conexaoAtiva(atletaId, tenantId);
        if (conexaoOpt.isEmpty()) {
            log.info("Retry intervals.icu pulado: atleta {} sem conexão ativa. treino={}", atletaId, treinoId);
            return false;
        }

        pushProcessor.processar(treino, conexaoOpt.get());
        return true;
    }

    /**
     * Resolve o atletaId a partir do treino, validando em profundidade que a assessoria do atleta
     * bate com {@code treino.getTenantId()} — defesa contra dado corrompido, mesmo padrão do
     * {@code DailyActivitySyncSchedulerImpl}.
     *
     * @return o atletaId validado, ou {@code null} quando o treino deve ser pulado
     */
    private UUID resolverAtletaTenantValidado(TreinoPlanejado treino) {
        Atleta atleta = treino.getAtleta();
        if (atleta == null || atleta.getAssessoria() == null) {
            log.warn("Retry intervals.icu pulado: treino {} sem atleta/assessoria resolvida", treino.getId());
            return null;
        }
        UUID tenantDoAtleta = atleta.getAssessoria().getId();
        if (!tenantDoAtleta.equals(treino.getTenantId())) {
            log.error("SECURITY: treino {} tenant_id={} diverge da assessoria do atleta {}: {}",
                    treino.getId(), treino.getTenantId(), atleta.getId(), tenantDoAtleta);
            return null;
        }
        return atleta.getId();
    }
}
