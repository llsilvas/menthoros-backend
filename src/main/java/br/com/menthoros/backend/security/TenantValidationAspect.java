package br.com.menthoros.backend.security;

import br.com.menthoros.backend.exception.AccessDeniedException;
import br.com.menthoros.backend.multitenancy.TenantContext;
import br.com.menthoros.backend.repository.TenantValidationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;

import org.aspectj.lang.reflect.MethodSignature;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Aspecto que valida isolamento de tenant.
 *
 * Intercepta métodos anotados com @RequireTenant e:
 * 1. Extrai o ID do recurso do parâmetro especificado
 * 2. Valida que o recurso pertence ao tenant atual
 * 3. Lança AccessDeniedException se a validação falhar
 * 4. Loga tentativas negadas para auditoria
 *
 * Porquê usar aspecto?
 * - Garante validação de tenant ANTES de qualquer lógica de negócio
 * - Centraliza validação (DRY principle)
 * - Previne que desenvolvedores "esqueçam" de validar tenant
 * - Logs auditáveis automáticos de tentativas de acesso
 *
 * Fluxo de Segurança:
 *   Request HTTP
 *   ↓ (JwtTenantFilter seta TenantContext)
 *   ↓
 *   Controller → Service
 *   ↓ (@RequireTenant intercepta)
 *   ↓
 *   TenantValidationAspect.validate()
 *   ↓ (throw AccessDeniedException se não pertence ao tenant)
 *   ↓
 *   Lógica de negócio (seguro, recurso validado)
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class TenantValidationAspect {

    private final TenantValidationRepository tenantValidationRepository;

    @Pointcut("@annotation(br.com.menthoros.backend.security.RequireTenant)")
    public void requireTenantAnnotated() {}

    @Around("requireTenantAnnotated()")
    public Object validateTenant(ProceedingJoinPoint joinPoint) throws Throwable {
        RequireTenant annotation = getAnnotation(joinPoint);
        Object[] args = joinPoint.getArgs();

        // Extrai o ID do recurso do parâmetro especificado
        if (annotation.resourceParamIndex() >= args.length) {
            log.error("RequireTenant: índice de parâmetro {} inválido para método {}",
                    annotation.resourceParamIndex(), joinPoint.getSignature().getName());
            throw new IllegalArgumentException("Configuração de @RequireTenant inválida");
        }

        Object resourceIdParam = args[annotation.resourceParamIndex()];

        // Valida que o parâmetro é um UUID
        if (!(resourceIdParam instanceof UUID resourceId)) {
            log.debug("RequireTenant: parâmetro não é UUID, pulando validação para {}",
                    joinPoint.getSignature().getName());
            return joinPoint.proceed();
        }

        // Obtém o tenant atual
        UUID currentTenant = TenantContext.getTenantId();
        if (currentTenant == null) {
            log.error("RequireTenant: Nenhum tenant configurado no contexto");
            throw new AccessDeniedException("Tenant não configurado");
        }

        // Valida que o recurso pertence ao tenant atual
        boolean belongsToTenant = tenantValidationRepository.resourceBelongsToTenant(resourceId, currentTenant);

        if (!belongsToTenant) {
            if (annotation.logDeniedAccess()) {
                // Log estruturado para auditoria de segurança
                log.warn(
                        "SECURITY_VIOLATION: Tentativa de acesso cross-tenant " +
                                "| tenant={} | resourceId={} | method={} | caller={}",
                        currentTenant,
                        resourceId,
                        joinPoint.getSignature().getName(),
                        extractCallerInfo(joinPoint)
                );
            }
            throw new AccessDeniedException(
                    "Acesso negado: recurso não pertence ao tenant atual"
            );
        }

        // Log de acesso bem-sucedido (opcional, DEBUG level)
        log.debug("RequireTenant: Validação passou | resourceId={} | tenant={}",
                resourceId, currentTenant);

        // Procede com a lógica de negócio
        return joinPoint.proceed();
    }

    /**
     * Extrai informação do chamador para log de auditoria.
     * Útil para rastreamento de quem tentou fazer o acesso.
     */
    private String extractCallerInfo(ProceedingJoinPoint joinPoint) {
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        for (StackTraceElement element : stackTrace) {
            // Pula frames do aspecto e Spring
            if (!element.getClassName().contains("AspectJ") &&
                    !element.getClassName().contains("SpringProxy") &&
                    element.getClassName().contains("br.com.menthoros")) {
                return element.getClassName() + "." + element.getMethodName();
            }
        }
        return "unknown";
    }

    /**
     * Extrai a anotação @RequireTenant do método.
     *
     * Usa MethodSignature para obter os tipos declarados dos parâmetros, não os tipos
     * em tempo de execução. Isso evita NoSuchMethodException quando o Spring passa
     * uma implementação concreta (ex: JwtAuthenticationToken) para um parâmetro
     * declarado como interface (ex: Authentication).
     */
    private RequireTenant getAnnotation(ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        RequireTenant annotation = method.getAnnotation(RequireTenant.class);

        if (annotation == null) {
            throw new IllegalStateException(
                    "RequireTenant anotação não encontrada em " + method.getName()
            );
        }

        return annotation;
    }
}
