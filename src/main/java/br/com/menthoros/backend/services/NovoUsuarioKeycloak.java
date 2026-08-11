package br.com.menthoros.backend.services;

import java.util.List;

/**
 * Dados de criação de um usuário no Keycloak.
 *
 * <p>O {@code toString()} é sobrescrito de propósito: o record geraria automaticamente uma
 * representação com a senha em texto claro, e basta um {@code log.debug("...{}", dados)} para o
 * segredo cair no arquivo de log. A spec proíbe senha em log, e a proibição precisa sobreviver a
 * quem for mexer nisto depois.</p>
 *
 * @param acoesObrigatorias required actions do Keycloak (ex.: {@code VERIFY_EMAIL})
 */
public record NovoUsuarioKeycloak(
        String email,
        String nome,
        String senha,
        boolean habilitado,
        List<String> acoesObrigatorias) {

    public NovoUsuarioKeycloak {
        acoesObrigatorias = acoesObrigatorias == null ? List.of() : List.copyOf(acoesObrigatorias);
    }

    @Override
    public String toString() {
        return "NovoUsuarioKeycloak[email=%s, nome=%s, senha=***, habilitado=%s, acoesObrigatorias=%s]"
                .formatted(email, nome, habilitado, acoesObrigatorias);
    }
}
