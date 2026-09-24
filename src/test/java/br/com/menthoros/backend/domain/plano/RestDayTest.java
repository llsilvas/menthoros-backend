package br.com.menthoros.backend.domain.plano;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * O dia chega como texto da LLM e é comparado com {@code DiaSemana.name()} na validação, na remoção
 * por prova e na remoção pelo coach. Normalizar na construção evita que um valor cru escape para o
 * banco e para a API quando a regra de cobertura está desligada pelo kill-switch (achado do /qa).
 */
@DisplayName("RestDay — normalização do dia")
class RestDayTest {

    @ParameterizedTest(name = "\"{0}\" vira QUINTA")
    @ValueSource(strings = {"QUINTA", "quinta", " Quinta "})
    @DisplayName("dia é normalizado para o nome do enum, sem espaços e em maiúsculas")
    void normalizaDia(String bruto) {
        assertThat(new RestDay(bruto, "motivo").dayOfWeek()).isEqualTo("QUINTA");
    }

    @Test
    @DisplayName("motivo perde espaços nas pontas")
    void normalizaMotivo() {
        assertThat(new RestDay("QUINTA", "  check-in de hoje: DESCANSAR  ").reason())
                .isEqualTo("check-in de hoje: DESCANSAR");
    }

    @Test
    @DisplayName("nulos continuam nulos — quem valida é a regra de cobertura")
    void nulosPassam() {
        RestDay semNada = new RestDay(null, null);

        assertThat(semNada.dayOfWeek()).isNull();
        assertThat(semNada.reason()).isNull();
    }

    @Test
    @DisplayName("dia inválido não é corrigido, só normalizado — a violação é da regra, não daqui")
    void diaInvalidoSoNormaliza() {
        assertThat(new RestDay(" segunda-feira ", "motivo").dayOfWeek()).isEqualTo("SEGUNDA-FEIRA");
    }
}
