package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.config.keycloak.KeycloakAdminProperties;
import br.com.menthoros.backend.exception.KeycloakIntegrationException;
import br.com.menthoros.backend.services.NovoUsuarioKeycloak;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.http.HttpMethod.DELETE;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.HttpMethod.PUT;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Primitivas do gateway usadas pelo auto-cadastro. Os contratos aqui não são adivinhados: os
 * endpoints, os códigos de resposta e o formato do corpo foram verificados contra um Keycloak 26.7
 * real em 2026-08-09.
 */
@DisplayName("KeycloakOrganizationGatewayImpl: primitivas do auto-cadastro")
class KeycloakOrganizationGatewayImplSignupTest {

    private static final String SERVER = "http://kc.test";
    private static final String TOKEN = SERVER + "/realms/master/protocol/openid-connect/token";
    private static final String USERS = SERVER + "/admin/realms/menthoros/users";
    private static final String USER_ID = "u-123";

    private MockRestServiceServer server;
    private KeycloakOrganizationGatewayImpl gateway;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(SERVER);
        server = MockRestServiceServer.bindTo(builder).build();

        KeycloakAdminProperties props = new KeycloakAdminProperties();
        props.setServerUrl(SERVER);
        props.setRealm("menthoros");
        props.setTokenRealm("master");
        props.setClientId("admin-cli");
        props.setUsername("admin");
        props.setPassword("pw");

        gateway = new KeycloakOrganizationGatewayImpl(builder.build(), props);
    }

    private void esperaToken() {
        server.expect(requestTo(TOKEN)).andExpect(method(POST))
                .andRespond(withSuccess("{\"access_token\":\"tkn\"}", MediaType.APPLICATION_JSON));
    }

    private static NovoUsuarioKeycloak novoUsuario() {
        return new NovoUsuarioKeycloak("maria@exemplo.com", "Maria Treinadora", "senha-secreta-123",
                true, List.of("VERIFY_EMAIL"));
    }

    @Nested
    @DisplayName("criarUsuario")
    class CriarUsuario {

        @Test
        @DisplayName("cadastro público: emailVerified false")
        void emailVerifiedFalseNoCadastroPublico() {
            esperaToken();
            server.expect(requestTo(USERS)).andExpect(method(POST))
                    .andExpect(content().string(containsString("\"emailVerified\":false")))
                    .andRespond(withStatus(HttpStatus.CREATED).header("Location", USERS + "/" + USER_ID));

            gateway.criarUsuario(novoUsuario());
            server.verify();
        }

        @Test
        @DisplayName("convite de fundadora: emailVerified true e sem VERIFY_EMAIL")
        void emailVerifiedTrueNoConvite() {
            esperaToken();
            server.expect(requestTo(USERS)).andExpect(method(POST))
                    .andExpect(content().string(containsString("\"emailVerified\":true")))
                    .andExpect(content().string(org.hamcrest.Matchers.not(containsString("VERIFY_EMAIL"))))
                    .andRespond(withStatus(HttpStatus.CREATED).header("Location", USERS + "/" + USER_ID));
            gateway.criarUsuario(new NovoUsuarioKeycloak("maria@exemplo.com", "Maria Treinadora",
                    "senha-secreta-123", true, List.of(), true));

            server.verify();
        }

        @Test
        @DisplayName("extrai o id do header Location")
        void extraiIdDaLocation() {
            esperaToken();
            server.expect(requestTo(USERS)).andExpect(method(POST))
                    .andExpect(header("Authorization", "Bearer tkn"))
                    .andExpect(content().string(containsString("\"email\":\"maria@exemplo.com\"")))
                    .andExpect(content().string(containsString("VERIFY_EMAIL")))
                    .andRespond(withStatus(HttpStatus.CREATED)
                            .header("Location", USERS + "/" + USER_ID));

            assertThat(gateway.criarUsuario(novoUsuario())).isEqualTo(USER_ID);
            server.verify();
        }

        @Test
        @DisplayName("envia a senha como credencial NÃO temporária — senha temporária forçaria troca no 1º login")
        void enviaCredencialDefinitiva() {
            esperaToken();
            server.expect(requestTo(USERS)).andExpect(method(POST))
                    .andExpect(content().string(containsString("\"temporary\":false")))
                    .andExpect(content().string(containsString("\"type\":\"password\"")))
                    .andRespond(withStatus(HttpStatus.CREATED).header("Location", USERS + "/" + USER_ID));

            gateway.criarUsuario(novoUsuario());
            server.verify();
        }

        @Test
        @DisplayName("Location ausente vira KeycloakIntegrationException, não NPE silencioso")
        void semLocation() {
            esperaToken();
            server.expect(requestTo(USERS)).andRespond(withStatus(HttpStatus.CREATED));

            assertThatThrownBy(() -> gateway.criarUsuario(novoUsuario()))
                    .isInstanceOf(KeycloakIntegrationException.class);
        }

        @Test
        @DisplayName("a mensagem de erro não carrega a senha")
        void erroNaoVazaSenha() {
            esperaToken();
            server.expect(requestTo(USERS)).andRespond(withStatus(HttpStatus.CONFLICT));

            assertThatThrownBy(() -> gateway.criarUsuario(novoUsuario()))
                    .isInstanceOf(KeycloakIntegrationException.class)
                    .hasMessageNotContaining("senha-secreta-123");
        }
    }

    @Nested
    @DisplayName("buscarUsuarioIdPorEmail")
    class BuscarUsuarioIdPorEmail {

        @Test
        @DisplayName("usa exact=true — sem isso o Keycloak faz busca por prefixo e casa o e-mail errado")
        void buscaExata() {
            esperaToken();
            server.expect(requestTo(USERS + "?email=maria@exemplo.com&exact=true"))
                    .andExpect(method(GET))
                    .andRespond(withSuccess("[{\"id\":\"" + USER_ID + "\"}]", MediaType.APPLICATION_JSON));

            assertThat(gateway.buscarUsuarioIdPorEmail("maria@exemplo.com")).contains(USER_ID);
            server.verify();
        }

        @Test
        @DisplayName("lista vazia vira Optional vazio, não exceção")
        void naoEncontrado() {
            esperaToken();
            server.expect(requestTo(USERS + "?email=ninguem@exemplo.com&exact=true"))
                    .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

            assertThat(gateway.buscarUsuarioIdPorEmail("ninguem@exemplo.com")).isEmpty();
        }
    }

    @Nested
    @DisplayName("enviarVerificacaoDeEmail")
    class EnviarVerificacaoDeEmail {

        @Test
        @DisplayName("chama PUT send-verify-email")
        void enviaEmail() {
            esperaToken();
            server.expect(requestTo(USERS + "/" + USER_ID + "/send-verify-email"))
                    .andExpect(method(PUT))
                    .andRespond(withStatus(HttpStatus.NO_CONTENT));

            gateway.enviarVerificacaoDeEmail(USER_ID);
            server.verify();
        }

        @Test
        @DisplayName("400 'User is disabled' propaga como falha — é o caminho real quando o usuário não está habilitado")
        void usuarioDesabilitado() {
            esperaToken();
            server.expect(requestTo(USERS + "/" + USER_ID + "/send-verify-email"))
                    .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                            .body("{\"errorMessage\":\"User is disabled\"}")
                            .contentType(MediaType.APPLICATION_JSON));

            assertThatThrownBy(() -> gateway.enviarVerificacaoDeEmail(USER_ID))
                    .isInstanceOf(KeycloakIntegrationException.class);
        }
    }

    @Nested
    @DisplayName("Compensação")
    class Compensacao {

        @Test
        @DisplayName("removerUsuario trata 404 como sucesso — compensar o que já sumiu não é erro")
        void removerUsuarioInexistente() {
            esperaToken();
            server.expect(requestTo(USERS + "/" + USER_ID)).andExpect(method(DELETE))
                    .andRespond(withStatus(HttpStatus.NOT_FOUND));

            assertThatCode(() -> gateway.removerUsuario(USER_ID)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("removerOrganization trata 404 como sucesso")
        void removerOrganizationInexistente() {
            esperaToken();
            server.expect(requestTo(SERVER + "/admin/realms/menthoros/organizations/org-9"))
                    .andExpect(method(DELETE))
                    .andRespond(withStatus(HttpStatus.NOT_FOUND));

            assertThatCode(() -> gateway.removerOrganization("org-9")).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("mas 500 na remoção PROPAGA — é o caso que vira RECONCILIATION_REQUIRED")
        void falhaRealNaCompensacaoPropaga() {
            esperaToken();
            server.expect(requestTo(USERS + "/" + USER_ID))
                    .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

            assertThatThrownBy(() -> gateway.removerUsuario(USER_ID))
                    .isInstanceOf(KeycloakIntegrationException.class);
        }
    }

    @Nested
    @DisplayName("Vínculos")
    class Vinculos {

        @Test
        @DisplayName("atribuirRoleDeRealm busca a role e a envia como lista")
        void atribuiRole() {
            esperaToken();
            server.expect(requestTo(SERVER + "/admin/realms/menthoros/roles/TECNICO"))
                    .andExpect(method(GET))
                    .andRespond(withSuccess("{\"id\":\"r-1\",\"name\":\"TECNICO\"}", MediaType.APPLICATION_JSON));
            server.expect(requestTo(USERS + "/" + USER_ID + "/role-mappings/realm"))
                    .andExpect(method(POST))
                    .andExpect(content().string(containsString("\"name\":\"TECNICO\"")))
                    .andRespond(withStatus(HttpStatus.NO_CONTENT));

            gateway.atribuirRoleDeRealm(USER_ID, "TECNICO");
            server.verify();
        }

        @Test
        @DisplayName("adicionarMembroNaOrganization envia o id do usuário como corpo cru")
        void adicionaMembro() {
            esperaToken();
            server.expect(requestTo(SERVER + "/admin/realms/menthoros/organizations/org-1/members"))
                    .andExpect(method(POST))
                    .andExpect(content().string(containsString(USER_ID)))
                    .andRespond(withStatus(HttpStatus.CREATED));

            gateway.adicionarMembroNaOrganization("org-1", USER_ID);
            server.verify();
        }

        @Test
        @DisplayName("definirHabilitado envia apenas o campo enabled")
        void habilita() {
            esperaToken();
            server.expect(requestTo(USERS + "/" + USER_ID)).andExpect(method(PUT))
                    .andExpect(content().string(containsString("\"enabled\":true")))
                    .andExpect(content().string(not(containsString("password"))))
                    .andRespond(withStatus(HttpStatus.NO_CONTENT));

            gateway.definirHabilitado(USER_ID, true);
            server.verify();
        }
    }

    @Nested
    @DisplayName("NovoUsuarioKeycloak")
    class Segredos {

        @Test
        @DisplayName("toString() não expõe a senha — o record a exporia por padrão")
        void toStringNaoVazaSenha() {
            assertThat(novoUsuario().toString())
                    .doesNotContain("senha-secreta-123")
                    .contains("senha=***")
                    .contains("maria@exemplo.com");
        }
    }
}
