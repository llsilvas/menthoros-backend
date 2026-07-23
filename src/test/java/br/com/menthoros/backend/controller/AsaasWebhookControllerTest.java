package br.com.menthoros.backend.controller;

import br.com.menthoros.backend.config.asaas.AsaasProperties;
import br.com.menthoros.backend.dto.asaas.AsaasWebhookEventDto;
import br.com.menthoros.backend.services.AsaasWebhookEventService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Valida o gate de autenticação do webhook (CA11): sem o header {@code asaas-access-token} correto,
 * a requisição é rejeitada (401) antes de qualquer processamento.
 */
@ExtendWith(MockitoExtension.class)
class AsaasWebhookControllerTest {

    private static final String TOKEN = "token-secreto-do-webhook";

    @Mock private AsaasWebhookEventService service;

    private AsaasWebhookController controller;

    @BeforeEach
    void setUp() {
        AsaasProperties props = new AsaasProperties();
        props.getWebhook().setAccessToken(TOKEN);
        controller = new AsaasWebhookController(props, service);
    }

    @Test
    @DisplayName("header válido: processa e retorna 200")
    void headerValidoProcessa() {
        AsaasWebhookEventDto event = new AsaasWebhookEventDto("evt1", "PAYMENT_CONFIRMED", null, null);

        ResponseEntity<Void> resp = controller.receiveEvent(TOKEN, event);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(service).processar(event);
    }

    @Test
    @DisplayName("header ausente: 401 sem processar (CA11)")
    void headerAusenteRejeita() {
        AsaasWebhookEventDto event = new AsaasWebhookEventDto("evt1", "PAYMENT_CONFIRMED", null, null);

        ResponseEntity<Void> resp = controller.receiveEvent(null, event);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("header incorreto: 401 sem processar (CA11)")
    void headerIncorretoRejeita() {
        AsaasWebhookEventDto event = new AsaasWebhookEventDto("evt1", "PAYMENT_CONFIRMED", null, null);

        ResponseEntity<Void> resp = controller.receiveEvent("token-errado", event);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verifyNoInteractions(service);
    }
}
