package br.com.menthoros.backend.exception;

import br.com.menthoros.backend.exception.handler.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** D11 de garmin-connection: canal desligado ou sem credenciais é 503, não 409 nem 500. */
class GlobalExceptionHandlerGarminCanalIndisponivelTest {

    @RestController
    static class FakeGarminController {
        @GetMapping("/test/garmin")
        public String endpoint() {
            throw new GarminCanalIndisponivelException("canal não configurado");
        }
    }

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new FakeGarminController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("GarminCanalIndisponivelException vira 503 com a mensagem")
    void devolve503() throws Exception {
        mockMvc.perform(get("/test/garmin"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.error").value("Service Unavailable"))
                .andExpect(jsonPath("$.message").value("canal não configurado"));
    }
}
