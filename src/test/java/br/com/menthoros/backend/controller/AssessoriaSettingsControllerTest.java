package br.com.menthoros.backend.controller;

import br.com.menthoros.backend.dto.output.AssessoriaMeOutputDto;
import br.com.menthoros.backend.enums.PlanoAssessoria;
import br.com.menthoros.backend.exception.DomainNotFoundException;
import br.com.menthoros.backend.security.JwtTenantFilter;
import br.com.menthoros.backend.security.StructuredLoggingFilter;
import br.com.menthoros.backend.services.AssessoriaLogoService;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
    @MockitoBean private AssessoriaLogoService logoService;

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

    @Nested
    @DisplayName("logo")
    class Logo {

        @Test
        @DisplayName("POST com PNG → 200 e repassa os bytes e a versão")
        void uploadValido() throws Exception {
            when(logoService.substituir(any(), any())).thenReturn(saida("Corridas Serra", 4L));

            mockMvc.perform(multipart("/api/v1/assessorias/me/logo")
                            .file(new MockMultipartFile("arquivo", "logo.png", "image/png",
                                    new byte[]{1, 2, 3}))
                            .param("version", "3"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.version").value(4));

            verify(logoService).substituir(any(), eq(3L));
        }

        @Test
        @DisplayName("GET → 200 com Content-Type e ETag do conteúdo")
        void servirLogo() throws Exception {
            when(logoService.buscarEtag()).thenReturn(Optional.of("hash-do-conteudo"));
            when(logoService.buscar()).thenReturn(Optional.of(new AssessoriaLogoService.LogoBinario(
                    new byte[]{10, 20, 30}, "image/png", "hash-do-conteudo")));

            mockMvc.perform(get("/api/v1/assessorias/me/logo"))
                    .andExpect(status().isOk())
                    .andExpect(header().string(HttpHeaders.ETAG, "\"hash-do-conteudo\""))
                    .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "image/png"));
        }

        /**
         * O ponto do 304 é não trafegar nem <b>ler</b> os bytes: se {@code buscar()} for chamado,
         * a economia se perde mesmo com a resposta correta.
         */
        @Test
        @DisplayName("GET com If-None-Match igual → 304 sem ler o conteúdo")
        void naoModificado() throws Exception {
            when(logoService.buscarEtag()).thenReturn(Optional.of("hash-do-conteudo"));

            mockMvc.perform(get("/api/v1/assessorias/me/logo")
                            .header(HttpHeaders.IF_NONE_MATCH, "\"hash-do-conteudo\""))
                    .andExpect(status().isNotModified());

            verify(logoService, never()).buscar();
        }

        @Test
        @DisplayName("GET com If-None-Match diferente → 200 com o conteúdo novo")
        void etagDesatualizado() throws Exception {
            when(logoService.buscarEtag()).thenReturn(Optional.of("hash-novo"));
            when(logoService.buscar()).thenReturn(Optional.of(new AssessoriaLogoService.LogoBinario(
                    new byte[]{9}, "image/jpeg", "hash-novo")));

            mockMvc.perform(get("/api/v1/assessorias/me/logo")
                            .header(HttpHeaders.IF_NONE_MATCH, "\"hash-antigo\""))
                    .andExpect(status().isOk())
                    .andExpect(header().string(HttpHeaders.ETAG, "\"hash-novo\""));
        }

        @Test
        @DisplayName("GET sem logo → 404")
        void semLogo() throws Exception {
            when(logoService.buscarEtag()).thenReturn(Optional.empty());

            mockMvc.perform(get("/api/v1/assessorias/me/logo"))
                    .andExpect(status().isNotFound());

            verify(logoService, never()).buscar();
        }

        @Test
        @DisplayName("DELETE → 204 e repassa a versão")
        void removerLogo() throws Exception {
            mockMvc.perform(delete("/api/v1/assessorias/me/logo").param("version", "3"))
                    .andExpect(status().isNoContent());

            verify(logoService).remover(3L);
        }

        @Test
        @DisplayName("DELETE sem versão → 400, nunca apaga por omissão")
        void removerSemVersao() throws Exception {
            mockMvc.perform(delete("/api/v1/assessorias/me/logo"))
                    .andExpect(status().isBadRequest());

            verify(logoService, never()).remover(any());
        }

        @Test
        @DisplayName("DELETE com versão obsoleta → 409")
        void removerVersaoObsoleta() throws Exception {
            doThrow(new OptimisticLockException("versão obsoleta"))
                    .when(logoService).remover(2L);

            mockMvc.perform(delete("/api/v1/assessorias/me/logo").param("version", "2"))
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
