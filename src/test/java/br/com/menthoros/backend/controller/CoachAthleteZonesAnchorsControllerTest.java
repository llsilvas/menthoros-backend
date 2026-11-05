package br.com.menthoros.backend.controller;

import br.com.menthoros.backend.config.core.JacksonConfig;
import br.com.menthoros.backend.dto.input.AthleteZoneAnchorsInputDto;
import br.com.menthoros.backend.dto.output.AthleteTrainingZonesOutputDto;
import br.com.menthoros.backend.exception.DomainNotFoundException;
import br.com.menthoros.backend.exception.InvalidZoneAnchorsException;
import br.com.menthoros.backend.repository.TenantValidationRepository;
import br.com.menthoros.backend.repository.UsuarioRepository;
import br.com.menthoros.backend.services.CoachAthleteZonesService;
import br.com.menthoros.backend.services.UsuarioSyncService;
import br.com.menthoros.backend.testsupport.AuthWebMvcTestConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contrato HTTP de {@code PUT .../zonas/ancoras} e {@code POST .../zonas/simulacao}: roles, 400, 404.
 * A validação dos campos é do serviço (ver o comentário no controller); aqui o serviço é mock e o 400 vem
 * de {@link InvalidZoneAnchorsException}, com o mesmo formato {@code fields} de produção.
 */
@WebMvcTest(CoachAthleteZonesController.class)
@Import({JacksonConfig.class, AuthWebMvcTestConfig.class})
@DisplayName("CoachAthleteZonesController — âncoras e simulação")
class CoachAthleteZonesAnchorsControllerTest {

    private static final String CORPO_PACE = """
            {"pace":{"limiarSegKm":290,"dataTeste":"2026-10-12","origem":"TESTE_CAMPO"}}""";

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

    private MockHttpServletRequestBuilder putAncoras(String corpo, RequestPostProcessor jwt) {
        return put("/api/v1/coach/atletas/{id}/zonas/ancoras", atletaId).with(jwt).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(corpo);
    }

    private MockHttpServletRequestBuilder simular(String corpo, RequestPostProcessor jwt) {
        return post("/api/v1/coach/atletas/{id}/zonas/simulacao", atletaId).with(jwt).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(corpo);
    }

    private static AthleteTrainingZonesOutputDto resposta() {
        return new AthleteTrainingZonesOutputDto("LTHR_FRIEL", null, null, null, null, List.of(), null);
    }

    @Nested
    @DisplayName("PUT .../zonas/ancoras")
    class Salvar {

        @Test
        @DisplayName("TECNICO e ADMIN: 200 com o DTO do serviço")
        void ok() throws Exception {
            when(service.salvarAncoras(eq(atletaId), any(AthleteZoneAnchorsInputDto.class))).thenReturn(resposta());

            mockMvc.perform(putAncoras(CORPO_PACE, tecnicoJwt())).andExpect(status().isOk())
                    .andExpect(jsonPath("$.modelo").value("LTHR_FRIEL"));
            mockMvc.perform(putAncoras(CORPO_PACE, adminJwt())).andExpect(status().isOk());
        }

        @Test
        @DisplayName("ATLETA: 403 sem tocar o serviço")
        void atletaBarrado() throws Exception {
            mockMvc.perform(putAncoras(CORPO_PACE, atletaJwt())).andExpect(status().isForbidden());

            verifyNoInteractions(service);
        }

        @Test
        @DisplayName("regra do serviço (máxima abaixo do topo da Z5): 400 com o campo, no mesmo formato")
        void regraDoServico() throws Exception {
            when(service.salvarAncoras(eq(atletaId), any())).thenThrow(
                    new InvalidZoneAnchorsException(Map.of("fc.maxima", "FC máxima deve ser no mínimo 178 bpm")));
            String corpo = """
                    {"fc":{"limiar":168,"maxima":169,"dataTeste":"2026-10-12"}}""";

            mockMvc.perform(putAncoras(corpo, tecnicoJwt())).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.fields['fc.maxima']").exists());
        }

        @Test
        @DisplayName("origem desconhecida: 400")
        void origemDesconhecida() throws Exception {
            String corpo = """
                    {"pace":{"limiarSegKm":290,"dataTeste":"2026-10-12","origem":"ADIVINHACAO"}}""";

            mockMvc.perform(putAncoras(corpo, tecnicoJwt())).andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("atleta de outro tenant: 404 vindo do serviço")
        void outroTenant() throws Exception {
            when(service.salvarAncoras(eq(atletaId), any())).thenThrow(new DomainNotFoundException("Atleta não encontrado"));

            mockMvc.perform(putAncoras(CORPO_PACE, tecnicoJwt())).andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("POST .../zonas/simulacao")
    class Simulacao {

        @Test
        @DisplayName("TECNICO: 200 com o DTO do serviço")
        void ok() throws Exception {
            when(service.simular(eq(atletaId), any(AthleteZoneAnchorsInputDto.class))).thenReturn(resposta());

            mockMvc.perform(simular(CORPO_PACE, tecnicoJwt())).andExpect(status().isOk())
                    .andExpect(jsonPath("$.modelo").value("LTHR_FRIEL"));
        }

        @Test
        @DisplayName("ATLETA: 403 sem tocar o serviço")
        void atletaBarrado() throws Exception {
            mockMvc.perform(simular(CORPO_PACE, atletaJwt())).andExpect(status().isForbidden());

            verifyNoInteractions(service);
        }

        @Test
        @DisplayName("âncora inválida: 400 com o caminho do campo, igual ao PUT")
        void invalida() throws Exception {
            when(service.simular(eq(atletaId), any())).thenThrow(
                    new InvalidZoneAnchorsException(Map.of("pace.limiarSegKm", "Pace de limiar deve estar entre 150 e 720 s/km")));

            mockMvc.perform(simular(CORPO_PACE, tecnicoJwt())).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fields['pace.limiarSegKm']").exists());
        }

        @Test
        @DisplayName("atleta de outro tenant: 404")
        void outroTenant() throws Exception {
            when(service.simular(eq(atletaId), any())).thenThrow(new DomainNotFoundException("Atleta não encontrado"));

            mockMvc.perform(simular(CORPO_PACE, tecnicoJwt())).andExpect(status().isNotFound());
        }
    }
}
