package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.dto.llm.EtapaTreinoLlmDto;
import br.com.menthoros.backend.dto.llm.TreinoPlanejadoLlmDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link TreinoNormalizador#distanciaPrincipalPorPace} e {@link TreinoNormalizador#principaisComDistanciaPorPace}
 * (fix-etapas-continuos-pace): a LLM concentra a distância do treino na PRINCIPAL — caso real 22/09,
 * REGENERATIVO com 5,5 km em 30min a 7:28-7:55.
 */
@DisplayName("TreinoNormalizador — distância da PRINCIPAL pelo pace")
class TreinoNormalizadorDistanciaPrincipalPorPaceTest {

    private TreinoNormalizador normalizador;

    @BeforeEach
    void setUp() {
        normalizador = new TreinoNormalizador(new PaceValidator());
    }

    @Nested
    @DisplayName("distanciaPrincipalPorPace")
    class DistanciaPrincipalPorPace {

        @Test
        @DisplayName("CA2: PRINCIPAL com ritmoAlvo recebe duração ÷ pace médio")
        void principalComRitmoRecebeDistanciaDoPace() {
            var treino = continuo(etapa("PRINCIPAL", 30, 5.5, "7:28-7:55/km"));

            var resultado = normalizador.distanciaPrincipalPorPace(treino, null);

            // pace médio de 7:28-7:55 = 7,69 min/km → 30 / 7,69 = 3,90 km
            assertThat(resultado.etapas().getFirst().distanciaKm()).isEqualTo(3.9);
        }

        @ParameterizedTest(name = "{0} min a {1} → {2} km")
        @CsvSource({
                // BVA na duração: 1 min (mínimo), valores típicos, 180 min (longo extremo)
                "1,   7:00-7:00/km, 0.14",
                "10,  6:00-6:00/km, 1.67",
                "30,  7:28-7:55/km, 3.9",
                "35,  6:45-7:06/km, 5.05",
                "45,  6:45-7:06/km, 6.5",
                "60,  5:30-6:00/km, 10.43",
                "180, 5:00-5:00/km, 36.0",
                // BVA no pace: muito rápido, muito lento, faixa de 1 segundo
                "20,  3:00-3:00/km, 6.67",
                "20,  9:59-9:59/km, 2.0",
                "20,  6:00-6:01/km, 3.33",
                // minuto de 1 dígito e de 2 dígitos na faixa
                "30,  9:30-10:30/km, 3.0"
        })
        @DisplayName("distância = round2(duração ÷ pace médio)")
        void distanciaPelaFormula(int duracaoMin, String ritmo, double esperada) {
            var resultado = normalizador.distanciaPrincipalPorPace(continuo(etapa("PRINCIPAL", duracaoMin, 99.0, ritmo)), null);

            assertThat(resultado.etapas().getFirst().distanciaKm()).isEqualTo(esperada);
        }

        @Test
        @DisplayName("PRINCIPAL sem distância da LLM (null) também recebe a distância pelo pace")
        void principalSemDistanciaRecebe() {
            var resultado = normalizador.distanciaPrincipalPorPace(continuo(etapa("PRINCIPAL", 30, null, "7:28-7:55/km")), null);

            assertThat(resultado.etapas().getFirst().distanciaKm()).isEqualTo(3.9);
        }

        @Test
        @DisplayName("PRINCIPAL com distância 0.0 (desconhecida) recebe a distância pelo pace")
        void principalComZeroRecebe() {
            var resultado = normalizador.distanciaPrincipalPorPace(continuo(etapa("PRINCIPAL", 30, 0.0, "7:28-7:55/km")), null);

            assertThat(resultado.etapas().getFirst().distanciaKm()).isEqualTo(3.9);
        }

        @ParameterizedTest(name = "ritmo=\"{0}\"")
        @NullAndEmptySource
        @ValueSource(strings = {"   ", "rápido", "7:28/km", "7:28-7:55", "7-8/km", "7:28 - 7:55/km",
                "7:55-7:28/km", "Z2", "7:28-7:55/mi", "abc-def/km"})
        @DisplayName("CA3: TEMPO_RUN com ritmoAlvo ausente, vazio, malformado ou invertido → distância da LLM preservada")
        void ritmoNaoInterpretavelPreserva(String ritmo) {
            var original = etapa("PRINCIPAL", 30, 5.5, ritmo);

            var resultado = normalizador.distanciaPrincipalPorPace(tempoRun(original), null);

            assertThat(resultado.etapas().getFirst()).isEqualTo(original);
        }

        @ParameterizedTest(name = "ritmo=\"{0}\"")
        @NullAndEmptySource
        @ValueSource(strings = {"   ", "rápido", "7:55-7:28/km", "Z2"})
        @DisplayName("ritmo ausente ou ilegível em tipo de ritmo leve → pace Z2 default: 30 ÷ 7,0 = 4,29 km, ritmo intacto")
        void ritmoNaoInterpretavelUsaZ2(String ritmo) {
            var resultado = normalizador.distanciaPrincipalPorPace(continuo(etapa("PRINCIPAL", 30, 5.5, ritmo)), null);

            assertThat(resultado.etapas().getFirst().distanciaKm()).isEqualTo(4.29);
            assertThat(resultado.etapas().getFirst().ritmoAlvo()).isEqualTo(ritmo);
        }

        @ParameterizedTest(name = "{0}, limiar={1} → {2} km")
        @CsvSource(value = {
                "REGENERATIVO, NULL, 1.43",
                "CONTINUO,     NULL, 1.43",
                "LONGO,        NULL, 1.43",
                "REGENERATIVO, 5.0,  1.67",
                "LONGO,        4.0,  2.08",
                // limiar inválido: sem fallback, distância da LLM — nunca infinita ou negativa
                "REGENERATIVO, 0,    0.8",
                "REGENERATIVO, -5.0, 0.8"
        }, nullValues = "NULL")
        @DisplayName("PRINCIPAL sem ritmo: 10 min ÷ pace Z2 (limiar × 1,20, ou 7:00 sem limiar); limiar ≤ 0 → intacta")
        void semRitmoUsaPaceZ2(String tipo, java.math.BigDecimal limiar, double esperada) {
            var treino = new TreinoPlanejadoLlmDto("SEGUNDA", tipo, null, null, null, null, null,
                    "25:00", 1.0, null, List.of(etapa("PRINCIPAL", 10, 0.8, null)));

            var resultado = normalizador.distanciaPrincipalPorPace(treino, limiar);

            assertThat(resultado.etapas().getFirst().distanciaKm()).isEqualTo(esperada);
        }

        @ParameterizedTest(name = "duração={0}")
        @CsvSource(value = {"NULL", "0"}, nullValues = "NULL")
        @DisplayName("PRINCIPAL sem ritmo e sem duração positiva → distância da LLM preservada (guarda antes do fallback)")
        void semRitmoSemDuracaoPreserva(Integer duracaoMin) {
            var original = etapa("PRINCIPAL", duracaoMin, 0.8, null);

            var resultado = normalizador.distanciaPrincipalPorPace(continuo(original), null);

            assertThat(resultado.etapas().getFirst()).isEqualTo(original);
        }

        @ParameterizedTest(name = "duração={0}")
        @CsvSource(value = {"NULL", "0", "-5"}, nullValues = "NULL")
        @DisplayName("PRINCIPAL sem duração positiva → distância da LLM preservada")
        void semDuracaoPositivaPreserva(Integer duracaoMin) {
            var original = etapa("PRINCIPAL", duracaoMin, 5.5, "7:28-7:55/km");

            var resultado = normalizador.distanciaPrincipalPorPace(continuo(original), null);

            assertThat(resultado.etapas().getFirst()).isEqualTo(original);
        }

        @ParameterizedTest(name = "tipoEtapa=\"{0}\"")
        @ValueSource(strings = {"principal", "Principal", " PRINCIPAL ", "PRINCIPAL"})
        @DisplayName("tipoEtapa é normalizado (caixa e espaços) antes de decidir")
        void tipoEtapaNormalizado(String tipoEtapa) {
            var resultado = normalizador.distanciaPrincipalPorPace(continuo(etapa(tipoEtapa, 30, 5.5, "7:28-7:55/km")), null);

            assertThat(resultado.etapas().getFirst().distanciaKm()).isEqualTo(3.9);
        }

        @ParameterizedTest(name = "tipoEtapa={0}")
        @ValueSource(strings = {"AQUECIMENTO", "DESAQUECIMENTO", "INTERVALADO", "RECUPERACAO", "MOBILIDADE"})
        @DisplayName("etapas que não são PRINCIPAL não são tocadas, mesmo com ritmoAlvo")
        void naoPrincipalIntocada(String tipoEtapa) {
            var original = etapa(tipoEtapa, 10, 0.5, "7:28-7:55/km");

            var resultado = normalizador.distanciaPrincipalPorPace(continuo(original), null);

            assertThat(resultado.etapas().getFirst()).isEqualTo(original);
        }

        @Test
        @DisplayName("tipoEtapa null não é PRINCIPAL e não quebra")
        void tipoEtapaNull() {
            var original = new EtapaTreinoLlmDto(1, null, "x", 30, 5.5, null, 1, "7:28-7:55/km");

            var resultado = normalizador.distanciaPrincipalPorPace(continuo(original), null);

            assertThat(resultado.etapas().getFirst()).isEqualTo(original);
        }

        @Test
        @DisplayName("CA6: LONGO com duas PRINCIPAL — cada uma recebe a própria distância")
        void cadaPrincipalRecebeAPropriaDistancia() {
            var treino = continuo(
                    etapa("PRINCIPAL", 40, 8.0, "6:45-7:06/km"),
                    etapa("PRINCIPAL", 10, 2.0, "6:00-6:10/km"));

            var resultado = normalizador.distanciaPrincipalPorPace(treino, null);

            // 40 / 6,925 = 5,78; 10 / 6,083 = 1,64
            assertThat(resultado.etapas()).extracting(EtapaTreinoLlmDto::distanciaKm).containsExactly(5.78, 1.64);
        }

        @Test
        @DisplayName("duas PRINCIPAL, uma sem ritmo: a com ritmo usa o dela, a sem ritmo o Z2 default")
        void principaisMistas() {
            var treino = continuo(etapa("PRINCIPAL", 40, 8.0, "6:45-7:06/km"), etapa("PRINCIPAL", 10, 2.0, null));

            var resultado = normalizador.distanciaPrincipalPorPace(treino, null);

            assertThat(resultado.etapas()).extracting(EtapaTreinoLlmDto::distanciaKm).containsExactly(5.78, 1.43);
        }

        @Test
        @DisplayName("PRINCIPAL sem ritmo usa o ritmoAlvo do treino: 30 ÷ 6,25 = 4,8 km")
        void semRitmoUsaRitmoDoTreino() {
            var treino = continuoComRitmo("6:00-6:30/km", etapa("PRINCIPAL", 30, 5.5, null));

            var resultado = normalizador.distanciaPrincipalPorPace(treino, null);

            assertThat(resultado.etapas().getFirst().distanciaKm()).isEqualTo(4.8);
        }

        @Test
        @DisplayName("ritmo da etapa tem precedência sobre o do treino")
        void ritmoDaEtapaPrevalece() {
            var treino = continuoComRitmo("6:00-6:30/km", etapa("PRINCIPAL", 30, 5.5, "7:28-7:55/km"));

            var resultado = normalizador.distanciaPrincipalPorPace(treino, null);

            assertThat(resultado.etapas().getFirst().distanciaKm()).isEqualTo(3.9);
        }

        @Test
        @DisplayName("só a distância muda: ordem, tipo, descrição, duração, FC, repetições e ritmo preservados")
        void soDistanciaMuda() {
            var original = new EtapaTreinoLlmDto(2, "PRINCIPAL", "Corrida regenerativa Z1", 30, 5.5,
                    "115-130 bpm", 1, "7:28-7:55/km");

            var resultado = normalizador.distanciaPrincipalPorPace(continuo(original), null).etapas().getFirst();

            assertThat(resultado).usingRecursiveComparison().ignoringFields("distanciaKm").isEqualTo(original);
            assertThat(resultado.distanciaKm()).isEqualTo(3.9);
        }

        @Test
        @DisplayName("campos do treino (total, duração, ritmo) não são tocados — isso é da reconciliação")
        void camposDoTreinoIntocados() {
            var treino = continuo(etapa("PRINCIPAL", 30, 5.5, "7:28-7:55/km"));

            var resultado = normalizador.distanciaPrincipalPorPace(treino, null);

            assertThat(resultado).usingRecursiveComparison().ignoringFields("etapas").isEqualTo(treino);
        }

        @Test
        @DisplayName("ordem das etapas preservada numa lista de 3")
        void ordemPreservada() {
            var aquec = etapa("AQUECIMENTO", 10, 0.0, null);
            var desaq = etapa("DESAQUECIMENTO", 5, 0.5, null);
            var treino = continuo(aquec, etapa("PRINCIPAL", 30, 5.5, "7:28-7:55/km"), desaq);

            var resultado = normalizador.distanciaPrincipalPorPace(treino, null);

            assertThat(resultado.etapas()).extracting(EtapaTreinoLlmDto::tipoEtapa)
                    .containsExactly("AQUECIMENTO", "PRINCIPAL", "DESAQUECIMENTO");
            assertThat(resultado.etapas().get(0)).isEqualTo(aquec);
            assertThat(resultado.etapas().get(2)).isEqualTo(desaq);
        }

        @Test
        @DisplayName("idempotente: aplicar duas vezes dá o mesmo que uma")
        void idempotente() {
            var treino = continuo(etapa("AQUECIMENTO", 10, 0.0, null), etapa("PRINCIPAL", 30, 5.5, "7:28-7:55/km"));

            var uma = normalizador.distanciaPrincipalPorPace(treino, null);
            var duas = normalizador.distanciaPrincipalPorPace(uma, null);

            assertThat(duas).isEqualTo(uma);
        }

        @Test
        @DisplayName("etapas null → devolve o treino intacto")
        void etapasNull() {
            var treino = new TreinoPlanejadoLlmDto("SEGUNDA", "REGENERATIVO", null, null, null, null, null,
                    "45:00", 6.0, "7:28-7:55/km", null);

            assertThat(normalizador.distanciaPrincipalPorPace(treino, null)).isSameAs(treino);
        }

        @Test
        @DisplayName("etapas vazias → devolve o treino intacto")
        void etapasVazias() {
            var treino = continuo();

            assertThat(normalizador.distanciaPrincipalPorPace(treino, null)).isSameAs(treino);
        }

        @Test
        @DisplayName("pace implícito de toda PRINCIPAL derivada cai dentro do ritmoAlvo (grade de durações × ritmos)")
        void paceImplicitoDentroDoRitmo() {
            String[] ritmos = {"4:30-4:45/km", "5:30-6:00/km", "6:45-7:06/km", "7:28-7:55/km", "8:30-9:00/km"};
            List<EtapaTreinoLlmDto> etapas = new ArrayList<>();
            for (String r : ritmos) {
                for (int dur = 10; dur <= 120; dur += 5) {
                    etapas.add(etapa("PRINCIPAL", dur, 99.0, r));
                }
            }

            var resultado = normalizador.distanciaPrincipalPorPace(continuo(etapas.toArray(EtapaTreinoLlmDto[]::new)), null);

            assertThat(resultado.etapas()).allSatisfy(e -> {
                double[] faixa = limites(e.ritmoAlvo());
                double pace = e.duracaoMin() / e.distanciaKm();
                // arredondar a distância a 2 casas desloca o pace em no máximo ~0,01 min/km nessa grade
                assertThat(pace).as("%d min a %s", e.duracaoMin(), e.ritmoAlvo())
                        .isBetween(faixa[0] - 0.02, faixa[1] + 0.02);
            });
        }
    }

    @Nested
    @DisplayName("principaisComDistanciaPorPace")
    class PrincipaisComDistanciaPorPace {

        @Test
        @DisplayName("PRINCIPAL única com duração e ritmo → true")
        void principalComPace() {
            assertThat(normalizador.principaisComDistanciaPorPace(
                    continuo(etapa("AQUECIMENTO", 10, 0.0, null), etapa("PRINCIPAL", 30, 5.5, "7:28-7:55/km")), null))
                    .isTrue();
        }

        @Test
        @DisplayName("duas PRINCIPAL com ritmo → true")
        void duasComPace() {
            assertThat(normalizador.principaisComDistanciaPorPace(continuo(
                    etapa("PRINCIPAL", 40, 8.0, "6:45-7:06/km"), etapa("PRINCIPAL", 10, 2.0, "6:00-6:10/km")), null))
                    .isTrue();
        }

        @Test
        @DisplayName("TEMPO_RUN com uma das PRINCIPAL sem ritmo → false (sem fallback)")
        void umaSemRitmo() {
            assertThat(normalizador.principaisComDistanciaPorPace(tempoRun(
                    etapa("PRINCIPAL", 40, 8.0, "6:45-7:06/km"), etapa("PRINCIPAL", 10, 2.0, null)), null))
                    .isFalse();
        }

        @ParameterizedTest(name = "ritmo=\"{0}\"")
        @NullAndEmptySource
        @ValueSource(strings = {"  ", "rápido", "7:55-7:28/km"})
        @DisplayName("TEMPO_RUN com PRINCIPAL de ritmo não interpretável → false")
        void ritmoNaoInterpretavel(String ritmo) {
            assertThat(normalizador.principaisComDistanciaPorPace(tempoRun(etapa("PRINCIPAL", 30, 5.5, ritmo)), null))
                    .isFalse();
        }

        @Test
        @DisplayName("tipo de ritmo leve com PRINCIPAL sem ritmo → true (pace Z2 de fallback)")
        void semRitmoComFallback() {
            assertThat(normalizador.principaisComDistanciaPorPace(continuo(
                    etapa("PRINCIPAL", 40, 8.0, "6:45-7:06/km"), etapa("PRINCIPAL", 10, 2.0, null)), null))
                    .isTrue();
        }

        @Test
        @DisplayName("tipoTreino null com PRINCIPAL sem ritmo → false, sem NPE")
        void tipoNuloSemFallback() {
            var treino = new TreinoPlanejadoLlmDto("SEGUNDA", null, null, null, null, null, null,
                    "45:00", 6.0, null, List.of(etapa("PRINCIPAL", 30, 5.5, null)));

            assertThat(normalizador.principaisComDistanciaPorPace(treino, null)).isFalse();
        }

        @Test
        @DisplayName("PRINCIPAL sem ritmo e sem duração → false mesmo com fallback")
        void semRitmoSemDuracao() {
            assertThat(normalizador.principaisComDistanciaPorPace(continuo(etapa("PRINCIPAL", null, 2.0, null)), null))
                    .isFalse();
        }

        @ParameterizedTest(name = "duração={0}")
        @CsvSource(value = {"NULL", "0", "-1"}, nullValues = "NULL")
        @DisplayName("PRINCIPAL sem duração positiva → false")
        void semDuracao(Integer duracao) {
            assertThat(normalizador.principaisComDistanciaPorPace(continuo(etapa("PRINCIPAL", duracao, 5.5, "7:28-7:55/km")), null))
                    .isFalse();
        }

        @Test
        @DisplayName("sem nenhuma PRINCIPAL → false (não há o que derivar)")
        void semPrincipal() {
            assertThat(normalizador.principaisComDistanciaPorPace(
                    continuo(etapa("AQUECIMENTO", 10, 1.0, "7:28-7:55/km"), etapa("DESAQUECIMENTO", 5, 0.5, null)), null))
                    .isFalse();
        }

        @Test
        @DisplayName("etapas null ou vazias → false")
        void semEtapas() {
            var semLista = new TreinoPlanejadoLlmDto("SEGUNDA", "REGENERATIVO", null, null, null, null, null,
                    "45:00", 6.0, null, null);

            assertThat(normalizador.principaisComDistanciaPorPace(semLista, null)).isFalse();
            assertThat(normalizador.principaisComDistanciaPorPace(continuo(), null)).isFalse();
        }

        @Test
        @DisplayName("etapas que não são PRINCIPAL sem ritmo não contam")
        void soPrincipalConta() {
            assertThat(normalizador.principaisComDistanciaPorPace(continuo(
                    etapa("AQUECIMENTO", 10, 0.0, null),
                    etapa("PRINCIPAL", 30, 5.5, "7:28-7:55/km"),
                    etapa("DESAQUECIMENTO", 5, 0.5, null)), null))
                    .isTrue();
        }
    }

    @Nested
    @DisplayName("adotarSomaDasEtapas")
    class AdotarSomaDasEtapas {

        @ParameterizedTest(name = "total {0} → {1}")
        @CsvSource(value = {"6.0, 5.88", "5.0, 5.88", "5.87, 5.88", "5.89, 5.88", "NULL, 5.88", "0.0, 5.88"},
                nullValues = "NULL")
        @DisplayName("qualquer desvio (inclusive 0,01 e total ausente) → total = soma, tolerância zero")
        void toleranciaZero(Double total, double esperado) {
            var treino = new TreinoPlanejadoLlmDto("SEGUNDA", "REGENERATIVO", null, null, null, null, null,
                    "45:00", total, null, List.of(
                    etapa("AQUECIMENTO", 10, 1.32, null), etapa("PRINCIPAL", 30, 3.9, null),
                    etapa("DESAQUECIMENTO", 5, 0.66, null)));

            assertThat(normalizador.adotarSomaDasEtapas(treino).distanciaKm()).isEqualTo(esperado);
        }

        @Test
        @DisplayName("soma arredondada a 2 casas: 1,32 + 3,90 + 0,66 não vira 5,880000000000001")
        void arredondaASoma() {
            var treino = continuo(etapa("AQUECIMENTO", 10, 1.32, null), etapa("PRINCIPAL", 30, 3.9, null),
                    etapa("DESAQUECIMENTO", 5, 0.66, null));

            assertThat(normalizador.adotarSomaDasEtapas(treino).distanciaKm()).isEqualTo(5.88);
        }

        @Test
        @DisplayName("total já igual à soma → devolve o mesmo treino")
        void jaIgual() {
            var treino = new TreinoPlanejadoLlmDto("SEGUNDA", "REGENERATIVO", null, null, null, null, null,
                    "45:00", 5.88, null, List.of(
                    etapa("AQUECIMENTO", 10, 1.32, null), etapa("PRINCIPAL", 30, 3.9, null),
                    etapa("DESAQUECIMENTO", 5, 0.66, null)));

            assertThat(normalizador.adotarSomaDasEtapas(treino)).isSameAs(treino);
        }

        @ParameterizedTest(name = "distância da etapa = {0}")
        @CsvSource(value = {"NULL", "0.0", "-1.0"}, nullValues = "NULL")
        @DisplayName("alguma etapa sem distância positiva → soma é piso, treino intacto")
        void etapaSemDistancia(Double distancia) {
            var treino = continuo(etapa("AQUECIMENTO", 10, distancia, null), etapa("PRINCIPAL", 30, 3.9, null));

            assertThat(normalizador.adotarSomaDasEtapas(treino)).isSameAs(treino);
        }

        @Test
        @DisplayName("etapas null ou vazias → treino intacto")
        void semEtapas() {
            var semLista = new TreinoPlanejadoLlmDto("SEGUNDA", "REGENERATIVO", null, null, null, null, null,
                    "45:00", 6.0, null, null);

            assertThat(normalizador.adotarSomaDasEtapas(semLista)).isSameAs(semLista);
            var vazio = continuo();
            assertThat(normalizador.adotarSomaDasEtapas(vazio)).isSameAs(vazio);
        }

        @Test
        @DisplayName("total e duração viram a soma; etapas e ritmo preservados")
        void soOTotalMuda() {
            var treino = continuo(etapa("AQUECIMENTO", 10, 1.32, null), etapa("PRINCIPAL", 30, 3.9, "7:28-7:55/km"));

            var resultado = normalizador.adotarSomaDasEtapas(treino);

            assertThat(resultado).usingRecursiveComparison().ignoringFields("distanciaKm", "duracaoMin").isEqualTo(treino);
            assertThat(resultado.distanciaKm()).isEqualTo(5.22);
            assertThat(resultado.duracaoMin()).isEqualTo("40:00");
        }

        @Test
        @DisplayName("total vira a soma e a duração do treino também: 96 min da LLM → 84 das etapas (CA1)")
        void duracaoAcompanhaASoma() {
            var treino = new TreinoPlanejadoLlmDto("SABADO", "LONGO", null, null, null, null, null,
                    "96:00", 12.0, "8:00-8:00/km", List.of(
                    etapa("AQUECIMENTO", 10, 1.67, null), etapa("PRINCIPAL", 69, 12.0, "5:36-5:54/km"),
                    etapa("DESAQUECIMENTO", 5, 0.83, null)));

            var resultado = normalizador.adotarSomaDasEtapas(treino);

            assertThat(resultado.distanciaKm()).isEqualTo(14.5);
            assertThat(resultado.duracaoMin()).isEqualTo("84:00");
        }

        @Test
        @DisplayName("total já igual à soma com duração divergente → só a duração muda (CA1b)")
        void totalIgualDuracaoDivergente() {
            var treino = new TreinoPlanejadoLlmDto("SABADO", "LONGO", null, null, null, null, null,
                    "116:00", 14.5, "8:00-8:00/km", List.of(
                    etapa("AQUECIMENTO", 10, 1.67, null), etapa("PRINCIPAL", 69, 12.0, "5:36-5:54/km"),
                    etapa("DESAQUECIMENTO", 5, 0.83, null)));

            var resultado = normalizador.adotarSomaDasEtapas(treino);

            assertThat(resultado.distanciaKm()).isEqualTo(14.5);
            assertThat(resultado.duracaoMin()).isEqualTo("84:00");
        }

        @Test
        @DisplayName("aquecimento com distância e sem duração → só o total muda, a duração fica para a cauda (CA2)")
        void etapaSemDuracaoNaoGravaDuracao() {
            var treino = continuo(etapa("AQUECIMENTO", null, 1.32, null), etapa("PRINCIPAL", 30, 3.9, "7:28-7:55/km"));

            var resultado = normalizador.adotarSomaDasEtapas(treino);

            assertThat(resultado.distanciaKm()).isEqualTo(5.22);
            assertThat(resultado.duracaoMin()).isEqualTo("45:00");
        }
    }

    /** "m:ss-m:ss/km" → {min, max} em min/km decimais. */
    private static double[] limites(String ritmo) {
        String[] partes = ritmo.replace("/km", "").split("-");
        return new double[]{minutos(partes[0]), minutos(partes[1])};
    }

    private static double minutos(String mmss) {
        String[] p = mmss.split(":");
        return Integer.parseInt(p[0]) + Integer.parseInt(p[1]) / 60.0;
    }

    private static EtapaTreinoLlmDto etapa(String tipo, Integer duracaoMin, Double distanciaKm, String ritmoAlvo) {
        return new EtapaTreinoLlmDto(1, tipo, "etapa", duracaoMin, distanciaKm, null, 1, ritmoAlvo);
    }

    // Treino sem ritmo próprio: os casos "sem ritmo" daqui são "sem ritmo em lugar nenhum" — o fallback
    // para o ritmo do treino (fix-total-treino-soma-etapas) tem testes próprios, com continuoComRitmo.
    private static TreinoPlanejadoLlmDto continuo(EtapaTreinoLlmDto... etapas) {
        return continuoComRitmo(null, etapas);
    }

    private static TreinoPlanejadoLlmDto tempoRun(EtapaTreinoLlmDto... etapas) {
        return new TreinoPlanejadoLlmDto("SEGUNDA", "TEMPO_RUN", null, null, null, null, null,
                "45:00", 6.0, null, List.of(etapas));
    }

    private static TreinoPlanejadoLlmDto continuoComRitmo(String ritmoTreino, EtapaTreinoLlmDto... etapas) {
        return new TreinoPlanejadoLlmDto("SEGUNDA", "REGENERATIVO", null, null, null, null, null,
                "45:00", 6.0, ritmoTreino, List.of(etapas));
    }
}
