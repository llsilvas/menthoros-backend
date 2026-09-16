package br.com.menthoros.backend.ai.ledger;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * SHA-256 do template v2 (semantic-session-schema), calculado uma vez no startup — mesmo padrão
 * de {@link PromptHashCalculator}, gêmeo dedicado em vez de generalizar a classe existente (evita
 * tocar o construtor/testes de {@link PromptHashCalculator}, usado por todo o caminho v1).
 *
 * <p>Falha no startup se o template v2 não existir — mesmo comportamento de v1.</p>
 */
@Slf4j
@Component
public class PromptHashCalculatorV2 {

    private static final String PROMPTS_BASE_PATH = "classpath:prompts/";

    private final String valor;

    public PromptHashCalculatorV2(ResourceLoader resourceLoader,
                                   @Value("${app.llm.plano.template-v2:plano-treino-system-v2.txt}") String templateName) {
        Resource resource = resourceLoader.getResource(PROMPTS_BASE_PATH + templateName);
        if (!resource.exists()) {
            throw new IllegalStateException("Template v2 do prompt de plano não encontrado no classpath: " + templateName);
        }
        try {
            this.valor = PromptHashCalculator.sha256(resource.getContentAsString(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException("Falha ao ler o template v2 do prompt de plano: " + templateName, e);
        }
        log.info("[llm-ledger] prompt_hash do template v2 {} = {}", templateName, valor);
    }

    /** Hash hexadecimal (64 chars) do template v2 carregado no startup. */
    public String valor() {
        return valor;
    }
}
