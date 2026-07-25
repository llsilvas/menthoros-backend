package br.com.menthoros.backend.controller;

import br.com.menthoros.backend.dto.output.RevisaoSemanalOutputDto;
import br.com.menthoros.backend.dto.output.WeekOverWeekDelta;
import br.com.menthoros.backend.enums.NivelAderencia;
import br.com.menthoros.backend.enums.RecommendationType;
import br.com.menthoros.backend.exception.DomainNotFoundException;
import br.com.menthoros.backend.security.JwtTenantFilter;
import br.com.menthoros.backend.security.StructuredLoggingFilter;
import br.com.menthoros.backend.services.RevisaoSemanalService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = CoachRevisaoSemanalController.class, excludeFilters = @ComponentScan.Filter(
        type = FilterType.ASSIGNABLE_TYPE,
        classes = {JwtTenantFilter.class, StructuredLoggingFilter.class}))
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("CoachRevisaoSemanalController")
class CoachRevisaoSemanalControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private RevisaoSemanalService revisaoSemanalService;

    private final UUID atletaId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken("coach", null, List.of(new SimpleGrantedAuthority("ROLE_TECNICO"))));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("200 com a revisão do atleta")
    void retorna200() throws Exception {
        when(revisaoSemanalService.buscarUltima(atletaId)).thenReturn(stub());

        mockMvc.perform(get("/api/v1/coach/atletas/{atletaId}/revisao-semanal", atletaId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recommendationType").value("MAINTAIN"))
                .andExpect(jsonPath("$.adherenceStatus").value("MEDIA"))
                .andExpect(jsonPath("$.weekOverWeekDelta.primeiraSemana").value(true));
    }

    @Test
    @DisplayName("404 quando não há revisão (nenhuma semana fechada)")
    void retorna404() throws Exception {
        when(revisaoSemanalService.buscarUltima(atletaId))
                .thenThrow(new DomainNotFoundException("Revisão semanal não disponível para o atleta"));

        mockMvc.perform(get("/api/v1/coach/atletas/{atletaId}/revisao-semanal", atletaId))
                .andExpect(status().isNotFound());
    }

    // Nota (mesma limitação de CoachKudosControllerTest): "403 quando role inválida" não é tecível
    // neste slice — @WebMvcTest(addFilters=false) + SecurityContextHolder manual não aplica o
    // @PreAuthorize (method security). A anotação hasAnyRole('TECNICO','ADMIN') está no controller,
    // idêntica ao padrão dos demais endpoints coach; o caminho negativo fica como débito de teste.

    private RevisaoSemanalOutputDto stub() {
        return new RevisaoSemanalOutputDto(
                UUID.randomUUID(), LocalDate.now().minusDays(6), LocalDate.now(),
                RecommendationType.MAINTAIN, NivelAderencia.MEDIA, new BigDecimal("75.00"),
                new BigDecimal("-5"), true, WeekOverWeekDelta.semAnterior(), Instant.now());
    }
}
