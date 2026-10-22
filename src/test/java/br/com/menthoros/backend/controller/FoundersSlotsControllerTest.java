package br.com.menthoros.backend.controller;

import br.com.menthoros.backend.config.core.JacksonConfig;
import br.com.menthoros.backend.dto.output.FoundersSlotsOutputDto;
import br.com.menthoros.backend.repository.TenantValidationRepository;
import br.com.menthoros.backend.repository.UsuarioRepository;
import br.com.menthoros.backend.services.FoundersSlotsService;
import br.com.menthoros.backend.services.UsuarioSyncService;
import br.com.menthoros.backend.testsupport.AuthWebMvcTestConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FoundersSlotsController.class)
@Import({JacksonConfig.class, AuthWebMvcTestConfig.class})
@DisplayName("GET /api/v1/founders/slots")
class FoundersSlotsControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private FoundersSlotsService foundersSlotsService;
    @MockitoBean private JwtDecoder jwtDecoder;
    @MockitoBean private UsuarioSyncService usuarioSyncService;
    @MockitoBean private UsuarioRepository usuarioRepository;
    @MockitoBean private TenantValidationRepository tenantValidationRepository;

    @Test
    @DisplayName("responde total/taken/remaining/open sem autenticação")
    void vagas() throws Exception {
        when(foundersSlotsService.obterVagas()).thenReturn(new FoundersSlotsOutputDto(10, 6, 4, true));

        mockMvc.perform(get("/api/v1/founders/slots"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(10))
                .andExpect(jsonPath("$.taken").value(6))
                .andExpect(jsonPath("$.remaining").value(4))
                .andExpect(jsonPath("$.open").value(true));
    }

    @Test
    @DisplayName("vagas esgotadas → open:false")
    void vagasEsgotadas() throws Exception {
        when(foundersSlotsService.obterVagas()).thenReturn(new FoundersSlotsOutputDto(10, 10, 0, false));

        mockMvc.perform(get("/api/v1/founders/slots"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.remaining").value(0))
                .andExpect(jsonPath("$.open").value(false));
    }
}
