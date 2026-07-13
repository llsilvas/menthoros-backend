package br.com.menthoros.backend.controller;

import br.com.menthoros.backend.dto.output.TreinoRealizadoOutputDto;
import br.com.menthoros.backend.enums.FonteDados;
import br.com.menthoros.backend.enums.TipoTreino;
import br.com.menthoros.backend.enums.TreinoExecucaoStatus;
import br.com.menthoros.backend.exception.DomainRuleViolationException;
import br.com.menthoros.backend.security.JwtTenantFilter;
import br.com.menthoros.backend.security.StructuredLoggingFilter;
import br.com.menthoros.backend.services.AtletaProgressService;
import br.com.menthoros.backend.services.TreinoService;
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

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AtletaTreinoController.class, excludeFilters = @ComponentScan.Filter(
        type = FilterType.ASSIGNABLE_TYPE,
        classes = {JwtTenantFilter.class, StructuredLoggingFilter.class}))
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("AtletaTreinoController")
class AtletaTreinoControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private TreinoService treinoService;
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
    @DisplayName("POST /api/v1/atletas/me/treinos")
    class PostRegistrarTreinoManual {

        @Test
        @DisplayName("201 com body válido e treino salvo")
        void retorna201QuandoDadosValidos() throws Exception {
            var outputDto = stubOutputDto();
            when(treinoService.registrarTreinoManualAtleta(any(), any())).thenReturn(outputDto);

            mockMvc.perform(post("/api/v1/atletas/me/treinos")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(bodyValido()))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.fonteDados.value").value("MANUAL"))
                    .andExpect(jsonPath("$.status.value").value("REALIZADO"));

            verify(atletaProgressService).resolverAtletaIdAtual();
        }

        @Test
        @DisplayName("400 quando campo obrigatório tipo está ausente")
        void retorna400QuandoTipoAusente() throws Exception {
            var bodySemTipo = mapper.writeValueAsString(Map.of(
                    "data", LocalDate.now().toString(),
                    "duracaoMinutos", 45,
                    "percepcaoEsforco", 6
            ));

            mockMvc.perform(post("/api/v1/atletas/me/treinos")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(bodySemTipo))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("400 quando percepcaoEsforco excede limite (RPE > 10)")
        void retorna400QuandoRpeInvalido() throws Exception {
            var bodyRpeInvalido = mapper.writeValueAsString(Map.of(
                    "tipo", "CONTINUO",
                    "data", LocalDate.now().toString(),
                    "duracaoMinutos", 45,
                    "percepcaoEsforco", 11
            ));

            mockMvc.perform(post("/api/v1/atletas/me/treinos")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(bodyRpeInvalido))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("400 quando data é no futuro")
        void retorna400QuandoDataNoFuturo() throws Exception {
            var bodyDataFutura = mapper.writeValueAsString(Map.of(
                    "tipo", "CONTINUO",
                    "data", LocalDate.now().plusDays(1).toString(),
                    "duracaoMinutos", 45,
                    "percepcaoEsforco", 6
            ));

            mockMvc.perform(post("/api/v1/atletas/me/treinos")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(bodyDataFutura))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("422 quando serviço lança DomainRuleViolationException (data > 7 dias)")
        void retorna422QuandoDataMaisQue7Dias() throws Exception {
            when(treinoService.registrarTreinoManualAtleta(any(), any()))
                    .thenThrow(new DomainRuleViolationException("Data do treino não pode ser anterior a 7 dias"));

            mockMvc.perform(post("/api/v1/atletas/me/treinos")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(bodyValido()))
                    .andExpect(status().isUnprocessableEntity());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/atletas/me/treinos")
    class GetListarTreinosRecentes {

        @Test
        @DisplayName("200 com lista de treinos")
        void retorna200ComListaDeTreinos() throws Exception {
            when(treinoService.listarTreinosRecentes(any(), anyInt()))
                    .thenReturn(List.of(stubOutputDto()));

            mockMvc.perform(get("/api/v1/atletas/me/treinos"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$[0].fonteDados.value").value("MANUAL"));

            verify(atletaProgressService).resolverAtletaIdAtual();
        }

        @Test
        @DisplayName("200 com lista vazia quando não há treinos")
        void retorna200ComListaVazia() throws Exception {
            when(treinoService.listarTreinosRecentes(any(), anyInt())).thenReturn(List.of());

            mockMvc.perform(get("/api/v1/atletas/me/treinos"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$").isEmpty());
        }

        @Test
        @DisplayName("200 quando parâmetro dias é informado")
        void retorna200QuandoDiasInformado() throws Exception {
            when(treinoService.listarTreinosRecentes(any(), anyInt())).thenReturn(List.of());

            mockMvc.perform(get("/api/v1/atletas/me/treinos").param("dias", "14"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("400 quando dias=0 (abaixo do mínimo permitido)")
        void retorna400QuandoDiasZero() throws Exception {
            mockMvc.perform(get("/api/v1/atletas/me/treinos").param("dias", "0"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("400 quando dias=31 (acima do máximo permitido)")
        void retorna400QuandoDias31() throws Exception {
            mockMvc.perform(get("/api/v1/atletas/me/treinos").param("dias", "31"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("200 quando dias=1 (valor mínimo permitido)")
        void retorna200QuandoDiasMinimo() throws Exception {
            when(treinoService.listarTreinosRecentes(any(), anyInt())).thenReturn(List.of());
            mockMvc.perform(get("/api/v1/atletas/me/treinos").param("dias", "1"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("200 quando dias=30 (valor máximo permitido)")
        void retorna200QuandoDiasMaximo() throws Exception {
            when(treinoService.listarTreinosRecentes(any(), anyInt())).thenReturn(List.of());
            mockMvc.perform(get("/api/v1/atletas/me/treinos").param("dias", "30"))
                    .andExpect(status().isOk());
        }
    }

    // -------------------------------------------------------------------------

    private String bodyValido() throws Exception {
        return mapper.writeValueAsString(Map.of(
                "tipo", "CONTINUO",
                "data", LocalDate.now().toString(),
                "duracaoMinutos", 45,
                "percepcaoEsforco", 6
        ));
    }

    private TreinoRealizadoOutputDto stubOutputDto() {
        return new TreinoRealizadoOutputDto(
                UUID.randomUUID(), LocalDate.now(), null, TipoTreino.CONTINUO,
                null, null, "00:45:00", null, null, null,
                null, null, null, null, null, null, null, null,
                6, null, null, null, // percepcaoEsforco..intensidadeReal
                null, // runningDynamics
                null, null, null, null, null, null, // decouplingPercentual..nivelEstresse
                FonteDados.MANUAL, TreinoExecucaoStatus.REALIZADO, null, null, null);
    }
}
