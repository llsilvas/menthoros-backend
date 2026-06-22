package br.com.menthoros.backend.controller;

import br.com.menthoros.backend.config.core.JacksonConfig;
import br.com.menthoros.backend.dto.output.TreinoPlanejadoOutputDto;
import br.com.menthoros.backend.enums.TipoTreino;
import br.com.menthoros.backend.enums.TreinoExecucaoStatus;
import br.com.menthoros.backend.exception.DomainNotFoundException;
import br.com.menthoros.backend.exception.DomainRuleViolationException;
import br.com.menthoros.backend.entity.Usuario;
import br.com.menthoros.backend.repository.UsuarioRepository;
import br.com.menthoros.backend.services.TreinoPlanejadoEditService;
import br.com.menthoros.backend.services.UsuarioSyncService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CoachTreinoEditController.class)
@Import(JacksonConfig.class)
class CoachTreinoEditControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TreinoPlanejadoEditService editService;

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
                .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_TECNICO"))
                .jwt(j -> j.claim("tenant_id", TENANT_ID.toString()).subject("tecnico-keycloak-id"));
    }

    @Nested
    @DisplayName("editarTreino — PATCH /{planoId}/treinos/{treinoId}")
    class EditarTreino {

        @Test
        @DisplayName("retorna 200 com DTO atualizado quando edição é bem-sucedida")
        void retorna200QuandoEdicaoBemSucedida() throws Exception {
            TreinoPlanejadoOutputDto output = outputStub(treinoId, true);
            when(editService.editarTreino(eq(planoId), eq(treinoId), any())).thenReturn(output);
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
            when(editService.editarTreino(any(), any(), any()))
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
            when(editService.editarTreino(any(), any(), any()))
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
            when(editService.editarTreino(any(), any(), any()))
                    .thenThrow(new OptimisticLockingFailureException("versão desatualizada"));
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
            // @WebMvcTest sem security config completa: Spring Security retorna 403 para request sem token
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

    private TreinoPlanejadoOutputDto outputStub(UUID id, boolean editadoPeloCoach) {
        return new TreinoPlanejadoOutputDto(
                id, null, null, null, TipoTreino.CONTINUO, null, null, "60:00", 10.0,
                null, null, null, null, null, null, 55, null, null, editadoPeloCoach,
                null, TreinoExecucaoStatus.PENDENTE, null, null, null, null
        );
    }
}
