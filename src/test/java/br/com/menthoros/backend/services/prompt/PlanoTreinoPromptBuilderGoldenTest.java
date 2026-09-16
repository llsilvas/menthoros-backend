package br.com.menthoros.backend.services.prompt;

import br.com.menthoros.backend.services.helper.TreinoHistoricoProvider;
import br.com.menthoros.backend.services.prompt.PlanoPromptArquetipos.Arquetipo;
import br.com.menthoros.backend.ai.ledger.PromptHashCalculator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.MockedStatic;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Golden-master de {@link PlanoTreinoPromptBuilder#buildOptimizedPrompt}.
 *
 * <p>Congela a saída do prompt para um conjunto de arquétipos de atleta. É a rede de regressão
 * da thread de modernização de IA: qualquer mudança não-intencional no texto do prompt faz o teste
 * falhar; mudanças intencionais regeneram os golden-masters com {@code -Dgolden.update=true}.</p>
 *
 * <p><b>Determinismo:</b> o builder é montado com colaboradores reais (puros) via
 * {@link PlanoPromptArquetipos#builder} e apenas o {@link TreinoHistoricoProvider} (acesso a banco) é
 * mockado. {@code LocalDate.now()} é congelado em {@link PlanoPromptArquetipos#HOJE} no escopo do
 * build — sem alterar o código de produção — e o {@link Locale} é fixado em pt-BR para que os
 * separadores decimais dos {@code String.format} sejam estáveis independentemente do ambiente de CI.</p>
 */
@DisplayName("PlanoTreinoPromptBuilder — golden-master de buildOptimizedPrompt")
class PlanoTreinoPromptBuilderGoldenTest {

    /** Diretório-fonte (versionado) onde as baselines são gravadas para commit. */
    private static final Path SRC_GOLDEN_DIR = Path.of("src", "test", "resources", "golden", "plano-prompt");
    /** Prefixo no classpath de onde as baselines são lidas (robusto a CWD). */
    private static final String CP_GOLDEN_PREFIX = "golden/plano-prompt/";

    private static Locale localeOriginal;

    @BeforeAll
    static void fixarLocale() {
        localeOriginal = Locale.getDefault();
        Locale.setDefault(Locale.forLanguageTag("pt-BR"));
    }

    @AfterAll
    static void restaurarLocale() {
        Locale.setDefault(localeOriginal);
    }

    static Stream<Arquetipo> arquetipos() {
        return PlanoPromptArquetipos.todos().stream();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("arquetipos")
    @DisplayName("user do arquétipo bate com o golden-master; system é byte-idêntico entre arquétipos (CA1)")
    void promptCongeladoBateComGolden(Arquetipo arq) throws IOException {
        PlanoTreinoPromptBuilder.PromptGerado gerado = montarPrompt(arq);
        // As duas partes são checadas juntas (achado do Codex no QA): comparar/gravar uma de cada
        // vez com fail-rápido deixava -Dgolden.update=true regenerar só "system" e nunca alcançar
        // o ".user" da mesma invocação — cada rodada da flag reescrevia e falhava na 1ª chamada.
        assertGoldenTodos(Map.of("system", gerado.system(), arq.nome() + ".user", gerado.user()));
    }

    private PlanoTreinoPromptBuilder.PromptGerado montarPrompt(Arquetipo arq) {
        TreinoHistoricoProvider provider = mock(TreinoHistoricoProvider.class);
        when(provider.prepararContexto(any())).thenReturn(arq.contexto());

        PlanoTreinoPromptBuilder builder = PlanoPromptArquetipos.builder(provider);

        try (MockedStatic<LocalDate> now = mockStatic(LocalDate.class, CALLS_REAL_METHODS)) {
            now.when(LocalDate::now).thenReturn(PlanoPromptArquetipos.HOJE);
            return builder.buildOptimizedPrompt(
                    arq.atleta(), arq.meta(), arq.prova(), arq.inicioSemana(), arq.diasEfetivos());
        }
    }

    /**
     * Compara (ou regenera) TODAS as partes de um arquétipo numa só passada, para que
     * {@code -Dgolden.update=true} regenere {@code system} e {@code <arquetipo>.user} juntos —
     * comparar/falhar uma parte por vez faria a JVM abortar o método no primeiro {@code fail()},
     * nunca alcançando a segunda parte (achado do Codex no QA, 2026-09-13).
     *
     * <p>Modo de regressão (padrão): assert estrito por parte; se alguma baseline estiver ausente,
     * o teste grava as ausentes e <b>falha</b> (nunca passa em silêncio sem comparar tudo).
     * Regeneração explícita: {@code -Dgolden.update=true} reescreve todas as partes do mapa e
     * <b>falha</b> propositalmente, forçando rodar de novo sem a flag.</p>
     */
    private static void assertGoldenTodos(Map<String, String> partes) throws IOException {
        if (Boolean.getBoolean("golden.update")) {
            for (var entry : partes.entrySet()) {
                gravarBaseline(entry.getKey(), entry.getValue());
            }
            fail("[golden] baselines regeneradas: %s. Remova -Dgolden.update=true e rode novamente."
                    .formatted(partes.keySet()));
        }

        List<String> ausentes = new ArrayList<>();
        for (var entry : partes.entrySet()) {
            if (!new ClassPathResource(CP_GOLDEN_PREFIX + entry.getKey() + ".txt").exists()) {
                gravarBaseline(entry.getKey(), entry.getValue());
                ausentes.add(entry.getKey());
            }
        }
        if (!ausentes.isEmpty()) {
            fail("[golden] baseline ausente para %s. Gerada(s) em %s — inspecione, commite e rode novamente."
                    .formatted(ausentes, SRC_GOLDEN_DIR));
        }

        for (var entry : partes.entrySet()) {
            String expected = new ClassPathResource(CP_GOLDEN_PREFIX + entry.getKey() + ".txt")
                    .getContentAsString(StandardCharsets.UTF_8);
            assertThat(entry.getValue())
                    .as("Prompt da parte '%s' divergiu do golden-master. "
                            + "Se a mudança é intencional, regenere com -Dgolden.update=true.", entry.getKey())
                    .isEqualTo(expected);
        }
    }

    private static void gravarBaseline(String nome, String conteudo) throws IOException {
        Files.createDirectories(SRC_GOLDEN_DIR);
        Files.writeString(SRC_GOLDEN_DIR.resolve(nome + ".txt"), conteudo, StandardCharsets.UTF_8);
        System.out.printf("[golden] baseline gravada: %s%n", SRC_GOLDEN_DIR.resolve(nome + ".txt"));
        gravarHashDoTemplate();
    }

    /**
     * Regenera {@code prompt.sha256} junto da baseline (add-plan-generation-ledger, D9): o hash do
     * template estático viaja em cada Chamada LLM, e {@code PromptHashCalculatorTest} falha se o
     * template mudar sem este arquivo acompanhar — o sinal de que falta subir {@code PromptVersion}.
     */
    private static void gravarHashDoTemplate() throws IOException {
        String template = new ClassPathResource("prompts/plano-treino-system.txt")
                .getContentAsString(StandardCharsets.UTF_8);
        Files.writeString(SRC_GOLDEN_DIR.resolve("prompt.sha256"),
                PromptHashCalculator.sha256(template) + System.lineSeparator(), StandardCharsets.UTF_8);
    }
}
