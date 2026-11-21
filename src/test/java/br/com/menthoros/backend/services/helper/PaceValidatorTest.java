package br.com.menthoros.backend.services.helper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import br.com.menthoros.backend.dto.llm.EtapaTreinoLlmDto;
import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PaceValidatorTest {

    private PaceValidator validator;

    @BeforeEach
    void setUp() {
        validator = new PaceValidator();
    }

    // ======================== validar ========================

    @Test
    @DisplayName("Deve aceitar ritmoAlvo válido quando paceMin é mais lento que o teto")
    void deveAceitarPaceValido() {
        // paceMin = 5:00 = 5.0000, teto = 4:50 = 4.8333 → 5.0 > 4.8333 → sem correção
        String ritmoAlvo = "5:00-5:30/km";
        BigDecimal teto = new BigDecimal("4.8333"); // 4:50/km

        String resultado = validator.validar(ritmoAlvo, teto);

        assertThat(resultado).isEqualTo(ritmoAlvo);
    }

    @Test
    @DisplayName("Deve corrigir pace mais rápido que o teto deslocando o intervalo inteiro")
    void deveCorrigirPaceMaisRapidoQueOTeto() {
        // paceMin = 4:40 = 4.6667, teto = 4:55 = 4.9167 → 4.6667 < 4.9167 → corrigir
        // diferença = 4.9167 - 4.6667 = 0.2500 min = 15 seg
        // novo paceMin = 4:55, novo paceMax = 4:50 + 0.25 = 5:05
        String ritmoAlvo = "4:40-4:50/km";
        BigDecimal teto = new BigDecimal("4.9167"); // 4:55/km

        String resultado = validator.validar(ritmoAlvo, teto);

        assertThat(resultado).isNotEqualTo(ritmoAlvo);
        assertThat(resultado).startsWith("4:55");
        assertThat(resultado).endsWith("/km");
        // paceMax deve ser deslocado na mesma diferença: ~5:05
        assertThat(resultado).contains("5:05");
    }

    @Test
    @DisplayName("Deve parsear formatos de ritmoAlvo corretamente sem lançar exceção")
    void deveParsearFormatoCorreto() {
        // Teto mais rápido que qualquer pace de teste (< 4:00) → nenhuma correção ocorre
        // Isso valida que os formatos são parseados corretamente (paceMin > teto → sem correção)
        BigDecimal tetoMuitoRapido = new BigDecimal("4.00"); // 4:00/km

        assertThat(validator.validar("5:00-5:30/km", tetoMuitoRapido)).isEqualTo("5:00-5:30/km");
        assertThat(validator.validar("4:45-5:00/km", tetoMuitoRapido)).isEqualTo("4:45-5:00/km");
        assertThat(validator.validar("10:00-10:30/km", tetoMuitoRapido)).isEqualTo("10:00-10:30/km");
    }

    @Test
    @DisplayName("Deve retornar original para formato de ritmoAlvo inválido sem lançar exceção")
    void deveRetornarOriginalParaFormatoInvalido() {
        String ritmoInvalido = "ritmo variado";
        BigDecimal teto = new BigDecimal("5.00");

        String resultado = validator.validar(ritmoInvalido, teto);

        assertThat(resultado).isEqualTo(ritmoInvalido);
    }

    @Test
    @DisplayName("Deve aplicar correção e retornar valor diferente do original quando teto é violado")
    void deveRetornarValorDiferenteCorrrecaoAplicada() {
        // Dado: pace mais rápido que teto → correção aplicada → return != input
        String ritmoAlvo = "4:30-4:45/km";
        BigDecimal teto = new BigDecimal("5.0000"); // 5:00/km

        String corrigido = validator.validar(ritmoAlvo, teto);

        // Deve ter sido corrigido
        assertThat(corrigido).isNotEqualTo(ritmoAlvo);
        // E o resultado deve ser um formato válido
        assertThat(corrigido).matches("\\d{1,2}:\\d{2}-\\d{1,2}:\\d{2}/km");
        // paceMin no resultado deve ser igual ao teto = 5:00
        assertThat(corrigido).startsWith("5:00");
    }

    // ======================== formatarDecimalMinutos ========================

    @Test
    @DisplayName("Deve formatar decimal minutos corretamente para exibição")
    void deveFormatarDecimalMinutos() {
        // 5.50 = 5:30, 4.75 = 4:45, 10.00 = 10:00
        assertThat(validator.formatarDecimalMinutos(new BigDecimal("5.50"))).isEqualTo("5:30");
        assertThat(validator.formatarDecimalMinutos(new BigDecimal("4.75"))).isEqualTo("4:45");
        assertThat(validator.formatarDecimalMinutos(new BigDecimal("10.00"))).isEqualTo("10:00");
    }

    // ======================== parsear (edge cases) ========================

    @Test
    @DisplayName("Deve retornar original para formatos inválidos de edge cases (null, vazio, sem intervalo, min>max)")
    void deveRetornarOriginalParaFormatosInvalidosEdgeCases() {
        BigDecimal teto = new BigDecimal("5.00");

        // null → retorna null
        assertThat(validator.validar(null, teto)).isNull();
        // vazio → retorna vazio
        assertThat(validator.validar("", teto)).isEqualTo("");
        // sem intervalo (formato inválido) → retorna original
        assertThat(validator.validar("5:30/km", teto)).isEqualTo("5:30/km");
        // paceMin > paceMax (incoerente) → retorna original
        assertThat(validator.validar("5:30-5:00/km", teto)).isEqualTo("5:30-5:00/km");
    }

    @Test
    @DisplayName("Deve retornar original quando teto é nulo")
    void deveRetornarOriginalQuandoTetoNulo() {
        String ritmoAlvo = "4:30-4:45/km";

        String resultado = validator.validar(ritmoAlvo, null);

        assertThat(resultado).isEqualTo(ritmoAlvo);
    }

    // ======================== sanear (validar-ritmo-etapa-contra-zona) ========================

    @org.junit.jupiter.params.ParameterizedTest(name = "{0} → {1}")
    @org.junit.jupiter.params.provider.CsvSource(value = {
            "11:00-7:00/km, 7:00-11:00/km",
            "5:30-5:00/km, 5:00-5:30/km",
            "00:00-00:00/km, NULL",
            "70:00-60:00/km, NULL",
            "20:00-20:00/km, NULL",
            "2:00-2:10/km, NULL",
            "6:30-7:00/km, 6:30-7:00/km",
            "4:10, 4:10",
            "leve, leve"
    }, nullValues = "NULL")
    @DisplayName("sanear desinverte faixa e descarta faixa zerada ou absurda (S1–S3)")
    void sanear(String entrada, String esperado) {
        assertThat(validator.sanear(entrada)).isEqualTo(esperado);
    }

    @Test
    @DisplayName("sanear devolve null para null")
    void sanearNull() {
        assertThat(validator.sanear(null)).isNull();
    }

    // ======================== validarZonaEtapa (validar-ritmo-etapa-contra-zona) ========================

    @org.junit.jupiter.api.Nested
    @DisplayName("validarZonaEtapa")
    class ValidarZonaEtapa {

        // limiar 3:50 (sub-3h), zonas de Friel: Z1 4:56–5:22 · Z2 4:20–4:56 · Z3 4:02–4:20 · Z4 3:48–4:02
        // · Z5 3:27–3:48 · Z6 3:09–3:27
        private final List<ZonaTreinoService.ZonaPace> zonas =
                new ZonaTreinoService().calcularZonasPace(new BigDecimal("3.83"));

        private EtapaTreinoLlmDto etapa(String tipo, String ritmo) {
            return new EtapaTreinoLlmDto(1, tipo, "x", 15, 2.0, null, 1, ritmo);
        }

        private void assertDentro(String ritmo, String min, String max) {
            PaceValidator.PaceRange r = validator.parsear(ritmo);
            assertThat(r.paceMin()).isGreaterThanOrEqualTo(new BigDecimal(min));
            assertThat(r.paceMax()).isLessThanOrEqualTo(new BigDecimal(max));
        }

        @Test
        @DisplayName("CA1: aquecimento 6:00–6:30 do sub-3h vai para o miolo da Z1")
        void aquecimentoLentoDemais() {
            var r = validator.validarZonaEtapa(etapa("AQUECIMENTO", "6:00-6:30/km"), "INTERVALADO", zonas, null);

            assertThat(r.ritmoAlvo()).isNotEqualTo("6:00-6:30/km");
            assertDentro(r.ritmoAlvo(), "4.94", "5.37");
        }

        @Test
        @DisplayName("CA2: ritmo dentro da zona esperada fica intacto")
        void dentroDaZona() {
            var e = etapa("PRINCIPAL", "4:30-4:45/km");

            assertThat(validator.validarZonaEtapa(e, "CONTINUO", zonas, null)).isSameAs(e);
        }

        @Test
        @DisplayName("sobreposição ≥ 50% é recortada para a interseção")
        void recorta() {
            // CONTINUO espera Z2–Z3 (4:01–4:56); 3:55–4:15 sobrepõe 14 de 20 s (70%) → recorta o lado rápido
            var r = validator.validarZonaEtapa(etapa("PRINCIPAL", "3:55-4:15/km"), "CONTINUO", zonas, null);

            assertThat(r.ritmoAlvo()).isEqualTo("4:01-4:15/km");
        }

        @Test
        @DisplayName("principal mais lenta que a zona não é acelerada — cautela da prescrição")
        void principalLentaNaoAcelera() {
            var e = etapa("PRINCIPAL", "5:45-6:05/km");

            assertThat(validator.validarZonaEtapa(e, "CONTINUO", zonas, null)).isSameAs(e);
        }

        @Test
        @DisplayName("principal mais rápida que a zona é desacelerada")
        void principalRapidaDesacelera() {
            var r = validator.validarZonaEtapa(etapa("PRINCIPAL", "3:40-3:50/km"), "CONTINUO", zonas, null);

            assertDentro(r.ritmoAlvo(), "4.01", "4.94");
        }

        @Test
        @DisplayName("tiro na Z6 de um intervalado não é desacelerado")
        void tiroZ6() {
            var e = etapa("PRINCIPAL", "3:15-3:20/km");

            assertThat(validator.validarZonaEtapa(e, "INTERVALADO", zonas, null)).isSameAs(e);
        }

        @Test
        @DisplayName("histórico mais lento que a zona inteira: o histórico vence, não corrige")
        void historicoMaisLentoQueZona() {
            var e = etapa("PRINCIPAL", "5:50-6:00/km");

            assertThat(validator.validarZonaEtapa(e, "CONTINUO", zonas, new BigDecimal("5.80"))).isSameAs(e);
        }

        @Test
        @DisplayName("teto do histórico dentro da zona limita a borda rápida")
        void tetoLimitaBordaRapida() {
            var r = validator.validarZonaEtapa(etapa("PRINCIPAL", "4:20-4:40/km"), "CONTINUO", zonas, new BigDecimal("4.50"));

            assertThat(r.ritmoAlvo()).isEqualTo("4:30-4:40/km");
        }

        @Test
        @DisplayName("PROVA, ritmo único e zonas ausentes ficam intactos (CA3)")
        void semValidacao() {
            var prova = etapa("PRINCIPAL", "4:10");
            var faixa = etapa("AQUECIMENTO", "6:00-6:30/km");

            assertThat(validator.validarZonaEtapa(faixa, "PROVA", zonas, null)).isSameAs(faixa);
            assertThat(validator.validarZonaEtapa(prova, "CONTINUO", zonas, null)).isSameAs(prova);
            assertThat(validator.validarZonaEtapa(faixa, "CONTINUO", null, null)).isSameAs(faixa);
        }
    }
}
