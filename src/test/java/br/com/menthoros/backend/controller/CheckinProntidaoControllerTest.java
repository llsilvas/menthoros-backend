package br.com.menthoros.backend.controller;

import br.com.menthoros.backend.dto.output.CheckinProntidaoOutputDto;
import br.com.menthoros.backend.enums.NivelProntidao;
import br.com.menthoros.backend.security.JwtTenantFilter;
import br.com.menthoros.backend.security.StructuredLoggingFilter;
import br.com.menthoros.backend.services.AtletaProgressService;
import br.com.menthoros.backend.services.CheckinProntidaoService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = CheckinProntidaoController.class, excludeFilters = @ComponentScan.Filter(
        type = FilterType.ASSIGNABLE_TYPE,
        classes = {JwtTenantFilter.class, StructuredLoggingFilter.class}))
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("CheckinProntidaoController")
class CheckinProntidaoControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private CheckinProntidaoService checkinProntidaoService;
    @MockitoBean private AtletaProgressService atletaProgressService;

    private final UUID atletaId = UUID.randomUUID();
    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        when(atletaProgressService.resolverAtletaIdAtual()).thenReturn(atletaId);
    }

    @Nested
    @DisplayName("POST /api/v1/checkins")
    class PostRegistrarCheckin {

        @Test
        @DisplayName("201 com body válido")
        void retorna201QuandoDadosValidos() throws Exception {
            when(checkinProntidaoService.registrarCheckin(any(), any())).thenReturn(stubOutputDto());

            mockMvc.perform(post("/api/v1/checkins")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(bodyValido()))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.nivelProntidao").value("PRONTO"));

            verify(atletaProgressService).resolverAtletaIdAtual();
            verify(checkinProntidaoService).registrarCheckin(eq(atletaId), any());
        }

        @Test
        @DisplayName("400 quando qualidadeSono está ausente")
        void retorna400QuandoCampoObrigatorioAusente() throws Exception {
            var bodySemSono = mapper.writeValueAsString(Map.of(
                    "humor", 7, "doresMusculares", 2, "nivelEnergia", 6, "estresse", 3));

            mockMvc.perform(post("/api/v1/checkins")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(bodySemSono))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("400 quando humor excede o limite (11 > 10)")
        void retorna400QuandoValorForaDaFaixa() throws Exception {
            var bodyInvalido = mapper.writeValueAsString(Map.of(
                    "qualidadeSono", 8, "humor", 11, "doresMusculares", 2, "nivelEnergia", 6, "estresse", 3));

            mockMvc.perform(post("/api/v1/checkins")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(bodyInvalido))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/checkins/{atletaId}/atual")
    class GetBuscarAtual {

        @Test
        @DisplayName("200 com o checkin quando existe")
        void retorna200QuandoExiste() throws Exception {
            when(checkinProntidaoService.buscarAtual(atletaId)).thenReturn(stubOutputDto());

            mockMvc.perform(get("/api/v1/checkins/{atletaId}/atual", atletaId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.nivelProntidao").value("PRONTO"));
        }

        @Test
        @DisplayName("204 quando não há checkin registrado")
        void retorna204QuandoAusente() throws Exception {
            when(checkinProntidaoService.buscarAtual(atletaId)).thenReturn(null);

            mockMvc.perform(get("/api/v1/checkins/{atletaId}/atual", atletaId))
                    .andExpect(status().isNoContent());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/checkins/{atletaId}")
    class GetBuscarHistorico {

        @Test
        @DisplayName("200 com lista de checkins usando dias default (7)")
        void retorna200ComDiasDefault() throws Exception {
            when(checkinProntidaoService.buscarHistorico(any(), anyInt())).thenReturn(List.of(stubOutputDto()));

            mockMvc.perform(get("/api/v1/checkins/{atletaId}", atletaId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1));

            verify(checkinProntidaoService).buscarHistorico(atletaId, 7);
        }

        @Test
        @DisplayName("400 quando dias excede o máximo permitido (91 > 90)")
        void retorna400QuandoDiasExcedeMaximo() throws Exception {
            mockMvc.perform(get("/api/v1/checkins/{atletaId}", atletaId).param("dias", "91"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("400 quando dias é menor que 1")
        void retorna400QuandoDiasMenorQueUm() throws Exception {
            mockMvc.perform(get("/api/v1/checkins/{atletaId}", atletaId).param("dias", "0"))
                    .andExpect(status().isBadRequest());
        }
    }

    private String bodyValido() throws Exception {
        return mapper.writeValueAsString(Map.of(
                "data", LocalDate.now().toString(),
                "qualidadeSono", 8,
                "humor", 7,
                "doresMusculares", 2,
                "nivelEnergia", 6,
                "estresse", 3,
                "observacoes", "Dormi bem"
        ));
    }

    private CheckinProntidaoOutputDto stubOutputDto() {
        return new CheckinProntidaoOutputDto(
                UUID.randomUUID(), atletaId, LocalDate.now(),
                8, 7, 2, 6, 3, "Dormi bem", new BigDecimal("0.820"), NivelProntidao.PRONTO);
    }
}
