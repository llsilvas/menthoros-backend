package br.com.menthoros.backend.services.prompt;

import br.com.menthoros.backend.services.helper.IntervaladoElegibilidadeService;
import br.com.menthoros.backend.services.helper.PaceZoneCalculator;
import br.com.menthoros.backend.services.helper.TreinoHistoricoProvider;
import br.com.menthoros.backend.services.helper.ZonaTreinoService;
import br.com.menthoros.backend.services.impl.MetricasAlertaService;
import br.com.menthoros.backend.services.prompt.PlanoPromptArquetipos.Arquetipo;
import br.com.menthoros.backend.skills.eligibility.IntervaladoElegibilidadeSkill;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.MockedStatic;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.DefaultResourceLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
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
 * <p><b>Determinismo:</b> o builder é montado com colaboradores reais (puros) e apenas o
 * {@link TreinoHistoricoProvider} (acesso a banco) é mockado. {@code LocalDate.now()} é congelado em
 * {@link PlanoPromptArquetipos#HOJE} no escopo do build — sem alterar o código de produção — para
 * neutralizar idade, dias até a prova e validade do teste de pace.</p>
 */
@DisplayName("PlanoTreinoPromptBuilder — golden-master de buildOptimizedPrompt")
class PlanoTreinoPromptBuilderGoldenTest {

    private static final Path GOLDEN_DIR = Path.of("src", "test", "resources", "golden", "plano-prompt");

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
        MetricasAlertaService metricas = new MetricasAlertaService();
        ZonaTreinoService zona = new ZonaTreinoService();

        TreinoHistoricoProvider provider = mock(TreinoHistoricoProvider.class);
        when(provider.prepararContexto(any())).thenReturn(arq.contexto());

        PlanoTreinoPromptBuilder builder = new PlanoTreinoPromptBuilder(
                new ClassPathResource("prompts/plano-treino-prompt.txt"),
                new PromptTemplateLoader(new DefaultResourceLoader()),
                metricas,
                zona,
                provider,
                new MetricasPromptFormatter(),
                new AlertasPromptFormatter(metricas),
                new RecuperacaoPromptFormatter(),
                new PeriodizacaoPromptFormatter(),
                new VariabilidadePromptFormatter(),
                new DisponibilidadePromptFormatter(),
                new IntervaladoElegibilidadeService(new IntervaladoElegibilidadeSkill()),
                new PaceHistoricoFormatter(),
                new PaceZoneCalculator(zona));

        try (MockedStatic<LocalDate> now = mockStatic(LocalDate.class, CALLS_REAL_METHODS)) {
            now.when(LocalDate::now).thenReturn(PlanoPromptArquetipos.HOJE);
            return builder.buildOptimizedPrompt(
                    arq.atleta(), arq.meta(), arq.prova(), arq.inicioSemana(), arq.diasEfetivos());
        }
    }

    /**
     * Compara o prompt com o golden-master versionado.
     *
     * <p>Regeneração explícita: {@code -Dgolden.update=true} reescreve todos os arquivos. Quando o
     * golden ainda não existe (baseline inicial) ele é criado e o teste passa — inspecione e commite.</p>
     */
    private static void assertGolden(String nome, String actual) throws IOException {
        Path file = GOLDEN_DIR.resolve(nome + ".txt");
        boolean update = Boolean.getBoolean("golden.update");

        if (update || Files.notExists(file)) {
            Files.createDirectories(GOLDEN_DIR);
            Files.writeString(file, actual, StandardCharsets.UTF_8);
            System.out.printf("[golden] baseline %s: %s%n", update ? "regenerado" : "criado", file);
            return;
        }

        String expected = Files.readString(file, StandardCharsets.UTF_8);
        assertThat(actual)
                .as("Prompt do arquétipo '%s' divergiu do golden-master (%s). "
                        + "Se a mudança é intencional, regenere com -Dgolden.update=true.", nome, file)
                .isEqualTo(expected);
    }
}
