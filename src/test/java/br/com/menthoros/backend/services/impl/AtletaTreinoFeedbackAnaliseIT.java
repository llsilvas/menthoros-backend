package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.AbstractIntegrationTest;
import br.com.menthoros.backend.entity.AnaliseWorkout;
import br.com.menthoros.backend.entity.Assessoria;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.TreinoRealizado;
import br.com.menthoros.backend.entity.Usuario;
import br.com.menthoros.backend.enums.AnaliseStatus;
import br.com.menthoros.backend.enums.AtletaStatus;
import br.com.menthoros.backend.enums.FonteDados;
import br.com.menthoros.backend.enums.NivelExperiencia;
import br.com.menthoros.backend.enums.PlanoAssessoria;
import br.com.menthoros.backend.enums.TipoTreino;
import br.com.menthoros.backend.enums.TreinoExecucaoStatus;
import br.com.menthoros.backend.enums.UserRole;
import br.com.menthoros.backend.repository.AiWorkoutAnalysisRepository;
import br.com.menthoros.backend.repository.AssessoriaRepository;
import br.com.menthoros.backend.repository.AtletaRepository;
import br.com.menthoros.backend.repository.TreinoRealizadoRepository;
import br.com.menthoros.backend.repository.UsuarioRepository;
import br.com.menthoros.backend.routing.ModelRouter;
import br.com.menthoros.backend.util.Utils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * "Como foi?" de um treino sincronizado dispara a análise pós-treino (fix-garmin-realizado-pos-ingestao,
 * CA7): commit real do feedback → {@code TreinoRegistradoEvent} → {@code WorkoutAnalysisListener}
 * (async, AFTER_COMMIT) → linha em {@code tb_analise_workout}.
 *
 * <p>O LLM é simulado indisponível ({@code ModelRouter} mockado): o teste prova que a análise foi
 * tentada e registrada (FAILED) sem depender de chave nem gerar custo. O caminho COMPLETED é
 * coberto pelos testes do próprio listener.</p>
 */
@AutoConfigureMockMvc
@DisplayName("Feedback do atleta dispara a análise do treino sincronizado")
class AtletaTreinoFeedbackAnaliseIT extends AbstractIntegrationTest {

    private static final Duration ESPERA_MAXIMA = Duration.ofSeconds(15);

    @Autowired private MockMvc mockMvc;
    @Autowired private AssessoriaRepository assessoriaRepository;
    @Autowired private AtletaRepository atletaRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private TreinoRealizadoRepository treinoRepository;
    @Autowired private AiWorkoutAnalysisRepository analiseRepository;
    @MockitoBean private ModelRouter modelRouter;

    private Assessoria assessoria;
    private UUID subjectAtleta;
    private TreinoRealizado realizado;

    @BeforeEach
    void seed() {
        when(modelRouter.route(any())).thenThrow(new IllegalStateException("LLM simulado indisponível"));

        assessoria = new Assessoria();
        assessoria.setNome("Assessoria Feedback Analise IT");
        assessoria.setDominio("feedback-analise-it-" + UUID.randomUUID());
        assessoria.setPlano(PlanoAssessoria.BASIC);
        assessoria = assessoriaRepository.save(assessoria);

        subjectAtleta = UUID.randomUUID();
        Usuario usuario = usuarioRepository.save(Usuario.builder()
                .id(subjectAtleta)
                .keycloakId(subjectAtleta.toString())
                .assessoria(assessoria)
                .email(subjectAtleta + "@menthoros.test")
                .nome("Usuario " + subjectAtleta)
                .role(UserRole.ATLETA)
                .ativo(true)
                .build());

        Atleta atleta = new Atleta();
        atleta.setNome("Atleta Feedback Analise IT");
        atleta.setEmail(subjectAtleta + "@menthoros.test");
        atleta.setObjetivo("Correr 10km");
        atleta.setNivelExperiencia(NivelExperiencia.INTERMEDIARIO);
        atleta.setAtivo(AtletaStatus.ATIVO);
        atleta.setAssessoria(assessoria);
        atleta.setUsuario(usuario);
        atleta = atletaRepository.save(atleta);

        // Como chega do Garmin: sem RPE — o evento do insert não passa na elegibilidade da análise.
        LocalDate hoje = LocalDate.now();
        TreinoRealizado tr = new TreinoRealizado();
        tr.setAtleta(atleta);
        tr.setTenantId(assessoria.getId());
        tr.setDataTreino(hoje);
        tr.setDiaSemana(Utils.converterDayOfWeekParaDiaSemana(hoje.getDayOfWeek()));
        tr.setTipoTreino(TipoTreino.CONTINUO);
        tr.setDuracaoMin(Duration.ofMinutes(42));
        tr.setDistanciaKm(BigDecimal.valueOf(6.06));
        tr.setFonteDados(FonteDados.GARMIN);
        tr.setExternalId("feedback-analise-it-" + UUID.randomUUID());
        tr.setStatus(TreinoExecucaoStatus.REALIZADO);
        realizado = treinoRepository.save(tr);
    }

    @Test
    @DisplayName("RPE pelo \"Como foi?\" gera a análise do treino sincronizado (CA7)")
    void feedbackGeraAnalise() throws Exception {
        assertThat(analiseDoRealizado()).isEmpty();

        mockMvc.perform(post("/api/v1/atletas/me/realizados/{id}/feedback", realizado.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"percepcaoEsforco\":4}")
                        .with(jwtAtleta()))
                .andExpect(status().isOk());

        AnaliseWorkout analise = esperarAnaliseConcluida();
        assertThat(analise.getStatus()).isEqualTo(AnaliseStatus.FAILED);
        assertThat(analise.getErrorMessage()).contains("LLM simulado indisponível");
    }

    /** O listener é @Async: espera a linha sair de PENDING, com teto — sem dependência nova de polling. */
    private AnaliseWorkout esperarAnaliseConcluida() throws InterruptedException {
        Instant limite = Instant.now().plus(ESPERA_MAXIMA);
        while (Instant.now().isBefore(limite)) {
            Optional<AnaliseWorkout> analise = analiseDoRealizado();
            if (analise.isPresent() && analise.get().getStatus() != AnaliseStatus.PENDING) {
                return analise.get();
            }
            Thread.sleep(100);
        }
        throw new AssertionError("Análise não registrada em " + ESPERA_MAXIMA + " para o realizado " + realizado.getId());
    }

    private Optional<AnaliseWorkout> analiseDoRealizado() {
        return analiseRepository.findByTreinoRealizadoIdAndTenantId(realizado.getId(), assessoria.getId());
    }

    private RequestPostProcessor jwtAtleta() {
        return jwt()
                .authorities(new SimpleGrantedAuthority("ROLE_ATLETA"))
                .jwt(j -> j.subject(subjectAtleta.toString())
                        .claim("tenant_id", assessoria.getId().toString())
                        .claim("email", subjectAtleta + "@menthoros.test")
                        .claim("roles", List.of("ATLETA")));
    }
}
