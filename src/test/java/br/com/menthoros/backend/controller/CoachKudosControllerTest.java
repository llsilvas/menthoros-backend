package br.com.menthoros.backend.controller;

import br.com.menthoros.backend.dto.output.KudosOutputDto;
import br.com.menthoros.backend.enums.MotivoKudos;
import br.com.menthoros.backend.exception.DuplicateResourceException;
import br.com.menthoros.backend.security.JwtTenantFilter;
import br.com.menthoros.backend.security.StructuredLoggingFilter;
import br.com.menthoros.backend.services.KudosService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
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
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = CoachKudosController.class, excludeFilters = @ComponentScan.Filter(
        type = FilterType.ASSIGNABLE_TYPE,
        classes = {JwtTenantFilter.class, StructuredLoggingFilter.class}))
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("CoachKudosController")
class CoachKudosControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private KudosService kudosService;

    private final UUID atletaId = UUID.randomUUID();
    private final UUID coachId = UUID.randomUUID();
    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        setCoachAuth();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Nested
    @DisplayName("POST /api/v1/coach/atletas/{atletaId}/kudos")
    class PostRegistrarKudo {

        @Test
        @DisplayName("201 com body válido")
        void retorna201QuandoDadosValidos() throws Exception {
            when(kudosService.registrar(eq(atletaId), any())).thenReturn(stubOutputDto());

            mockMvc.perform(post("/api/v1/coach/atletas/{atletaId}/kudos", atletaId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(mapper.writeValueAsString(Map.of("motivo", "CONSISTENCIA"))))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.motivo").value("CONSISTENCIA"));

            verify(kudosService).registrar(eq(atletaId), any());
        }

        @Test
        @DisplayName("400 quando motivo está ausente")
        void retorna400QuandoMotivoAusente() throws Exception {
            mockMvc.perform(post("/api/v1/coach/atletas/{atletaId}/kudos", atletaId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("400 quando motivo está fora do enum")
        void retorna400QuandoMotivoForaDoEnum() throws Exception {
            mockMvc.perform(post("/api/v1/coach/atletas/{atletaId}/kudos", atletaId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(mapper.writeValueAsString(Map.of("motivo", "INEXISTENTE"))))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("409 ao repetir o mesmo motivo/atleta/coach no mesmo dia")
        void retorna409QuandoDuplicataMesmoDia() throws Exception {
            when(kudosService.registrar(eq(atletaId), any()))
                    .thenThrow(new DuplicateResourceException("Você já reconheceu a consistência deste atleta hoje."));

            mockMvc.perform(post("/api/v1/coach/atletas/{atletaId}/kudos", atletaId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(mapper.writeValueAsString(Map.of("motivo", "CONSISTENCIA"))))
                    .andExpect(status().isConflict());
        }
    }

    private void setCoachAuth() {
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken("coach", null, List.of(new SimpleGrantedAuthority("ROLE_TECNICO"))));
    }

    private KudosOutputDto stubOutputDto() {
        return new KudosOutputDto(UUID.randomUUID(), atletaId, coachId, MotivoKudos.CONSISTENCIA, Instant.now());
    }
}
