package br.com.menthoros.backend.services.helper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import br.com.menthoros.backend.ai.ledger.Violacao;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * {@code plan-generation-repair-turn}, seção 1 — passo puro, sem colaboradores: monta a
 * mensagem de correção do turno de reparo a partir das violações completas da tentativa
 * anterior (design.md, Decisão 1 e 6 — JSON completo + violações completas, "corrija isto
 * mantendo o resto").
 */
@DisplayName("RepairTurnMessageBuilder")
class RepairTurnMessageBuilderTest {

    private final RepairTurnMessageBuilder builder = new RepairTurnMessageBuilder();

    @Nested
    @DisplayName("construirCorrecao")
    class ConstruirCorrecao {

        @Test
        @DisplayName("1 violação: chave e mensagem aparecem na correção")
        void umaViolacao() {
            String correcao = builder.construirCorrecao(List.of(
                    new Violacao("NORMALIZACAO_SEGUNDA", "Treino SEGUNDA inválido: gerou apenas 4 etapas (mínimo 6)")));

            assertThat(correcao)
                    .contains("NORMALIZACAO_SEGUNDA")
                    .contains("Treino SEGUNDA inválido: gerou apenas 4 etapas (mínimo 6)")
                    .contains("mantendo o resto");
        }

        @Test
        @DisplayName("N violações: todas aparecem, uma por linha, não só a primeira")
        void nViolacoes() {
            String correcao = builder.construirCorrecao(List.of(
                    new Violacao("NORMALIZACAO_SEGUNDA", "violação da segunda"),
                    new Violacao("NORMALIZACAO_QUINTA", "violação da quinta"),
                    new Violacao("COMPLIANCE_TSS", "violação de TSS")));

            assertThat(correcao)
                    .contains("NORMALIZACAO_SEGUNDA").contains("violação da segunda")
                    .contains("NORMALIZACAO_QUINTA").contains("violação da quinta")
                    .contains("COMPLIANCE_TSS").contains("violação de TSS");
        }

        @Test
        @DisplayName("violação com mensagem vazia não lança e ainda mostra a chave")
        void mensagemVazia() {
            String correcao = builder.construirCorrecao(List.of(new Violacao("NORMALIZACAO_SEGUNDA", "")));

            assertThat(correcao).contains("NORMALIZACAO_SEGUNDA");
        }

        @Test
        @DisplayName("lista vazia não lança — devolve instrução genérica de correção")
        void listaVazia() {
            assertThatNoException().isThrownBy(() -> builder.construirCorrecao(List.of()));

            String correcao = builder.construirCorrecao(List.of());
            assertThat(correcao).isNotBlank();
        }
    }
}
