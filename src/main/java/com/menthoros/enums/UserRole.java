package com.menthoros.enums;

/**
 * Roles de usuários no sistema
 * Mapeados dos Client Roles do Keycloak
 */
public enum UserRole {
    /**
     * Administrador da assessoria
     * - Pode gerenciar usuários (criar, editar, excluir)
     * - Pode gerenciar configurações da assessoria
     * - Acesso total a todos os recursos
     */
    ADMIN,

    /**
     * Técnico da assessoria
     * - Pode gerenciar atletas (criar, editar, excluir)
     * - Pode criar e modificar planos de treino
     * - Pode visualizar relatórios e métricas
     * - Não pode gerenciar usuários ou configurações da assessoria
     */
    TECNICO,

    /**
     * Visualizador (assistente)
     * - Apenas visualização de dados
     * - Não pode criar ou modificar nada
     * - Útil para estagiários, assistentes, etc.
     */
    VISUALIZADOR
}
