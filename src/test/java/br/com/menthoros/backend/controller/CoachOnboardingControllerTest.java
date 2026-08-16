package br.com.menthoros.backend.controller;

import br.com.menthoros.backend.exception.DomainNotFoundException;
import br.com.menthoros.backend.security.JwtTenantFilter;
import br.com.menthoros.backend.security.StructuredLoggingFilter;
import br.com.menthoros.backend.services.CoachOnboardingService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = CoachOnboardingController.class, excludeFilters = @ComponentScan.Filter(
        type = FilterType.ASSIGNABLE_TYPE,
        classes = {JwtTenantFilter.class, StructuredLoggingFilter.class}))
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("CoachOnboardingController")
class CoachOnboardingControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private CoachOnboardingService service;

    @Test
    @DisplayName("POST concluir → 204 sem corpo")
    void concluir() throws Exception {
        mockMvc.perform(post("/api/v1/users/me/onboarding/concluir"))
                .andExpect(status().isNoContent());

        verify(service).concluir();
    }

    /** Idempotência é contrato: o wizard não precisa saber se já concluiu antes de chamar. */
    @Test
    @DisplayName("chamar duas vezes devolve 204 nas duas")
    void concluirDuasVezes() throws Exception {
        mockMvc.perform(post("/api/v1/users/me/onboarding/concluir"))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/v1/users/me/onboarding/concluir"))
                .andExpect(status().isNoContent());

        verify(service, times(2)).concluir();
    }

    @Test
    @DisplayName("usuário fora do tenant → 404")
    void usuarioForaDoTenant() throws Exception {
        doThrow(new DomainNotFoundException("Usuário não encontrado no tenant atual"))
                .when(service).concluir();

        mockMvc.perform(post("/api/v1/users/me/onboarding/concluir"))
                .andExpect(status().isNotFound());
    }

    /**
     * O path desta change não pode colidir com o do atleta
     * (`/api/v1/atletas/{id}/onboarding/concluir`), que é outro recurso e outro controller.
     */
    @Test
    @DisplayName("o endpoint do coach não responde no path do atleta")
    void naoColideComOnboardingDoAtleta() throws Exception {
        mockMvc.perform(post("/api/v1/atletas/qualquer-id/onboarding/concluir"))
                .andExpect(status().isNotFound());
    }
}
