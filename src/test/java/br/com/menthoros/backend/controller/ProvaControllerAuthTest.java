package br.com.menthoros.backend.controller;

import br.com.menthoros.backend.config.core.JacksonConfig;
import br.com.menthoros.backend.dto.output.ProvaOutputDto;
import br.com.menthoros.backend.enums.DistanciaProva;
import br.com.menthoros.backend.enums.ProvaStatus;
import br.com.menthoros.backend.enums.TipoProva;
import br.com.menthoros.backend.exception.ProvaRealizadaImutavelException;
import br.com.menthoros.backend.exception.ResourceNotFoundException;
import br.com.menthoros.backend.repository.TenantValidationRepository;
import br.com.menthoros.backend.repository.UsuarioRepository;
import br.com.menthoros.backend.services.ProvaService;
import br.com.menthoros.backend.services.UsuarioSyncService;
import br.com.menthoros.backend.testsupport.AuthWebMvcTestConfig;
import br.com.menthoros.backend.testsupport.JwtTestSupport;
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

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static br.com.menthoros.backend.testsupport.JwtTestSupport.adminJwt;
import static br.com.menthoros.backend.testsupport.JwtTestSupport.atletaJwt;
import static br.com.menthoros.backend.testsupport.JwtTestSupport.tecnicoJwt;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contrato de autorização do {@link ProvaController} com a cadeia de segurança real: os três
 * papéis passam em todos os verbos, sem token é 401, e o DELETE delega ao service a decisão
 * remover/cancelar. Posse e tenant são regra do service (ver ProvaAtletaAccessIT).
 */
@WebMvcTest(ProvaController.class)
@Import({JacksonConfig.class, AuthWebMvcTestConfig.class})
@DisplayName("ProvaController — autorização")
class ProvaControllerAuthTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProvaService provaService;

    @MockitoBean
    private TenantValidationRepository tenantValidationRepository;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private UsuarioSyncService usuarioSyncService;

    @MockitoBean
    private UsuarioRepository usuarioRepository;

    private final UUID atletaId = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private final UUID provaId = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000001");

    private static final String BODY = """
            {"nomeProva":"Maratona SP","dataProva":"2027-04-11","tipoProva":"MARATONA",
             "distancia":"KM_42","provaAlvo":true}
            """;

    @BeforeEach
    void stubUsuarioAtivo() {
        JwtTestSupport.stubUsuarioAtivo(usuarioSyncService);
    }

    @Nested
    @DisplayName("GET /api/v1/atletas/{atletaId}/provas")
    class Listar {

        @Test
        @DisplayName("200 para ATLETA, TECNICO e ADMIN")
        void okParaOsTresPapeis() throws Exception {
            when(provaService.listarProvas(atletaId)).thenReturn(List.of(stub()));

            mockMvc.perform(get("/api/v1/atletas/{atletaId}/provas", atletaId).with(atletaJwt()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].nomeProva").value("Maratona SP"));
            mockMvc.perform(get("/api/v1/atletas/{atletaId}/provas", atletaId).with(tecnicoJwt()))
                    .andExpect(status().isOk());
            mockMvc.perform(get("/api/v1/atletas/{atletaId}/provas", atletaId).with(adminJwt()))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("401 sem token")
        void semToken() throws Exception {
            mockMvc.perform(get("/api/v1/atletas/{atletaId}/provas", atletaId))
                    .andExpect(status().isUnauthorized());
            verifyNoInteractions(provaService);
        }

        @Test
        @DisplayName("404 quando o service não reconhece o atleta (posse ou tenant)")
        void naoEncontrado() throws Exception {
            when(provaService.listarProvas(atletaId)).thenThrow(new ResourceNotFoundException("Atleta não encontrado"));

            mockMvc.perform(get("/api/v1/atletas/{atletaId}/provas", atletaId).with(atletaJwt()))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/atletas/{atletaId}/provas/{provaId}")
    class Buscar {

        @Test
        @DisplayName("200 para ATLETA, TECNICO e ADMIN")
        void okParaOsTresPapeis() throws Exception {
            when(provaService.buscarProvaPorId(atletaId, provaId)).thenReturn(stub());

            mockMvc.perform(get("/api/v1/atletas/{atletaId}/provas/{provaId}", atletaId, provaId).with(atletaJwt()))
                    .andExpect(status().isOk());
            mockMvc.perform(get("/api/v1/atletas/{atletaId}/provas/{provaId}", atletaId, provaId).with(tecnicoJwt()))
                    .andExpect(status().isOk());
            mockMvc.perform(get("/api/v1/atletas/{atletaId}/provas/{provaId}", atletaId, provaId).with(adminJwt()))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("401 sem token")
        void semToken() throws Exception {
            mockMvc.perform(get("/api/v1/atletas/{atletaId}/provas/{provaId}", atletaId, provaId))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("POST /api/v1/atletas/{atletaId}/provas")
    class Criar {

        @Test
        @DisplayName("201 para ATLETA, TECNICO e ADMIN")
        void criadoParaOsTresPapeis() throws Exception {
            when(provaService.criarProva(eq(atletaId), any())).thenReturn(stub());

            mockMvc.perform(post("/api/v1/atletas/{atletaId}/provas", atletaId).with(atletaJwt())
                            .contentType(MediaType.APPLICATION_JSON).content(BODY))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.preparacaoCurta").value(false))
                    .andExpect(jsonPath("$.semanasFaltando").value(12));
            mockMvc.perform(post("/api/v1/atletas/{atletaId}/provas", atletaId).with(tecnicoJwt())
                            .contentType(MediaType.APPLICATION_JSON).content(BODY))
                    .andExpect(status().isCreated());
            mockMvc.perform(post("/api/v1/atletas/{atletaId}/provas", atletaId).with(adminJwt())
                            .contentType(MediaType.APPLICATION_JSON).content(BODY))
                    .andExpect(status().isCreated());
        }

        @Test
        @DisplayName("400 com nome em branco")
        void nomeEmBranco() throws Exception {
            mockMvc.perform(post("/api/v1/atletas/{atletaId}/provas", atletaId).with(atletaJwt())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"nomeProva\":\" \",\"dataProva\":\"2027-04-11\",\"tipoProva\":\"MARATONA\",\"distancia\":\"KM_42\"}"))
                    .andExpect(status().isBadRequest());
            verifyNoInteractions(provaService);
        }

        @Test
        @DisplayName("400 com distância customizada sem quilometragem")
        void customizadaSemKm() throws Exception {
            mockMvc.perform(post("/api/v1/atletas/{atletaId}/provas", atletaId).with(tecnicoJwt())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"nomeProva\":\"Ultra\",\"dataProva\":\"2027-04-11\",\"tipoProva\":\"TRAIL\",\"distancia\":\"CUSTOMIZADA\"}"))
                    .andExpect(status().isBadRequest());
            verifyNoInteractions(provaService);
        }

        @Test
        @DisplayName("401 sem token")
        void semToken() throws Exception {
            mockMvc.perform(post("/api/v1/atletas/{atletaId}/provas", atletaId)
                            .contentType(MediaType.APPLICATION_JSON).content(BODY))
                    .andExpect(status().isUnauthorized());
            verifyNoInteractions(provaService);
        }
    }

    @Nested
    @DisplayName("PUT /api/v1/atletas/{atletaId}/provas/{provaId}")
    class Atualizar {

        @Test
        @DisplayName("200 para ATLETA, TECNICO e ADMIN")
        void okParaOsTresPapeis() throws Exception {
            when(provaService.atualizarProva(eq(atletaId), eq(provaId), any())).thenReturn(stub());

            mockMvc.perform(put("/api/v1/atletas/{atletaId}/provas/{provaId}", atletaId, provaId).with(atletaJwt())
                            .contentType(MediaType.APPLICATION_JSON).content(BODY))
                    .andExpect(status().isOk());
            mockMvc.perform(put("/api/v1/atletas/{atletaId}/provas/{provaId}", atletaId, provaId).with(tecnicoJwt())
                            .contentType(MediaType.APPLICATION_JSON).content(BODY))
                    .andExpect(status().isOk());
            mockMvc.perform(put("/api/v1/atletas/{atletaId}/provas/{provaId}", atletaId, provaId).with(adminJwt())
                            .contentType(MediaType.APPLICATION_JSON).content(BODY))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("409 quando o atleta altera prova realizada")
        void conflitoProvaRealizada() throws Exception {
            when(provaService.atualizarProva(eq(atletaId), eq(provaId), any()))
                    .thenThrow(new ProvaRealizadaImutavelException("realizada"));

            mockMvc.perform(put("/api/v1/atletas/{atletaId}/provas/{provaId}", atletaId, provaId).with(atletaJwt())
                            .contentType(MediaType.APPLICATION_JSON).content(BODY))
                    .andExpect(status().isConflict());
        }

        @Test
        @DisplayName("401 sem token")
        void semToken() throws Exception {
            mockMvc.perform(put("/api/v1/atletas/{atletaId}/provas/{provaId}", atletaId, provaId)
                            .contentType(MediaType.APPLICATION_JSON).content(BODY))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("DELETE /api/v1/atletas/{atletaId}/provas/{provaId}")
    class Remover {

        @Test
        @DisplayName("204 para ATLETA, TECNICO e ADMIN, sempre via removerProva")
        void noContentParaOsTresPapeis() throws Exception {
            mockMvc.perform(delete("/api/v1/atletas/{atletaId}/provas/{provaId}", atletaId, provaId).with(atletaJwt()))
                    .andExpect(status().isNoContent());
            mockMvc.perform(delete("/api/v1/atletas/{atletaId}/provas/{provaId}", atletaId, provaId).with(tecnicoJwt()))
                    .andExpect(status().isNoContent());
            mockMvc.perform(delete("/api/v1/atletas/{atletaId}/provas/{provaId}", atletaId, provaId).with(adminJwt()))
                    .andExpect(status().isNoContent());

            verify(provaService, org.mockito.Mockito.times(3)).removerProva(atletaId, provaId);
        }

        @Test
        @DisplayName("409 quando o atleta cancela prova realizada")
        void conflitoProvaRealizada() throws Exception {
            org.mockito.Mockito.doThrow(new ProvaRealizadaImutavelException("realizada"))
                    .when(provaService).removerProva(atletaId, provaId);

            mockMvc.perform(delete("/api/v1/atletas/{atletaId}/provas/{provaId}", atletaId, provaId).with(atletaJwt()))
                    .andExpect(status().isConflict());
        }

        @Test
        @DisplayName("401 sem token")
        void semToken() throws Exception {
            mockMvc.perform(delete("/api/v1/atletas/{atletaId}/provas/{provaId}", atletaId, provaId))
                    .andExpect(status().isUnauthorized());
            verifyNoInteractions(provaService);
        }
    }

    @Nested
    @DisplayName("PATCH /api/v1/atletas/{atletaId}/provas/{provaId}/ciente")
    class Ciente {

        @Test
        @DisplayName("200 para TECNICO e ADMIN")
        void okParaCoach() throws Exception {
            when(provaService.marcarCiente(atletaId, provaId)).thenReturn(stub());

            mockMvc.perform(patch("/api/v1/atletas/{atletaId}/provas/{provaId}/ciente", atletaId, provaId).with(tecnicoJwt()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.revisadaPeloCoach").value(true));
            mockMvc.perform(patch("/api/v1/atletas/{atletaId}/provas/{provaId}/ciente", atletaId, provaId).with(adminJwt()))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("403 para ATLETA")
        void proibidoParaAtleta() throws Exception {
            mockMvc.perform(patch("/api/v1/atletas/{atletaId}/provas/{provaId}/ciente", atletaId, provaId).with(atletaJwt()))
                    .andExpect(status().isForbidden());
            verifyNoInteractions(provaService);
        }

        @Test
        @DisplayName("404 quando a prova é de outro tenant")
        void outroTenant() throws Exception {
            when(provaService.marcarCiente(atletaId, provaId)).thenThrow(new ResourceNotFoundException("Prova não encontrada"));

            mockMvc.perform(patch("/api/v1/atletas/{atletaId}/provas/{provaId}/ciente", atletaId, provaId).with(tecnicoJwt()))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/atletas/{atletaId}/provas/pendentes-revisao")
    class PendentesRevisao {

        @Test
        @DisplayName("200 para TECNICO; 403 para ATLETA")
        void somenteCoach() throws Exception {
            when(provaService.listarPendentesRevisao(atletaId)).thenReturn(List.of(stub()));

            mockMvc.perform(get("/api/v1/atletas/{atletaId}/provas/pendentes-revisao", atletaId).with(tecnicoJwt()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].id").value(provaId.toString()));
            mockMvc.perform(get("/api/v1/atletas/{atletaId}/provas/pendentes-revisao", atletaId).with(atletaJwt()))
                    .andExpect(status().isForbidden());
        }
    }

    private ProvaOutputDto stub() {
        return new ProvaOutputDto(
                provaId, "Maratona SP", LocalDate.of(2027, 4, 11),
                TipoProva.MARATONA, DistanciaProva.KM_42, null, true, ProvaStatus.PLANEJADA,
                null, null, null, false, null, null, null, null, null,
                null, 16, LocalDate.of(2026, 12, 20), 90, false, 12, true, null, null);
    }
}
