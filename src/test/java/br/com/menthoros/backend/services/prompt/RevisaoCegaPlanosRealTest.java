package br.com.menthoros.backend.services.prompt;

import br.com.menthoros.backend.services.helper.AthleteZones;
import br.com.menthoros.backend.services.helper.EvalCandidateRunner;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Gera os planos da revisão humana cega (migrate-openai-retired-models, task 3.5): cada arquétipo do
 * eval set, {@code REVISAO_REPETICOES} vezes, em cada candidato. Grava o JSON de cada plano e o
 * contexto do atleta em {@code target/revisao-cega/}; o markdown cego é montado por
 * {@code docs/ia/revisao-cega-planos-montar.py}. Chamadas pagas, tag {@code eval-real}.
 *
 * <pre>
 * OPENAI_API_KEY=... REVISAO_CANDIDATOS='gpt-4o;gpt-6-luna:medium' REVISAO_REPETICOES=3 \
 * ./mvnw test -Peval-real -Dtest=RevisaoCegaPlanosRealTest
 * </pre>
 */
@Tag("eval-real")
@DisplayName("Revisão cega de planos (real)")
class RevisaoCegaPlanosRealTest {

    @Test
    @DisplayName("grava o plano de cada arquétipo, repetição e candidato")
    void gravaPlanos() throws Exception {
        String apiKey = System.getenv("OPENAI_API_KEY");
        String candidatos = System.getenv("REVISAO_CANDIDATOS");
        if (apiKey == null || apiKey.isBlank() || candidatos == null || candidatos.isBlank()) {
            throw new IllegalStateException("OPENAI_API_KEY e REVISAO_CANDIDATOS são obrigatórios");
        }
        int repeticoes = Integer.parseInt(System.getenv().getOrDefault("REVISAO_REPETICOES", "1"));
        Path saida = Path.of("target", "revisao-cega");
        Files.createDirectories(saida);
        var grader = EvalRunnerTest.construirGraderReal();

        for (var c : EvalCandidateFixtures.todos()) {
            var arq = c.arquetipo();
            Files.writeString(saida.resolve(arq.nome() + ".contexto.txt"), EvalRunnerTest.contextoParaJuiz(arq));
            var prompt = EvalRunnerTest.montarPrompt(arq, c.skeleton());
            var zonas = new AthleteZones(arq.atleta().getFcMaximaCalculada(), arq.atleta().getFcLimiarCalculada(),
                    arq.atleta().getPaceLimiar());
            for (String item : candidatos.split(";")) {
                String[] partes = item.trim().split(":", 2);
                var runner = new EvalCandidateRunner(
                        EvalRunnerTest.construirChatClientReal(apiKey, partes[0], partes.length > 1 ? partes[1] : null),
                        new LlmJsonSchemaBuilder(), grader);
                for (int r = 1; r <= repeticoes; r++) {
                    var resultado = runner.rodar(prompt, c.skeleton(), zonas, arq.atleta(), arq.inicioSemana());
                    Files.writeString(saida.resolve(arq.nome() + "." + item.trim().replace(':', '_') + "." + r + ".json"),
                            resultado.responseJson());
                }
            }
        }
        assertThat(saida).isDirectoryContaining(p -> p.toString().endsWith(".json"));
    }
}
