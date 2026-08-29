package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.entity.FoundingInvite;
import br.com.menthoros.backend.enums.PlanoAssessoria;
import br.com.menthoros.backend.enums.ProvisioningOrigin;

import java.util.List;

/**
 * O que muda na saga de provisionamento conforme a porta de entrada.
 *
 * <p>Resolvido <strong>antes</strong> da saga começar, para que os passos fiquem lineares: cada
 * um lê o modo em vez de decidir sozinho. Dois modos, e as diferenças estão todas aqui — quem
 * quiser um terceiro (ex.: convite de técnico) acrescenta uma fábrica, não um {@code if} no meio
 * do fluxo.</p>
 *
 * @param invite            convite consumido no sucesso; {@code null} no cadastro público
 * @param emailVerificado   {@code true} quando a posse do e-mail já foi provada pelo token
 * @param acoesObrigatorias required actions do usuário no Keycloak
 * @param enviarVerificacao se a saga dispara {@code send-verify-email}
 * @param aplicarLimites    se os limites anti-abuso por e-mail/dia e teto diário valem
 */
record ProvisioningMode(
        ProvisioningOrigin origin,
        PlanoAssessoria plano,
        int maxAtletas,
        int maxTecnicos,
        boolean founding,
        FoundingInvite invite,
        boolean emailVerificado,
        List<String> acoesObrigatorias,
        boolean enviarVerificacao,
        boolean aplicarLimites) {

    static final String ACAO_VERIFICAR_EMAIL = "VERIFY_EMAIL";

    static ProvisioningMode publicSignup() {
        return new ProvisioningMode(ProvisioningOrigin.PUBLIC_SIGNUP, PlanoAssessoria.BASIC, 20, 1,
                false, null, false, List.of(ACAO_VERIFICAR_EMAIL), true, true);
    }

    /**
     * Fundadora: GRATUITO 10/1, marcada como {@code founding}, sem verificação de e-mail (o token
     * entregue por e-mail já provou a posse) e sem os limites anti-abuso — o token é o portão.
     */
    static ProvisioningMode foundingInvite(FoundingInvite invite) {
        return new ProvisioningMode(ProvisioningOrigin.FOUNDING_INVITE, PlanoAssessoria.GRATUITO, 10, 1,
                true, invite, true, List.of(), false, false);
    }

    boolean porConvite() {
        return invite != null;
    }
}
