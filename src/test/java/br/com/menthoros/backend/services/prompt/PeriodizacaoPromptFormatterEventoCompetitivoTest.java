package br.com.menthoros.backend.services.prompt;

import br.com.menthoros.backend.entity.Prova;
import br.com.menthoros.backend.enums.DistanciaProva;
import br.com.menthoros.backend.enums.ProvaStatus;
import br.com.menthoros.backend.enums.TipoProva;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * D3 (prova-no-plano-semanal): {@code formatarEventoCompetitivoSemana} ganha a instrução
 * explícita "não prescreva outro treino nesse dia" — complemento textual à garantia
 * determinística de {@code ProvaNoPlanoService} (o service corrige o dia; a instrução evita que
 * o LLM planeje a semana como se o domingo estivesse livre).
 */
@DisplayName("PeriodizacaoPromptFormatter.formatarEventoCompetitivoSemana — instrução por prova")
class PeriodizacaoPromptFormatterEventoCompetitivoTest {

    private final PeriodizacaoPromptFormatter formatter = new PeriodizacaoPromptFormatter();

    @Nested
    @DisplayName("instrução de não prescrever outro treino")
    class InstrucaoPorProva {

        @Test
        @DisplayName("uma prova na semana gera uma linha de instrução")
        void umaProvaGeraUmaLinha() {
            LocalDate inicioSemana = LocalDate.of(2026, 12, 7); // segunda
            Prova prova = provaCom("Meia Maratona de SP", LocalDate.of(2026, 12, 13)); // domingo

            String prompt = formatter.formatarEventoCompetitivoSemana(prova, List.of(), inicioSemana);

            long ocorrencias = prompt.lines()
                    .filter(l -> l.startsWith("- Prescreva no dia"))
                    .count();
            assertThat(ocorrencias).isEqualTo(1);
            assertThat(prompt).contains(
                    "- Prescreva no dia DOMINGO (13/12/2026) um único treino do tipo PROVA com o nome "
                            + "Meia Maratona de SP. Não prescreva outro treino nesse dia.");
        }

        @Test
        @DisplayName("duas provas na semana geram duas linhas, uma por prova")
        void duasProvasGeramDuasLinhas() {
            LocalDate inicioSemana = LocalDate.of(2026, 12, 7); // segunda
            Prova alvo = provaCom("Meia Maratona de SP", LocalDate.of(2026, 12, 13)); // domingo
            Prova preparatoria = provaCom("10K de teste", LocalDate.of(2026, 12, 10)); // quinta

            String prompt = formatter.formatarEventoCompetitivoSemana(alvo, List.of(preparatoria), inicioSemana);

            long ocorrencias = prompt.lines()
                    .filter(l -> l.startsWith("- Prescreva no dia"))
                    .count();
            assertThat(ocorrencias).isEqualTo(2);
            assertThat(prompt).contains(
                    "- Prescreva no dia DOMINGO (13/12/2026) um único treino do tipo PROVA com o nome Meia Maratona de SP.");
            assertThat(prompt).contains(
                    "- Prescreva no dia QUINTA-FEIRA (10/12/2026) um único treino do tipo PROVA com o nome 10K de teste.");
        }

        @Test
        @DisplayName("sem prova na semana, não gera nenhuma linha de instrução")
        void semProvaNaoGeraLinha() {
            LocalDate inicioSemana = LocalDate.of(2026, 12, 7);

            String prompt = formatter.formatarEventoCompetitivoSemana(null, List.of(), inicioSemana);

            assertThat(prompt).doesNotContain("- Prescreva no dia");
        }
    }

    private Prova provaCom(String nome, LocalDate dataProva) {
        return Prova.builder()
                .nomeProva(nome)
                .dataProva(dataProva)
                .distancia(DistanciaProva.KM_21)
                .distanciaKm(BigDecimal.valueOf(21.1))
                .tipoProva(TipoProva.MEIA)
                .statusProva(ProvaStatus.PLANEJADA)
                .provaAlvo(true)
                .build();
    }
}
