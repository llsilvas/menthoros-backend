package br.com.menthoros.backend.controller;

import br.com.menthoros.backend.AbstractIntegrationTest;
import br.com.menthoros.backend.entity.AnaliseWorkout;
import br.com.menthoros.backend.entity.Assessoria;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.TreinoRealizado;
import br.com.menthoros.backend.entity.Usuario;
import br.com.menthoros.backend.enums.AnaliseStatus;
import br.com.menthoros.backend.enums.AtletaStatus;
import br.com.menthoros.backend.enums.DiaSemana;
import br.com.menthoros.backend.enums.FonteDados;
import br.com.menthoros.backend.enums.NivelExperiencia;
import br.com.menthoros.backend.enums.PlanoAssessoria;
import br.com.menthoros.backend.enums.TipoTreino;
import br.com.menthoros.backend.enums.UserRole;
import br.com.menthoros.backend.repository.AiWorkoutAnalysisRepository;
import br.com.menthoros.backend.repository.AssessoriaRepository;
import br.com.menthoros.backend.repository.AtletaRepository;
import br.com.menthoros.backend.repository.TreinoRealizadoRepository;
import br.com.menthoros.backend.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Hardening do endpoint do coach (analise-ia-treino-atleta, task 2.5 / Codex #1): com o bloco do
 * atleta no {@code AnaliseWorkoutOutputDto}, o {@code isAuthenticated()} de antes deixaria um
 * atleta ler a análise de outro atleta do mesmo tenant. Agora o endpoint é TECNICO/ADMIN; o
 * atleta usa {@code /atletas/me/realizados/{id}/analise}, escopado por dono.
 */
@AutoConfigureMockMvc
@DisplayName("GET /api/v1/analises/treino/{id} — acesso por papel")
class AnaliseWorkoutAccessIT extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private AssessoriaRepository assessoriaRepository;
    @Autowired private AtletaRepository atletaRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private TreinoRealizadoRepository treinoRealizadoRepository;
    @Autowired private AiWorkoutAnalysisRepository analiseRepository;

    private UUID tenantAId;
    private UUID treinoRealizadoId;
    private UUID subAtletaA;
    private UUID subTecnicoA;

    @BeforeEach
    void setUp() {
        Assessoria assessoriaA = new Assessoria();
        assessoriaA.setNome("Assessoria A");
        assessoriaA.setDominio("assessoria-a-" + UUID.randomUUID());
        assessoriaA.setPlano(PlanoAssessoria.BASIC);
        assessoriaA = assessoriaRepository.save(assessoriaA);
        tenantAId = assessoriaA.getId();

        Atleta atletaA = new Atleta();
        atletaA.setNome("Atleta Dono");
        atletaA.setEmail("atleta-" + UUID.randomUUID() + "@menthoros.test");
        atletaA.setObjetivo("Correr uma maratona");
        atletaA.setNivelExperiencia(NivelExperiencia.INTERMEDIARIO);
        atletaA.setAtivo(AtletaStatus.ATIVO);
        atletaA.setAssessoria(assessoriaA);
        atletaA = atletaRepository.save(atletaA);

        subAtletaA = UUID.randomUUID();
        subTecnicoA = UUID.randomUUID();
        Usuario usuarioAtleta = criarUsuario(subAtletaA, assessoriaA, UserRole.ATLETA);
        atletaA.setUsuario(usuarioAtleta);
        atletaRepository.save(atletaA);
        criarUsuario(subTecnicoA, assessoriaA, UserRole.TECNICO);

        TreinoRealizado treino = new TreinoRealizado();
        treino.setAtleta(atletaA);
        treino.setTenantId(tenantAId);
        treino.setDataTreino(LocalDate.now());
        treino.setDiaSemana(DiaSemana.SEGUNDA);
        treino.setTipoTreino(TipoTreino.FACIL);
        treino.setDuracaoMin(Duration.ofMinutes(50));
        treino.setDistanciaKm(new BigDecimal("9.0"));
        treino.setFonteDados(FonteDados.MANUAL);
        treino.setPercepcaoEsforco(7);
        treino = treinoRealizadoRepository.save(treino);
        treinoRealizadoId = treino.getId();

        AnaliseWorkout analise = new AnaliseWorkout();
        analise.setTreinoRealizadoId(treinoRealizadoId);
        analise.setTenantId(tenantAId);
        analise.setStatus(AnaliseStatus.COMPLETED);
        analise.setSummaryPt("Execução dentro do esperado");
        analise.setAtletaReconhecimento("Você segurou o ritmo.");
        analise.setAtletaComoFoi("Saiu como planejado.");
        analise.setAtletaEsforco("Pesou um pouco mais que o esperado.");
        analise.setAtletaProximoTreino("Capriche no sono hoje.");
        analiseRepository.save(analise);
    }

    @Test
    @DisplayName("TECNICO do tenant lê a análise com o bloco 'o que o atleta leu' -> 200")
    void tecnicoLeComBlocoDoAtleta() throws Exception {
        mockMvc.perform(get("/api/v1/analises/treino/{id}", treinoRealizadoId)
                        .with(jwtDe(subTecnicoA, tenantAId, "TECNICO")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary").value("Execução dentro do esperado"))
                .andExpect(jsonPath("$.atletaComoFoi").value("Saiu como planejado."))
                .andExpect(jsonPath("$.atletaProximoTreino").value("Capriche no sono hoje."));
    }

    @Test
    @DisplayName("ATLETA (mesmo o dono) não usa o endpoint do coach -> 403")
    void atletaRecebe403() throws Exception {
        mockMvc.perform(get("/api/v1/analises/treino/{id}", treinoRealizadoId)
                        .with(jwtDe(subAtletaA, tenantAId, "ATLETA")))
                .andExpect(status().isForbidden());
    }

    private Usuario criarUsuario(UUID subject, Assessoria assessoria, UserRole role) {
        Usuario usuario = Usuario.builder()
                .id(subject)
                .keycloakId(subject.toString())
                .assessoria(assessoria)
                .email(subject + "@menthoros.test")
                .nome("Usuario " + subject)
                .role(role)
                .ativo(true)
                .build();
        return usuarioRepository.save(usuario);
    }

    private RequestPostProcessor jwtDe(UUID subject, UUID tenantId, String papel) {
        return jwt()
                .authorities(new SimpleGrantedAuthority("ROLE_" + papel))
                .jwt(j -> j.subject(subject.toString()).claim("tenant_id", tenantId.toString()));
    }
}
