package br.com.menthoros.backend.services.prompt;

import br.com.menthoros.backend.services.helper.AthleteZones;
import br.com.menthoros.backend.services.helper.EvalLlmJudge;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Bake-off real (chamadas pagas à OpenAI). Fora do CI, do {@code verify} e do perfil {@code eval}:
 * a tag {@code eval-real} só roda quando pedida explicitamente.
 *
 * <pre>
 * OPENAI_API_KEY=... \
 * BAKEOFF_CANDIDATOS='gpt-4o;gpt-6-luna:none;gpt-6-luna:low;gpt-6-luna:medium' \
 * EVAL_JUDGE_MODEL=gpt-4o \
 * ./mvnw test -Dtest=OpenAiSuccessorBakeoffRealTest -Dgroups=eval-real -DexcludedGroups=
 * </pre>
 *
 * <p>{@code BAKEOFF_CANDIDATOS}: lista {@code modelo[:esforço]} separada por {@code ;}. Sem esforço,
 * o candidato roda no perfil clássico ({@code temperature 0.2}); com {@code none} também mantém a
 * temperatura; com qualquer outro esforço, só o esforço. O relatório vai para
 * {@code docs/ia/bakeoff-openai-sucessor-<data>.md}.
 */
@Tag("eval-real")
@DisplayName("OpenAiSuccessorBakeoff (real)")
class OpenAiSuccessorBakeoffRealTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    @DisplayName("roda os candidatos de BAKEOFF_CANDIDATOS e grava o relatório")
    void rodaEGravaRelatorio() throws Exception {
        String apiKey = System.getenv("OPENAI_API_KEY");
        String candidatosEnv = System.getenv("BAKEOFF_CANDIDATOS");
        if (apiKey == null || apiKey.isBlank() || candidatosEnv == null || candidatosEnv.isBlank()) {
            throw new IllegalStateException("OPENAI_API_KEY e BAKEOFF_CANDIDATOS são obrigatórios");
        }
        String modeloJuiz = System.getenv().getOrDefault("EVAL_JUDGE_MODEL", "gpt-4o");

        List<OpenAiSuccessorBakeoff.Candidato> candidatos = new ArrayList<>();
        for (String item : candidatosEnv.split(";")) {
            String[] partes = item.trim().split(":", 2);
            String esforco = partes.length > 1 ? partes[1] : null;
            candidatos.add(new OpenAiSuccessorBakeoff.Candidato(item.trim(),
                    EvalRunnerTest.construirChatClientReal(apiKey, partes[0], esforco)));
        }

        var juiz = new EvalLlmJudge(EvalRunnerTest.construirChatClientReal(apiKey, modeloJuiz, null),
                new EvalJudgeSchemaBuilder(), OBJECT_MAPPER);

        List<OpenAiSuccessorBakeoff.Caso> casos = EvalCandidateFixtures.todos().stream().map(c -> {
            var arq = c.arquetipo();
            var zonas = new AthleteZones(arq.atleta().getFcMaximaCalculada(), arq.atleta().getFcLimiarCalculada(),
                    arq.atleta().getPaceLimiar());
            return new OpenAiSuccessorBakeoff.Caso(arq.nome(), EvalRunnerTest.montarPrompt(arq, c.skeleton()),
                    c.skeleton(), zonas, arq.atleta(), arq.inicioSemana(), EvalRunnerTest.contextoParaJuiz(arq));
        }).toList();

        String markdown = OpenAiSuccessorBakeoff.rodar(candidatos, casos, juiz, modeloJuiz,
                EvalRunnerTest.construirGraderReal());

        Path destino = Path.of("docs", "ia", "bakeoff-openai-sucessor-" + LocalDate.now() + ".md");
        Files.createDirectories(destino.getParent());
        Files.writeString(destino, markdown);
        System.out.println(markdown);
        assertThat(destino).exists();
    }
}
