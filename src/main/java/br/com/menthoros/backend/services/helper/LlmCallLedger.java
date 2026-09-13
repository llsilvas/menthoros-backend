package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.ai.ledger.GenerationOutcome;
import br.com.menthoros.backend.ai.ledger.LlmCallContext;
import br.com.menthoros.backend.ai.ledger.LlmCallRegistro;
import br.com.menthoros.backend.ai.ledger.LlmCallResult;
import br.com.menthoros.backend.ai.ledger.Violacao;
import br.com.menthoros.backend.entity.LlmCall;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Porta de escrita do ledger de Chamadas LLM (add-plan-generation-ledger). <b>Best-effort por
 * contrato</b>: nenhum método lança — falha de banco vira {@code warn} e a geração segue. A
 * transação (curta, própria, com teto) vive no {@link LlmCallLedgerWriter}; aqui só o {@code catch},
 * de propósito (ver Javadoc do writer).
 *
 * <p>A resposta bruta é dado sensível (D7): o nome do atleta é substituído por {@code [ATLETA]}
 * antes de gravar. Lesão em texto livre não é redigível com segurança e fica coberta pela
 * retenção (D8) e pela anulação na exclusão do atleta.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LlmCallLedger {

    static final String MARCADOR_ATLETA = "[ATLETA]";

    private final LlmCallLedgerWriter writer;
    private final ObjectMapper objectMapper;

    /**
     * Grava uma Chamada LLM e devolve o id da linha, ou vazio se a escrita falhou.
     * Idempotent: NÃO — uma linha por chamada.
     * Side Effects: Database insert (transação própria do writer). Nunca lança.
     * Tenant-aware: NÃO — grava o tenant recebido, nulo quando desconhecido (com {@code warn}).
     */
    public Optional<UUID> registrarChamada(LlmCallRegistro registro) {
        try {
            if (registro.tenantId() == null) {
                log.warn("[llm-ledger] chamada da rota {} sem tenant — custo ficará sem assessoria", registro.route());
            }
            return Optional.of(writer.inserir(toEntity(registro)));
        } catch (Exception e) {
            log.warn("[llm-ledger] falha ao gravar chamada da rota {} (ignorado): {}", registro.route(), e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Fecha o resultado de uma chamada da rota plano depois da validação (ou da falha de parse).
     * Idempotent: SIM. Side Effects: Database update. Nunca lança. Tenant-aware: NÃO.
     */
    public void registrarResultado(@Nullable UUID callId, LlmCallResult result, List<Violacao> violacoes) {
        if (callId == null) {
            log.warn("[llm-ledger] resultado {} sem id de chamada — linha não foi gravada antes", result);
            return;
        }
        try {
            String json = violacoes == null || violacoes.isEmpty() ? null : objectMapper.writeValueAsString(violacoes);
            if (!writer.atualizarResultado(callId, result, json)) {
                log.warn("[llm-ledger] chamada {} não encontrada ao gravar resultado {}", callId, result);
            }
        } catch (Exception e) {
            log.warn("[llm-ledger] falha ao gravar resultado {} da chamada {} (ignorado): {}", result, callId, e.getMessage());
        }
    }

    /**
     * Grava o desfecho da Requisição de geração na sua última chamada (design D6).
     * Idempotent: SIM. Side Effects: Database update. Nunca lança. Tenant-aware: NÃO.
     */
    public void registrarDesfecho(@Nullable UUID generationRequestId, GenerationOutcome outcome) {
        if (generationRequestId == null) {
            return;
        }
        try {
            if (!writer.atualizarDesfecho(generationRequestId, outcome)) {
                log.warn("[llm-ledger] requisição {} sem chamadas ao gravar desfecho {}", generationRequestId, outcome);
            }
        } catch (Exception e) {
            log.warn("[llm-ledger] falha ao gravar desfecho {} da requisição {} (ignorado): {}",
                    outcome, generationRequestId, e.getMessage());
        }
    }

    private LlmCall toEntity(LlmCallRegistro registro) throws JsonProcessingException {
        LlmCall chamada = new LlmCall();
        chamada.setTenantId(registro.tenantId());
        chamada.setRoute(registro.route());
        chamada.setModel(registro.model());
        chamada.setInputTokens(registro.inputTokens());
        chamada.setOutputTokens(registro.outputTokens());
        chamada.setCacheReadTokens(registro.cacheReadTokens());
        chamada.setCacheWriteTokens(registro.cacheWriteTokens());
        chamada.setCostUsd(registro.costUsd());
        chamada.setLatencyMs(registro.latencyMs());
        chamada.setResult(registro.result());
        chamada.setTransportRetries(registro.transportRetries());

        Optional<LlmCallContext> ctx = registro.context();
        if (ctx.isPresent()) {
            LlmCallContext c = ctx.get();
            chamada.setGenerationRequestId(c.generationRequestId());
            chamada.setAtletaId(c.atletaId());
            chamada.setAttempt(c.attempt());
            chamada.setPromptVersion(c.promptVersion());
            chamada.setPromptHash(c.promptHash());
            chamada.setSchemaVersion(c.schemaVersion());
            chamada.setResponseJson(responseJson(registro.responseText(), c.atletaNome()));
        }
        return chamada;
    }

    /**
     * A coluna é JSONB: texto que não é JSON válido (resposta truncada, prosa) é embrulhado como
     * string JSON para não perder a fixture. O nome do atleta é redigido antes.
     */
    private @Nullable String responseJson(@Nullable String texto, @Nullable String atletaNome) throws JsonProcessingException {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        String redigido = redigirNome(texto, atletaNome);
        try {
            objectMapper.readTree(redigido);
            return redigido;
        } catch (JsonProcessingException naoEhJson) {
            return objectMapper.writeValueAsString(redigido);
        }
    }

    static String redigirNome(String texto, @Nullable String atletaNome) {
        if (atletaNome == null || atletaNome.isBlank()) {
            return texto;
        }
        String resultado = texto;
        // Nome completo primeiro, depois cada parte com 3+ letras (evita apagar "de", "da").
        resultado = resultado.replaceAll("(?iu)" + Pattern.quote(atletaNome.trim()), MARCADOR_ATLETA);
        for (String parte : atletaNome.trim().split("\\s+")) {
            if (parte.length() >= 3) {
                resultado = resultado.replaceAll("(?iu)\\b" + Pattern.quote(parte) + "\\b", MARCADOR_ATLETA);
            }
        }
        return resultado;
    }
}
