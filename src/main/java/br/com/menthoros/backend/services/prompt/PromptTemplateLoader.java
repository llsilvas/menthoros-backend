package br.com.menthoros.backend.services.prompt;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UnknownFormatConversionException;

/**
 * Gerenciador de templates de prompts para IA.
 * Responsável por carregar e cachear templates de arquivos de recursos.
 *
 * @author Menthoros Team
 * @since 1.0
 */
@Slf4j
@Component
public class PromptTemplateLoader {

    private static final Logger logger = LoggerFactory.getLogger(PromptTemplateLoader.class);
    private static final String PROMPTS_BASE_PATH = "classpath:prompts/";

    private final ResourceLoader resourceLoader;
    private final Map<String, String> templateCache;

    public PromptTemplateLoader(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
        this.templateCache = new HashMap<>();
        logger.info("PromptTemplateLoader inicializado");
    }

    /**
     * Carrega um template de prompt pelo nome do arquivo.
     * Os templates são cacheados após o primeiro carregamento.
     *
     * @param templateName Nome do arquivo de template (sem o caminho base)
     * @return Conteúdo do template
     * @throws RuntimeException se o template não puder ser carregado
     */
    public String loadTemplate(String templateName) {
        // Verificar cache primeiro
        if (templateCache.containsKey(templateName)) {
            logger.debug("Template '{}' carregado do cache", templateName);
            return templateCache.get(templateName);
        }

        try {
            String resourcePath = PROMPTS_BASE_PATH + templateName;
            Resource resource = resourceLoader.getResource(resourcePath);

            if (!resource.exists()) {
                throw new RuntimeException("Template não encontrado: " + templateName);
            }

            String content = new String(
                resource.getInputStream().readAllBytes(),
                StandardCharsets.UTF_8
            );

            // Armazenar no cache
            templateCache.put(templateName, content);
            logger.info("Template '{}' carregado com sucesso ({} bytes)",
                       templateName, content.length());

            return content;

        } catch (IOException e) {
            String errorMsg = String.format(
                "Erro ao carregar template '%s': %s",
                templateName,
                e.getMessage()
            );
            logger.error(errorMsg, e);
            throw new RuntimeException(errorMsg, e);
        }
    }

    /**
     * Carrega um template e aplica formatação com String.format.
     *
     * @param templateName Nome do arquivo de template
     * @param args Argumentos para String.format
     * @return Template formatado
     */
    public String loadAndFormat(String templateName, Object... args) {
        try {
            String template = loadTemplate(templateName);

            // 🛡️ ESCAPE AUTOMÁTICO - Remove TODOS os % problemáticos
            template = escapeTemplate(template);

            log.debug("Template escapado com sucesso: {} argumentos", args.length);

            return String.format(template, args);

        } catch (UnknownFormatConversionException e) {
            log.error("❌ Erro de conversão: {}", e.getConversion());
            throw new RuntimeException(
                    "Erro ao formatar template '" + templateName + "': Conversion = '" + e.getConversion() + "'", e
            );
        }
    }

    /**
     * Escapa todos os % no template exceto %s e %d
     */
    private String escapeTemplate(String template) {
        StringBuilder result = new StringBuilder();
        int length = template.length();

        for (int i = 0; i < length; i++) {
            char c = template.charAt(i);

            if (c == '%') {
                // Verificar próximo caractere
                if (i + 1 < length) {
                    char next = template.charAt(i + 1);

                    // Se for %s, %d ou %%, manter
                    if (next == 's' || next == 'd' || next == '%') {
                        result.append(c);
                    } else {
                        // Escapar qualquer outro %
                        result.append("%%");
                    }
                } else {
                    // % no final do arquivo, escapar
                    result.append("%%");
                }
            } else {
                result.append(c);
            }
        }

        return result.toString();
    }

    /**
     * Limpa o cache de templates.
     * Útil para forçar recarregamento em ambiente de desenvolvimento.
     */
    public void clearCache() {
        int size = templateCache.size();
        templateCache.clear();
        logger.info("Cache de templates limpo ({} templates removidos)", size);
    }

    /**
     * Remove um template específico do cache.
     *
     * @param templateName Nome do template a ser removido
     * @return true se o template estava no cache e foi removido
     */
    public boolean evictFromCache(String templateName) {
        boolean removed = templateCache.remove(templateName) != null;
        if (removed) {
            logger.debug("Template '{}' removido do cache", templateName);
        }
        return removed;
    }

    /**
     * Retorna o número de templates em cache.
     *
     * @return Tamanho do cache
     */
    public int getCacheSize() {
        return templateCache.size();
    }

    /**
     * Verifica se um template específico está em cache.
     *
     * @param templateName Nome do template
     * @return true se está em cache
     */
    public boolean isInCache(String templateName) {
        return templateCache.containsKey(templateName);
    }
}