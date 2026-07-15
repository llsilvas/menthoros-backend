package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.entity.IntegracaoExterna;
import br.com.menthoros.backend.events.PlanoDeletadoEvent;
import br.com.menthoros.backend.services.IntervalsIcuConnectionService;
import br.com.menthoros.backend.services.WorkoutChannel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Escuta {@link PlanoDeletadoEvent} e remove, no intervals.icu, os eventos {@code menthoros-*} que
 * ficariam órfãos após a deleção do plano — sem isso, o calendário/relógio do atleta mantém uma
 * prescrição fantasma que não existe mais no Menthoros.
 *
 * <p>Roda fora da transação de deleção (AFTER_COMMIT + REQUIRES_NEW): a deleção do plano já
 * commitou quando este listener executa, então a limpeza é sempre best-effort — uma falha aqui
 * nunca desfaz nem revalida a deleção.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IntervalsIcuPlanoDeletadoListener {

    private final IntervalsIcuConnectionService connectionService;
    private final WorkoutChannel workoutChannel;

    /**
     * Remove os eventos {@code menthoros-*} da janela do plano deletado no intervals.icu.
     *
     * <p><b>Idempotente:</b> YES — remover os mesmos eventos órfãos novamente é um no-op no canal
     * externo (ver {@code WorkoutChannel#removerOrfaos}, nunca lança para 404 de delete).
     * <p><b>Side Effects:</b> chamada HTTP externa (remoção de eventos no intervals.icu).
     * <p><b>Tenant-aware:</b> YES — usa {@code event.tenantId()} explicitamente; o plano já foi
     * deletado quando este listener roda, então não há {@code TenantContext} de requisição ativo.
     *
     * @param event evento de deleção do plano
     */
    @Async("intervalsIcuPushExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onPlanoDeletado(PlanoDeletadoEvent event) {
        UUID planoId = event.planoId();
        UUID atletaId = event.atletaId();
        UUID tenantId = event.tenantId();

        try {
            Optional<IntegracaoExterna> conexaoOpt = connectionService.conexaoAtiva(atletaId, tenantId);
            if (conexaoOpt.isEmpty()) {
                log.info("Limpeza intervals.icu ignorada: sem conexão ativa. planoId={}, atletaId={}",
                        planoId, atletaId);
                return;
            }
            IntegracaoExterna conexao = conexaoOpt.get();
            workoutChannel.removerOrfaos(conexao, event.semanaInicio(), event.semanaFim(), Set.of());
        } catch (Exception e) {
            // Limpeza é best-effort — a deleção do plano JÁ commitou quando este listener roda,
            // então uma falha aqui nunca pode propagar (não há mais nada a desfazer).
            log.error("Erro ao limpar eventos órfãos do intervals.icu para o plano deletado {}: {}",
                    planoId, e.getMessage(), e);
        }
    }
}
