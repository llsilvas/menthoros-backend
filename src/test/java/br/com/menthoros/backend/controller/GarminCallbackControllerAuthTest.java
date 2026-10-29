package br.com.menthoros.backend.controller;

import br.com.menthoros.backend.config.core.JacksonConfig;
import br.com.menthoros.backend.exception.GarminCanalIndisponivelException;
import br.com.menthoros.backend.repository.TenantValidationRepository;
import br.com.menthoros.backend.repository.UsuarioRepository;
import br.com.menthoros.backend.services.GarminOAuthService;
import br.com.menthoros.backend.services.GarminOAuthService.Resultado;
import br.com.menthoros.backend.services.UsuarioSyncService;
import br.com.menthoros.backend.testsupport.AuthWebMvcTestConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** CA2/CA3/CA13: o callback é público e todo caminho termina em 302 para dentro do hash. */
@WebMvcTest(GarminCallbackController.class)
@Import({JacksonConfig.class, AuthWebMvcTestConfig.class})
class GarminCallbackControllerAuthTest {

    private static final String CALLBACK = "/api/v1/integracoes/garmin/callback";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GarminOAuthService oauthService;
    @MockitoBean
    private TenantValidationRepository tenantValidationRepository;
    @MockitoBean
    private JwtDecoder jwtDecoder;
    @MockitoBean
    private UsuarioSyncService usuarioSyncService;
    @MockitoBean
    private UsuarioRepository usuarioRepository;

    private String location(String... params) throws Exception {
        var req = get(CALLBACK);
        for (int i = 0; i < params.length; i += 2) {
            req = req.param(params[i], params[i + 1]);
        }
        var res = mockMvc.perform(req).andExpect(status().isFound()).andReturn();
        return res.getResponse().getHeader("Location");
    }

    @Test
    @DisplayName("sem JWT, sucesso: 302 com garmin=success dentro do hash")
    void sucesso() throws Exception {
        when(oauthService.concluir("s", "c")).thenReturn(Resultado.SUCESSO);

        String location = location("code", "c", "state", "s");

        assertThat(location).contains("#/athlete/profile").endsWith("garmin=success");
        assertThat(location.indexOf('#')).isLessThan(location.indexOf('?'));
    }

    @Test
    @DisplayName("DENIED vira garmin=denied")
    void denied() throws Exception {
        when(oauthService.concluir("s", null)).thenReturn(Resultado.DENIED);

        assertThat(location("state", "s")).endsWith("garmin=denied");
    }

    @Test
    @DisplayName("?error=access_denied descarta o code e usa o fluxo de recusa")
    void erroDoProvedor() throws Exception {
        when(oauthService.concluir("s", null)).thenReturn(Resultado.DENIED);

        assertThat(location("state", "s", "error", "access_denied", "code", "ignorado")).endsWith("garmin=denied");
        verify(oauthService).concluir("s", null);
    }

    @ParameterizedTest
    @EnumSource(value = Resultado.class, names = {"SUCESSO", "DENIED"}, mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("qualquer outro resultado vira garmin=error")
    void falhas(Resultado resultado) throws Exception {
        when(oauthService.concluir(any(), any())).thenReturn(resultado);

        assertThat(location("code", "c", "state", "s")).endsWith("garmin=error");
    }

    @Test
    @DisplayName("canal desligado: redirect com error, não 503")
    void canalDesligado() throws Exception {
        when(oauthService.concluir(any(), any())).thenThrow(new GarminCanalIndisponivelException("off"));

        assertThat(location("code", "c", "state", "s")).endsWith("garmin=error");
    }

    @Test
    @DisplayName("exceção inesperada: redirect com error")
    void inesperada() throws Exception {
        when(oauthService.concluir(any(), any())).thenThrow(new IllegalStateException("boom"));

        assertThat(location("code", "c", "state", "s")).endsWith("garmin=error");
    }

    @Test
    @DisplayName("sem nenhum parâmetro ainda é 302")
    void semParametros() throws Exception {
        when(oauthService.concluir(null, null)).thenReturn(Resultado.STATE_INVALIDO);

        assertThat(location()).endsWith("garmin=error");
    }
}
