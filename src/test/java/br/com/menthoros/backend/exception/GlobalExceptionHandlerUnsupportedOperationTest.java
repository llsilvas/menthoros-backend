package br.com.menthoros.backend.exception;

import br.com.menthoros.backend.exception.handler.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Testa que GlobalExceptionHandler mapeia UnsupportedOperationException → 501 Not Implemented.
 *
 * Use case real: placeholders ainda não implementados (ex.: KeycloakOrganizationGatewayImpl)
 * lançam UnsupportedOperationException. O handler deve retornar 501 em vez de 500 genérico.
 *
 * Usa MockMvc standalone para isolar o handler sem subir o contexto Spring completo.
 */
class GlobalExceptionHandlerUnsupportedOperationTest {

    @RestController
    static class FakeUnsupportedController {
        @GetMapping("/test/unsupported")
        public String endpoint() {
            throw new UnsupportedOperationException("Provisionamento de organização Keycloak não implementado");
        }
    }

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new FakeUnsupportedController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("Quando UnsupportedOperationException é lançada, retorna 501 Not Implemented")
    void devolveNotImplementedQuandoOperacaoNaoSuportada() throws Exception {
        mockMvc.perform(get("/test/unsupported")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotImplemented())
                .andExpect(jsonPath("$.status").value(501))
                .andExpect(jsonPath("$.error").value("Not Implemented"))
                .andExpect(jsonPath("$.message").value("Provisionamento de organização Keycloak não implementado"));
    }
}
