package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.config.core.WorkoutAnalysisProperties;
import br.com.menthoros.backend.dto.llm.AnaliseWorkoutRawDto;
import br.com.menthoros.backend.dto.llm.AthleteMessageDto;
import br.com.menthoros.backend.entity.AnaliseWorkout;
import br.com.menthoros.backend.entity.TreinoRealizado;
import br.com.menthoros.backend.enums.AnaliseStatus;
import br.com.menthoros.backend.enums.PrimaryAnalysisCause;
import br.com.menthoros.backend.events.TreinoRegistradoEvent;
import br.com.menthoros.backend.repository.AiWorkoutAnalysisRepository;
import br.com.menthoros.backend.repository.TreinoRealizadoRepository;
import br.com.menthoros.backend.routing.ModelRouter;
import br.com.menthoros.backend.routing.TaskComplexity;
import br.com.menthoros.backend.services.AthleteMessageClassifier;
import br.com.menthoros.backend.services.AthleteMessageGenerator;
import br.com.menthoros.backend.services.AthleteMessageValidator;
import br.com.menthoros.backend.services.WorkoutAnalysisEligibility;
import br.com.menthoros.backend.services.WorkoutAnalysisPromptDataBuilder;
import br.com.menthoros.backend.services.WorkoutAnalysisTranslator;
import br.com.menthoros.backend.services.prompt.PromptTemplateLoader;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import br.com.menthoros.backend.config.external.MultiModelConfig;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.anthropic.api.AnthropicCacheTtl;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.converter.StructuredOutputConverter;
import org.springframework.core.io.ResourceLoader;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WorkoutAnalysisListenerTest {

    @Mock private TreinoRealizadoRepository treinoRealizadoRepository;
    @Mock private AiWorkoutAnalysisRepository analiseRepository;
    @Mock private ModelRouter modelRouter;
    @Mock private WorkoutAnalysisTranslator translator;
    @Mock private ResourceLoader resourceLoader;
    @Mock private PromptTemplateLoader templateLoader;
    @Mock private WorkoutAnalysisPromptDataBuilder promptDataBuilder;
    @Mock private AthleteMessageGenerator athleteMessageGenerator;
    @Mock private AthleteMessageClassifier athleteMessageClassifier;

    @Spy private MeterRegistry meterRegistry = new SimpleMeterRegistry();

    @Spy private WorkoutAnalysisProperties workoutAnalysisProperties = new WorkoutAnalysisProperties();

    // Reais, não mocks: os guards (sem RPE, idade) e o validador exercitam a regra de verdade.
    @Spy private WorkoutAnalysisEligibility eligibility = new WorkoutAnalysisEligibility(workoutAnalysisProperties);
    @Spy private AthleteMessageValidator athleteMessageValidator = new AthleteMessageValidator();

    @InjectMocks
    private WorkoutAnalysisListener listener;

    private UUID treinoId;
    private UUID tenantId;
    private TreinoRegistradoEvent event;
    private ChatClient.ChatClientRequestSpec requestSpec;
    private ChatClient.CallResponseSpec callSpec;

    @BeforeEach
    void setUp() {
        treinoId = UUID.randomUUID();
        tenantId = UUID.randomUUID();
        event = new TreinoRegistradoEvent(treinoId, tenantId);
    }

    @Test
    void skips_analysis_when_completed_already_exists() {
        when(analiseRepository.existsByTreinoRealizadoIdAndStatus(treinoId, AnaliseStatus.COMPLETED))
                .thenReturn(true);

        listener.onTreinoRegistrado(event);

        verify(treinoRealizadoRepository, never()).findById(any());
        verify(analiseRepository, never()).save(any());
    }

    @Test
    void skips_analysis_when_treino_not_found() {
        when(analiseRepository.existsByTreinoRealizadoIdAndStatus(treinoId, AnaliseStatus.COMPLETED))
                .thenReturn(false);
        when(treinoRealizadoRepository.findByIdAndTenantId(treinoId, tenantId)).thenReturn(Optional.empty());

        listener.onTreinoRegistrado(event);

        verify(analiseRepository, never()).save(any());
    }

    @Test
    void skips_analysis_when_rpe_is_null() {
        TreinoRealizado treino = new TreinoRealizado();
        treino.setPercepcaoEsforco(null);

        when(analiseRepository.existsByTreinoRealizadoIdAndStatus(treinoId, AnaliseStatus.COMPLETED))
                .thenReturn(false);
        when(treinoRealizadoRepository.findByIdAndTenantId(treinoId, tenantId)).thenReturn(Optional.of(treino));

        listener.onTreinoRegistrado(event);

        verify(analiseRepository, never()).save(any());
    }

    @Test
    void skips_analysis_when_treino_older_than_max_idade_dias() {
        workoutAnalysisProperties.setMaxIdadeDias(30);
        TreinoRealizado treino = new TreinoRealizado();
        treino.setPercepcaoEsforco(7);
        treino.setDataTreino(LocalDate.now().minusDays(31));

        when(analiseRepository.existsByTreinoRealizadoIdAndStatus(treinoId, AnaliseStatus.COMPLETED))
                .thenReturn(false);
        when(treinoRealizadoRepository.findByIdAndTenantId(treinoId, tenantId)).thenReturn(Optional.of(treino));

        listener.onTreinoRegistrado(event);

        verify(analiseRepository, never()).save(any());
        verifyNoInteractions(modelRouter);
    }

    @Test
    void analisa_treino_dentro_do_limite_de_idade() {
        workoutAnalysisProperties.setMaxIdadeDias(30);
        TreinoRealizado treino = new TreinoRealizado();
        treino.setPercepcaoEsforco(7);
        treino.setDataTreino(LocalDate.now().minusDays(30));

        AnaliseWorkout saved = new AnaliseWorkout();
        saved.setStatus(AnaliseStatus.PENDING);

        when(analiseRepository.existsByTreinoRealizadoIdAndStatus(treinoId, AnaliseStatus.COMPLETED))
                .thenReturn(false);
        when(treinoRealizadoRepository.findByIdAndTenantId(treinoId, tenantId)).thenReturn(Optional.of(treino));
        when(analiseRepository.findByTreinoRealizadoIdAndTenantId(treinoId, tenantId))
                .thenReturn(Optional.empty());
        when(analiseRepository.save(any())).thenReturn(saved);
        when(modelRouter.route(any())).thenThrow(new RuntimeException("model unavailable"));

        listener.onTreinoRegistrado(event);

        verify(analiseRepository, atLeastOnce()).save(any());
    }

    @Test
    void nao_pula_analise_quando_dataTreino_e_nula() {
        // Defensivo: treino sem dataTreino não deveria existir em produção (CA8 do ingestor), mas
        // o guard de idade não pode presumir "antigo demais" na ausência do dado.
        TreinoRealizado treino = new TreinoRealizado();
        treino.setPercepcaoEsforco(7);

        AnaliseWorkout saved = new AnaliseWorkout();
        saved.setStatus(AnaliseStatus.PENDING);

        when(analiseRepository.existsByTreinoRealizadoIdAndStatus(treinoId, AnaliseStatus.COMPLETED))
                .thenReturn(false);
        when(treinoRealizadoRepository.findByIdAndTenantId(treinoId, tenantId)).thenReturn(Optional.of(treino));
        when(analiseRepository.findByTreinoRealizadoIdAndTenantId(treinoId, tenantId))
                .thenReturn(Optional.empty());
        when(analiseRepository.save(any())).thenReturn(saved);
        when(modelRouter.route(any())).thenThrow(new RuntimeException("model unavailable"));

        listener.onTreinoRegistrado(event);

        verify(analiseRepository, atLeastOnce()).save(any());
    }

    @Test
    void saves_pending_status_before_calling_llm() {
        TreinoRealizado treino = new TreinoRealizado();
        treino.setPercepcaoEsforco(7);

        AnaliseWorkout saved = new AnaliseWorkout();
        saved.setStatus(AnaliseStatus.PENDING);

        when(analiseRepository.existsByTreinoRealizadoIdAndStatus(treinoId, AnaliseStatus.COMPLETED))
                .thenReturn(false);
        when(treinoRealizadoRepository.findByIdAndTenantId(treinoId, tenantId)).thenReturn(Optional.of(treino));
        when(analiseRepository.findByTreinoRealizadoIdAndTenantId(treinoId, tenantId))
                .thenReturn(Optional.empty());
        when(analiseRepository.save(any())).thenReturn(saved);
        // Falha no roteamento do modelo impede a execução completa — aqui validamos só o gate de save PENDING
        when(modelRouter.route(any())).thenThrow(new RuntimeException("model unavailable"));

        listener.onTreinoRegistrado(event);

        // Primeiro save foi PENDING; o segundo deve ser FAILED por erro na chamada ao LLM
        verify(analiseRepository, atLeastOnce()).save(argThat(a ->
                a.getStatus() == AnaliseStatus.PENDING || a.getStatus() == AnaliseStatus.FAILED));
    }

    @Test
    void sets_failed_status_when_llm_call_throws() {
        TreinoRealizado treino = new TreinoRealizado();
        treino.setPercepcaoEsforco(7);

        AnaliseWorkout saved = new AnaliseWorkout();
        saved.setTreinoRealizadoId(treinoId);
        saved.setTenantId(tenantId);

        when(analiseRepository.existsByTreinoRealizadoIdAndStatus(treinoId, AnaliseStatus.COMPLETED))
                .thenReturn(false);
        when(treinoRealizadoRepository.findByIdAndTenantId(treinoId, tenantId)).thenReturn(Optional.of(treino));
        when(analiseRepository.findByTreinoRealizadoIdAndTenantId(treinoId, tenantId))
                .thenReturn(Optional.empty());
        when(analiseRepository.save(any())).thenReturn(saved);
        when(modelRouter.route(any())).thenThrow(new RuntimeException("network error"));

        listener.onTreinoRegistrado(event);

        verify(analiseRepository).save(argThat(a -> a.getStatus() == AnaliseStatus.FAILED));
    }

    // ===== Bloco do atleta (task 1.3) =====

    private static AthleteMessageDto blocoValido() {
        return new AthleteMessageDto(
                "Você segurou o ritmo nos dois blocos de tempo.",
                "Saiu como planejado: 58 min contra 61 previstos.",
                "Um 7 num treino previsto como 6 — pesou um pouco mais que o esperado.",
                "Capriche no sono hoje e vale comentar com seu coach como você acorda amanhã.");
    }

    private AnaliseWorkoutRawDto stubCaminhoCompleto() {
        return stubCaminhoCompleto(PrimaryAnalysisCause.NORMAL);
    }

    private AnaliseWorkoutRawDto stubCaminhoCompleto(PrimaryAnalysisCause causa) {
        TreinoRealizado treino = new TreinoRealizado();
        treino.setPercepcaoEsforco(7);

        when(analiseRepository.existsByTreinoRealizadoIdAndStatus(treinoId, AnaliseStatus.COMPLETED))
                .thenReturn(false);
        when(treinoRealizadoRepository.findByIdAndTenantId(treinoId, tenantId)).thenReturn(Optional.of(treino));
        when(analiseRepository.findByTreinoRealizadoIdAndTenantId(treinoId, tenantId))
                .thenReturn(Optional.empty());
        when(analiseRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(promptDataBuilder.build(treino)).thenReturn("{\"actual\":{\"rpe\":7}}");
        when(templateLoader.loadAndFormat(anyString(), any())).thenReturn("prompt");

        AnaliseWorkoutRawDto raw = new AnaliseWorkoutRawDto(
                "summary", "interpretation", causa,
                "recommendation", null, 8, "rationale");

        ChatClient sonnet = mock(ChatClient.class);
        requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        callSpec = mock(ChatClient.CallResponseSpec.class);
        when(modelRouter.route(TaskComplexity.COMPLEX)).thenReturn(sonnet);
        when(sonnet.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(nullable(String.class))).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.options(any(ChatOptions.class))).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callSpec);
        when(callSpec.entity(any(StructuredOutputConverter.class))).thenReturn(raw);
        when(translator.translate(raw)).thenReturn(raw);
        return raw;
    }

    @Test
    void publica_tenant_do_evento_durante_a_chamada_ao_llm_e_limpa_depois() {
        // add-plan-generation-ledger CA12: sem isso o custo desta chamada ficaria sem assessoria no ledger
        stubCaminhoCompleto();
        java.util.concurrent.atomic.AtomicReference<UUID> vistoDentro = new java.util.concurrent.atomic.AtomicReference<>();
        when(athleteMessageGenerator.gerar(anyString(), any())).thenAnswer(inv -> {
            vistoDentro.set(br.com.menthoros.backend.multitenancy.TenantContext.getTenantId());
            return Optional.empty();
        });

        listener.onTreinoRegistrado(event);

        assertThat(vistoDentro.get()).isEqualTo(tenantId);
        assertThat(br.com.menthoros.backend.multitenancy.TenantContext.getTenantId()).isNull();
    }

    @Test
    void limpa_tenant_mesmo_quando_a_analise_falha() {
        stubCaminhoCompleto();
        when(promptDataBuilder.build(any())).thenThrow(new IllegalStateException("prompt quebrado"));

        listener.onTreinoRegistrado(event);

        assertThat(br.com.menthoros.backend.multitenancy.TenantContext.getTenantId()).isNull();
    }

    @Test
    void limpa_tenant_quando_sai_cedo_sem_treino() {
        when(analiseRepository.existsByTreinoRealizadoIdAndStatus(treinoId, AnaliseStatus.COMPLETED)).thenReturn(false);
        when(treinoRealizadoRepository.findByIdAndTenantId(treinoId, tenantId)).thenReturn(Optional.empty());

        listener.onTreinoRegistrado(event);

        assertThat(br.com.menthoros.backend.multitenancy.TenantContext.getTenantId()).isNull();
    }

    @Test
    void persiste_bloco_do_atleta_quando_valido() {
        AnaliseWorkoutRawDto raw = stubCaminhoCompleto();
        when(athleteMessageGenerator.gerar(anyString(), eq(raw.primaryCause())))
                .thenReturn(Optional.of(blocoValido()));

        listener.onTreinoRegistrado(event);

        // atLeastOnce: o listener salva a MESMA instância no PENDING e no COMPLETED; o matcher
        // reavalia no verify contra o objeto já mutado, então as duas chamadas coincidem.
        verify(analiseRepository, atLeastOnce()).save(argThat(a ->
                a.getStatus() == AnaliseStatus.COMPLETED
                        && blocoValido().howItWent().equals(a.getAtletaComoFoi())
                        && blocoValido().recognition().equals(a.getAtletaReconhecimento())
                        && a.getAtletaBloqueadoMotivo() == null));
    }

    @Test
    void nulifica_bloco_bloqueado_pelo_validador_mantendo_completed() {
        stubCaminhoCompleto();
        AthleteMessageDto comJargao = new AthleteMessageDto(
                blocoValido().recognition(),
                "Seu TSB está em -28, sinal de fadiga.",
                blocoValido().effortReading(),
                blocoValido().nextWorkoutTip());
        when(athleteMessageGenerator.gerar(anyString(), any())).thenReturn(Optional.of(comJargao));

        listener.onTreinoRegistrado(event);

        // atLeastOnce: o listener salva a MESMA instância no PENDING e no COMPLETED; o matcher
        // reavalia no verify contra o objeto já mutado, então as duas chamadas coincidem.
        verify(analiseRepository, atLeastOnce()).save(argThat(a ->
                a.getStatus() == AnaliseStatus.COMPLETED
                        && a.getAtletaComoFoi() == null
                        && AthleteMessageValidator.MOTIVO_JARGAO.equals(a.getAtletaBloqueadoMotivo())
                        && "summary".equals(a.getSummaryPt())));
    }

    @Test
    void nulifica_bloco_marcado_pelo_classificador() {
        stubCaminhoCompleto();
        when(athleteMessageGenerator.gerar(anyString(), any())).thenReturn(Optional.of(blocoValido()));
        when(athleteMessageClassifier.mandaMudarPlano(any())).thenReturn(true);

        listener.onTreinoRegistrado(event);

        verify(analiseRepository, atLeastOnce()).save(argThat(a ->
                a.getStatus() == AnaliseStatus.COMPLETED
                        && a.getAtletaComoFoi() == null
                        && AthleteMessageClassifier.MOTIVO_CLASSIFICADOR.equals(a.getAtletaBloqueadoMotivo())));
    }

    @Test
    void incrementa_contador_quando_causa_nao_normal_e_bloco_gerado() {
        stubCaminhoCompleto(PrimaryAnalysisCause.ACCUMULATED_FATIGUE);
        when(athleteMessageGenerator.gerar(anyString(), any())).thenReturn(Optional.of(blocoValido()));

        listener.onTreinoRegistrado(event);

        assertThat(meterRegistry.counter("atleta_analise_remete_coach_total").count()).isEqualTo(1.0);
    }

    @Test
    void nao_incrementa_contador_quando_causa_normal() {
        stubCaminhoCompleto(PrimaryAnalysisCause.NORMAL);
        when(athleteMessageGenerator.gerar(anyString(), any())).thenReturn(Optional.of(blocoValido()));

        listener.onTreinoRegistrado(event);

        assertThat(meterRegistry.counter("atleta_analise_remete_coach_total").count()).isEqualTo(0.0);
    }

    @Test
    void bloco_ausente_mantem_completed_com_campos_nulos_sem_motivo() {
        stubCaminhoCompleto();
        when(athleteMessageGenerator.gerar(anyString(), any())).thenReturn(Optional.empty());

        listener.onTreinoRegistrado(event);

        // atLeastOnce: o listener salva a MESMA instância no PENDING e no COMPLETED; o matcher
        // reavalia no verify contra o objeto já mutado, então as duas chamadas coincidem.
        verify(analiseRepository, atLeastOnce()).save(argThat(a ->
                a.getStatus() == AnaliseStatus.COMPLETED
                        && a.getAtletaComoFoi() == null
                        && a.getAtletaBloqueadoMotivo() == null
                        && "summary".equals(a.getSummaryPt())));
    }

    @Test
    void resposta_com_chave_repetida_termina_completed_com_o_ultimo_valor() {
        // fix-workout-analysis-duplicate-keys CA1: antes, o record recusava a segunda ocorrência e a
        // análise ficava FAILED.
        stubCaminhoCompleto();
        respostaDoLlm("""
                {"summary":"summary","primary_cause":"NORMAL","execution_score":6,
                 "primary_cause":"PACING_ERROR","execution_score":8}
                """);
        when(translator.translate(any())).thenAnswer(inv -> inv.getArgument(0));
        when(athleteMessageGenerator.gerar(anyString(), any())).thenReturn(Optional.empty());

        listener.onTreinoRegistrado(event);

        verify(analiseRepository, atLeastOnce()).save(argThat(a ->
                a.getStatus() == AnaliseStatus.COMPLETED
                        && Integer.valueOf(8).equals(a.getExecutionScore())
                        && a.getPrimaryCause() == PrimaryAnalysisCause.PACING_ERROR));
    }

    @Test
    void resposta_que_nao_e_json_termina_failed() {
        // CA3: a tolerância é só para chave repetida — texto livre continua falhando.
        stubCaminhoCompleto();
        respostaDoLlm("desculpe, não consegui analisar este treino");

        listener.onTreinoRegistrado(event);

        verify(analiseRepository, atLeastOnce()).save(argThat(a -> a.getStatus() == AnaliseStatus.FAILED));
        verify(analiseRepository, never()).save(argThat(a -> a.getStatus() == AnaliseStatus.COMPLETED));
    }

    @Test
    void chama_o_llm_com_temperatura_baixa_preservando_o_cache_da_rota() {
        // CA5: sem o cacheOptions explícito, o Spring AI 1.1.6 trataria a opção por chamada como
        // cache DISABLED e o system prompt (a skill) deixaria de ser cacheado.
        stubCaminhoCompleto();
        when(athleteMessageGenerator.gerar(anyString(), any())).thenReturn(Optional.empty());

        listener.onTreinoRegistrado(event);

        ArgumentCaptor<ChatOptions> captor = ArgumentCaptor.forClass(ChatOptions.class);
        verify(requestSpec).options(captor.capture());
        assertThat(captor.getValue()).isInstanceOf(AnthropicChatOptions.class);
        AnthropicChatOptions opcoes = (AnthropicChatOptions) captor.getValue();
        assertThat(opcoes.getTemperature()).isEqualTo(0.2);
        assertThat(opcoes.getModel()).isNull();
        assertThat(opcoes.getMaxTokens()).isNull();
        assertThat(opcoes.getCacheOptions().getStrategy())
                .isEqualTo(MultiModelConfig.cacheDoSystemPrompt().getStrategy());
        assertThat(opcoes.getCacheOptions().getMessageTypeTtl())
                .containsEntry(MessageType.SYSTEM, AnthropicCacheTtl.ONE_HOUR);
    }

    private void respostaDoLlm(String texto) {
        when(callSpec.entity(any(StructuredOutputConverter.class))).thenAnswer(inv ->
                ((StructuredOutputConverter<?>) inv.getArgument(0)).convert(texto));
    }
}
