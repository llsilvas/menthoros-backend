package br.com.menthoros.backend.controller;

import br.com.menthoros.backend.config.core.JacksonConfig;
import br.com.menthoros.backend.dto.output.TrajetoShareConsentOutputDto;
import br.com.menthoros.backend.repository.TenantValidationRepository;
import br.com.menthoros.backend.repository.UsuarioRepository;
import br.com.menthoros.backend.services.TrajetoShareConsentService;
import br.com.menthoros.backend.services.UsuarioSyncService;
import br.com.menthoros.backend.testsupport.AuthWebMvcTestConfig;
import br.com.menthoros.backend.testsupport.JwtTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static br.com.menthoros.backend.testsupport.JwtTestSupport.atletaJwt;
import static br.com.menthoros.backend.testsupport.JwtTestSupport.tecnicoJwt;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Autorização com a cadeia real: só o próprio atleta decide sobre o consentimento (CA8). O teste
 * de contrato ({@link AtletaConsentimentoControllerTest}) roda sem filtros e não prova isso.
 */
@WebMvcTest(AtletaConsentimentoController.class)
@Import({JacksonConfig.class, AuthWebMvcTestConfig.class})
class AtletaConsentimentoControllerAuthTest {

    private static final String URL = "/api/v1/atletas/me/consentimentos/trajeto-compartilhamento";

    @Autowired private MockMvc mockMvc;
    @MockitoBean private TrajetoShareConsentService service;
    @MockitoBean private TenantValidationRepository tenantValidationRepository;
    @MockitoBean private JwtDecoder jwtDecoder;
    @MockitoBean private UsuarioSyncService usuarioSyncService;
    @MockitoBean private UsuarioRepository usuarioRepository;

    @BeforeEach
    void setUp() {
        JwtTestSupport.stubUsuarioAtivo(usuarioSyncService);
    }

    @Test
    @DisplayName("atleta: 200 no GET")
    void atleta_200() throws Exception {
        when(service.estadoAtual()).thenReturn(new TrajetoShareConsentOutputDto(false, "2026-11-09", null));
        mockMvc.perform(get(URL).with(atletaJwt())).andExpect(status().isOk());
    }

    @Test
    @DisplayName("TECNICO não decide pelo atleta: 403 nos três verbos (CA8)")
    void tecnico_403() throws Exception {
        mockMvc.perform(get(URL).with(tecnicoJwt())).andExpect(status().isForbidden());
        mockMvc.perform(post(URL).with(tecnicoJwt()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"policyVersion\":\"2026-11-09\"}")).andExpect(status().isForbidden());
        mockMvc.perform(delete(URL).with(tecnicoJwt())).andExpect(status().isForbidden());
        verify(service, never()).conceder(any());
        verify(service, never()).revogar();
    }

    @Test
    @DisplayName("anônimo: 401")
    void anonimo_401() throws Exception {
        mockMvc.perform(get(URL)).andExpect(status().isUnauthorized());
    }
}
