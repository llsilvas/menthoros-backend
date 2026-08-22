package br.com.menthoros.backend.config.core;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "app.security.public-paths[0]=/api/public/**",
    "app.security.public-paths[1]=/actuator/health",
    "app.security.strava-paths[0]=/api/v1/strava/webhook",
    "app.strava.webhook-verify-token=test-webhook-token",
    // Sobe o contexto inteiro para exercitar a cadeia de filtros — e o contexto inteiro arrasta
    // Flyway e JPA. Sem H2, o datasource default aponta para localhost:5432 e o teste passa apenas
    // onde já existe um Postgres no ar: verde na máquina do dev, vermelho em qualquer runner limpo.
    // Mesmo tratamento de HealthConfigTest e AuditConfigTest.
    "spring.datasource.url=jdbc:h2:mem:securitydb;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    // Contexto sem profile herda o application.yml real, que deixa client-id/client-secret
    // vazios (vem de env var em runtime). IntervalsIcuProperties e @NotBlank e sem isto o
    // contexto nao sobe. Ver D11 da change intervals-icu-oauth2-integration.
    "app.intervals-icu.client-id=test-intervals-client-id",
    "app.intervals-icu.client-secret=test-intervals-client-secret"
})
class CoreSecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void should_permit_public_paths() throws Exception {
        mockMvc.perform(get("/actuator/health"))
            .andExpect(status().isOk());
    }

    @Test
    void should_hide_health_details_when_unauthenticated() throws Exception {
        // show-details: when-authorized → anônimo recebe só o status, sem detalhes de componentes
        mockMvc.perform(get("/actuator/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").exists())
            .andExpect(jsonPath("$.components").doesNotExist());
    }

    @Test
    void should_permit_strava_webhook_without_auth() throws Exception {
        mockMvc.perform(get("/api/v1/strava/webhook")
                .param("hub.mode", "subscribe")
                .param("hub.verify_token", "test-webhook-token")
                .param("hub.challenge", "test-challenge"))
            .andExpect(status().isOk());
    }
}
