package br.com.menthoros.backend.controller;

import br.com.menthoros.backend.config.core.JacksonConfig;
import br.com.menthoros.backend.dto.output.TreinoPlanejadoOutputDto;
import br.com.menthoros.backend.entity.Usuario;
import br.com.menthoros.backend.enums.TipoTreino;
import br.com.menthoros.backend.enums.TreinoExecucaoStatus;
import br.com.menthoros.backend.exception.DomainNotFoundException;
import br.com.menthoros.backend.exception.DomainRuleViolationException;
import br.com.menthoros.backend.repository.UsuarioRepository;
import br.com.menthoros.backend.services.TreinoPlanejadoService;
import br.com.menthoros.backend.services.UsuarioSyncService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CoachTreinoController.class)
@Import(JacksonConfig.class)
class CoachTreinoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TreinoPlanejadoService treinoPlanejadoService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private UsuarioSyncService usuarioSyncService;

    @MockitoBean
    private UsuarioRepository usuarioRepository;

    private static final UUID TENANT_ID = UUID.fromString("cccccccc-0000-0000-0000-000000000003");
    private final UUID planoId = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private final UUID treinoId = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");

    private RequestPostProcessor tecnicoJwt() {
        return jwt()
                .authorities(new SimpleGrantedAuthority("ROLE_TECNICO"))
                .jwt(j -> j.claim("tenant_id", TENANT_ID.toString()).subject("tecnico-keycloak-id"));
    }

    private RequestPostProcessor adminJwt() {
        return jwt()
                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))
                .jwt(j -> j.claim("tenant_id", TENANT_ID.toString()).subject("admin-keycloak-id"));
    }

    @Nested
    @DisplayName("adicionarTreino — POST /{planoId}/treinos")
    class AdicionarTreino {

        @Test
        @DisplayName("retorna 201 com DTO do treino criado")
        void retorna201ComDtoCriado() throws Exception {
            TreinoPlanejadoOutputDto output = outputStub(UUID.randomUUID(), false, true);
            when(treinoPlanejadoService.adicionarTreino(eq(planoId), any())).thenReturn(output);
            stubUsuarioAtivo();

            mockMvc.perform(post("/api/v1/coach/planos/{planoId}/treinos", planoId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"tipoTreino": "CONTINUO", "dataTreino": "2026-07-03"}
                                    """)
                            .with(tecnicoJwt()))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.adicionadoPeloCoach").value(true));
        }

        @Test
        @DisplayName("retorna 404 quando plano não encontrado")
        void retorna404QuandoPlanoNaoEncontrado() throws Exception {
            when(treinoPlanejadoService.adicionarTreino(any(), any()))
                    .thenThrow(new DomainNotFoundException("Plano não encontrado"));
            stubUsuarioAtivo();

            mockMvc.perform(post("/api/v1/coach/planos/{planoId}/treinos", planoId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"tipoTreino": "CONTINUO", "dataTreino": "2026-07-03"}
                                    """)
                            .with(tecnicoJwt()))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("retorna 422 quando plano não está em AGUARDANDO_REVISAO")
        void retorna422QuandoPlanoNaoEstaEmRevisao() throws Exception {
            when(treinoPlanejadoService.adicionarTreino(any(), any()))
                    .thenThrow(new DomainRuleViolationException("Plano não está em revisão"));
            stubUsuarioAtivo();

            mockMvc.perform(post("/api/v1/coach/planos/{planoId}/treinos", planoId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"tipoTreino": "CONTINUO", "dataTreino": "2026-07-03"}
                                    """)
                            .with(tecnicoJwt()))
                    .andExpect(status().isUnprocessableEntity());
        }

        @Test
        @DisplayName("retorna 422 quando data fora do intervalo do plano")
        void retorna422QuandoDataForaDoIntervalo() throws Exception {
            when(treinoPlanejadoService.adicionarTreino(any(), any()))
                    .thenThrow(new DomainRuleViolationException("Data do treino fora do intervalo"));
            stubUsuarioAtivo();

            mockMvc.perform(post("/api/v1/coach/planos/{planoId}/treinos", planoId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"tipoTreino": "CONTINUO", "dataTreino": "2026-07-08"}
                                    """)
                            .with(tecnicoJwt()))
                    .andExpect(status().isUnprocessableEntity());
        }

        @Test
        @DisplayName("retorna 422 quando limite de 14 treinos atingido")
        void retorna422QuandoLimiteQuatorzeTreinosAtingido() throws Exception {
            when(treinoPlanejadoService.adicionarTreino(any(), any()))
                    .thenThrow(new DomainRuleViolationException("Limite de 14 treinos por semana atingido"));
            stubUsuarioAtivo();

            mockMvc.perform(post("/api/v1/coach/planos/{planoId}/treinos", planoId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"tipoTreino": "CONTINUO", "dataTreino": "2026-07-03"}
                                    """)
                            .with(tecnicoJwt()))
                    .andExpect(status().isUnprocessableEntity());
        }

        @Test
        @DisplayName("retorna 400 quando tipoTreino ausente (Bean Validation)")
        void retorna400QuandoTipoTreinoAusente() throws Exception {
            stubUsuarioAtivo();

            mockMvc.perform(post("/api/v1/coach/planos/{planoId}/treinos", planoId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"dataTreino": "2026-07-03"}
                                    """)
                            .with(tecnicoJwt()))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("retorna 400 quando dataTreino ausente (Bean Validation)")
        void retorna400QuandoDataTreinoAusente() throws Exception {
            stubUsuarioAtivo();

            mockMvc.perform(post("/api/v1/coach/planos/{planoId}/treinos", planoId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"tipoTreino": "CONTINUO"}
                                    """)
                            .with(tecnicoJwt()))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("retorna 201 quando role ADMIN (também autorizado)")
        void retorna201QuandoRoleAdmin() throws Exception {
            TreinoPlanejadoOutputDto output = outputStub(UUID.randomUUID(), false, true);
            when(treinoPlanejadoService.adicionarTreino(eq(planoId), any())).thenReturn(output);
            stubUsuarioAtivo();

            mockMvc.perform(post("/api/v1/coach/planos/{planoId}/treinos", planoId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"tipoTreino": "CONTINUO", "dataTreino": "2026-07-03"}
                                    """)
                            .with(adminJwt()))
                    .andExpect(status().isCreated());
        }

        @Test
        @DisplayName("retorna 403 quando requisição sem autenticação")
        void retorna403SemAutenticacao() throws Exception {
            mockMvc.perform(post("/api/v1/coach/planos/{planoId}/treinos", planoId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"tipoTreino": "CONTINUO", "dataTreino": "2026-07-03"}
                                    """))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("editarTreino — PATCH /{planoId}/treinos/{treinoId}")
    class EditarTreino {

        @Test
        @DisplayName("retorna 200 com DTO atualizado quando edição é bem-sucedida")
        void retorna200QuandoEdicaoBemSucedida() throws Exception {
            TreinoPlanejadoOutputDto output = outputStub(treinoId, true, false);
            when(treinoPlanejadoService.editarTreino(eq(planoId), eq(treinoId), any())).thenReturn(output);
            stubUsuarioAtivo();

            mockMvc.perform(patch("/api/v1/coach/planos/{planoId}/treinos/{treinoId}", planoId, treinoId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"descricao": "Treino ajustado pelo coach", "distanciaKm": 18.0}
                                    """)
                            .with(tecnicoJwt()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(treinoId.toString()))
                    .andExpect(jsonPath("$.editadoPeloCoach").value(true));
        }

        @Test
        @DisplayName("retorna 404 quando plano ou treino não encontrado")
        void retorna404QuandoNaoEncontrado() throws Exception {
            when(treinoPlanejadoService.editarTreino(any(), any(), any()))
                    .thenThrow(new DomainNotFoundException("Plano não encontrado"));
            stubUsuarioAtivo();

            mockMvc.perform(patch("/api/v1/coach/planos/{planoId}/treinos/{treinoId}", planoId, treinoId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}")
                            .with(tecnicoJwt()))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("retorna 422 quando plano não está em revisão")
        void retorna422QuandoPlanoNaoEstaEmRevisao() throws Exception {
            when(treinoPlanejadoService.editarTreino(any(), any(), any()))
                    .thenThrow(new DomainRuleViolationException("Plano não está em revisão"));
            stubUsuarioAtivo();

            mockMvc.perform(patch("/api/v1/coach/planos/{planoId}/treinos/{treinoId}", planoId, treinoId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}")
                            .with(tecnicoJwt()))
                    .andExpect(status().isUnprocessableEntity());
        }

        @Test
        @DisplayName("retorna 409 quando há conflito de edição concorrente")
        void retorna409QuandoConflitoConcorrente() throws Exception {
            when(treinoPlanejadoService.editarTreino(any(), any(), any()))
                    .thenThrow(new org.springframework.dao.OptimisticLockingFailureException("versão desatualizada"));
            stubUsuarioAtivo();

            mockMvc.perform(patch("/api/v1/coach/planos/{planoId}/treinos/{treinoId}", planoId, treinoId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}")
                            .with(tecnicoJwt()))
                    .andExpect(status().isConflict());
        }

        @Test
        @DisplayName("retorna 403 quando requisição sem autenticação")
        void retorna403SemAutenticacao() throws Exception {
            mockMvc.perform(patch("/api/v1/coach/planos/{planoId}/treinos/{treinoId}", planoId, treinoId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isForbidden());
        }
    }

    private void stubUsuarioAtivo() {
        Usuario usuario = new Usuario();
        usuario.setAtivo(true);
        when(usuarioSyncService.syncUsuarioFromJwt(any(), any())).thenReturn(usuario);
    }

    private TreinoPlanejadoOutputDto outputStub(UUID id, boolean editadoPeloCoach, boolean adicionadoPeloCoach) {
        return new TreinoPlanejadoOutputDto(
                id, null, null, null, TipoTreino.CONTINUO, null, null, null, null,
                null, null, null, null, null, null, null, null, null,
                editadoPeloCoach, adicionadoPeloCoach, null, TreinoExecucaoStatus.PENDENTE, null, null, null, null,
                null, null
        );
    }
}
