package br.com.menthoros.backend.services.prompt;

import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.domain.planner.WeekPlanSkeleton;
import br.com.menthoros.backend.services.helper.AthleteZones;
import br.com.menthoros.backend.services.helper.EvalCandidateRunner;
import br.com.menthoros.backend.services.helper.EvalDeterministicGrader;
import br.com.menthoros.backend.services.helper.EvalLlmJudge;
import org.jspecify.annotations.Nullable;
import org.springframework.ai.chat.client.ChatClient;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Bake-off OpenAI → OpenAI (migrate-openai-retired-models, D3): roda as mesmas fixtures de candidato
 * em N configurações de modelo/esforço e devolve uma tabela markdown com uma linha por candidato.
 *
 * <p>Cada candidato traz o {@link ChatClient} já montado sobre um {@code OpenAiChatModel} cujo
 * {@code defaultOptions} é o perfil dele — o runner chama {@code .options(schema)} por requisição,
 * que substitui os defaults do {@code ChatClient}; só o default do model sobrevive (design D1.1).
 *
 * <p>O juiz é um só para todos os candidatos e vai no cabeçalho: comparar notas de juízes
 * diferentes não vale (CA7). Falha de um caso (400, timeout, JSON inválido) conta em "falhas" e
 * não derruba a rodada — um modelo que falha em schema estrito precisa aparecer na tabela.
 */
final class OpenAiSuccessorBakeoff {

    private OpenAiSuccessorBakeoff() {
    }

    record Candidato(String nome, ChatClient cliente) {
    }

    record Caso(String nome, PlanoTreinoPromptBuilder.PromptGerado prompt,
                @Nullable WeekPlanSkeleton skeleton, AthleteZones zonas, Atleta atleta,
                LocalDate semanaInicio, String contextoJuiz) {
    }

    private static final class Acumulador {
        int casos;
        int falhas;
        int violacoesQualidade;
        int violacoesCompliance;
        int notasJuiz;
        int somaNotasJuiz;
        long latenciaMs;
        BigDecimal custoUsd = BigDecimal.ZERO;
        final Set<String> modelosServidos = new LinkedHashSet<>();
    }

    static String rodar(List<Candidato> candidatos, List<Caso> casos, EvalLlmJudge juiz, String modeloJuiz,
                        EvalDeterministicGrader grader) {
        StringBuilder md = new StringBuilder();
        md.append("# Bake-off OpenAI — sucessor do gpt-4o\n\n");
        md.append("Juiz: `").append(modeloJuiz).append("` (o mesmo para todos os candidatos)\n\n");
        md.append("Casos por candidato: ").append(casos.size()).append("\n\n");
        md.append("| candidato | modelo servido | violações qualidade | violações compliance "
                + "| juiz (média) | latência média (s) | custo total (USD) | falhas |\n");
        md.append("|---|---|---|---|---|---|---|---|\n");
        for (Candidato candidato : candidatos) {
            Acumulador acc = medir(candidato, casos, juiz, grader);
            md.append(linha(candidato.nome(), acc));
        }
        return md.toString();
    }

    private static Acumulador medir(Candidato candidato, List<Caso> casos, EvalLlmJudge juiz,
                                    EvalDeterministicGrader grader) {
        var runner = new EvalCandidateRunner(candidato.cliente(), new LlmJsonSchemaBuilder(), grader);
        Acumulador acc = new Acumulador();
        for (Caso caso : casos) {
            acc.casos++;
            long inicio = System.nanoTime();
            EvalCandidateRunner.ResultadoCandidato resultado;
            try {
                resultado = runner.rodar(caso.prompt(), caso.skeleton(), caso.zonas(), caso.atleta(),
                        caso.semanaInicio());
            } catch (RuntimeException e) {
                acc.falhas++;
                continue;
            }
            acc.latenciaMs += Duration.ofNanos(System.nanoTime() - inicio).toMillis();
            acc.violacoesQualidade += resultado.avaliacao().violacoesQualidade().size();
            acc.violacoesCompliance += resultado.avaliacao().violacoesCompliance().size();
            if (resultado.custoUsd() != null) {
                acc.custoUsd = acc.custoUsd.add(resultado.custoUsd());
            }
            if (resultado.modeloRetornado() != null) {
                acc.modelosServidos.add(resultado.modeloRetornado());
            }
            try {
                var nota = juiz.avaliarCompleta(resultado.responseJson(), caso.contextoJuiz());
                acc.notasJuiz++;
                acc.somaNotasJuiz += nota.nota().notaGeral();
                if (nota.custoUsd() != null) {
                    acc.custoUsd = acc.custoUsd.add(nota.custoUsd());
                }
            } catch (RuntimeException e) {
                acc.falhas++;
            }
        }
        return acc;
    }

    private static String linha(String nome, Acumulador acc) {
        int medidos = acc.casos - acc.falhas;
        String juiz = acc.notasJuiz == 0 ? "-"
                : String.format(Locale.ROOT, "%.2f", (double) acc.somaNotasJuiz / acc.notasJuiz);
        String latencia = acc.casos == 0 || acc.latenciaMs == 0 ? "-"
                : String.format(Locale.ROOT, "%.1f", acc.latenciaMs / 1000.0 / Math.max(1, medidos));
        List<String> colunas = new ArrayList<>();
        colunas.add(nome);
        colunas.add(acc.modelosServidos.isEmpty() ? "-" : String.join(", ", acc.modelosServidos));
        colunas.add(String.valueOf(acc.violacoesQualidade));
        colunas.add(String.valueOf(acc.violacoesCompliance));
        colunas.add(juiz);
        colunas.add(latencia);
        colunas.add(acc.custoUsd.toPlainString());
        colunas.add(String.valueOf(acc.falhas));
        return "| " + String.join(" | ", colunas) + " |\n";
    }
}
