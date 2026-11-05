package br.com.menthoros.backend.controller;

import br.com.menthoros.backend.config.core.JacksonConfig;
import br.com.menthoros.backend.dto.input.AthleteZonesManualInputDto;
import br.com.menthoros.backend.dto.output.AthleteTrainingZonesOutputDto;
import br.com.menthoros.backend.dto.output.AthleteTrainingZonesOutputDto.Modos;
import br.com.menthoros.backend.enums.ModoZonas;
import br.com.menthoros.backend.exception.DomainNotFoundException;
import br.com.menthoros.backend.exception.InvalidZoneAnchorsException;
import br.com.menthoros.backend.repository.TenantValidationRepository;
import br.com.menthoros.backend.repository.UsuarioRepository;
import br.com.menthoros.backend.services.CoachAthleteZonesService;
import br.com.menthoros.backend.services.UsuarioSyncService;
import br.com.menthoros.backend.testsupport.AuthWebMvcTestConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static br.com.menthoros.backend.testsupport.JwtTestSupport.adminJwt;
import static br.com.menthoros.backend.testsupport.JwtTestSupport.atletaJwt;
import static br.com.menthoros.backend.testsupport.JwtTestSupport.stubUsuarioAtivo;
import static br.com.menthoros.backend.testsupport.JwtTestSupport.tecnicoJwt;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Contrato HTTP de {@code PUT .../zonas/manuais} (coach-zonas-manuais, D3): roles, 400 com caminho, 404. */
@WebMvcTest(CoachAthleteZonesController.class)
@Import({JacksonConfig.class, AuthWebMvcTestConfig.class})
@DisplayName("CoachAthleteZonesController — zonas manuais")
class CoachAthleteZonesManuaisControllerTest {

    private static final String CORPO = """
            {"pace":{"modo":"LIMIAR"}}""";

    @Autowired private MockMvc mockMvc;

    @MockitoBean private CoachAthleteZonesService service;
    @MockitoBean private JwtDecoder jwtDecoder;
    @MockitoBean private UsuarioSyncService usuarioSyncService;
    @MockitoBean private UsuarioRepository usuarioRepository;
    @MockitoBean private TenantValidationRepository tenantValidationRepository;

    private final UUID atletaId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        stubUsuarioAtivo(usuarioSyncService);
    }

    private MockHttpServletRequestBuilder putManuais(String corpo, RequestPostProcessor jwt) {
        return put("/api/v1/coach/atletas/{id}/zonas/manuais", atletaId).with(jwt).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(corpo);
    }

    @Test
    @DisplayName("TECNICO e ADMIN: 200 com o DTO do serviço, incluindo os modos")
    void ok() throws Exception {
        when(service.salvarZonasManuais(eq(atletaId), any(AthleteZonesManualInputDto.class))).thenReturn(
                new AthleteTrainingZonesOutputDto("LTHR_FRIEL", null, null, null, null, List.of(),
                        new Modos(ModoZonas.LIMIAR, ModoZonas.MANUAL)));

        mockMvc.perform(putManuais(CORPO, tecnicoJwt())).andExpect(status().isOk())
                .andExpect(jsonPath("$.modos.fc").value("LIMIAR"))
                .andExpect(jsonPath("$.modos.pace").value("MANUAL"));
        mockMvc.perform(putManuais(CORPO, adminJwt())).andExpect(status().isOk());
    }

    @Test
    @DisplayName("ATLETA: 403 sem tocar o serviço")
    void atletaBarrado() throws Exception {
        mockMvc.perform(putManuais(CORPO, atletaJwt())).andExpect(status().isForbidden());

        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("faixa sobreposta: 400 com o caminho do campo")
    void sobreposta() throws Exception {
        when(service.salvarZonasManuais(eq(atletaId), any())).thenThrow(
                new InvalidZoneAnchorsException(Map.of("fc.faixas[2].min", "Sobrepõe a Z2")));

        mockMvc.perform(putManuais(CORPO, tecnicoJwt())).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields['fc.faixas[2].min']").value("Sobrepõe a Z2"));
    }

    @Test
    @DisplayName("atleta de outro tenant: 404")
    void outroTenant() throws Exception {
        when(service.salvarZonasManuais(eq(atletaId), any())).thenThrow(new DomainNotFoundException("Atleta não encontrado"));

        mockMvc.perform(putManuais(CORPO, tecnicoJwt())).andExpect(status().isNotFound());
    }
}
