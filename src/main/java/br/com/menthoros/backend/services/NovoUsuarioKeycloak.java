package br.com.menthoros.backend.services;

import java.util.List;

/**
 * Dados para criar um usuário no Keycloak via Admin REST API.
 *
 * <p>O {@code toString()} é sobrescrito de propósito: o record geraria automaticamente uma
 * versão com a senha em claro, e qualquer log acidental ({@code log.info("{}", dados)}) a
 * exporia.</p>
 *
 * @param emailVerificado {@code true} quando a posse do e-mail já foi provada por outro canal
 *                        (token de convite) e o Keycloak não deve exigir verificação
 */
public record NovoUsuarioKeycloak(
        String email,
        String nome,
        String senha,
        boolean habilitado,
        List<String> acoesObrigatorias,
        boolean emailVerificado) {

    public NovoUsuarioKeycloak {
        acoesObrigatorias = acoesObrigatorias == null ? List.of() : List.copyOf(acoesObrigatorias);
    }

    /** Cadastro público: e-mail ainda não verificado (o Keycloak envia a verificação). */
    public NovoUsuarioKeycloak(String email, String nome, String senha, boolean habilitado,
                               List<String> acoesObrigatorias) {
        this(email, nome, senha, habilitado, acoesObrigatorias, false);
    }

    @Override
    public String toString() {
        return "NovoUsuarioKeycloak[email=%s, nome=%s, senha=***, habilitado=%s, acoesObrigatorias=%s, emailVerificado=%s]"
                .formatted(email, nome, habilitado, acoesObrigatorias, emailVerificado);
    }
}
