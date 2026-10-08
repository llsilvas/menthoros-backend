package br.com.menthoros.backend.controller;

import br.com.menthoros.backend.AbstractIntegrationTest;
import br.com.menthoros.backend.exception.EmailDeliveryException;
import br.com.menthoros.backend.repository.WaitlistRepository;
import br.com.menthoros.backend.services.email.EmailSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration test do fluxo evento → listener → e-mail, de ponta a ponta, contra o contexto real
 * — executor assíncrono de produção incluso, sem substituição. As asserções usam
 * {@code Mockito.timeout(...)}, que já faz polling além da janela assíncrona real; não há motivo
 * para forçar um executor síncrono (e o bean-override que isso exigiria é mais máquina do que a
 * garantia vale — achado do {@code code-reviewer}). {@code EmailSender} mockado para capturar os
 * envios sem depender do transporte real.
 */
@AutoConfigureMockMvc
@TestPropertySource(properties = "app.founder.notification-email=founder@menthoros.com")
@DisplayName("Notificação de lead da waitlist (evento + listener)")
class WaitlistNotificationIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private WaitlistRepository waitlistRepository;

    @MockitoBean
    private EmailSender emailSender;

    @BeforeEach
    void limparWaitlist() {
        waitlistRepository.deleteAll();
    }

    private String body(String email, String perfil) {
        return """
                {"nome":"Maria","email":"%s","perfil":"%s","qtdAtletas":"DE_11_A_30","aceiteLgpd":true,
                 "utmSource":"instagram"}
                """.formatted(email, perfil).replace("\n", "");
    }

    @Test
    @DisplayName("treinador criado: confirmação ao inscrito + notificação ao founder")
    void treinadorNotificaInscritoEFounder() throws Exception {
        mockMvc.perform(post("/api/v1/waitlist")
                        .header("X-Forwarded-For", "10.1.0.1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("treinador@exemplo.com", "TREINADOR")))
                .andExpect(status().isCreated());

        verify(emailSender, timeout(2000)).send(argThat(m -> m.to().equals("treinador@exemplo.com")));
        verify(emailSender, timeout(2000)).send(argThat(m -> m.to().equals("founder@menthoros.com")));
    }

    @Test
    @DisplayName("atleta criado: só confirmação ao inscrito, sem notificação ao founder")
    void atletaNaoNotificaFounder() throws Exception {
        mockMvc.perform(post("/api/v1/waitlist")
                        .header("X-Forwarded-For", "10.1.0.2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("atleta@exemplo.com", "ATLETA")))
                .andExpect(status().isCreated());

        verify(emailSender, timeout(2000)).send(argThat(m -> m.to().equals("atleta@exemplo.com")));
        verify(emailSender, never()).send(argThat(m -> m.to().equals("founder@menthoros.com")));
    }

    @Test
    @DisplayName("reenvio do mesmo e-mail (JA_INSCRITO) não dispara nova notificação")
    void reenvioNaoNotifica() throws Exception {
        String corpo = body("dup@exemplo.com", "TREINADOR");
        mockMvc.perform(post("/api/v1/waitlist").header("X-Forwarded-For", "10.1.0.3")
                        .contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isCreated());
        verify(emailSender, timeout(2000).times(2)).send(any()); // inscrito + founder

        mockMvc.perform(post("/api/v1/waitlist").header("X-Forwarded-For", "10.1.0.3")
                        .contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isOk());

        // Nenhuma chamada adicional além das 2 do primeiro envio.
        verify(emailSender, timeout(500).times(2)).send(any());
    }

    @Test
    @DisplayName("falha no envio de e-mail não derruba o cadastro (responde 201 normalmente)")
    void falhaNoEnvioNaoDerrubaCadastro() throws Exception {
        doThrow(new EmailDeliveryException("falha", null)).when(emailSender).send(any());

        mockMvc.perform(post("/api/v1/waitlist")
                        .header("X-Forwarded-For", "10.1.0.4")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("falha-email@exemplo.com", "TREINADOR")))
                .andExpect(status().isCreated());

        assertThat(waitlistRepository.existsByEmailNormalized("falha-email@exemplo.com")).isTrue();
    }
}
