package br.com.menthoros.backend.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Anotação para validar que o recurso acessado pertence ao tenant atual.
 *
 * Uso:
 * - Marque métodos que recebem IDs de recursos
 * - Especifique o índice do parâmetro que é o ID do recurso
 * - O aspecto validará que o recurso pertence ao TenantContext.getTenantId()
 *
 * Exemplo:
 *   @RequireTenant(resourceParamIndex = 0)
 *   public AtletaOutputDto getAtleta(UUID atletaId) { ... }
 *
 * Porquê:
 * - Previne acesso cross-tenant por esquecer validação manual
 * - Centraliza lógica de segurança
 * - Logs auditáveis automáticos
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireTenant {
    /**
     * Índice do parâmetro que contém o ID do recurso a validar
     * (zero-indexed)
     */
    int resourceParamIndex() default 0;

    /**
     * Se true, log de acesso negado será gravado em nível WARN
     */
    boolean logDeniedAccess() default true;
}
