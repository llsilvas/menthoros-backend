package br.com.menthoros.backend.services.prompt;

import br.com.menthoros.backend.domain.compliance.SkeletonComplianceChecker;
import br.com.menthoros.backend.domain.planner.PlannerEngine;
import br.com.menthoros.backend.domain.planner.WeekPlanSkeleton;
import br.com.menthoros.backend.services.ProgressaoTreinoService;
import br.com.menthoros.backend.services.helper.AthleteZones;
import br.com.menthoros.backend.services.helper.EvalAgreementGrader;
import br.com.menthoros.backend.services.helper.EvalCandidateRunner;
import br.com.menthoros.backend.services.helper.EvalDeterministicGrader;
import br.com.menthoros.backend.services.helper.EvalLlmJudge;
import br.com.menthoros.backend.services.helper.PlannerShadowService;
import br.com.menthoros.backend.services.helper.SessionResolver;
import br.com.menthoros.backend.services.helper.TreinoHistoricoProvider;
import br.com.menthoros.backend.services.helper.TssCalculatorService;
import br.com.menthoros.backend.services.helper.ZoneResolver;
import br.com.menthoros.backend.services.helper.ZonaTreinoService;
import br.com.menthoros.backend.services.prompt.PeriodizacaoPromptFormatter;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
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

    private void rodarModoAuditoria() throws Exception {
        var sessionResolver = new SessionResolver(new ZoneResolver(new ZonaTreinoService()),
                new TssCalculatorService());
        var grader = new EvalAgreementGrader(OBJECT_MAPPER, sessionResolver);
        String apiKey = System.getenv("OPENAI_API_KEY");
        String modelo = System.getenv().getOrDefault("EVAL_MODEL", "gpt-4o");
        EvalLlmJudge judge = apiKey != null && !apiKey.isBlank()
                ? new EvalLlmJudge(construirChatClientReal(apiKey, modelo),
                        new br.com.menthoros.backend.services.prompt.EvalJudgeSchemaBuilder(), OBJECT_MAPPER)
                : null;

        List<Path> arquivos;
        try (Stream<Path> lista = Files.list(FIXTURES_AUDITORIA)) {
            arquivos = lista.filter(p -> p.toString().endsWith(".json")).sorted().toList();
        }

        double custoTotal = 0.0;
        System.out.println("=== eval set — modo auditoria ===");
        System.out.printf("%-25s %10s %10s %10s %8s %14s%n", "fixture", "comparados", "divergên.",
                "não comp.", "juiz", "status juiz");
        for (Path arquivo : arquivos) {
            var fixture = OBJECT_MAPPER.readValue(arquivo.toFile(), EvalFixtureAuditoriaView.class);
            var zonas = new AthleteZones(fixture.zonasAtleta().fcMaxima(), fixture.zonasAtleta().fcLimiar(),
                    fixture.zonasAtleta().paceLimiar());
            var resultado = grader.avaliar(fixture.respostaHistoricaJson(), fixture.schemaVersion(), zonas,
                    fixture.planoFinalPersistidoJson());

            String notaJuiz = "-";
            if (judge != null) {
                var resultadoJuiz = judge.avaliarReduzida(fixture.respostaHistoricaJson());
                notaJuiz = String.valueOf(resultadoJuiz.nota().notaGeral());
                if (resultadoJuiz.custoUsd() != null) {
                    custoTotal += resultadoJuiz.custoUsd().doubleValue();
                }
            }
            // Calibração: sempre "NÃO CALIBRADO" nesta versão — task 2.3, sem 20 casos humanos.
            // treinosNaRespostaNaoComparados: achado do /qa (Codex) — cardinalidade diferente entre
            // resposta e plano final (ex. coach excluiu um treino) não pode ficar invisível na tabela.
            System.out.printf("%-25s %10d %10d %10d %8s %14s%n", arquivo.getFileName(),
                    resultado.totalTreinosComparados(), resultado.divergencias().size(),
                    resultado.treinosNaRespostaNaoComparados(), notaJuiz, "NÃO CALIBRADO");
        }
        System.out.printf("custo total (juiz): USD %.6f%n", custoTotal);
        assertThat(arquivos).isNotEmpty();
    }

    private void rodarModoCandidato() throws Exception {
        String apiKey = System.getenv("OPENAI_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "OPENAI_API_KEY ausente — modo candidato precisa de chamada real à LLM");
        }
        // Achado do /qa (Codex): modelo estava fixo em "gpt-4o", sem seguir app.llm.routing.plano —
        // uma mudança de modelo em produção nunca seria exercitada pelo gate. EVAL_MODEL permite
        // sobrepor; default alinhado com application.yml (app.llm.routing.plano.model).
        String modelo = System.getenv().getOrDefault("EVAL_MODEL", "gpt-4o");
        ChatClient chatClient = construirChatClientReal(apiKey, modelo);
        var llmJsonSchemaBuilder = new LlmJsonSchemaBuilder();
        var sessionResolver = new SessionResolver(new ZoneResolver(new ZonaTreinoService()),
                new TssCalculatorService());
        // PlannerShadowService real (não mock) — achado do /qa (Codex): um mock sem stub sempre
        // devolve lista vazia de PlannerViolation, então a coluna "compliance" nunca refletia o
        // skeleton de verdade. checkPreRedistribution só usa complianceChecker/resolverConstraints
        // (nunca plannerEngine/progressaoTreinoService/periodizacaoPromptFormatter) — os demais
        // colaboradores ficam mockados sem uso real.
        var plannerShadowService = new PlannerShadowService(
                mock(PlannerEngine.class), new SkeletonComplianceChecker(),
                mock(ProgressaoTreinoService.class), mock(PeriodizacaoPromptFormatter.class),
                new SimpleMeterRegistry(), OBJECT_MAPPER, false, 30);
        var grader = new EvalDeterministicGrader(OBJECT_MAPPER, sessionResolver, plannerShadowService);
        var runner = new EvalCandidateRunner(chatClient, llmJsonSchemaBuilder, grader);
        var judge = new EvalLlmJudge(chatClient, new br.com.menthoros.backend.services.prompt.EvalJudgeSchemaBuilder(),
                OBJECT_MAPPER);

        double custoTotal = 0.0;
        System.out.println("=== eval set — modo candidato (LLM real, modelo=" + modelo + ") ===");
        System.out.printf("%-20s %10s %11s %6s %14s%n", "arquétipo", "qualidade", "compliance",
                "juiz", "status juiz");
        for (EvalCandidateFixtures.Candidato candidato : EvalCandidateFixtures.todos()) {
            var arq = candidato.arquetipo();
            // Achado do /qa (Codex): o prompt era montado sem o skeleton (overload de 5 args) mas
            // a avaliação recebia candidato.skeleton() — a LLM nunca via as instruções do skeleton
            // que a compliance depois cobrava. Agora os dois usam o mesmo skeleton.
            var prompt = montarPrompt(arq, candidato.skeleton());
            var zonas = new AthleteZones(arq.atleta().getFcMaximaCalculada(), arq.atleta().getFcLimiarCalculada(),
                    arq.atleta().getPaceLimiar());
            var resultado = runner.rodar(prompt, candidato.skeleton(), zonas, arq.atleta(), arq.inicioSemana());
            if (resultado.custoUsd() != null) {
                custoTotal += resultado.custoUsd().doubleValue();
            }

            // Achado do /qa (Codex): contexto do juiz era só "arquétipo=<nome>" — progressão,
            // segurança/lesão e exequibilidade exigem o histórico/perfil de verdade.
            String contexto = contextoParaJuiz(arq);
            var resultadoJuiz = judge.avaliarCompleta(resultado.responseJson(), contexto);
            if (resultadoJuiz.custoUsd() != null) {
                custoTotal += resultadoJuiz.custoUsd().doubleValue();
            }
            // Calibração: sempre "NÃO CALIBRADO" nesta versão — os 20 casos de auditoria (quando
            // existirem) não validam a rubrica completa, achado da rodada 4 de DoR (Codex).
            System.out.printf("%-20s %10d %11d %6d %14s%n", arq.nome(),
                    resultado.avaliacao().violacoesQualidade().size(),
                    resultado.avaliacao().violacoesCompliance().size(), resultadoJuiz.nota().notaGeral(),
                    "NÃO CALIBRADO");
        }
        System.out.printf("custo total (candidato + juiz): USD %.6f%n", custoTotal);
    }

    private PlanoTreinoPromptBuilder.PromptGerado montarPrompt(PlanoPromptArquetipos.Arquetipo arq,
                                                                @org.jspecify.annotations.Nullable WeekPlanSkeleton skeleton) {
        TreinoHistoricoProvider provider = mock(TreinoHistoricoProvider.class);
        when(provider.prepararContexto(any())).thenReturn(arq.contexto());
        PlanoTreinoPromptBuilder builder = PlanoPromptArquetipos.builder(provider);

        try (var now = mockStatic(LocalDate.class, CALLS_REAL_METHODS)) {
            now.when(LocalDate::now).thenReturn(PlanoPromptArquetipos.HOJE);
            return builder.buildOptimizedPrompt(
                    arq.atleta(), arq.meta(), arq.prova(), arq.inicioSemana(), arq.diasEfetivos(),
                    null, null, skeleton, false);
        }
    }

    /** Contexto real para o juiz-LLM em modo candidato — histórico recente, lesão e prova. */
    private String contextoParaJuiz(PlanoPromptArquetipos.Arquetipo arq) {
        var atleta = arq.atleta();
        StringBuilder sb = new StringBuilder();
        sb.append("Nível: ").append(atleta.getNivelExperiencia()).append('\n');
        if (Boolean.TRUE.equals(atleta.getTemLesao())) {
            sb.append("Lesão ativa: ").append(atleta.getDescricaoLesao()).append('\n');
        }
        if (arq.prova() != null) {
            sb.append("Prova-alvo: ").append(arq.prova().getNomeProva())
                    .append(" em ").append(arq.prova().getDataProva()).append('\n');
        }
        var historico = arq.contexto().treinosUltimas4Semanas();
        sb.append("Treinos recentes: ").append(historico != null ? historico.size() : 0).append(" sessões");
        return sb.toString();
    }

    private ChatClient construirChatClientReal(String apiKey, String modelo) {
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
                .defaultOptions(OpenAiChatOptions.builder().model(modelo).temperature(0.2).maxTokens(12000).build())
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
