package br.com.menthoros.backend.controller;

import br.com.menthoros.backend.config.core.JacksonConfig;
import br.com.menthoros.backend.dto.output.AtletaOutputDto;
import br.com.menthoros.backend.exception.ResourceNotFoundException;
import br.com.menthoros.backend.enums.NivelExperiencia;
import br.com.menthoros.backend.mapper.AtletaMapper;
import br.com.menthoros.backend.repository.TenantValidationRepository;
import br.com.menthoros.backend.repository.UsuarioRepository;
import br.com.menthoros.backend.services.AtletaService;
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
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static br.com.menthoros.backend.testsupport.JwtTestSupport.atletaJwt;
import static br.com.menthoros.backend.testsupport.JwtTestSupport.stubUsuarioAtivo;
import static br.com.menthoros.backend.testsupport.JwtTestSupport.tecnicoJwt;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contrato de {@code GET /api/v1/atletas/{id}}. A edição abria o campo "Data de Nascimento" vazio
 * porque o GET devolvia só a idade: a data sai como {@code yyyy-MM-dd} (formato do input
 * type=date), a idade continua presente, e o atleta legado sem data omite a chave (NON_NULL).
 * Como a data de nascimento é dado pessoal, também trava o acesso: o papel ATLETA não lê o
 * registro por este endpoint e id de outro tenant responde 404.
 */
@WebMvcTest(controllers = AtletaController.class)
@Import({JacksonConfig.class, AuthWebMvcTestConfig.class})
class AtletaPorIdContratoTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private AtletaService atletaService;
    @MockitoBean private AtletaMapper atletaMapper;
    @MockitoBean private JwtDecoder jwtDecoder;
    @MockitoBean private UsuarioSyncService usuarioSyncService;
    @MockitoBean private UsuarioRepository usuarioRepository;
    @MockitoBean private TenantValidationRepository tenantValidationRepository;

    private final UUID atletaId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        stubUsuarioAtivo(usuarioSyncService);
        when(tenantValidationRepository.resourceBelongsToTenant(any(), any())).thenReturn(true);
    }

    @Test
    @DisplayName("GET /atletas/{id} devolve dataNascimento em yyyy-MM-dd e mantém idade")
    void devolveDataEIdade() throws Exception {
        when(atletaService.getAtletaById(atletaId)).thenReturn(atleta(LocalDate.of(1990, 5, 17)));

        mockMvc.perform(get("/api/v1/atletas/{id}", atletaId).with(tecnicoJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dataNascimento").value("1990-05-17"))
                .andExpect(jsonPath("$.idade").value(35));
    }

    @Test
    @DisplayName("atleta legado sem data omite a chave dataNascimento")
    void legadoOmiteChave() throws Exception {
        when(atletaService.getAtletaById(atletaId)).thenReturn(atleta(null));

        mockMvc.perform(get("/api/v1/atletas/{id}", atletaId).with(tecnicoJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dataNascimento").doesNotExist())
                .andExpect(jsonPath("$.idade").value(35));
    }

    @Test
    @DisplayName("papel ATLETA não lê atleta por id: 403 e o service nem é consultado")
    void atletaNaoLePorId() throws Exception {
        mockMvc.perform(get("/api/v1/atletas/{id}", atletaId).with(atletaJwt()))
                .andExpect(status().isForbidden());

        verifyNoInteractions(atletaService);
    }

    @Test
    @DisplayName("id de outro tenant (service não encontra no tenant) responde 404 sem corpo de atleta")
    void outroTenantResponde404() throws Exception {
        when(atletaService.getAtletaById(atletaId)).thenThrow(new ResourceNotFoundException("Atleta não encontrado: " + atletaId));

        mockMvc.perform(get("/api/v1/atletas/{id}", atletaId).with(tecnicoJwt()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.dataNascimento").doesNotExist());
    }

    private AtletaOutputDto atleta(LocalDate dataNascimento) {
        return new AtletaOutputDto(atletaId, "Ana Silva", 35, new BigDecimal("60.0"), new BigDecimal("165.0"),
                "Correr 10km", NivelExperiencia.INTERMEDIARIO, Set.of(), null, false, null, List.of(),
                null, null, "ana@exemplo.com", null, dataNascimento);
    }
}
