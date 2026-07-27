package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.events.SemanaEncerradaEvent;
import br.com.menthoros.backend.repository.RevisaoSemanalRepository;
import br.com.menthoros.backend.services.RevisaoSemanalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Gera a revisão semanal quando um plano é encerrado — reage ao {@link SemanaEncerradaEvent}
 * em {@code AFTER_COMMIT} (padrão do módulo), garantindo que a revisão só nasce após o
 * encerramento comitar. A geração é idempotente (insert-if-absent) no serviço; falha aqui
 * não desfaz o encerramento já comitado nem impede outros listeners.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RevisaoSemanalListener {

    private final RevisaoSemanalService revisaoSemanalService;
    private final RevisaoSemanalRepository revisaoSemanalRepository;
    private final WeeklyFocusNarrativeService weeklyFocusNarrativeService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void aoEncerrarSemana(SemanaEncerradaEvent event) {
        try {
            revisaoSemanalService.gerarNoEncerramento(event.planoId(), event.tenantId());
            // A narrativa por LLM roda fora desta thread (D8) e só sobrescreve o template quando
            // passa pelo checker — o encerramento não espera por ela nem falha por causa dela.
            revisaoSemanalRepository.findByPlanoSemanalIdAndTenant(event.planoId(), event.tenantId())
                    .ifPresent(revisao ->
                            weeklyFocusNarrativeService.gerarNarrativa(revisao.getId(), event.tenantId()));
        } catch (Exception e) {
            // Engole para não desfazer o encerramento já comitado; loga com stacktrace (causa raiz).
            // Débito conhecido (pilot): falha aqui deixa a revisão ausente até o próximo encerramento
            // do mesmo plano — sem reconciliação automática ainda.
            log.warn("[revisao-semanal] falha ao gerar revisão do plano {}", event.planoId(), e);
        }
    }
}
