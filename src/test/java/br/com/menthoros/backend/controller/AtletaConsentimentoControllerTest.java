package br.com.menthoros.backend.controller;

import br.com.menthoros.backend.dto.output.TrajetoShareConsentOutputDto;
import br.com.menthoros.backend.exception.ConsentVersionStaleException;
import br.com.menthoros.backend.security.JwtTenantFilter;
import br.com.menthoros.backend.security.StructuredLoggingFilter;
import br.com.menthoros.backend.services.TrajetoShareConsentService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AtletaConsentimentoController.class, excludeFilters = @ComponentScan.Filter(
        type = FilterType.ASSIGNABLE_TYPE,
        classes = {JwtTenantFilter.class, StructuredLoggingFilter.class}))
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("AtletaConsentimentoController")
class AtletaConsentimentoControllerTest {

    private static final String URL = "/api/v1/atletas/me/consentimentos/trajeto-compartilhamento";

    @Autowired private MockMvc mockMvc;
    @MockitoBean private TrajetoShareConsentService service;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void autenticar(String role) {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(
                "user", null, List.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }

    @Test
    @DisplayName("GET: atleta recebe o estado")
    void get_atleta() throws Exception {
        autenticar("ATLETA");
        when(service.estadoAtual()).thenReturn(new TrajetoShareConsentOutputDto(false, "2026-11-09", null));

        mockMvc.perform(get(URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.granted").value(false))
                .andExpect(jsonPath("$.policyVersion").value("2026-11-09"));
    }

    @Test
    @DisplayName("POST: 201 com a versão apresentada repassada ao service")
    void post_concede() throws Exception {
        autenticar("ATLETA");
        when(service.conceder("2026-11-09")).thenReturn(new TrajetoShareConsentOutputDto(true, "2026-11-09", null));

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("{\"policyVersion\":\"2026-11-09\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.granted").value(true));
    }

    @Test
    @DisplayName("POST sem versão: 400, sem chamar o service")
    void post_semVersao() throws Exception {
        autenticar("ATLETA");
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        verify(service, never()).conceder(any());
    }

    @Test
    @DisplayName("POST com versão defasada: 409 CONSENT_VERSION_STALE (CA6)")
    void post_defasada() throws Exception {
        autenticar("ATLETA");
        when(service.conceder("2026-10-26")).thenThrow(new ConsentVersionStaleException("defasada"));

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("{\"policyVersion\":\"2026-10-26\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONSENT_VERSION_STALE"));
    }

    @Test
    @DisplayName("DELETE: revoga (CA7)")
    void delete_revoga() throws Exception {
        autenticar("ATLETA");
        when(service.revogar()).thenReturn(new TrajetoShareConsentOutputDto(false, "2026-11-09", null));

        mockMvc.perform(delete(URL)).andExpect(status().isOk()).andExpect(jsonPath("$.granted").value(false));
    }
}
