package br.com.menthoros.backend.services;

/**
 * Conclusão do wizard de boas-vindas do coach.
 *
 * <p>Separado do onboarding do <b>atleta</b> ({@code OnboardingService}), que é outro conceito
 * inteiramente — lá se coletam dados de linha de base do atleta; aqui apenas se marca que o coach
 * já viu a tela de boas-vindas.
 */
public interface CoachOnboardingService {

    /**
     * Marca o onboarding do usuário autenticado como concluído.
     *
     * <p><b>Idempotent:</b> YES — concluir de novo é no-op; o wizard pode reenviar sem medo, e o
     * duplo clique no botão "concluir" não é um caso a tratar na UI.
     * <p><b>Side Effects:</b> Database update em {@code tb_usuario} (só na primeira vez).
     * <p><b>Tenant-aware:</b> YES — resolve o usuário por {@code sub} + tenant corrente.
     *
     * @throws br.com.menthoros.backend.exception.DomainNotFoundException se o usuário não existir
     *         no tenant atual
     */
    void concluir();
}
