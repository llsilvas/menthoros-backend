package br.com.menthoros.backend.controller;

import br.com.menthoros.backend.dto.output.UsuarioMeOutputDto;
import br.com.menthoros.backend.exception.ConsentVersionStaleException;
import br.com.menthoros.backend.enums.UserRole;
import br.com.menthoros.backend.exception.DomainNotFoundException;
import br.com.menthoros.backend.security.JwtTenantFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import br.com.menthoros.backend.dto.input.ConsentInputDto;
import org.springframework.http.MediaType;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

    @Autowired
    private ObjectMapper objectMapper;

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
                    id, "João Silva", "joao@exemplo.com", null, UserRole.ATLETA,
                    new UsuarioMeOutputDto.Assessoria(UUID.randomUUID(), "Corridas Serra", "corridasserra"),
                    atletaId, true, "2026-06-30", "2026-06-30", null, null, null, true));

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

    @Nested
    @DisplayName("POST /api/v1/users/me/consent")
    class RegistrarConsentimento {

        @Test
        @DisplayName("retorna 200 quando o aceite é completo e as versões conferem")
        void aceiteCompleto() throws Exception {
            mockMvc.perform(post("/api/v1/users/me/consent")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(new ConsentInputDto(true, true, "2026-06-30", "2026-06-30"))))
                    .andExpect(status().isOk());
            verify(usuarioService).registerConsent(any(ConsentInputDto.class));
        }

        @Test
        @DisplayName("retorna 400 quando os Termos não são aceitos")
        void termosRecusados() throws Exception {
            mockMvc.perform(post("/api/v1/users/me/consent")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(new ConsentInputDto(false, true, "2026-06-30", "2026-06-30"))))
                    .andExpect(status().isBadRequest());
            verify(usuarioService, never()).registerConsent(any());
        }

        @Test
        @DisplayName("retorna 400 quando a Política não é aceita")
        void politicaRecusada() throws Exception {
            mockMvc.perform(post("/api/v1/users/me/consent")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(new ConsentInputDto(true, false, "2026-06-30", "2026-06-30"))))
                    .andExpect(status().isBadRequest());
            verify(usuarioService, never()).registerConsent(any());
        }

        @Test
        @DisplayName("retorna 400 quando um dos aceites vem nulo")
        void aceiteNulo() throws Exception {
            mockMvc.perform(post("/api/v1/users/me/consent")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(new ConsentInputDto(null, true, "2026-06-30", "2026-06-30"))))
                    .andExpect(status().isBadRequest());
            verify(usuarioService, never()).registerConsent(any());
        }

        @Test
        @DisplayName("retorna 400 quando a versão vem em branco")
        void versaoEmBranco() throws Exception {
            mockMvc.perform(post("/api/v1/users/me/consent")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(new ConsentInputDto(true, true, "  ", "2026-06-30"))))
                    .andExpect(status().isBadRequest());
            verify(usuarioService, never()).registerConsent(any());
        }

        @Test
        @DisplayName("retorna 409 CONSENT_VERSION_STALE quando a versão está defasada")
        void versaoDefasada() throws Exception {
            doThrow(new ConsentVersionStaleException("Os termos foram atualizados."))
                    .when(usuarioService).registerConsent(any(ConsentInputDto.class));

            mockMvc.perform(post("/api/v1/users/me/consent")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(new ConsentInputDto(true, true, "2020-01-01", "2020-01-01"))))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("CONSENT_VERSION_STALE"));
        }

        private String json(ConsentInputDto dto) throws Exception {
            return objectMapper.writeValueAsString(dto);
        }
    }
}
