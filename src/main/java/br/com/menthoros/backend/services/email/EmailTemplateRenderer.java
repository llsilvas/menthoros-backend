package br.com.menthoros.backend.services.email;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Substituição de {@code {{chave}}} em templates de {@code resources/templates/email/}.
 *
 * <p>Sem Thymeleaf/Freemarker de propósito: a dependência nova desta change é o starter de mail,
 * e um convite não precisa de lógica de template. Valores são escapados no HTML ({@code .html})
 * e passados crus no texto ({@code .txt}). Placeholder sem valor é erro — um {@code {{nome}}}
 * sobrando no e-mail é bug, não default.</p>
 */
@Component
public class EmailTemplateRenderer {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{\\s*([a-zA-Z0-9_]+)\\s*}}");
    private static final String BASE = "templates/email/";

    private final Map<String, String> cache = new ConcurrentHashMap<>();

    /**
     * Renderiza {@code templates/email/<name>}.
     *
     * <p><strong>Idempotent:</strong> YES.
     * <p><strong>Side Effects:</strong> NONE (leitura de classpath, com cache).
     * <p><strong>Tenant-aware:</strong> NO.
     *
     * @param name nome do arquivo com extensão, ex.: {@code founding-invite.html}
     * @throws IllegalArgumentException placeholder sem valor no mapa
     */
    public String render(String name, Map<String, String> values) {
        boolean html = name.endsWith(".html");
        String template = cache.computeIfAbsent(name, this::load);

        Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String key = matcher.group(1);
            String value = values.get(key);
            if (value == null) {
                throw new IllegalArgumentException(
                        "Template " + name + " exige o valor '" + key + "' e ele não foi informado");
            }
            String safe = html ? HtmlUtils.htmlEscape(value, StandardCharsets.UTF_8.name()) : value;
            matcher.appendReplacement(out, Matcher.quoteReplacement(safe));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    private String load(String name) {
        try {
            return new ClassPathResource(BASE + name).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Template de e-mail não encontrado: " + BASE + name, e);
        }
    }
}
