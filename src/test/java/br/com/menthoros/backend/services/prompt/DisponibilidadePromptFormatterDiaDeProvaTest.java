package br.com.menthoros.backend.services.prompt;

import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.PlanoMetaDados;
import br.com.menthoros.backend.enums.DiaSemana;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * O bloco de disponibilidade nomeia o dia da prova (fix-planner-ignora-prova-na-semana, task 1.2):
 * sem isso o prompt diz "PROVA no domingo" e "disponíveis: SEG/QUA/SEX/SAB" ao mesmo tempo.
 */
@DisplayName("DisponibilidadePromptFormatter — dia de prova")
class DisponibilidadePromptFormatterDiaDeProvaTest {

    private static final LocalDate INICIO = PlanoPromptArquetipos.INICIO_SEMANA;

    private final DisponibilidadePromptFormatter formatter = new DisponibilidadePromptFormatter();
    private final Atleta atleta = PlanoPromptArquetipos.todos().getFirst().atleta();
    private final PlanoMetaDados meta = PlanoPromptArquetipos.todos().getFirst().meta();

    @Test
    @DisplayName("sem prova na semana, a saída é byte-idêntica à de antes")
    void semProvaNaoMuda() {
        String antes = formatter.formatarDisponibilidade(atleta, meta, INICIO, null);
        String depois = formatter.formatarDisponibilidade(atleta, meta, INICIO, null, List.of());

        assertThat(depois).isEqualTo(antes);
    }

    @Test
    @DisplayName("com prova no domingo, o bloco nomeia o dia como dia a cobrir e deixa a lista do atleta intacta")
    void nomeiaODiaDaProva() {
        String texto = formatter.formatarDisponibilidade(atleta, meta, INICIO, null, List.of(DiaSemana.DOMINGO));

        assertThat(texto).contains("**Dia de prova na semana:** Domingo (prova)");
        assertThat(texto).contains("**Dias disponíveis para treinar (configuração do atleta):** "
                + "Segunda-feira, Quarta-feira, Sexta-feira, Sábado\n");
    }

    @Test
    @DisplayName("SEMANA_ATUAL: o dia da prova na lista de dias restantes leva a marca (prova); os demais não")
    void marcaDiaDaProvaEntreOsRestantes() {
        String texto = formatter.formatarDisponibilidade(atleta, meta, INICIO,
                List.of(DiaSemana.SEXTA, DiaSemana.SABADO, DiaSemana.DOMINGO), List.of(DiaSemana.DOMINGO));

        assertThat(texto).contains("**Dias restantes disponíveis para treino nesta semana:** "
                + "Sexta-feira, Sábado, Domingo (prova)\n");
        assertThat(texto).contains("**Total de dias restantes:** 3");
    }

    @Test
    @DisplayName("prova num dia que o atleta já tem como disponível: o dia também é nomeado")
    void provaEmDiaJaDisponivel() {
        String texto = formatter.formatarDisponibilidade(atleta, meta, INICIO, null, List.of(DiaSemana.SABADO));

        assertThat(texto).contains("**Dia de prova na semana:** Sábado (prova)");
    }

    @Test
    @DisplayName("duas provas em dias diferentes: ambos nomeados, na ordem recebida")
    void duasProvas() {
        String texto = formatter.formatarDisponibilidade(atleta, meta, INICIO, null,
                List.of(DiaSemana.SABADO, DiaSemana.DOMINGO));

        assertThat(texto).contains("**Dia de prova na semana:** Sábado (prova), Domingo (prova)");
    }
}
