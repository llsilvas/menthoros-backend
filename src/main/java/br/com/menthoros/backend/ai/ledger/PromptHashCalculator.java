package br.com.menthoros.backend.ai.ledger;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * SHA-256 do template estático da geração de plano, calculado uma vez no startup e gravado em
 * cada Chamada LLM da rota {@code plano} (add-plan-generation-ledger, design D9).
 *
 * <p>Falha no startup se o template não existir: melhor que descobrir na primeira geração.
 * O valor é logado em INFO para correlacionar deploy e linhas do ledger.
 */
@Slf4j
@Component
public class PromptHashCalculator {

    private static final String PROMPTS_BASE_PATH = "classpath:prompts/";

    private final String valor;

    public PromptHashCalculator(ResourceLoader resourceLoader,
                                @Value("${app.llm.plano.template:plano-treino-otimizado-claude.txt}") String templateName) {
        Resource resource = resourceLoader.getResource(PROMPTS_BASE_PATH + templateName);
        if (!resource.exists()) {
            throw new IllegalStateException("Template do prompt de plano não encontrado no classpath: " + templateName);
        }
        try {
            this.valor = sha256(resource.getContentAsString(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException("Falha ao ler o template do prompt de plano: " + templateName, e);
        }
        log.info("[llm-ledger] prompt_hash do template {} = {}", templateName, valor);
    }

    /** Hash hexadecimal (64 chars) do template carregado no startup. */
    public String valor() {
        return valor;
    }

    /** SHA-256 hexadecimal de um texto em UTF-8; usado também pelo golden test para regenerar {@code prompt.sha256}. */
    public static String sha256(String texto) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(texto.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível", e);
        }
    }
}
