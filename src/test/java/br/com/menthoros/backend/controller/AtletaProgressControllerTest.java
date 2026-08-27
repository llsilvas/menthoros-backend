package br.com.menthoros.backend.controller;

import br.com.menthoros.backend.dto.output.AderenciasSemanalDto;
import br.com.menthoros.backend.dto.output.AtletaHomeDto;
import br.com.menthoros.backend.dto.output.EtapaTreinoDto;
import br.com.menthoros.backend.dto.output.PmcPontoDto;
import br.com.menthoros.backend.dto.output.ProvaOutputDto;
import br.com.menthoros.backend.dto.output.ReadinessDto;
import br.com.menthoros.backend.dto.output.RecordeDto;
import br.com.menthoros.backend.dto.output.ZonaDistribuicaoDto;
import br.com.menthoros.backend.enums.DistanciaProva;
import br.com.menthoros.backend.enums.ProvaStatus;
import br.com.menthoros.backend.enums.TipoProva;
import br.com.menthoros.backend.exception.DomainNotFoundException;
import br.com.menthoros.backend.security.JwtTenantFilter;
import br.com.menthoros.backend.security.StructuredLoggingFilter;
import br.com.menthoros.backend.services.AtletaProgressService;
import br.com.menthoros.backend.services.ProvaService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AtletaProgressController.class, excludeFilters = @ComponentScan.Filter(
        type = FilterType.ASSIGNABLE_TYPE,
        classes = {JwtTenantFilter.class, StructuredLoggingFilter.class}))
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("AtletaProgressController")
class AtletaProgressControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private AtletaProgressService service;
    @MockitoBean private ProvaService provaService;

    private final UUID atletaId = UUID.randomUUID();

    @Test
    @DisplayName("GET /{id}/metricas/historico → 200 com a série")
    void historico() throws Exception {
        when(service.getHistoricoPmc(eq(atletaId), any(), any()))
                .thenReturn(List.of(new PmcPontoDto(LocalDate.of(2026, 6, 1), 50.0, 60.0, -10.0, 80, "ACUMULANDO_FADIGA")));

        mockMvc.perform(get("/api/v1/atletas/{id}/metricas/historico", atletaId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].ctl").value(50.0))
                .andExpect(jsonPath("$[0].tss").value(80));
    }

    @Test
    @DisplayName("GET /{id}/metricas/historico repassa from/to ao service")
    void historicoComParametros() throws Exception {
        when(service.getHistoricoPmc(eq(atletaId), eq(LocalDate.of(2026, 5, 1)), eq(LocalDate.of(2026, 6, 1))))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/v1/atletas/{id}/metricas/historico", atletaId)
                        .param("from", "2026-05-01").param("to", "2026-06-01"))
                .andExpect(status().isOk());

        verify(service).getHistoricoPmc(atletaId, LocalDate.of(2026, 5, 1), LocalDate.of(2026, 6, 1));
    }

    @Test
    @DisplayName("GET /{id}/metricas/historico → 404 quando atleta não existe no tenant")
    void historicoNotFound() throws Exception {
        when(service.getHistoricoPmc(eq(atletaId), any(), any()))
                .thenThrow(new DomainNotFoundException("Atleta não encontrado"));

        mockMvc.perform(get("/api/v1/atletas/{id}/metricas/historico", atletaId))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /{id}/metricas/zonas → 200")
    void zonas() throws Exception {
        when(service.getDistribuicaoZonas(eq(atletaId), any(), any()))
                .thenReturn(new ZonaDistribuicaoDto(600, 0, 300, 0, 0, 900));

        mockMvc.perform(get("/api/v1/atletas/{id}/metricas/zonas", atletaId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.duracaoTotalSegundos").value(900));
    }

    @Test
    @DisplayName("GET /{id}/recordes → 200")
    void recordes() throws Exception {
        when(service.getRecordes(atletaId))
                .thenReturn(List.of(new RecordeDto("10k", 2730L, LocalDate.of(2026, 5, 8), UUID.randomUUID())));

        mockMvc.perform(get("/api/v1/atletas/{id}/recordes", atletaId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].distancia").value("10k"));
    }

    @Test
    @DisplayName("GET /me/readiness → 200 e resolve o atleta do token")
    void readiness() throws Exception {
        when(service.resolverAtletaIdAtual()).thenReturn(atletaId);
        when(service.getReadinessAtual(atletaId)).thenReturn(
                new ReadinessDto(72, "BOM", new ReadinessDto.Fatores(8.0, 50.0, 44.0, 6), "nota"));

        mockMvc.perform(get("/api/v1/atletas/me/readiness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.score").value(72))
                .andExpect(jsonPath("$.classificacao").value("BOM"));

        verify(service).getReadinessAtual(atletaId);
    }

    @Test
    @DisplayName("GET /me/home → 200")
    void home() throws Exception {
        when(service.resolverAtletaIdAtual()).thenReturn(atletaId);
        UUID bloco = UUID.randomUUID();
        when(service.getHome(atletaId)).thenReturn(new AtletaHomeDto(
                new AtletaHomeDto.ProximoTreino(LocalDate.now().plusDays(1), "INTERVALADO", "6x800m", 45, "Z4", 70, 0.95,
                        List.of(new EtapaTreinoDto(1, "AQUECIMENTO", "Trote", 10, null, null, null, null, null, null),
                                new EtapaTreinoDto(2, "ESFORCO", null, 4, null, null, null, null, bloco, 2))),
                new AtletaHomeDto.MetricasChave(52.0, 44.0, 8.0, 0, null, "FORMA_IDEAL")));

        mockMvc.perform(get("/api/v1/atletas/me/home"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.proximoTreino.tipoTreino").value("INTERVALADO"))
                .andExpect(jsonPath("$.proximoTreino.duracaoMin").value(45))
                .andExpect(jsonPath("$.proximoTreino.zonaAlvo").value("Z4"))
                .andExpect(jsonPath("$.proximoTreino.etapas.length()").value(2))
                .andExpect(jsonPath("$.proximoTreino.etapas[1].blocoId").value(bloco.toString()))
                .andExpect(jsonPath("$.proximoTreino.etapas[1].blocoRepeticoes").value(2))
                .andExpect(jsonPath("$.proximoTreino.etapas[0].blocoId").doesNotExist());
    }

    @Test
    @DisplayName("GET /me/home → sem etapas nem duração, os campos são omitidos do JSON (não null, não [])")
    void homeSemEtapasOmiteCampos() throws Exception {
        when(service.resolverAtletaIdAtual()).thenReturn(atletaId);
        when(service.getHome(atletaId)).thenReturn(new AtletaHomeDto(
                new AtletaHomeDto.ProximoTreino(LocalDate.now(), "FACIL", "Trote", null, null, null, null, null),
                new AtletaHomeDto.MetricasChave(null, null, null, null, null, null)));

        mockMvc.perform(get("/api/v1/atletas/me/home"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.proximoTreino.tipoTreino").value("FACIL"))
                .andExpect(jsonPath("$.proximoTreino.etapas").doesNotExist())
                .andExpect(jsonPath("$.proximoTreino.duracaoMin").doesNotExist());
    }

    @Test
    @DisplayName("GET /me/readiness → 404 quando o atleta do token não é resolvido")
    void readinessNotFound() throws Exception {
        when(service.resolverAtletaIdAtual()).thenThrow(new DomainNotFoundException("Atleta não encontrado"));

        mockMvc.perform(get("/api/v1/atletas/me/readiness"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /me/home → 404 quando o atleta do token não é resolvido")
    void homeNotFound() throws Exception {
        when(service.resolverAtletaIdAtual()).thenThrow(new DomainNotFoundException("Atleta não encontrado"));

        mockMvc.perform(get("/api/v1/atletas/me/home"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /me/metricas/historico → 200 e resolve o atleta do token")
    void meHistorico() throws Exception {
        when(service.resolverAtletaIdAtual()).thenReturn(atletaId);
        when(service.getHistoricoPmc(eq(atletaId), any(), any()))
                .thenReturn(List.of(new PmcPontoDto(LocalDate.of(2026, 6, 1), 50.0, 60.0, -10.0, 80, "ACUMULANDO_FADIGA")));

        mockMvc.perform(get("/api/v1/atletas/me/metricas/historico"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].ctl").value(50.0));

        verify(service).getHistoricoPmc(eq(atletaId), any(), any());
    }

    @Test
    @DisplayName("GET /me/metricas/historico → 404 quando o atleta do token não é resolvido")
    void meHistoricoNotFound() throws Exception {
        when(service.resolverAtletaIdAtual()).thenThrow(new DomainNotFoundException("Atleta não encontrado"));

        mockMvc.perform(get("/api/v1/atletas/me/metricas/historico"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /me/metricas/zonas → 200 e resolve o atleta do token")
    void meZonas() throws Exception {
        when(service.resolverAtletaIdAtual()).thenReturn(atletaId);
        when(service.getDistribuicaoZonas(eq(atletaId), any(), any()))
                .thenReturn(new ZonaDistribuicaoDto(600, 0, 300, 0, 0, 900));

        mockMvc.perform(get("/api/v1/atletas/me/metricas/zonas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.duracaoTotalSegundos").value(900));

        verify(service).getDistribuicaoZonas(eq(atletaId), any(), any());
    }

    @Test
    @DisplayName("GET /me/metricas/zonas → 404 quando o atleta do token não é resolvido")
    void meZonasNotFound() throws Exception {
        when(service.resolverAtletaIdAtual()).thenThrow(new DomainNotFoundException("Atleta não encontrado"));

        mockMvc.perform(get("/api/v1/atletas/me/metricas/zonas"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /me/recordes → 200 e resolve o atleta do token")
    void meRecordes() throws Exception {
        when(service.resolverAtletaIdAtual()).thenReturn(atletaId);
        when(service.getRecordes(atletaId))
                .thenReturn(List.of(new RecordeDto("10k", 2730L, LocalDate.of(2026, 5, 8), UUID.randomUUID())));

        mockMvc.perform(get("/api/v1/atletas/me/recordes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].distancia").value("10k"));

        verify(service).getRecordes(atletaId);
    }

    @Test
    @DisplayName("GET /me/recordes → 200 com lista vazia (atleta sem recordes ainda)")
    void meRecordesVazio() throws Exception {
        when(service.resolverAtletaIdAtual()).thenReturn(atletaId);
        when(service.getRecordes(atletaId)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/atletas/me/recordes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    @DisplayName("GET /me/recordes → 404 quando o atleta do token não é resolvido")
    void meRecordesNotFound() throws Exception {
        when(service.resolverAtletaIdAtual()).thenThrow(new DomainNotFoundException("Atleta não encontrado"));

        mockMvc.perform(get("/api/v1/atletas/me/recordes"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /me/aderencia → 200, resolve o atleta do token e usa semanas=4 por default")
    void meAderenciaDefault() throws Exception {
        when(service.resolverAtletaIdAtual()).thenReturn(atletaId);
        when(service.getAderenciaSemanal(atletaId, 4))
                .thenReturn(List.of(new AderenciasSemanalDto(LocalDate.of(2026, 6, 1), 5, 4, 80)));

        mockMvc.perform(get("/api/v1/atletas/me/aderencia"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].percentual").value(80));

        verify(service).getAderenciaSemanal(atletaId, 4);
    }

    @Test
    @DisplayName("GET /me/aderencia?semanas=N → repassa N ao service")
    void meAderenciaComParametro() throws Exception {
        when(service.resolverAtletaIdAtual()).thenReturn(atletaId);
        when(service.getAderenciaSemanal(atletaId, 8)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/atletas/me/aderencia").param("semanas", "8"))
                .andExpect(status().isOk());

        verify(service).getAderenciaSemanal(atletaId, 8);
    }

    @Test
    @DisplayName("GET /me/aderencia?semanas=0 → 400 (abaixo do mínimo)")
    void meAderenciaSemanasAbaixoDoMinimo() throws Exception {
        mockMvc.perform(get("/api/v1/atletas/me/aderencia").param("semanas", "0"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /me/aderencia?semanas=105 → 400 (acima do máximo)")
    void meAderenciaSemanasAcimaDoMaximo() throws Exception {
        mockMvc.perform(get("/api/v1/atletas/me/aderencia").param("semanas", "105"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /me/aderencia → 404 quando o atleta do token não é resolvido")
    void meAderenciaNotFound() throws Exception {
        when(service.resolverAtletaIdAtual()).thenThrow(new DomainNotFoundException("Atleta não encontrado"));

        mockMvc.perform(get("/api/v1/atletas/me/aderencia"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /me/provas → 200, resolve o atleta do token e delega em ProvaService.listarProvas")
    void meProvas() throws Exception {
        when(service.resolverAtletaIdAtual()).thenReturn(atletaId);
        when(provaService.listarProvas(atletaId)).thenReturn(List.of(provaStub()));

        mockMvc.perform(get("/api/v1/atletas/me/provas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nomeProva").value("Maratona de São Paulo"))
                .andExpect(jsonPath("$[0].diasFaltando").value(45));

        verify(provaService).listarProvas(atletaId);
    }

    @Test
    @DisplayName("GET /me/provas → 200 com lista vazia (atleta sem provas cadastradas)")
    void meProvasVazio() throws Exception {
        when(service.resolverAtletaIdAtual()).thenReturn(atletaId);
        when(provaService.listarProvas(atletaId)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/atletas/me/provas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    @DisplayName("GET /me/provas → 404 quando o atleta do token não é resolvido")
    void meProvasNotFound() throws Exception {
        when(service.resolverAtletaIdAtual()).thenThrow(new DomainNotFoundException("Atleta não encontrado"));

        mockMvc.perform(get("/api/v1/atletas/me/provas"))
                .andExpect(status().isNotFound());
    }

    private static ProvaOutputDto provaStub() {
        return new ProvaOutputDto(
                UUID.randomUUID(), "Maratona de São Paulo", LocalDate.of(2026, 8, 18),
                TipoProva.MARATONA, DistanciaProva.KM_42, null, true, ProvaStatus.CONFIRMADA,
                null, null, null, false, null, null, null, null, null, null, null, null, 45);
    }
}
