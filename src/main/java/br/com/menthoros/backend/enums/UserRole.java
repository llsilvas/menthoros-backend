package br.com.menthoros.backend.enums;

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
    VISUALIZADOR,

    /**
     * Atleta vinculado à assessoria
     * - Acessa apenas seus próprios dados (treinos, planos, métricas)
     * - Conta criada via convite e efetivada no aceite
     */
    ATLETA,

    /**
     * Dono da assessoria — configura a identidade da própria assessoria.
     *
     * <p>No Keycloak é uma role <b>composite</b> que inclui {@code TECNICO}, então o token do dono
     * traz as duas e ele mantém tudo o que um técnico faz.
     *
     * <p><b>Nunca entra em {@code UsuarioSyncServiceImpl.mapToUserRole}.</b> {@code Usuario.role}
     * guarda um único valor: se o dono fosse resolvido como {@code PROPRIETARIO}, ele sairia de
     * {@code countByTenantIdAndRoleAndAtivoTrue} (a contagem de técnicos do plano, com
     * {@code maxTecnicos = 1} no BASIC), de {@code isTecnico()} e de {@code podeEscrever()}.
     * A propriedade da assessoria vive na flag {@code Usuario.owner}, espelhada do JWT — esta
     * constante existe para o mapeamento de authorities e para o {@code @PreAuthorize}.
     */
    PROPRIETARIO
}
