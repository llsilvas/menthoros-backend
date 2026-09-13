package br.com.menthoros.backend.scheduler;

import br.com.menthoros.backend.services.helper.LlmCallLedger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Purga diária da resposta bruta do ledger (add-plan-generation-ledger, design D8).
 * {@code response_json} é dado sensível (D7 — reproduz o que o LLM escreveu, nome do atleta
 * redigido mas não a lesão em texto livre) e não precisa viver mais que {@link #RETENCAO_DIAS}
 * dias; as demais colunas (custo, latência, versão, resultado) ficam para sempre — são a fonte de
 * FinOps e de correlação com o veredito do coach.
 *
 * <p>Cross-tenant por natureza, como o {@code AssinaturaSuspensaoScheduler}: {@code tb_llm_call}
 * não é tenant-scoped em toda rota (D2), então o corte é global, não por assessoria.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LlmCallRetentionScheduler {

    private static final int RETENCAO_DIAS = 90;

    private final LlmCallLedger llmCallLedger;
    private final Clock clock;

    /**
     * Roda diariamente (3h15, horário de Brasília) — 15 min depois do
     * {@code AssinaturaSuspensaoScheduler} e do {@code EncerramentoSemanaScheduler}, para não
     * competir com eles pela mesma janela.
     */
    @Scheduled(cron = "${app.llm.ledger.retention-cron:0 15 3 * * *}", zone = "America/Sao_Paulo")
    public void purgarRespostasAntigas() {
        Instant corte = clock.instant().minus(RETENCAO_DIAS, ChronoUnit.DAYS);
        int total = llmCallLedger.purgarRespostasAntigas(corte);
        if (total > 0) {
            log.info("[llm-ledger] purga diária: {} resposta(s) anulada(s) (retenção de {} dias)", total, RETENCAO_DIAS);
        } else {
            log.debug("[llm-ledger] purga diária: nenhuma resposta além de {} dias", RETENCAO_DIAS);
        }
    }
}
