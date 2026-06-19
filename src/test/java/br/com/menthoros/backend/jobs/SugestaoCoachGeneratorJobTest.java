package br.com.menthoros.backend.jobs;

import br.com.menthoros.backend.dto.output.CoachAttentionItemOutputDto;
import br.com.menthoros.backend.dto.output.RecommendationExplanation;
import br.com.menthoros.backend.entity.Assessoria;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.SugestaoCoach;
import br.com.menthoros.backend.enums.ExplanationConfidence;
import br.com.menthoros.backend.enums.MotivoAtencao;
import br.com.menthoros.backend.enums.Severidade;
import br.com.menthoros.backend.enums.StatusSugestao;
import br.com.menthoros.backend.enums.TipoSugestao;
import br.com.menthoros.backend.multitenancy.TenantContext;
import br.com.menthoros.backend.repository.AssessoriaRepository;
import br.com.menthoros.backend.repository.AtletaRepository;
import br.com.menthoros.backend.repository.SugestaoCoachRepository;
import br.com.menthoros.backend.services.CoachAttentionQueueService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SugestaoCoachGeneratorJobTest {

    @Mock private AssessoriaRepository assessoriaRepository;
    @Mock private CoachAttentionQueueService coachAttentionQueueService;
    @Mock private SugestaoCoachRepository sugestaoCoachRepository;
    @Mock private AtletaRepository atletaRepository;
    @Mock private ObjectMapper objectMapper;

    @InjectMocks private SugestaoCoachGeneratorJob job;

    private UUID tenantId;
    private UUID atletaId;
    private Assessoria assessoria;
    private Atleta atleta;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        atletaId = UUID.randomUUID();
        assessoria = Assessoria.builder().id(tenantId).nome("Assessoria Teste").build();
        atleta = Atleta.builder().id(atletaId).build();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Nested
    @DisplayName("gerarSugestoes")
    class GerarSugestoes {

        @Test
        @DisplayName("cria sugestão para sinal CRITICA com confidence HIGH e tipo RECOVERY")
        void criaParaSinalCritica() throws JsonProcessingException {
            CoachAttentionItemOutputDto item = itemDto(Severidade.CRITICA, MotivoAtencao.FADIGA, null);
            when(assessoriaRepository.findByAtivoTrue()).thenReturn(List.of(assessoria));
            when(coachAttentionQueueService.getAttentionQueue()).thenReturn(List.of(item));
            when(atletaRepository.findById(atletaId)).thenReturn(Optional.of(atleta));

            job.gerarSugestoes();

            ArgumentCaptor<SugestaoCoach> captor = ArgumentCaptor.forClass(SugestaoCoach.class);
            verify(sugestaoCoachRepository).save(captor.capture());
            SugestaoCoach salva = captor.getValue();
            assertThat(salva.getTipo()).isEqualTo(TipoSugestao.RECOVERY);
            assertThat(salva.getStatus()).isEqualTo(StatusSugestao.PENDING);
            assertThat(salva.getConfidence()).isEqualTo("HIGH");
            assertThat(salva.getSummary()).isEqualTo(MotivoAtencao.FADIGA.getSuggestedAction());
            assertThat(salva.getTenantId()).isEqualTo(tenantId);
            assertThat(salva.getExpiresAt()).isAfter(Instant.now());
        }

        @Test
        @DisplayName("cria sugestão para sinal ALTA com confidence MEDIUM e tipo NEW_PLAN")
        void criaParaSinalAlta() throws JsonProcessingException {
            CoachAttentionItemOutputDto item = itemDto(Severidade.ALTA, MotivoAtencao.SEM_PLANO, null);
            when(assessoriaRepository.findByAtivoTrue()).thenReturn(List.of(assessoria));
            when(coachAttentionQueueService.getAttentionQueue()).thenReturn(List.of(item));
            when(atletaRepository.findById(atletaId)).thenReturn(Optional.of(atleta));

            job.gerarSugestoes();

            ArgumentCaptor<SugestaoCoach> captor = ArgumentCaptor.forClass(SugestaoCoach.class);
            verify(sugestaoCoachRepository).save(captor.capture());
            assertThat(captor.getValue().getConfidence()).isEqualTo("MEDIUM");
            assertThat(captor.getValue().getTipo()).isEqualTo(TipoSugestao.NEW_PLAN);
        }

        @Test
        @DisplayName("ignora sinal com severidade MEDIA — save nunca chamado")
        void ignoraSinalMedia() {
            CoachAttentionItemOutputDto item = itemDto(Severidade.MEDIA, MotivoAtencao.ADERENCIA, null);
            when(assessoriaRepository.findByAtivoTrue()).thenReturn(List.of(assessoria));
            when(coachAttentionQueueService.getAttentionQueue()).thenReturn(List.of(item));

            job.gerarSugestoes();

            verifyNoInteractions(sugestaoCoachRepository);
        }

        @Test
        @DisplayName("idempotência: DataIntegrityViolationException é capturada silenciosamente")
        void idempotenciaCapturaDuplicata() throws JsonProcessingException {
            CoachAttentionItemOutputDto item = itemDto(Severidade.CRITICA, MotivoAtencao.FADIGA, null);
            when(assessoriaRepository.findByAtivoTrue()).thenReturn(List.of(assessoria));
            when(coachAttentionQueueService.getAttentionQueue()).thenReturn(List.of(item));
            when(atletaRepository.findById(atletaId)).thenReturn(Optional.of(atleta));
            when(sugestaoCoachRepository.save(any())).thenThrow(new DataIntegrityViolationException("uk_sugestao_pending"));

            assertThatNoException().isThrownBy(() -> job.gerarSugestoes());
            verify(sugestaoCoachRepository).save(any());
        }

        @Test
        @DisplayName("TenantContext limpo no finally mesmo quando getAttentionQueue lança exceção")
        void tenantContextLimpoNoFinally() {
            when(assessoriaRepository.findByAtivoTrue()).thenReturn(List.of(assessoria));
            when(coachAttentionQueueService.getAttentionQueue()).thenThrow(new RuntimeException("falha simulada"));

            assertThatNoException().isThrownBy(() -> job.gerarSugestoes());
            assertThat(TenantContext.getTenantId()).isNull();
        }

        @Test
        @DisplayName("serializa RecommendationExplanation no reasoningJson quando explanation presente")
        void serializaReasoningQuandoPresente() throws JsonProcessingException {
            RecommendationExplanation explanation = new RecommendationExplanation(
                    "TSB muito negativo", List.of("RecoveryCargaSkill.FADIGA_EXTREMA"), ExplanationConfidence.HIGH);
            CoachAttentionItemOutputDto item = itemDto(Severidade.CRITICA, MotivoAtencao.FADIGA, explanation);
            when(assessoriaRepository.findByAtivoTrue()).thenReturn(List.of(assessoria));
            when(coachAttentionQueueService.getAttentionQueue()).thenReturn(List.of(item));
            when(atletaRepository.findById(atletaId)).thenReturn(Optional.of(atleta));
            when(objectMapper.writeValueAsString(explanation)).thenReturn("{\"rationale\":\"TSB muito negativo\"}");

            job.gerarSugestoes();

            ArgumentCaptor<SugestaoCoach> captor = ArgumentCaptor.forClass(SugestaoCoach.class);
            verify(sugestaoCoachRepository).save(captor.capture());
            assertThat(captor.getValue().getReasoningJson()).isEqualTo("{\"rationale\":\"TSB muito negativo\"}");
            verify(objectMapper).writeValueAsString(eq(explanation));
        }

        @Test
        @DisplayName("reasoningJson é null quando explanation é null — objectMapper não chamado")
        void reasoningNullQuandoSemExplanation() throws JsonProcessingException {
            CoachAttentionItemOutputDto item = itemDto(Severidade.ALTA, MotivoAtencao.ZONAS_VENCIDAS, null);
            when(assessoriaRepository.findByAtivoTrue()).thenReturn(List.of(assessoria));
            when(coachAttentionQueueService.getAttentionQueue()).thenReturn(List.of(item));
            when(atletaRepository.findById(atletaId)).thenReturn(Optional.of(atleta));

            job.gerarSugestoes();

            ArgumentCaptor<SugestaoCoach> captor = ArgumentCaptor.forClass(SugestaoCoach.class);
            verify(sugestaoCoachRepository).save(captor.capture());
            assertThat(captor.getValue().getReasoningJson()).isNull();
            verifyNoInteractions(objectMapper);
        }

        @Test
        @DisplayName("ignora atleta não encontrado no repositório — sem exceção")
        void ignoraAtletaNaoEncontrado() {
            CoachAttentionItemOutputDto item = itemDto(Severidade.CRITICA, MotivoAtencao.FADIGA, null);
            when(assessoriaRepository.findByAtivoTrue()).thenReturn(List.of(assessoria));
            when(coachAttentionQueueService.getAttentionQueue()).thenReturn(List.of(item));
            when(atletaRepository.findById(atletaId)).thenReturn(Optional.empty());

            assertThatNoException().isThrownBy(() -> job.gerarSugestoes());
            verifyNoInteractions(sugestaoCoachRepository);
        }
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private CoachAttentionItemOutputDto itemDto(Severidade severidade, MotivoAtencao motivo,
                                                 RecommendationExplanation explanation) {
        return new CoachAttentionItemOutputDto(
                atletaId,
                "Atleta Teste",
                severidade,
                100,
                motivo,
                motivo.getSuggestedAction(),
                Instant.now(),
                List.of(),
                explanation
        );
    }
}
