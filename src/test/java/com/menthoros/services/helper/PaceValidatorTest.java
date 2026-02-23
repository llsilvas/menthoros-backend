package com.menthoros.services.helper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

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
}
