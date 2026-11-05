package br.com.menthoros.backend.controller;

import br.com.menthoros.backend.dto.output.TreinoRealizadoOutputDto;
import br.com.menthoros.backend.entity.TempoEmZonas;
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

import java.util.List;
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
        @DisplayName("200 com os campos de execução no formato do contrato")
        void retorna200ComCamposDeExecucao() throws Exception {
            UUID id = UUID.randomUUID();
            when(treinoService.getTreinoById(eq(id))).thenReturn(stubComExecucao(id));

            mockMvc.perform(get("/api/v1/treinos/realizados/{id}", id))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.velocidadeMaxKmh").value(12.19))
                    .andExpect(jsonPath("$.temperaturaMinC").value(19.0))
                    .andExpect(jsonPath("$.temperaturaMaxC").value(27.0))
                    .andExpect(jsonPath("$.elapsedTimeSeg").value(6032))
                    .andExpect(jsonPath("$.tempoEmZonas.fonte").value("FIT_RECORDS"))
                    .andExpect(jsonPath("$.tempoEmZonas.zonas[0].zona").value("Z5"))
                    .andExpect(jsonPath("$.tempoEmZonas.zonas[0].bpmMin").value(167))
                    .andExpect(jsonPath("$.tempoEmZonas.zonas[0].segundos").value(15))
                    .andExpect(jsonPath("$.tempoEmZonas.zonas[0].bpmMax").doesNotExist());
        }

        @Test
        @DisplayName("campos de execução ausentes não aparecem no JSON (NON_NULL)")
        void camposDeExecucaoAusentesFicamForaDoJson() throws Exception {
            UUID id = UUID.randomUUID();
            when(treinoService.getTreinoById(eq(id))).thenReturn(stubComDecoupling(id, null));

            mockMvc.perform(get("/api/v1/treinos/realizados/{id}", id))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.velocidadeMaxKmh").doesNotExist())
                    .andExpect(jsonPath("$.temperaturaMinC").doesNotExist())
                    .andExpect(jsonPath("$.temperaturaMaxC").doesNotExist())
                    .andExpect(jsonPath("$.elapsedTimeSeg").doesNotExist())
                    .andExpect(jsonPath("$.tempoEmZonas").doesNotExist());
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

    private static TreinoRealizadoOutputDto stubComExecucao(UUID id) {
        TempoEmZonas zonas = new TempoEmZonas(TempoEmZonas.FONTE_FIT_RECORDS,
                List.of(new TempoEmZonas.Zona("Z5", 167, null, 15)));
        TreinoRealizadoOutputDto base = stubComDecoupling(id, null);
        return new TreinoRealizadoOutputDto(
                base.id(), null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null,
                12.19, 19.0, 27.0, 6032, zonas);
    }

    private static TreinoRealizadoOutputDto stubComDecoupling(UUID id, Double decoupling) {
        // Construtor posicional (37 campos, V62: +nivelDor/nivelFadiga/nivelRecuperacao).
        // decouplingPercentual é o 24º campo (após runningDynamics, V53).
        return new TreinoRealizadoOutputDto(
                id, null, null, null, null, null, null, null, null, null,   // 1-10
                null, null, null, null, null, null, null, null, null, null, // 11-20
                null, null, // 21-22 (intensidadeReal)
                null, // 23 runningDynamics
                decoupling, null, null, null, null, null, null, // 24-30 (24=decouplingPercentual, 25=envelope, 26=serie)
                null, null, null, // 31-33 (nivelDor, nivelFadiga, nivelRecuperacao)
                null, null, null, null, null, null, null, null, null, null, null); // 34-37 + sensacoes, feedbackRegistradoEm
    }
}
