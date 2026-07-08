package br.com.menthoros.backend.services.prompt;

import br.com.menthoros.backend.dto.DecisaoProgressao;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.Prova;
import br.com.menthoros.backend.enums.DistanciaProva;
import br.com.menthoros.backend.enums.EstadoProgressao;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PeriodizacaoPromptFormatterTest {

    private PeriodizacaoPromptFormatter formatter;

    @BeforeEach
    void setUp() {
        formatter = new PeriodizacaoPromptFormatter();
    }

    @Test
    void formatarProvas_deveSerResilienteAValoresNulos() {
        Atleta atleta = Atleta.builder().nome("Teste").objetivo("Corrida").build();
        Prova prova = Prova.builder()
                .nomeProva("10K Teste")
                .dataProva(LocalDate.of(2026, 3, 9))
                .distancia(DistanciaProva.KM_10)
                .atleta(atleta)
                .build();

        String texto = assertDoesNotThrow(() -> formatter.formatarProvas(prova, List.of()));
        assertTrue(texto.contains("09/03/2026"));
        assertTrue(texto.contains("Pace alvo:"));
        assertTrue(texto.contains("TSB ideal na prova:"));
    }

    @Nested
    @DisplayName("formatarDecisaoProgressao")
    class FormatarDecisaoProgressao {

        @Test
        @DisplayName("null retorna string vazia — sem bloco no prompt")
        void nullRetornaVazio() {
            assertThat(formatter.formatarDecisaoProgressao(null)).isEmpty();
        }

        @Test
        @DisplayName("PROGREDIR inclui estado, ajuste positivo de volume, longão e motivo")
        void progredir() {
            DecisaoProgressao decisao = new DecisaoProgressao(
                    EstadoProgressao.PROGREDIR, 0.06, 10, true, "atleta respondendo bem ao treino");

            String resultado = formatter.formatarDecisaoProgressao(decisao);

            assertThat(resultado).contains("PROGREDIR");
            assertThat(resultado).contains("+6%");
            assertThat(resultado).contains("+10 min");
            assertThat(resultado).contains("atleta respondendo bem ao treino");
            assertThat(resultado).contains("Permitir progressão de intensidade");
        }

        @Test
        @DisplayName("REDUZIR inclui ajuste negativo de volume e restrição de intensidade")
        void reduzir() {
            DecisaoProgressao decisao = new DecisaoProgressao(
                    EstadoProgressao.REDUZIR, -0.05, -10, false, "TSB crítico (-25.0)");

            String resultado = formatter.formatarDecisaoProgressao(decisao);

            assertThat(resultado).contains("REDUZIR");
            assertThat(resultado).contains("-5%");
            assertThat(resultado).contains("-10 min");
            assertThat(resultado).contains("TSB crítico");
            assertThat(resultado).contains("Não progredir intensidade");
        }

        @Test
        @DisplayName("MANTER com ajuste zero — sem longão e sem progressão de intensidade")
        void manter() {
            DecisaoProgressao decisao = new DecisaoProgressao(
                    EstadoProgressao.MANTER, 0.0, 0, false, "manter volume atual");

            String resultado = formatter.formatarDecisaoProgressao(decisao);

            assertThat(resultado).contains("MANTER");
            assertThat(resultado).contains("+0%");
            assertThat(resultado).contains("+0 min");
            assertThat(resultado).contains("manter volume atual");
        }
    }
}

