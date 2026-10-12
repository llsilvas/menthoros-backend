package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.enums.DiaSemana;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * fix-planner-ignora-prova-na-semana, task 6.3 — regressão de risco (proposal.md "Riscos"): o dia
 * de uma prova da semana passa a contar em {@code effectiveDays} (task 1.1), e
 * {@code limiteDescansos()}/{@code diasDaSequenciaLonga()} leem esse campo. O risco aceito é o
 * limite de descanso crescer sem querer; este teste prova que, no caso de 3 dias configurados + 1
 * dia de prova, o limite continua 1 (não vira 2).
 */
@DisplayName("WeeklyCoverageContext — risco do dia de prova em effectiveDays")
class WeeklyCoverageContextTest {

    @Test
    @DisplayName("3 dias configurados + 1 dia de prova (4 no total): limiteDescansos() continua 1")
    void limiteDescansosContinua1ComDiaDeProva() {
        var semEfeitoDaProva = new WeeklyCoverageContext(
                List.of(DiaSemana.SEGUNDA, DiaSemana.QUARTA, DiaSemana.SEXTA), List.of(), true, null, 7);
        var comDiaDeProva = new WeeklyCoverageContext(
                List.of(DiaSemana.SEGUNDA, DiaSemana.QUARTA, DiaSemana.SEXTA, DiaSemana.DOMINGO), List.of(), true, null, 7);

        assertThat(semEfeitoDaProva.limiteDescansos()).isEqualTo(1);
        assertThat(comDiaDeProva.limiteDescansos()).isEqualTo(1);
    }

    @Test
    @DisplayName("semana cheia (7 dias, o máximo possível): limiteDescansos() continua 1 — o risco não tem como se materializar numa só semana")
    void limiteDescansosNuncaPassaDe1DentroDeUmaSemana() {
        // 7 é o teto de effectiveDays (dias da semana) — o dia de prova nunca soma um 8º dia
        // distinto, então floor(dias/4) nunca passa de 1 aqui. O risco citado no design (prova
        // empurrar o limite para 2) exigiria 8+ dias, impossível dentro de uma semana.
        var semanaCheia = new WeeklyCoverageContext(
                List.of(DiaSemana.SEGUNDA, DiaSemana.TERCA, DiaSemana.QUARTA, DiaSemana.QUINTA,
                        DiaSemana.SEXTA, DiaSemana.SABADO, DiaSemana.DOMINGO), List.of(), true, null, 7);

        assertThat(semanaCheia.limiteDescansos()).isEqualTo(1);
    }

    @Test
    @DisplayName("provasDaSemana e posProva não interferem em limiteDescansos/diasDaSequenciaLonga — são campos independentes")
    void camposDeProvaNaoAlteramOsCalculosExistentes() {
        var dias = List.of(DiaSemana.SEGUNDA, DiaSemana.TERCA, DiaSemana.QUARTA, DiaSemana.QUINTA);
        var semProva = new WeeklyCoverageContext(dias, List.of(), true, null, 3);
        var comProva = new WeeklyCoverageContext(dias, List.of(), true, null, 3,
                List.of(new br.com.menthoros.backend.dto.llm.TreinoPlanejadoLlmDto(
                        "DOMINGO", "PROVA", null, null, null, null, null, null, null, null, List.of())));

        assertThat(comProva.limiteDescansos()).isEqualTo(semProva.limiteDescansos());
        assertThat(comProva.diasDaSequenciaLonga()).isEqualTo(semProva.diasDaSequenciaLonga());
    }
}
