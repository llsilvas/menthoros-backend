package br.com.menthoros.backend.controller;

import br.com.menthoros.backend.config.core.JacksonConfig;
import br.com.menthoros.backend.repository.TenantValidationRepository;
import br.com.menthoros.backend.repository.UsuarioRepository;
import br.com.menthoros.backend.services.IntervalsIcuOAuthService;
import br.com.menthoros.backend.services.IntervalsIcuOAuthService.Resultado;
import br.com.menthoros.backend.services.UsuarioSyncService;
import br.com.menthoros.backend.testsupport.AuthWebMvcTestConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CA13 — o callback é uma superfície de redirect, não uma API. Quem está do outro lado é uma
 * pessoa num browser: <b>nenhum</b> caminho pode produzir 4xx/5xx, porque isso a deixaria numa
 * página de erro em vez de devolvê-la ao Menthoros.
 *
 * <p>É o teste que separa esta implementação do molde {@code StravaAuthController.callback}, que
 * faz {@code UUID.fromString(state)} sem try/catch.
 */
@WebMvcTest(IntervalsIcuCallbackController.class)
@Import({JacksonConfig.class, AuthWebMvcTestConfig.class})
class IntervalsIcuCallbackControllerAuthTest {

    private static final String CALLBACK = "/api/v1/integracoes/intervals-icu/callback";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private IntervalsIcuOAuthService oauthService;

    @MockitoBean
    private TenantValidationRepository tenantValidationRepository;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private UsuarioSyncService usuarioSyncService;

    @MockitoBean
    private UsuarioRepository usuarioRepository;

    @Nested
    @DisplayName("publico")
    class Publico {

        // O provedor redireciona o browser do atleta para cá, sem JWT. Se este endpoint exigisse
        // autenticação, o fluxo inteiro seria inalcançável.
        @Test
        @DisplayName("responde sem JWT — não é 401")
        void naoExigeAutenticacao() throws Exception {
            when(oauthService.exchangeCodeForToken(any(), any())).thenReturn(Resultado.SUCESSO);

            mockMvc.perform(get(CALLBACK).param("code", "c").param("state", "s"))
                    .andExpect(status().isFound());
        }
    }

    @Nested
    @DisplayName("sucesso")
    class Sucesso {

        @Test
        @DisplayName("302 para o front com intervals-icu=success")
        void redirecionaComSuccess() throws Exception {
            when(oauthService.exchangeCodeForToken("code-ok", "state-ok")).thenReturn(Resultado.SUCESSO);

            mockMvc.perform(get(CALLBACK).param("code", "code-ok").param("state", "state-ok"))
                    .andExpect(status().isFound())
                    .andExpect(header().string("Location",
                            org.hamcrest.Matchers.containsString("intervals-icu=success")));
        }

        // O front usa createHashRouter. Sem o "#/athlete/profile" o atleta cairia na rota raiz e
        // o parâmetro ficaria ANTES do '#', invisível para o useSearchParams — o fluxo terminaria
        // "com sucesso" e sem mostrar nada a ele. É a regressão que este teste impede.
        @Test
        @DisplayName("o parâmetro vai DENTRO do hash, na rota do perfil do atleta")
        void parametroVaiDentroDoHash() throws Exception {
            when(oauthService.exchangeCodeForToken(any(), any())).thenReturn(Resultado.SUCESSO);

            MvcResult resultado = mockMvc.perform(
                            get(CALLBACK).param("code", "c").param("state", "s"))
                    .andReturn();

            String location = resultado.getResponse().getHeader("Location");
            assertThat(location).contains("#/athlete/profile");
            // O '?' precisa vir depois do '#', senão o router nunca vê o parâmetro.
            assertThat(location.indexOf('#')).isLessThan(location.indexOf('?'));
        }
    }

    @Nested
    @DisplayName("caminhosDeFalha")
    class CaminhosDeFalha {

        @Test
        @DisplayName("?error=access_denied redireciona com erro e não chama o service")
        void erroDoProvedor() throws Exception {
            mockMvc.perform(get(CALLBACK).param("error", "access_denied"))
                    .andExpect(status().isFound())
                    .andExpect(header().string("Location",
                            org.hamcrest.Matchers.containsString("intervals-icu=error")));

            verifyNoInteractions(oauthService);
        }

        // Todo desfecho que não é SUCESSO vira redirect de erro. @EnumSource garante que um
        // valor novo no enum force uma decisão aqui em vez de passar despercebido.
        @ParameterizedTest
        @EnumSource(value = Resultado.class, names = "SUCESSO", mode = EnumSource.Mode.EXCLUDE)
        @DisplayName("todo resultado não-SUCESSO vira redirect com erro")
        void resultadosDeFalhaViramRedirect(Resultado resultado) throws Exception {
            when(oauthService.exchangeCodeForToken(any(), any())).thenReturn(resultado);

            mockMvc.perform(get(CALLBACK).param("code", "c").param("state", "s"))
                    .andExpect(status().isFound())
                    .andExpect(header().string("Location",
                            org.hamcrest.Matchers.containsString("intervals-icu=error")));
        }

        @Test
        @DisplayName("sem code e sem state ainda redireciona (não é 400)")
        void semParametros() throws Exception {
            when(oauthService.exchangeCodeForToken(any(), any())).thenReturn(Resultado.STATE_INVALIDO);

            mockMvc.perform(get(CALLBACK))
                    .andExpect(status().isFound())
                    .andExpect(header().string("Location",
                            org.hamcrest.Matchers.containsString("intervals-icu=error")));
        }

        // A rede de proteção do controller: se o service lançar algo que ele não previu, o atleta
        // ainda volta ao Menthoros. Sem este catch, viraria 500 na cara dele.
        @Test
        @DisplayName("exceção inesperada do service ainda redireciona — nunca 500")
        void excecaoInesperadaAindaRedireciona() throws Exception {
            when(oauthService.exchangeCodeForToken(any(), any()))
                    .thenThrow(new IllegalStateException("bug inesperado"));

            mockMvc.perform(get(CALLBACK).param("code", "c").param("state", "s"))
                    .andExpect(status().isFound())
                    .andExpect(header().string("Location",
                            org.hamcrest.Matchers.containsString("intervals-icu=error")));
        }
    }

    @Nested
    @DisplayName("naoVazaCredencial")
    class NaoVazaCredencial {

        // CA10 — a URL de redirect fica visível na barra do browser e no histórico. O code é
        // credencial de troca; o state carrega o atletaId.
        @Test
        @DisplayName("o code não aparece na URL de redirect")
        void codeNaoVazaNoRedirect() throws Exception {
            when(oauthService.exchangeCodeForToken(any(), any())).thenReturn(Resultado.FALHA_NA_TROCA);

            MvcResult resultado = mockMvc.perform(
                            get(CALLBACK).param("code", "code-super-secreto").param("state", "s"))
                    .andReturn();

            assertThat(resultado.getResponse().getHeader("Location"))
                    .doesNotContain("code-super-secreto");
        }

        @Test
        @DisplayName("o state não aparece na URL de redirect")
        void stateNaoVazaNoRedirect() throws Exception {
            when(oauthService.exchangeCodeForToken(any(), any())).thenReturn(Resultado.SUCESSO);

            MvcResult resultado = mockMvc.perform(
                            get(CALLBACK).param("code", "c").param("state", "state-com-atleta-id"))
                    .andReturn();

            assertThat(resultado.getResponse().getHeader("Location"))
                    .doesNotContain("state-com-atleta-id");
        }
    }
}
