package br.com.menthoros.backend.services.prompt;

import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.Prova;
import br.com.menthoros.backend.enums.DiaSemana;
import br.com.menthoros.backend.enums.ProvaStatus;
import br.com.menthoros.backend.services.helper.TreinoHistoricoProvider;
import br.com.menthoros.backend.services.helper.TreinoHistoricoProvider.ContextoTreino;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * O dia de uma prova da semana conta como dia efetivo da cobertura
 * (fix-planner-ignora-prova-na-semana, task 1.1): sem isso o validador rejeita a PROVA no dia da
 * prova por "dia indisponível" e o turno de reparo empurra o longo para a véspera.
 */
@DisplayName("PlanoTreinoPromptBuilder — dia de prova é dia efetivo da cobertura")
class PlanoTreinoPromptBuilderDiaDeProvaTest {

    private static final LocalDate HOJE = PlanoPromptArquetipos.HOJE;                // quinta 15/01/2026
    private static final LocalDate INICIO_PROXIMA = PlanoPromptArquetipos.INICIO_SEMANA; // segunda 19/01/2026
    private static final LocalDate DOMINGO_PROXIMA = INICIO_PROXIMA.plusDays(6);       // 25/01/2026
    private static final LocalDate INICIO_ATUAL = LocalDate.of(2026, 1, 12);            // segunda desta semana

    private static final List<DiaSemana> DIAS_UTEIS =
            List.of(DiaSemana.SEGUNDA, DiaSemana.QUARTA, DiaSemana.SEXTA, DiaSemana.SABADO);

    @Test
    @DisplayName("PROXIMA_SEMANA: prova preparatória no domingo entra nos dias efetivos; semanaAtual segue false")
    void provaPreparatoriaNaProximaSemana() {
        var gerado = montar(INICIO_PROXIMA, null, null, List.of(prova(DOMINGO_PROXIMA, false)));

        assertThat(gerado.cobertura().effectiveDays())
                .containsExactlyInAnyOrder(DiaSemana.SEGUNDA, DiaSemana.QUARTA, DiaSemana.SEXTA,
                        DiaSemana.SABADO, DiaSemana.DOMINGO);
        assertThat(gerado.cobertura().semanaAtual()).isFalse();
    }

    @Test
    @DisplayName("prova-alvo passada como argumento também entra")
    void provaAlvoEntra() {
        var gerado = montar(INICIO_PROXIMA, null, prova(DOMINGO_PROXIMA, true), List.of());

        assertThat(gerado.cobertura().effectiveDays()).contains(DiaSemana.DOMINGO);
    }

    @Test
    @DisplayName("dia de prova que já é dia disponível não duplica")
    void naoDuplica() {
        var gerado = montar(INICIO_PROXIMA, null, null,
                List.of(prova(INICIO_PROXIMA.plusDays(5), false)));   // sábado, já disponível

        assertThat(gerado.cobertura().effectiveDays()).containsExactlyInAnyOrderElementsOf(DIAS_UTEIS);
    }

    @Test
    @DisplayName("SEMANA_ATUAL: prova depois de hoje entra e semanaAtual segue true")
    void provaFuturaNaSemanaAtual() {
        var gerado = montar(INICIO_ATUAL, List.of(DiaSemana.SEXTA, DiaSemana.SABADO), null,
                List.of(prova(INICIO_ATUAL.plusDays(6), false)));    // domingo 18/01

        assertThat(gerado.cobertura().effectiveDays())
                .containsExactlyInAnyOrder(DiaSemana.SEXTA, DiaSemana.SABADO, DiaSemana.DOMINGO);
        assertThat(gerado.cobertura().semanaAtual()).isTrue();
    }

    @Test
    @DisplayName("CA6: SEMANA_ATUAL — prova já passada (terça) não entra nos dias efetivos")
    void provaPassadaNaoEntra() {
        var gerado = montar(INICIO_ATUAL, List.of(DiaSemana.SEXTA, DiaSemana.SABADO), null,
                List.of(prova(INICIO_ATUAL.plusDays(1), false)));    // terça 13/01 < hoje

        assertThat(gerado.cobertura().effectiveDays())
                .containsExactlyInAnyOrder(DiaSemana.SEXTA, DiaSemana.SABADO);
    }

    @Test
    @DisplayName("CA6: SEMANA_ATUAL — prova HOJE não entra (hoje já conta como dia passado, como em diaSemanaJaPassou)")
    void provaHojeNaoEntra() {
        var gerado = montar(INICIO_ATUAL, List.of(DiaSemana.SEXTA, DiaSemana.SABADO), null,
                List.of(prova(HOJE, false)));

        assertThat(gerado.cobertura().effectiveDays())
                .containsExactlyInAnyOrder(DiaSemana.SEXTA, DiaSemana.SABADO);
    }

    @Test
    @DisplayName("prova cancelada não entra")
    void provaCanceladaNaoEntra() {
        Prova cancelada = prova(DOMINGO_PROXIMA, false);
        cancelada.setStatusProva(ProvaStatus.CANCELADA);

        var gerado = montar(INICIO_PROXIMA, null, null, List.of(cancelada));

        assertThat(gerado.cobertura().effectiveDays()).containsExactlyInAnyOrderElementsOf(DIAS_UTEIS);
    }

    @Test
    @DisplayName("prova-alvo fora da semana (meses à frente) não entra")
    void provaForaDaSemanaNaoEntra() {
        var gerado = montar(INICIO_PROXIMA, null, prova(DOMINGO_PROXIMA.plusMonths(3), true), List.of());

        assertThat(gerado.cobertura().effectiveDays()).containsExactlyInAnyOrderElementsOf(DIAS_UTEIS);
    }

    @Test
    @DisplayName("sem prova na semana, os dias efetivos são exatamente os de antes")
    void semProvaNaoMuda() {
        var gerado = montar(INICIO_PROXIMA, null, null, List.of());

        assertThat(gerado.cobertura().effectiveDays()).containsExactlyInAnyOrderElementsOf(DIAS_UTEIS);
    }

    @Test
    @DisplayName("task 2.1: o contexto carrega a PROVA determinística de cada prova da janela, com provaId")
    void contextoCarregaOsDtosDaProva() {
        Prova domingo = prova(DOMINGO_PROXIMA, false);
        var gerado = montar(INICIO_PROXIMA, null, null, List.of(domingo));

        assertThat(gerado.cobertura().provasDaSemana()).singleElement().satisfies(dto -> {
            assertThat(dto.diaSemana()).isEqualTo("DOMINGO");
            assertThat(dto.tipoTreino()).isEqualTo("PROVA");
            assertThat(dto.provaId()).isEqualTo(domingo.getId());
        });
    }

    @Test
    @DisplayName("task 2.1: prova passada, de hoje, cancelada ou sem prova → lista vazia, nunca null")
    void foraDaJanelaListaVazia() {
        Prova cancelada = prova(DOMINGO_PROXIMA, false);
        cancelada.setStatusProva(ProvaStatus.CANCELADA);

        assertThat(montar(INICIO_PROXIMA, null, null, List.of(cancelada)).cobertura().provasDaSemana()).isEmpty();
        assertThat(montar(INICIO_PROXIMA, null, null, List.of()).cobertura().provasDaSemana()).isEmpty();
        assertThat(montar(INICIO_ATUAL, List.of(DiaSemana.SEXTA), null, List.of(prova(HOJE, false)))
                .cobertura().provasDaSemana()).isEmpty();
    }

    private PlanoTreinoPromptBuilder.PromptGerado montar(LocalDate inicioSemana, List<DiaSemana> diasEfetivos,
                                                         Prova provaAlvo, List<Prova> preparatorias) {
        var arquetipo = PlanoPromptArquetipos.todos().getFirst();
        Atleta atleta = arquetipo.atleta().toBuilder().diasDisponiveis(DIAS_UTEIS).build();
        ContextoTreino base = arquetipo.contexto();
        ContextoTreino contexto = new ContextoTreino(HOJE, base.treinosUltimas4Semanas(), preparatorias,
                base.checkinsUltimos7Dias());

        TreinoHistoricoProvider provider = mock(TreinoHistoricoProvider.class);
        when(provider.prepararContexto(any())).thenReturn(contexto);
        PlanoTreinoPromptBuilder builder = PlanoPromptArquetipos.builder(provider);

        try (MockedStatic<LocalDate> now = mockStatic(LocalDate.class, CALLS_REAL_METHODS)) {
            now.when(LocalDate::now).thenReturn(HOJE);
            return builder.buildOptimizedPrompt(atleta, arquetipo.meta(), provaAlvo, inicioSemana,
                    diasEfetivos, null, null, null, false);
        }
    }

    private static Prova prova(LocalDate data, boolean alvo) {
        Prova p = new Prova();
        p.setId(java.util.UUID.randomUUID());
        p.setNomeProva("Prova " + data);
        p.setDataProva(data);
        p.setDistanciaKm(new BigDecimal("21.097"));
        p.setProvaAlvo(alvo);
        return p;
    }
}
