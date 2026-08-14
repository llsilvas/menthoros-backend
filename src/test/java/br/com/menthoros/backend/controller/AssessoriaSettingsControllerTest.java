package br.com.menthoros.backend.controller;

import br.com.menthoros.backend.dto.output.AssessoriaMeOutputDto;
import br.com.menthoros.backend.enums.PlanoAssessoria;
import br.com.menthoros.backend.exception.DomainNotFoundException;
import br.com.menthoros.backend.security.JwtTenantFilter;
import br.com.menthoros.backend.security.StructuredLoggingFilter;
import br.com.menthoros.backend.services.AssessoriaSettingsService;
import jakarta.persistence.OptimisticLockException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contrato HTTP do GET. A autorização (`PROPRIETARIO` vs. `TECNICO`) e o isolamento de tenant são
 * exercitados no {@code *IT} correspondente — este slice roda com os filtros desligados, como os
 * demais do módulo, e provaria nada sobre roles.
 */
@WebMvcTest(controllers = AssessoriaSettingsController.class, excludeFilters = @ComponentScan.Filter(
        type = FilterType.ASSIGNABLE_TYPE,
        classes = {JwtTenantFilter.class, StructuredLoggingFilter.class}))
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("AssessoriaSettingsController")
class AssessoriaSettingsControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private AssessoriaSettingsService service;

    @Test
    @DisplayName("GET /assessorias/me → 200 com identidade, uso e versão")
    void buscarMinhaAssessoria() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.buscarDoTenantCorrente()).thenReturn(new AssessoriaMeOutputDto(
                id, "Corridas Serra", true, "/api/v1/assessorias/me/logo",
                PlanoAssessoria.BASIC,
                new AssessoriaMeOutputDto.Uso(7L, 10, 1L, 1),
                3L));

        mockMvc.perform(get("/api/v1/assessorias/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.nome").value("Corridas Serra"))
                .andExpect(jsonPath("$.temLogo").value(true))
                .andExpect(jsonPath("$.logoUrl").value("/api/v1/assessorias/me/logo"))
                .andExpect(jsonPath("$.plano").value("BASIC"))
                .andExpect(jsonPath("$.uso.atletas").value(7))
                .andExpect(jsonPath("$.uso.maxAtletas").value(10))
                .andExpect(jsonPath("$.uso.tecnicos").value(1))
                .andExpect(jsonPath("$.version").value(3));
    }

    /**
     * As cores não estão no contrato (D3). Se voltarem sem decisão de produto, este teste avisa.
     */
    @Test
    @DisplayName("a resposta não expõe cores da assessoria")
    void semCoresNoContrato() throws Exception {
        when(service.buscarDoTenantCorrente()).thenReturn(new AssessoriaMeOutputDto(
                UUID.randomUUID(), "Corridas Serra", false, null,
                PlanoAssessoria.BASIC,
                new AssessoriaMeOutputDto.Uso(0L, 10, 1L, 1),
                0L));

        mockMvc.perform(get("/api/v1/assessorias/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.corPrimaria").doesNotExist())
                .andExpect(jsonPath("$.corSecundaria").doesNotExist());
    }

    @Test
    @DisplayName("sem logo, logoUrl é omitido do JSON")
    void semLogoOmiteUrl() throws Exception {
        when(service.buscarDoTenantCorrente()).thenReturn(new AssessoriaMeOutputDto(
                UUID.randomUUID(), "Corridas Serra", false, null,
                PlanoAssessoria.BASIC,
                new AssessoriaMeOutputDto.Uso(0L, 10, 1L, 1),
                0L));

        mockMvc.perform(get("/api/v1/assessorias/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.temLogo").value(false))
                .andExpect(jsonPath("$.logoUrl").doesNotExist());
    }

    @Nested
    @DisplayName("PATCH /assessorias/me")
    class Patch {

        @Test
        @DisplayName("nome válido → 200 com a versão nova")
        void nomeValido() throws Exception {
            when(service.atualizarDoTenantCorrente(any())).thenReturn(saida("Corridas Serra Pro", 4L));

            mockMvc.perform(patch("/api/v1/assessorias/me")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"nome":"Corridas Serra Pro","version":3}
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.nome").value("Corridas Serra Pro"))
                    .andExpect(jsonPath("$.version").value(4));
        }

        /**
         * O núcleo da decisão D3. O default do Spring Boot descartaria `corPrimaria` em silêncio e
         * responderia 200, fazendo o cliente acreditar que salvou uma cor que ninguém persistiu.
         */
        @Test
        @DisplayName("cor no payload → 400, nunca ignorada em silêncio")
        void corNoPayloadRejeitada() throws Exception {
            mockMvc.perform(patch("/api/v1/assessorias/me")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"nome":"Corridas Serra","version":3,"corPrimaria":"#FF6B35"}
                                    """))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(service);
        }

        @Test
        @DisplayName("versão ausente → 400")
        void versaoAusente() throws Exception {
            mockMvc.perform(patch("/api/v1/assessorias/me")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"nome":"Corridas Serra"}
                                    """))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(service);
        }

        @Test
        @DisplayName("nome em branco → 400")
        void nomeEmBranco() throws Exception {
            mockMvc.perform(patch("/api/v1/assessorias/me")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"nome":"   ","version":3}
                                    """))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(service);
        }

        @Test
        @DisplayName("versão obsoleta → 409")
        void versaoObsoleta() throws Exception {
            when(service.atualizarDoTenantCorrente(any()))
                    .thenThrow(new OptimisticLockException("A assessoria foi alterada por outra sessão."));

            mockMvc.perform(patch("/api/v1/assessorias/me")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"nome":"Corridas Serra","version":2}
                                    """))
                    .andExpect(status().isConflict());
        }
    }

    private AssessoriaMeOutputDto saida(String nome, Long version) {
        return new AssessoriaMeOutputDto(
                UUID.randomUUID(), nome, false, null,
                PlanoAssessoria.BASIC,
                new AssessoriaMeOutputDto.Uso(0L, 10, 1L, 1),
                version);
    }

    @Test
    @DisplayName("tenant sem assessoria → 404")
    void tenantSemAssessoria() throws Exception {
        when(service.buscarDoTenantCorrente())
                .thenThrow(new DomainNotFoundException("Assessoria não encontrada para o tenant corrente"));

        mockMvc.perform(get("/api/v1/assessorias/me"))
                .andExpect(status().isNotFound());
    }
}
