package br.com.menthoros.backend.services.prompt;

import br.com.menthoros.backend.services.helper.TreinoHistoricoProvider;
import br.com.menthoros.backend.services.prompt.PlanoPromptArquetipos.Arquetipo;
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
import java.util.Locale;
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
    @DisplayName("prompt do arquétipo bate com o golden-master")
    void promptCongeladoBateComGolden(Arquetipo arq) throws IOException {
        String prompt = montarPrompt(arq);
        assertGolden(arq.nome(), prompt);
    }

    private String montarPrompt(Arquetipo arq) {
        TreinoHistoricoProvider provider = mock(TreinoHistoricoProvider.class);
        when(provider.prepararContexto(any())).thenReturn(arq.contexto());

        PlanoTreinoPromptBuilder builder = PlanoPromptArquetipos.builder(provider);

        try (MockedStatic<LocalDate> now = mockStatic(LocalDate.class, CALLS_REAL_METHODS)) {
            now.when(LocalDate::now).thenReturn(PlanoPromptArquetipos.HOJE);
            return builder.buildOptimizedPrompt(
                    arq.atleta(), arq.meta(), arq.prova(), arq.inicioSemana(), arq.diasEfetivos()).prompt();
        }
    }

    /**
     * Compara o prompt com o golden-master versionado (lido do classpath).
     *
     * <p>Modo de regressão (padrão): assert estrito; se o golden estiver ausente, o teste
     * <b>falha</b> (a baseline é gravada em {@link #SRC_GOLDEN_DIR} para facilitar o commit, mas o
     * teste nunca passa em silêncio sem comparar). Regeneração explícita: {@code -Dgolden.update=true}
     * reescreve a baseline e <b>falha</b> propositalmente, forçando rodar de novo sem a flag.</p>
     */
    private static void assertGolden(String nome, String actual) throws IOException {
        if (Boolean.getBoolean("golden.update")) {
            gravarBaseline(nome, actual);
            fail("[golden] baseline de '%s' regenerada. Remova -Dgolden.update=true e rode novamente.".formatted(nome));
        }

        ClassPathResource golden = new ClassPathResource(CP_GOLDEN_PREFIX + nome + ".txt");
        if (!golden.exists()) {
            gravarBaseline(nome, actual);
            fail("[golden] baseline ausente para '%s'. Gerada em %s — inspecione, commite e rode novamente."
                    .formatted(nome, SRC_GOLDEN_DIR.resolve(nome + ".txt")));
        }

        String expected = new String(golden.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(actual)
                .as("Prompt do arquétipo '%s' divergiu do golden-master. "
                        + "Se a mudança é intencional, regenere com -Dgolden.update=true.", nome)
                .isEqualTo(expected);
    }

    private static void gravarBaseline(String nome, String conteudo) throws IOException {
        Files.createDirectories(SRC_GOLDEN_DIR);
        Files.writeString(SRC_GOLDEN_DIR.resolve(nome + ".txt"), conteudo, StandardCharsets.UTF_8);
        System.out.printf("[golden] baseline gravada: %s%n", SRC_GOLDEN_DIR.resolve(nome + ".txt"));
    }
}
