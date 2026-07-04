package br.com.menthoros.backend.controller;

import br.com.menthoros.backend.dto.output.KudosRecenteOutputDto;
import br.com.menthoros.backend.enums.MotivoKudos;
import br.com.menthoros.backend.security.JwtTenantFilter;
import br.com.menthoros.backend.security.StructuredLoggingFilter;
import br.com.menthoros.backend.services.AtletaProgressService;
import br.com.menthoros.backend.services.KudosService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AtletaKudosController.class, excludeFilters = @ComponentScan.Filter(
        type = FilterType.ASSIGNABLE_TYPE,
        classes = {JwtTenantFilter.class, StructuredLoggingFilter.class}))
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("AtletaKudosController")
class AtletaKudosControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private KudosService kudosService;
    @MockitoBean private AtletaProgressService atletaProgressService;

    private final UUID atletaId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        when(atletaProgressService.resolverAtletaIdAtual()).thenReturn(atletaId);
        setAtletaAuth();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Nested
    @DisplayName("GET /api/v1/atletas/me/kudos/recentes")
    class GetKudosRecentes {

        @Test
        @DisplayName("200 com os kudos do atleta")
        void retorna200ComKudos() throws Exception {
            when(kudosService.listarRecentes(atletaId)).thenReturn(List.of(
                    new KudosRecenteOutputDto(UUID.randomUUID(), MotivoKudos.CONSISTENCIA, Instant.now())));

            mockMvc.perform(get("/api/v1/atletas/me/kudos/recentes"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].motivo").value("CONSISTENCIA"));
        }

        @Test
        @DisplayName("200 com lista vazia quando não há kudos (não é erro)")
        void retorna200ComListaVazia() throws Exception {
            when(kudosService.listarRecentes(atletaId)).thenReturn(List.of());

            mockMvc.perform(get("/api/v1/atletas/me/kudos/recentes"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(0));
        }
    }

    private void setAtletaAuth() {
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken("atleta", null, List.of(new SimpleGrantedAuthority("ROLE_ATLETA"))));
    }
}
