package br.com.menthoros.backend.controller;

import br.com.menthoros.backend.dto.output.UsuarioMeOutputDto;
import br.com.menthoros.backend.enums.UserRole;
import br.com.menthoros.backend.exception.DomainNotFoundException;
import br.com.menthoros.backend.security.JwtTenantFilter;
import br.com.menthoros.backend.security.StructuredLoggingFilter;
import br.com.menthoros.backend.services.UsuarioService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Teste de slice do controller (rota/status/JSON). Segurança desabilitada (addFilters=false):
 * o 401 é enforced pela SecurityFilterChain global e validado em teste de integração, não aqui.
 */
@WebMvcTest(controllers = UsuarioController.class, excludeFilters = @ComponentScan.Filter(
        type = FilterType.ASSIGNABLE_TYPE,
        classes = {JwtTenantFilter.class, StructuredLoggingFilter.class}))
@AutoConfigureMockMvc(addFilters = false)
class UsuarioControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UsuarioService usuarioService;

    @Nested
    @DisplayName("GET /api/v1/users/me")
    class GetMe {

        @Test
        @DisplayName("retorna 200 com a identidade serializada em JSON")
        void retornaIdentidade() throws Exception {
            UUID id = UUID.randomUUID();
            UUID atletaId = UUID.randomUUID();
            when(usuarioService.getCurrentUser()).thenReturn(new UsuarioMeOutputDto(
                    id, "João Silva", "joao@exemplo.com", UserRole.ATLETA,
                    new UsuarioMeOutputDto.Assessoria(UUID.randomUUID(), "Corridas Serra", "corridasserra"),
                    atletaId));

            mockMvc.perform(get("/api/v1/users/me"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(id.toString()))
                    .andExpect(jsonPath("$.nome").value("João Silva"))
                    .andExpect(jsonPath("$.role").value("ATLETA"))
                    .andExpect(jsonPath("$.assessoria.dominio").value("corridasserra"))
                    .andExpect(jsonPath("$.atletaId").value(atletaId.toString()));
            verify(usuarioService).getCurrentUser();
        }

        @Test
        @DisplayName("retorna 404 quando o service lança DomainNotFoundException")
        void retorna404() throws Exception {
            when(usuarioService.getCurrentUser())
                    .thenThrow(new DomainNotFoundException("Usuário não encontrado no tenant atual"));

            mockMvc.perform(get("/api/v1/users/me"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status").value(404));
            verify(usuarioService).getCurrentUser();
        }
    }
}
