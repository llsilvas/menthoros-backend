package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.ai.ledger.LlmCallResult;
import br.com.menthoros.backend.ai.ledger.LlmCallScope;
import br.com.menthoros.backend.ai.ledger.PromptHashCalculator;
import br.com.menthoros.backend.ai.ledger.Violacao;
import br.com.menthoros.backend.domain.compliance.PromptVersion;
import br.com.menthoros.backend.domain.compliance.SchemaVersion;
import br.com.menthoros.backend.exception.LLMException;
import br.com.menthoros.backend.exception.PlanoNaoConformeException;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Amarra a rota {@code plano} ao ledger (add-plan-generation-ledger, D3/D6) sem que a
 * {@code IaServiceImpl} precise conhecer escopo, versões ou resultados: por geração, uma
 * {@link Sessao} abre a tentativa em volta da chamada ao modelo e fecha o resultado depois da
 * validação.
 *
 * <p>Regra do resultado: o advisor grava a linha {@code PENDING} durante a chamada e registra o id
 * no escopo. Se a chamada lança <b>depois</b> de o id existir, o HTTP deu certo e a falha é de
 * conversão → {@code PARSE_ERROR}. Se lança sem id, o provider falhou e o advisor já gravou
 * {@code LLM_ERROR}/{@code TIMEOUT}. Após {@code validar}: {@code SUCCESS}, ou
 * {@code VALIDATION_REJECTED} com as violações (as keys reais quando é
 * {@link PlanoNaoConformeException}; uma genérica para as demais rejeições estruturais).
 */
@Component
@RequiredArgsConstructor
public class PlanoLlmLedgerHook {

    static final String KEY_PARSE = "PARSE_ERROR";
    static final String KEY_ESTRUTURAL = "VALIDACAO_ESTRUTURAL";
    static final int MAX_MENSAGEM = 300;

    private final LlmCallLedger ledger;
    private final PromptHashCalculator promptHash;

    /**
     * Idempotent: SIM — só cria estado em memória.
     * Side Effects: NONE.
     * Tenant-aware: NÃO.
     */
    public Sessao novaSessao() {
        return new Sessao();
    }

    /** Estado de uma geração (uma requisição): o id da última chamada gravada. Não é thread-safe; uma por geração. */
    public final class Sessao {

        private @Nullable UUID callId;

        private Sessao() {
        }

        /**
         * Executa a chamada ao modelo dentro do escopo da tentativa. Fecha o escopo sempre.
         * Idempotent: NÃO. Side Effects: escrita no ledger via advisor e, em falha de conversão,
         * update para PARSE_ERROR (best-effort). Tenant-aware: NÃO.
         */
        public <T> T chamar(int tentativa, Supplier<T> chamada) {
            LlmCallScope.openAttempt(tentativa, PromptVersion.CURRENT, promptHash.valor(), SchemaVersion.CURRENT);
            try {
                T resultado = chamada.get();
                callId = LlmCallScope.lastCallId().orElse(null);
                return resultado;
            } catch (RuntimeException e) {
                callId = LlmCallScope.lastCallId().orElse(null);
                if (callId != null) {
                    ledger.registrarResultado(callId, LlmCallResult.PARSE_ERROR,
                            List.of(new Violacao(KEY_PARSE, truncar(e.getMessage()))));
                }
                throw e;
            } finally {
                LlmCallScope.closeAttempt();
            }
        }

        /**
         * Executa a validação e fecha o resultado da última chamada. Relança para o retry decidir.
         * Idempotent: SIM. Side Effects: update no ledger (best-effort). Tenant-aware: NÃO.
         */
        public <T> T validar(Supplier<T> validacao) {
            try {
                T validado = validacao.get();
                ledger.registrarResultado(callId, LlmCallResult.SUCCESS, List.of());
                return validado;
            } catch (PlanoNaoConformeException e) {
                ledger.registrarResultado(callId, LlmCallResult.VALIDATION_REJECTED, e.violacoes());
                throw e;
            } catch (LLMException e) {
                ledger.registrarResultado(callId, LlmCallResult.VALIDATION_REJECTED,
                        List.of(new Violacao(KEY_ESTRUTURAL, truncar(e.getMessage()))));
                throw e;
            }
        }

        public Optional<UUID> ultimaChamada() {
            return Optional.ofNullable(callId);
        }
    }

    static String truncar(@Nullable String mensagem) {
        if (mensagem == null) {
            return "sem mensagem";
        }
        return mensagem.length() <= MAX_MENSAGEM ? mensagem : mensagem.substring(0, MAX_MENSAGEM) + "…";
    }
}
