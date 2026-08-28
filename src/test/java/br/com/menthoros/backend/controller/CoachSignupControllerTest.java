package br.com.menthoros.backend.controller;

import br.com.menthoros.backend.config.signup.CoachSignupProperties;
import br.com.menthoros.backend.dto.output.CoachSignupOutputDto;
import br.com.menthoros.backend.exception.DuplicateResourceException;
import br.com.menthoros.backend.exception.KeycloakIntegrationException;
import br.com.menthoros.backend.services.CoachSignupService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import br.com.menthoros.backend.security.JwtTenantFilter;
import br.com.menthoros.backend.security.StructuredLoggingFilter;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Mesmo recorte dos demais slices: o JwtTenantFilter é @Component e arrastaria UsuarioSyncService
// e UsuarioRepository para dentro do slice. A isenção dele para /api/public/** é verificada onde
// pertence — no JwtTenantFilterShouldNotFilterTest, contra o método de decisão.
@WebMvcTest(controllers = CoachSignupController.class, excludeFilters = @ComponentScan.Filter(
        type = FilterType.ASSIGNABLE_TYPE,
        classes = {JwtTenantFilter.class, StructuredLoggingFilter.class}))
@AutoConfigureMockMvc(addFilters = false)
@org.springframework.boot.context.properties.EnableConfigurationProperties(CoachSignupProperties.class)
@org.springframework.test.context.TestPropertySource(properties = "app.coach-signup.enabled=true")
@DisplayName("POST /api/public/coach-signups")
class CoachSignupControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private CoachSignupService coachSignupService;
    @Autowired private CoachSignupProperties properties;

    private static Map<String, Object> corpoValido() {
        return Map.of(
                "nome", "Maria Treinadora",
                "email", "maria@exemplo.com",
                "senha", "senha-forte-o-suficiente",
                "nomeAssessoria", "Assessoria Corrida na Serra",
                "slug", "corridasserra");
    }

    private org.springframework.test.web.servlet.ResultActions enviar(Map<String, Object> corpo) throws Exception {
        return mockMvc.perform(post("/api/public/coach-signups").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(corpo)));
    }

    @Test
    @DisplayName("201 com o próximo passo, e NENHUM token no corpo")
    void cadastroValido() throws Exception {
        when(coachSignupService.cadastrar(any(), anyString(), anyString()))
                .thenReturn(CoachSignupOutputDto.de("corridasserra", "maria@exemplo.com"));

        var resposta = enviar(corpoValido())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.slug").value("corridasserra"))
                .andExpect(jsonPath("$.proximoPasso").exists())
                .andExpect(jsonPath("$.accessToken").doesNotExist())
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andExpect(jsonPath("$.token").doesNotExist())
                .andReturn();

        // A senha enviada não pode voltar em campo nenhum, nem por acidente de serialização.
        assertThat(resposta.getResponse().getContentAsString())
                .doesNotContain("senha-forte-o-suficiente");
    }

    @Test
    @DisplayName("repassa o Idempotency-Key recebido")
    void repassaChaveDeIdempotencia() throws Exception {
        when(coachSignupService.cadastrar(any(), anyString(), anyString()))
                .thenReturn(CoachSignupOutputDto.de("corridasserra", "maria@exemplo.com"));

        mockMvc.perform(post("/api/public/coach-signups").with(csrf())
                        .header("Idempotency-Key", "chave-do-cliente")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(corpoValido())))
                .andExpect(status().isCreated());

        verify(coachSignupService).cadastrar(any(), eq("chave-do-cliente"), anyString());
    }

    @Test
    @DisplayName("sem Idempotency-Key ainda funciona — a chave é gerada para manter o rastro")
    void semChaveDeIdempotencia() throws Exception {
        when(coachSignupService.cadastrar(any(), anyString(), anyString()))
                .thenReturn(CoachSignupOutputDto.de("corridasserra", "maria@exemplo.com"));

        enviar(corpoValido()).andExpect(status().isCreated());

        verify(coachSignupService).cadastrar(any(), anyString(), anyString());
    }

    @Test
    @DisplayName("400 quando o slug tem formato inválido")
    void slugInvalido() throws Exception {
        var corpo = new java.util.HashMap<>(corpoValido());
        corpo.put("slug", "Corrida Serra");

        enviar(corpo).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("409 quando o slug já está em uso")
    void conflito() throws Exception {
        when(coachSignupService.cadastrar(any(), anyString(), anyString()))
                .thenThrow(new DuplicateResourceException("Identificador já está em uso"));

        enviar(corpoValido()).andExpect(status().isConflict());
    }

    @Test
    @DisplayName("502 quando o Keycloak falha — não 500: a falha é de dependência externa")
    void falhaNoKeycloak() throws Exception {
        when(coachSignupService.cadastrar(any(), anyString(), anyString()))
                .thenThrow(new KeycloakIntegrationException("keycloak fora"));

        enviar(corpoValido()).andExpect(status().isBadGateway());
    }

    @Test
    @DisplayName("com a flag DESLIGADA responde 404 e não chama o serviço")
    void flagDesligadaResponde404() throws Exception {
        properties.setEnabled(false);
        try {
            enviar(corpoValido()).andExpect(status().isNotFound());
            org.mockito.Mockito.verifyNoInteractions(coachSignupService);
        } finally {
            properties.setEnabled(true);
        }
    }

    @Test
    @DisplayName("com a flag DESLIGADA mas com token de convite, o cadastro segue para o serviço")
    void flagDesligadaComConvitePassa() throws Exception {
        properties.setEnabled(false);
        try {
            when(coachSignupService.cadastrar(any(), anyString(), anyString()))
                    .thenReturn(CoachSignupOutputDto.prontoParaEntrar("corridasserra", "maria@exemplo.com"));
            var corpo = new java.util.HashMap<String, Object>(corpoValido());
            corpo.put("inviteToken", "tok-convite");

            enviar(corpo)
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.proximoPasso").value(CoachSignupOutputDto.PRONTO_PARA_ENTRAR));

            var captor = org.mockito.ArgumentCaptor.forClass(br.com.menthoros.backend.dto.input.CoachSignupInputDto.class);
            verify(coachSignupService).cadastrar(captor.capture(), anyString(), anyString());
            assertThat(captor.getValue().inviteToken()).isEqualTo("tok-convite");
        } finally {
            properties.setEnabled(true);
        }
    }

    @Test
    @DisplayName("token de convite inválido responde 404 — indistinguível de cadastro desligado")
    void conviteInvalidoResponde404() throws Exception {
        properties.setEnabled(false);
        try {
            when(coachSignupService.cadastrar(any(), anyString(), anyString()))
                    .thenThrow(new br.com.menthoros.backend.exception.DomainNotFoundException("Convite inválido ou expirado"));
            var corpo = new java.util.HashMap<String, Object>(corpoValido());
            corpo.put("inviteToken", "tok-invalido");

            enviar(corpo).andExpect(status().isNotFound());
        } finally {
            properties.setEnabled(true);
        }
    }

    @Test
    @DisplayName("token em branco conta como ausente: com a flag desligada, 404 sem chamar o serviço")
    void tokenEmBrancoEhAusente() throws Exception {
        properties.setEnabled(false);
        try {
            var corpo = new java.util.HashMap<String, Object>(corpoValido());
            corpo.put("inviteToken", "   ");

            enviar(corpo).andExpect(status().isNotFound());
            org.mockito.Mockito.verifyNoInteractions(coachSignupService);
        } finally {
            properties.setEnabled(true);
        }
    }

    @Test
    @DisplayName("o default da propriedade é DESLIGADO — o deploy nunca liga sozinho")
    void defaultEhDesligado() {
        assertThat(new CoachSignupProperties().isEnabled()).isFalse();
    }

    @Test
    @DisplayName("o teto de corpo tem default definido — sem ele o parsing acontece antes da recusa")
    void tetoDeCorpoTemDefault() {
        assertThat(new CoachSignupProperties().getMaxRequestBytes()).isPositive();
    }
}
