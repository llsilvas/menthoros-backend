package br.com.menthoros.backend.security;

import br.com.menthoros.backend.multitenancy.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Filtro que adiciona contexto estruturado para logs distribuídos.
 *
 * O que faz:
 * - Gera um requestId único para rastreamento da request
 * - Extrai tenantId do TenantContext e adiciona ao MDC
 * - Loga método HTTP + URI com contexto completo
 * - Rastreia tempo de execução
 *
 * Porquê usar MDC (Mapped Diagnostic Context)?
 * - Logs podem ser correlacionados em sistemas distribuídos
 * - Ferramentas de logging (ELK, Datadog, etc) capturam MDC automaticamente
 * - Permite buscar por requestId em toda a stack trace
 * - Centraliza context sem passar por cada método
 *
 * Exemplo de log estruturado:
 * ```
 * 2026-05-14T10:30:15.123Z INFO StructuredLoggingFilter
 * request_method=GET uri=/api/v1/atletas request_id=a1b2c3d4-e5f6-7890 tenant_id=xxxx duration_ms=45
 * ```
 *
 * Integração com Log Aggregation:
 * - Filebeat / Logstash podem extrair requestId, tenantId, duration automaticamente
 * - Dashboards podem agrupar por tenant, por tempo de resposta, etc
 * - Alertas podem ser configurados por tenant específico
 */
@Slf4j
@Component
public class StructuredLoggingFilter extends OncePerRequestFilter {

    private static final String REQUEST_ID_HEADER = "X-Request-ID";
    private static final String MDC_REQUEST_ID = "request_id";
    private static final String MDC_TENANT_ID = "tenant_id";
    private static final String MDC_METHOD = "request_method";
    private static final String MDC_URI = "request_uri";
    private static final String MDC_DURATION = "duration_ms";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        // Gera ou recupera requestId
        String requestId = request.getHeader(REQUEST_ID_HEADER);
        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString();
        }

        long startTime = System.currentTimeMillis();

        try {
            // Popula MDC para contexto distribuído
            MDC.put(MDC_REQUEST_ID, requestId);
            MDC.put(MDC_METHOD, request.getMethod());
            MDC.put(MDC_URI, request.getRequestURI());

            // Tenta adicionar tenantId ao MDC
            addTenantToMDC();

            // Loga início da request
            log.info(
                    "request_started | method={} | uri={}",
                    request.getMethod(),
                    request.getRequestURI()
            );

            // Continua processamento
            filterChain.doFilter(request, response);

        } finally {
            // Calcula duração total
            long duration = System.currentTimeMillis() - startTime;
            MDC.put(MDC_DURATION, String.valueOf(duration));

            // Loga fim com status
            log.info(
                    "request_completed | status={} | duration_ms={} | method={} | uri={}",
                    response.getStatus(),
                    duration,
                    request.getMethod(),
                    request.getRequestURI()
            );

            // Limpa MDC para evitar vazamento entre threads
            MDC.clear();
        }
    }

    /**
     * Tenta adicionar tenantId do TenantContext ao MDC.
     * Se não houver tenant, apenas loga DEBUG (não é erro).
     */
    private void addTenantToMDC() {
        try {
            if (TenantContext.hasTenant()) {
                String tenantId = TenantContext.getTenantId().toString();
                MDC.put(MDC_TENANT_ID, tenantId);
                log.debug("Tenant {} configurado no MDC", tenantId);
            }
        } catch (Exception e) {
            // TenantContext não está disponível ou não configurado
            // Isso é normal para endpoints públicos
            log.debug("TenantContext não disponível (provavelmente endpoint público)");
        }
    }
}
