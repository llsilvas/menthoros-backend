package br.com.menthoros.backend.services.prompt;

import br.com.menthoros.backend.services.helper.AthleteZones;
import br.com.menthoros.backend.services.helper.EvalAgreementGrader;
import br.com.menthoros.backend.services.helper.EvalCandidateRunner;
import br.com.menthoros.backend.services.helper.EvalDeterministicGrader;
import br.com.menthoros.backend.services.helper.PlannerShadowService;
import br.com.menthoros.backend.services.helper.SessionResolver;
import br.com.menthoros.backend.services.helper.TreinoHistoricoProvider;
import br.com.menthoros.backend.services.helper.TssCalculatorService;
import br.com.menthoros.backend.services.helper.ZoneResolver;
import br.com.menthoros.backend.services.helper.ZonaTreinoService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Runner de eval (plan-generation-eval-set, task 1.9) — carrega a família de fixture certa por
 * modo (propriedade {@code modo}, default {@code auditoria}) e imprime uma tabela em stdout.
 *
 * <p><b>Modo candidato chama a LLM real</b> (custo real, `OPENAI_API_KEY` obrigatória) — por
 * design, é isso que fecha o bloqueador original do Codex (ver design.md). Roda só sob
 * {@code -Peval}, nunca no inner loop nem no gate de PR.
 */
@Tag("eval")
@DisplayName("EvalRunner")
class EvalRunnerTest {

    private static final Path FIXTURES_AUDITORIA = Path.of("src", "test", "resources", "eval",
            "plan-generation", "auditoria");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    @DisplayName("roda o modo indicado pela propriedade 'modo' (default: auditoria)")
    void rodaModoIndicado() throws Exception {
        String modo = System.getProperty("modo", "auditoria");
        if ("candidato".equals(modo)) {
            rodarModoCandidato();
        } else {
            rodarModoAuditoria();
        }
    }

    private void rodarModoAuditoria() throws IOException {
        var sessionResolver = new SessionResolver(new ZoneResolver(new ZonaTreinoService()),
                new TssCalculatorService());
        var grader = new EvalAgreementGrader(OBJECT_MAPPER, sessionResolver);

        List<Path> arquivos;
        try (Stream<Path> lista = Files.list(FIXTURES_AUDITORIA)) {
            arquivos = lista.filter(p -> p.toString().endsWith(".json")).sorted().toList();
        }

        System.out.println("=== eval set — modo auditoria ===");
        System.out.printf("%-30s %10s %10s%n", "fixture", "comparados", "divergências");
        for (Path arquivo : arquivos) {
            var fixture = OBJECT_MAPPER.readValue(arquivo.toFile(), EvalFixtureAuditoriaView.class);
            var zonas = new AthleteZones(fixture.zonasAtleta().fcMaxima(), fixture.zonasAtleta().fcLimiar(),
                    fixture.zonasAtleta().paceLimiar());
            var resultado = grader.avaliar(fixture.respostaHistoricaJson(), fixture.schemaVersion(), zonas,
                    fixture.planoFinalPersistidoJson());
            System.out.printf("%-30s %10d %10d%n", arquivo.getFileName(),
                    resultado.totalTreinosComparados(), resultado.divergencias().size());
        }
        assertThat(arquivos).isNotEmpty();
    }

    private void rodarModoCandidato() throws Exception {
        String apiKey = System.getenv("OPENAI_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "OPENAI_API_KEY ausente — modo candidato precisa de chamada real à LLM");
        }
        ChatClient chatClient = construirChatClientReal(apiKey);
        var llmJsonSchemaBuilder = new LlmJsonSchemaBuilder();
        var sessionResolver = new SessionResolver(new ZoneResolver(new ZonaTreinoService()),
                new TssCalculatorService());
        var plannerShadowService = mock(PlannerShadowService.class);
        var grader = new EvalDeterministicGrader(OBJECT_MAPPER, sessionResolver, plannerShadowService);
        var runner = new EvalCandidateRunner(chatClient, llmJsonSchemaBuilder, grader);

        System.out.println("=== eval set — modo candidato (LLM real) ===");
        System.out.printf("%-25s %12s %12s%n", "arquétipo", "qualidade", "compliance");
        for (EvalCandidateFixtures.Candidato candidato : EvalCandidateFixtures.todos()) {
            var arq = candidato.arquetipo();
            var prompt = montarPrompt(arq);
            var zonas = new AthleteZones(arq.atleta().getFcMaximaCalculada(), arq.atleta().getFcLimiarCalculada(),
                    arq.atleta().getPaceLimiar());
            var resultado = runner.rodar(prompt, candidato.skeleton(), zonas, arq.atleta(), arq.inicioSemana());
            System.out.printf("%-25s %12d %12d%n", arq.nome(),
                    resultado.avaliacao().violacoesQualidade().size(),
                    resultado.avaliacao().violacoesCompliance().size());
        }
    }

    private PlanoTreinoPromptBuilder.PromptGerado montarPrompt(PlanoPromptArquetipos.Arquetipo arq) {
        TreinoHistoricoProvider provider = mock(TreinoHistoricoProvider.class);
        when(provider.prepararContexto(any())).thenReturn(arq.contexto());
        PlanoTreinoPromptBuilder builder = PlanoPromptArquetipos.builder(provider);

        try (var now = mockStatic(LocalDate.class, CALLS_REAL_METHODS)) {
            now.when(LocalDate::now).thenReturn(PlanoPromptArquetipos.HOJE);
            return builder.buildOptimizedPrompt(
                    arq.atleta(), arq.meta(), arq.prova(), arq.inicioSemana(), arq.diasEfetivos());
        }
    }

    private ChatClient construirChatClientReal(String apiKey) {
        // Timeout de leitura 120s — mesmo valor de app.llm.routing.plano.timeout (application.yml).
        // Sem isso, o RestClient default do Spring AI usa um read timeout curto e a geração de um
        // plano (até 12000 tokens) estoura antes de a resposta voltar.
        var factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(120_000);
        var restClientBuilder = org.springframework.web.client.RestClient.builder().requestFactory(factory);

        OpenAiApi api = OpenAiApi.builder().apiKey(apiKey).restClientBuilder(restClientBuilder).build();
        OpenAiChatModel model = OpenAiChatModel.builder()
                .openAiApi(api)
                .defaultOptions(OpenAiChatOptions.builder().model("gpt-4o").temperature(0.2).maxTokens(12000).build())
                .build();
        return ChatClient.builder(model).build();
    }

    /** Shape mínimo de {@code FixtureAuditoria} lido do disco — evita depender de visibilidade
     * de pacote de {@code EvalFixtureExtractor.FixtureAuditoria} a partir de outro pacote. */
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    private record EvalFixtureAuditoriaView(String respostaHistoricaJson, String schemaVersion,
                                             String planoFinalPersistidoJson, ZonasAtletaView zonasAtleta) {
    }

    private record ZonasAtletaView(Integer fcMaxima, Integer fcLimiar, BigDecimal paceLimiar) {
    }
}
