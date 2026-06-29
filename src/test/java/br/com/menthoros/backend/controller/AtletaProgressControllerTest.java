package br.com.menthoros.backend.controller;

import br.com.menthoros.backend.dto.output.AtletaHomeDto;
import br.com.menthoros.backend.dto.output.PmcPontoDto;
import br.com.menthoros.backend.dto.output.ReadinessDto;
import br.com.menthoros.backend.dto.output.RecordeDto;
import br.com.menthoros.backend.dto.output.ZonaDistribuicaoDto;
import br.com.menthoros.backend.exception.DomainNotFoundException;
import br.com.menthoros.backend.security.JwtTenantFilter;
import br.com.menthoros.backend.security.StructuredLoggingFilter;
import br.com.menthoros.backend.services.AtletaProgressService;
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
        when(service.getHome(atletaId)).thenReturn(new AtletaHomeDto(
                new AtletaHomeDto.ProximoTreino(LocalDate.now().plusDays(1), "INTERVALADO", "6x800m"),
                new AtletaHomeDto.MetricasChave(52.0, 44.0, 8.0, 0, null, "FORMA_IDEAL")));

        mockMvc.perform(get("/api/v1/atletas/me/home"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.proximoTreino.tipoTreino").value("INTERVALADO"));
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
}
