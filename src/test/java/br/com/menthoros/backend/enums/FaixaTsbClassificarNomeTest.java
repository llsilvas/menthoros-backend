package br.com.menthoros.backend.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("FaixaTsb.classificarNome")
class FaixaTsbClassificarNomeTest {

    @Test
    @DisplayName("tsb null → null (sem classificação inventada)")
    void tsbNuloRetornaNull() {
        assertThat(FaixaTsb.classificarNome(null)).isNull();
    }

    @ParameterizedTest
    @CsvSource({
            "-40.0, FADIGA_EXCESSIVA",
            "-35.0, FADIGA_EXCESSIVA",   // max inclusivo: -35 pertence à faixa crítica
            "-32.0, FADIGA_ALTA",
            "-30.0, FADIGA_ALTA",
            "-20.0, FADIGA_MODERADA",
            "-10.0, ACUMULANDO_FADIGA",  // fronteira cai na faixa de baixo (min exclusivo)
            "-5.0,  FATIGADO",
            "0.0,   FATIGADO",
            "5.0,   RECUPERANDO",
            "8.0,   FORMA_IDEAL",
            "15.0,  FORMA_IDEAL",
            "25.0,  DESCANSADO",
            "30.0,  MUITO_DESCANSADO"
    })
    @DisplayName("mapeia o TSB ao name() da faixa (regra min exclusivo, max inclusivo)")
    void mapeiaTsbAoNome(double tsb, String esperado) {
        assertThat(FaixaTsb.classificarNome(tsb)).isEqualTo(esperado);
    }
}
