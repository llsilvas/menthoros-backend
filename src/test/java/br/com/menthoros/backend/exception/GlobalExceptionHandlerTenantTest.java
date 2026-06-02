package br.com.menthoros.backend.exception;

import br.com.menthoros.backend.exception.handler.GlobalExceptionHandler;
import br.com.menthoros.backend.multitenancy.TenantContext;
import org.junit.jupiter.api.AfterEach;
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
 * Testa que GlobalExceptionHandler mapeia IllegalStateException → 403 Forbidden.
 *
 * Use case real: quando TenantContext.getRequiredTenantId() é chamado sem JWT
 * (tenant não configurado na thread), lança IllegalStateException.
 * O handler deve retornar 403 com mensagem segura — sem expor detalhes internos.
 *
 * Usa MockMvc standalone para isolar o handler sem subir o contexto Spring completo.
 */
class GlobalExceptionHandlerTenantTest {

    /**
     * Controller mínimo que simula um endpoint que chama TenantContext.getRequiredTenantId()
     * sem que o tenant tenha sido configurado na thread.
     */
    @RestController
    static class FakeTenantController {
        @GetMapping("/test/tenant-required")
        public String endpoint() {
            // Simula o que acontece quando o JWT não chegou: lança IllegalStateException
            TenantContext.getRequiredTenantId();
            return "ok";
        }
    }

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        // IMPORTANTE: NÃO configurar TenantContext — isso simula a ausência de JWT
        mockMvc = MockMvcBuilders
                .standaloneSetup(new FakeTenantController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Quando TenantContext não tem tenant e IllegalStateException é lançada, retorna 403 Forbidden")
    void devolveForbiddenQuandoTenantContextVazio() throws Exception {
        mockMvc.perform(get("/test/tenant-required")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Acesso não autorizado: contexto de tenant ausente"));
    }

    @Test
    @DisplayName("Resposta 403 não expõe a mensagem interna do IllegalStateException")
    void respostaNaoExpoeDetalhesInternos() throws Exception {
        mockMvc.perform(get("/test/tenant-required")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                // A mensagem interna "Tenant não configurado para a requisição atual" NÃO deve aparecer na resposta
                .andExpect(jsonPath("$.message").doesNotExist());
    }
}
