package br.com.menthoros.backend.controller;

import br.com.menthoros.backend.dto.output.TreinoRealizadoOutputDto;
import br.com.menthoros.backend.exception.DomainNotFoundException;
import br.com.menthoros.backend.mapper.TreinoMapper;
import br.com.menthoros.backend.security.JwtTenantFilter;
import br.com.menthoros.backend.security.StructuredLoggingFilter;
import br.com.menthoros.backend.services.StravaActivityService;
import br.com.menthoros.backend.services.TreinoService;
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

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = TreinoRealizadoController.class, excludeFilters = @ComponentScan.Filter(
        type = FilterType.ASSIGNABLE_TYPE,
        classes = {JwtTenantFilter.class, StructuredLoggingFilter.class}))
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("TreinoRealizadoController")
class TreinoRealizadoControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private TreinoService treinoService;
    @MockitoBean private TreinoMapper treinoMapper;
    @MockitoBean private StravaActivityService stravaActivityService;

    @Nested
    @DisplayName("GET /api/v1/treinos/realizados/{id}")
    class ObterRealizado {

        @Test
        @DisplayName("200 com o detalhe do realizado incluindo decouplingPercentual")
        void retorna200ComDecoupling() throws Exception {
            UUID id = UUID.randomUUID();
            when(treinoService.getTreinoById(eq(id))).thenReturn(stubComDecoupling(id, 4.2));

            mockMvc.perform(get("/api/v1/treinos/realizados/{id}", id))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(id.toString()))
                    .andExpect(jsonPath("$.decouplingPercentual").value(4.2));
        }

        @Test
        @DisplayName("404 quando o treino não existe no tenant")
        void retorna404QuandoNaoEncontrado() throws Exception {
            UUID id = UUID.randomUUID();
            when(treinoService.getTreinoById(eq(id)))
                    .thenThrow(new DomainNotFoundException("Treino realizado não encontrado: " + id));

            mockMvc.perform(get("/api/v1/treinos/realizados/{id}", id))
                    .andExpect(status().isNotFound());
        }
    }

    private static TreinoRealizadoOutputDto stubComDecoupling(UUID id, Double decoupling) {
        // Construtor posicional (41 campos). decouplingPercentual é o 31º campo (após os 8 novos de running dynamics, V53).
        return new TreinoRealizadoOutputDto(
                id, null, null, null, null, null, null, null, null, null,   // 1-10
                null, null, null, null, null, null, null, null, null, null, // 11-20
                null, null, // 21-22 (intensidadeReal)
                null, null, null, null, null, null, null, null, // 23-30 running dynamics (V53) — todos null
                decoupling, null, null, null, null, null, null, // 31-37 (31=decouplingPercentual, 32=envelope, 33=serie)
                null, null, null, null); // 38-41
    }
}
