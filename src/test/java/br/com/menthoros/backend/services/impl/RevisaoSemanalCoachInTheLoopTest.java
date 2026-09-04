package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.enums.ConsumedReviewOutcome;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CA5 — coach-in-the-loop. A revisão semanal é insumo do coach: nunca é exposta ao atleta e nunca
 * altera o plano sozinha.
 *
 * <p>Testes estruturais sobre o código-fonte, e não sobre uma chamada: a garantia que interessa é
 * "nenhuma rota de atleta devolve revisão", que um teste de comportamento sobre uma rota específica
 * não cobriria — bastaria alguém adicionar a próxima rota para o vazamento voltar.
 */
class RevisaoSemanalCoachInTheLoopTest {

    private static final Path CONTROLLERS =
            Path.of("src/main/java/br/com/menthoros/backend/controller");

    @Test
    @DisplayName("nenhum controller de atleta expõe a revisão semanal (CA5)")
    void revisaoNaoVazaAoAtleta() throws IOException {
        try (Stream<Path> arquivos = Files.list(CONTROLLERS)) {
            List<String> vazamentos = arquivos
                    .filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> !p.getFileName().toString().startsWith("Coach"))
                    .filter(RevisaoSemanalCoachInTheLoopTest::mencionaRevisao)
                    .map(p -> p.getFileName().toString())
                    .toList();

            assertThat(vazamentos)
                    .as("apenas controllers Coach* podem devolver a revisão semanal")
                    .isEmpty();
        }
    }

    @Test
    @DisplayName("o endpoint de leitura da revisão é coach-only e read-only (CA5)")
    void endpointCoachOnlyEReadOnly() throws IOException {
        String fonte = Files.readString(CONTROLLERS.resolve("CoachRevisaoSemanalController.java"));

        assertThat(fonte).contains("hasAnyRole");
        assertThat(fonte)
                .as("revisão é read-only: nenhum verbo de mutação no controller")
                .doesNotContain("@PostMapping")
                .doesNotContain("@PutMapping")
                .doesNotContain("@PatchMapping")
                .doesNotContain("@DeleteMapping");
    }

    @Test
    @DisplayName("a geração do plano nunca aprova sozinha por causa da revisão (CA5)")
    void revisaoNaoAprovaPlano() throws IOException {
        // O metodo migrou de PlanoServiceImpl para o persister da fase de escrita
        // (refactor-llm-call-outside-transaction); a garantia e a mesma.
        String fonte = Files.readString(
                Path.of("src/main/java/br/com/menthoros/backend/services/helper/PlanGenerationPersister.java"));

        int inicio = fonte.indexOf("registrarRevisaoConsumida(PlanoSemanal plano");
        assertThat(inicio).as("método da revisão consumida deve existir").isPositive();
        String metodo = fonte.substring(inicio, fonte.indexOf("\n    }", inicio));

        assertThat(metodo)
                .as("o consumo da revisão só grava vínculo e desfecho — nunca transiciona review status")
                .doesNotContain("setReviewStatus")
                .doesNotContain("aprovarTransicao");
        assertThat(metodo).contains(ConsumedReviewOutcome.NOT_CONSUMED.name());
    }

    private static boolean mencionaRevisao(Path arquivo) {
        try {
            String fonte = Files.readString(arquivo);
            return fonte.contains("RevisaoSemanal");
        } catch (IOException e) {
            throw new IllegalStateException("falha ao ler " + arquivo, e);
        }
    }
}
