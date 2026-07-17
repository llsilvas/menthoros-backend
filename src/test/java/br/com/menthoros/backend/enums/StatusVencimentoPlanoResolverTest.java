package br.com.menthoros.backend.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("StatusVencimentoPlano.resolver")
class StatusVencimentoPlanoResolverTest {

    private static final LocalDate HOJE = LocalDate.of(2026, 7, 17);

    @Test
    @DisplayName("dataVencimento null → null (atleta sem dados de cobrança)")
    void dataVencimentoNulaRetornaNull() {
        assertThat(StatusVencimentoPlano.resolver(null, HOJE)).isNull();
    }

    @ParameterizedTest
    @CsvSource({
            "-30, VENCIDO",
            "-1,  VENCIDO",
            "0,   PROXIMO_VENCIMENTO",
            "1,   PROXIMO_VENCIMENTO",
            "7,   PROXIMO_VENCIMENTO",
            "8,   EM_DIA",
            "30,  EM_DIA"
    })
    @DisplayName("mapeia o offset em dias (dataVencimento - hoje) ao status (janela de alerta: 7 dias inclusive)")
    void mapeiaOffsetAoStatus(int offsetDias, String esperado) {
        LocalDate dataVencimento = HOJE.plusDays(offsetDias);
        assertThat(StatusVencimentoPlano.resolver(dataVencimento, HOJE))
                .isEqualTo(StatusVencimentoPlano.valueOf(esperado));
    }
}
