package br.com.menthoros.backend.controller;

import br.com.menthoros.backend.config.core.JacksonConfig;
import br.com.menthoros.backend.dto.output.AtletaOutputDto;
import br.com.menthoros.backend.dto.output.AtletaPerfilCoachOutputDto;
import br.com.menthoros.backend.dto.output.CoachAtletaResumoDto;
import br.com.menthoros.backend.enums.AthleteBillingStatus;
import br.com.menthoros.backend.enums.NivelExperiencia;
import br.com.menthoros.backend.mapper.AtletaMapper;
import br.com.menthoros.backend.repository.TenantValidationRepository;
import br.com.menthoros.backend.repository.UsuarioRepository;
import br.com.menthoros.backend.services.AtletaService;
import br.com.menthoros.backend.services.CoachAthleteProfileService;
import br.com.menthoros.backend.services.CoachDashboardService;
import br.com.menthoros.backend.services.UsuarioSyncService;
import br.com.menthoros.backend.testsupport.AuthWebMvcTestConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static br.com.menthoros.backend.testsupport.JwtTestSupport.stubUsuarioAtivo;
import static br.com.menthoros.backend.testsupport.JwtTestSupport.tecnicoJwt;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Design D10: como TECNICO (não proprietário), os endpoints de leitura de atleta devolvem o
 * status de cobrança e o próximo vencimento, e o JSON não contém nenhuma chave financeira —
 * o valor só sai por {@code AthleteContractController}, atrás de PROPRIETARIO (CA6).
 */
@WebMvcTest(controllers = {AtletaController.class, CoachDashboardController.class, CoachAthleteProfileController.class})
@Import({JacksonConfig.class, AuthWebMvcTestConfig.class})
class AthleteBillingExposureTest {

    private static final List<String> CHAVES_FINANCEIRAS = List.of("\"amount\"", "\"valor", "\"paid", "\"pago");

    @Autowired private MockMvc mockMvc;

    @MockitoBean private AtletaService atletaService;
    @MockitoBean private AtletaMapper atletaMapper;
    @MockitoBean private CoachDashboardService coachDashboardService;
    @MockitoBean private CoachAthleteProfileService coachAthleteProfileService;
    @MockitoBean private JwtDecoder jwtDecoder;
    @MockitoBean private UsuarioSyncService usuarioSyncService;
    @MockitoBean private UsuarioRepository usuarioRepository;
    @MockitoBean private TenantValidationRepository tenantValidationRepository;

    private final UUID atletaId = UUID.randomUUID();
    private final LocalDate vencimento = LocalDate.of(2026, 10, 10);

    @BeforeEach
    void setUp() {
        stubUsuarioAtivo(usuarioSyncService);
        when(tenantValidationRepository.resourceBelongsToTenant(any(), any())).thenReturn(true);
    }

    @Test
    @DisplayName("GET /atletas/{id} como TECNICO: billingStatus e nextDueDate presentes, nenhuma chave financeira")
    void atletaPorId() throws Exception {
        when(atletaService.getAtletaById(atletaId)).thenReturn(atleta());

        String corpo = mockMvc.perform(get("/api/v1/atletas/{id}", atletaId).with(tecnicoJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.billingStatus").value("OVERDUE"))
                .andExpect(jsonPath("$.nextDueDate").value("2026-10-10"))
                .andReturn().getResponse().getContentAsString();

        assertSemChaveFinanceira(corpo);
    }

    @Test
    @DisplayName("GET /atletas como TECNICO: nenhuma chave financeira na lista")
    void listaAtletas() throws Exception {
        when(atletaService.getAllAtletas(null, null, null)).thenReturn(List.of(atleta()));

        String corpo = mockMvc.perform(get("/api/v1/atletas").with(tecnicoJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].billingStatus").value("OVERDUE"))
                .andReturn().getResponse().getContentAsString();

        assertSemChaveFinanceira(corpo);
    }

    @Test
    @DisplayName("GET /coach/atletas (roster) como TECNICO: status e vencimento sem valor (CA6)")
    void roster() throws Exception {
        when(coachDashboardService.getRoster()).thenReturn(List.of(new CoachAtletaResumoDto(
                atletaId, "Ana Silva", 52.3, 44.0, 8.3, "BUILD", "warning",
                LocalDate.of(2026, 9, 15), new BigDecimal("32.5"), 80, "FORMA_IDEAL",
                AthleteBillingStatus.OVERDUE, vencimento, false)));

        String corpo = mockMvc.perform(get("/api/v1/coach/atletas").with(tecnicoJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].billingStatus").value("OVERDUE"))
                .andExpect(jsonPath("$[0].nextDueDate").value("2026-10-10"))
                .andReturn().getResponse().getContentAsString();

        assertSemChaveFinanceira(corpo);
    }

    @Test
    @DisplayName("GET /coach/atletas/{id}/perfil como TECNICO: status e vencimento sem valor")
    void perfil() throws Exception {
        when(coachAthleteProfileService.buscarPerfil(atletaId)).thenReturn(new AtletaPerfilCoachOutputDto(
                atletaId, "Ana Silva", "Correr maratona", null, "INTERMEDIARIO", null,
                List.of(), List.of(), null, List.of(), List.of(), List.of(),
                Instant.now(), null, null, AthleteBillingStatus.DUE_SOON, vencimento, List.of(), List.of(), false));

        String corpo = mockMvc.perform(get("/api/v1/coach/atletas/{id}/perfil", atletaId).with(tecnicoJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.billingStatus").value("DUE_SOON"))
                .andReturn().getResponse().getContentAsString();

        assertSemChaveFinanceira(corpo);
    }

    private static void assertSemChaveFinanceira(String json) {
        for (String chave : CHAVES_FINANCEIRAS) {
            assertThat(json).as("chave financeira %s vazou para treinador não proprietário", chave)
                    .doesNotContainIgnoringCase(chave);
        }
    }

    private AtletaOutputDto atleta() {
        return new AtletaOutputDto(atletaId, "Ana Silva", 30, new BigDecimal("60.0"), new BigDecimal("165.0"),
                "Correr 10km", NivelExperiencia.INTERMEDIARIO, Set.of(), null, false, null, List.of(),
                AthleteBillingStatus.OVERDUE, vencimento, "ana@exemplo.com", null);
    }
}
