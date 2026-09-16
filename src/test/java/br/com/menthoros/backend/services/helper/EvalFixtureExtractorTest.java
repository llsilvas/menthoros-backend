package br.com.menthoros.backend.services.helper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("EvalFixtureExtractor")
class EvalFixtureExtractorTest {

    private final EvalFixtureExtractor extractor = new EvalFixtureExtractor(new EvalPiiRedactor());

    @Nested
    @DisplayName("estratificarERedigir")
    class EstratificarERedigir {

        @Test
        @DisplayName("distribui a amostra entre os grupos observados (round-robin), até o alvo")
        void distribuiEntreGrupos() {
            List<EvalFixtureExtractor.CandidatoAmostra> candidatos = List.of(
                    candidato("iniciante", true, "APROVADO", "c1"),
                    candidato("iniciante", true, "APROVADO", "c2"),
                    candidato("iniciante", true, "APROVADO", "c3"),
                    candidato("avancado", false, "REJEITADO", "c4"),
                    candidato("avancado", false, "REJEITADO", "c5"));

            var fixtures = extractor.estratificarERedigir(candidatos, 4);

            assertThat(fixtures).hasSize(4);
            long doGrupoIniciante = fixtures.stream().filter(f -> f.arquetipo().equals("iniciante")).count();
            long doGrupoAvancado = fixtures.stream().filter(f -> f.arquetipo().equals("avancado")).count();
            assertThat(doGrupoIniciante).isEqualTo(2);
            assertThat(doGrupoAvancado).isEqualTo(2);
        }

        @Test
        @DisplayName("redige PII na resposta histórica e no plano final persistido")
        void redigePiiNosDoisBlocos() {
            var alvo = new EvalPiiRedactor.PiiAlvo("Maria Souza", null, null, null, null);
            var candidato = new EvalFixtureExtractor.CandidatoAmostra(
                    UUID.randomUUID(), "{\"nome\":\"Maria Souza\"}", "schema-v1", "v1",
                    "{\"justificativaIa\":\"Plano para Maria Souza\"}", 190, 160, BigDecimal.valueOf(4.5),
                    "iniciante", true, "APROVADO", alvo);

            var fixtures = extractor.estratificarERedigir(List.of(candidato), 1);

            assertThat(fixtures).singleElement().satisfies(f -> {
                assertThat(f.respostaHistoricaJson()).doesNotContain("Maria Souza");
                assertThat(f.planoFinalPersistidoJson()).doesNotContain("Maria Souza");
            });
        }

        @Test
        @DisplayName("monta zonasAtleta a partir dos campos do candidato")
        void montaZonasAtleta() {
            var candidato = candidato("iniciante", true, "APROVADO", "c1");

            var fixtures = extractor.estratificarERedigir(List.of(candidato), 1);

            assertThat(fixtures.get(0).zonasAtleta()).isEqualTo(new AthleteZones(190, 160, BigDecimal.valueOf(4.5)));
        }

        @Test
        @DisplayName("retorna vazio quando não há candidatos ou o alvo é zero")
        void retornaVazioEmCasosDegenerados() {
            assertThat(extractor.estratificarERedigir(List.of(), 10)).isEmpty();
            assertThat(extractor.estratificarERedigir(List.of(candidato("x", true, "APROVADO", "c1")), 0)).isEmpty();
        }

        @Test
        @DisplayName("não excede o alvo mesmo com mais candidatos disponíveis")
        void naoExcedeOAlvo() {
            List<EvalFixtureExtractor.CandidatoAmostra> candidatos = List.of(
                    candidato("iniciante", true, "APROVADO", "c1"),
                    candidato("iniciante", true, "APROVADO", "c2"),
                    candidato("iniciante", true, "APROVADO", "c3"));

            assertThat(extractor.estratificarERedigir(candidatos, 2)).hasSize(2);
        }

        private EvalFixtureExtractor.CandidatoAmostra candidato(String arquetipo, boolean coldStart,
                                                                  String veredito, String tag) {
            return new EvalFixtureExtractor.CandidatoAmostra(
                    UUID.randomUUID(), "{\"tag\":\"" + tag + "\"}", "schema-v1", "v1",
                    "{\"tag\":\"" + tag + "\"}", 190, 160, BigDecimal.valueOf(4.5),
                    arquetipo, coldStart, veredito, new EvalPiiRedactor.PiiAlvo(null, null, null, null, null));
        }
    }
}
