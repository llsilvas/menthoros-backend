package br.com.menthoros.backend.controller;

import br.com.menthoros.backend.config.core.JacksonConfig;
import br.com.menthoros.backend.dto.output.FoundingInviteLookupOutputDto;
import br.com.menthoros.backend.exception.DomainNotFoundException;
import br.com.menthoros.backend.repository.UsuarioRepository;
import br.com.menthoros.backend.services.FoundingInviteService;
import br.com.menthoros.backend.services.UsuarioSyncService;
import br.com.menthoros.backend.testsupport.AuthWebMvcTestConfig;
import br.com.menthoros.backend.repository.TenantValidationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FoundingInviteController.class)
@Import({JacksonConfig.class, AuthWebMvcTestConfig.class})
@DisplayName("GET /api/public/founding-invites/{token}")
class FoundingInviteControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private FoundingInviteService foundingInviteService;
    @MockitoBean private JwtDecoder jwtDecoder;
    @MockitoBean private UsuarioSyncService usuarioSyncService;
    @MockitoBean private UsuarioRepository usuarioRepository;
    @MockitoBean private TenantValidationRepository tenantValidationRepository;

    @Test
    @DisplayName("token ativo → 200 com nome e e-mail, sem autenticação")
    void ativo() throws Exception {
        when(foundingInviteService.lookup("tok-1"))
                .thenReturn(new FoundingInviteLookupOutputDto("Maria Treinadora", "maria@exemplo.com"));

        mockMvc.perform(get("/api/public/founding-invites/tok-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Maria Treinadora"))
                .andExpect(jsonPath("$.email").value("maria@exemplo.com"));
    }

    @Test
    @DisplayName("token inválido em qualquer forma → 404, com corpo que não revela o motivo")
    void invalido() throws Exception {
        when(foundingInviteService.lookup("tok-x")).thenThrow(new DomainNotFoundException("Convite inválido ou expirado"));

        var corpo = mockMvc.perform(get("/api/public/founding-invites/tok-x"))
                .andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();

        // A mensagem é a mesma para todos os estados; o que não pode aparecer é o motivo real.
        assertThat(corpo).doesNotContain("convertido").doesNotContain("invalidado").doesNotContain("inexistente");
    }
}
