package com.menthoros.services.prompt;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Gerenciador de templates de prompts para IA.
 * Responsável por carregar e cachear templates de arquivos de recursos.
 *
 * @author Menthoros Team
 * @since 1.0
 */
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
        String template = loadTemplate(templateName);

        try {
            return String.format(template, args);
        } catch (Exception e) {
            String errorMsg = String.format(
                "Erro ao formatar template '%s': %s. Verifique se o número de placeholders corresponde aos argumentos fornecidos.",
                templateName,
                e.getMessage()
            );
            logger.error(errorMsg, e);
            throw new RuntimeException(errorMsg, e);
        }
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