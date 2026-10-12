package br.com.menthoros.backend.services.prompt;

import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.Prova;
import br.com.menthoros.backend.enums.DiaSemana;
import br.com.menthoros.backend.enums.ProvaStatus;
import br.com.menthoros.backend.repository.ProvaRepository;
import br.com.menthoros.backend.services.helper.TreinoHistoricoProvider;
import br.com.menthoros.backend.services.helper.TreinoHistoricoProvider.ContextoTreino;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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

    private ProvaRepository provaRepository;

    @BeforeEach
    void setUp() {
        provaRepository = mock(ProvaRepository.class);
    }

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

    // ── task 3.3: dia seguinte à prova do último dia da semana anterior ──────────────────────────

    private static final LocalDate DOMINGO_ANTERIOR = INICIO_PROXIMA.minusDays(1);       // 18/01/2026

    @Test
    @DisplayName("CA13: prova no domingo anterior e segunda entre os dias a cobrir → posProva preenchido")
    void posProvaPreenchido() {
        quandoHaProvaEm(DOMINGO_ANTERIOR, "Meia de Domingo");

        var gerado = montar(INICIO_PROXIMA, null, null, List.of());

        assertThat(gerado.cobertura().posProva()).isNotNull().satisfies(pos -> {
            assertThat(pos.diaDoPlano()).isEqualTo(DiaSemana.SEGUNDA);
            assertThat(pos.nomeProva()).isEqualTo("Meia de Domingo");
            assertThat(pos.data()).isEqualTo(DOMINGO_ANTERIOR);
        });
    }

    @Test
    @DisplayName("CA13: sem prova no dia anterior → posProva nulo")
    void semProvaNoDiaAnterior() {
        var gerado = montar(INICIO_PROXIMA, null, null, List.of());

        assertThat(gerado.cobertura().posProva()).isNull();
    }

    @Test
    @DisplayName("CA13: segunda fora dos dias a cobrir → posProva nulo e a consulta nem é feita")
    void segundaForaDosDias() {
        quandoHaProvaEm(DOMINGO_ANTERIOR, "Meia de Domingo");

        var gerado = montar(INICIO_PROXIMA, null, null, List.of(), List.of(DiaSemana.TERCA, DiaSemana.QUINTA));

        assertThat(gerado.cobertura().posProva()).isNull();
        verify(provaRepository, never()).findByAtletaAndDataProvaBetweenOrderByDataProvaAsc(
                any(), eq(DOMINGO_ANTERIOR), eq(DOMINGO_ANTERIOR));
    }

    @Test
    @DisplayName("CA13: SEMANA_ATUAL com a segunda já passada → posProva nulo")
    void semanaAtualComSegundaPassada() {
        quandoHaProvaEm(INICIO_ATUAL.minusDays(1), "Meia de Domingo");

        var gerado = montar(INICIO_ATUAL, List.of(DiaSemana.SEXTA, DiaSemana.SABADO), null, List.of());

        assertThat(gerado.cobertura().posProva()).isNull();
    }

    @Test
    @DisplayName("CA13: com skeleton não há cobertura, logo nem posProva")
    void comSkeletonNaoHaCobertura() {
        quandoHaProvaEm(DOMINGO_ANTERIOR, "Meia de Domingo");

        var skeleton = org.mockito.Mockito.mock(br.com.menthoros.backend.domain.planner.WeekPlanSkeleton.class);
        var gerado = montar(INICIO_PROXIMA, null, null, List.of(), DIAS_UTEIS, skeleton);

        assertThat(gerado.cobertura()).isNull();
        assertThat(gerado.user()).doesNotContain("DIA APOS PROVA DA SEMANA ANTERIOR");
        verify(provaRepository, never()).findByAtletaAndDataProvaBetweenOrderByDataProvaAsc(
                any(), eq(DOMINGO_ANTERIOR), eq(DOMINGO_ANTERIOR));
    }

    @Test
    @DisplayName("task 3.4: com posProva, o prompt traz o bloco do dia após a prova; sem ele, não")
    void blocoAparecePeloPosProva() {
        quandoHaProvaEm(DOMINGO_ANTERIOR, "Meia de Domingo");

        assertThat(montar(INICIO_PROXIMA, null, null, List.of()).user())
                .contains("DIA APOS PROVA DA SEMANA ANTERIOR").contains("Meia de Domingo");
        assertThat(montar(INICIO_PROXIMA, null, null, List.of(), List.of(DiaSemana.TERCA)).user())
                .doesNotContain("DIA APOS PROVA DA SEMANA ANTERIOR");
    }

    private void quandoHaProvaEm(LocalDate data, String nome) {
        Prova p = prova(data, false);
        p.setNomeProva(nome);
        when(provaRepository.findByAtletaAndDataProvaBetweenOrderByDataProvaAsc(any(), eq(data), eq(data)))
                .thenReturn(List.of(p));
    }

    private PlanoTreinoPromptBuilder.PromptGerado montar(LocalDate inicioSemana, List<DiaSemana> diasEfetivos,
                                                         Prova provaAlvo, List<Prova> preparatorias) {
        return montar(inicioSemana, diasEfetivos, provaAlvo, preparatorias, DIAS_UTEIS);
    }

    private PlanoTreinoPromptBuilder.PromptGerado montar(LocalDate inicioSemana, List<DiaSemana> diasEfetivos,
                                                         Prova provaAlvo, List<Prova> preparatorias,
                                                         List<DiaSemana> diasDoAtleta) {
        return montar(inicioSemana, diasEfetivos, provaAlvo, preparatorias, diasDoAtleta, null);
    }

    private PlanoTreinoPromptBuilder.PromptGerado montar(LocalDate inicioSemana, List<DiaSemana> diasEfetivos,
                                                         Prova provaAlvo, List<Prova> preparatorias,
                                                         List<DiaSemana> diasDoAtleta,
                                                         br.com.menthoros.backend.domain.planner.WeekPlanSkeleton skeleton) {
        var arquetipo = PlanoPromptArquetipos.todos().getFirst();
        Atleta atleta = arquetipo.atleta().toBuilder().diasDisponiveis(diasDoAtleta).build();
        ContextoTreino base = arquetipo.contexto();
        ContextoTreino contexto = new ContextoTreino(HOJE, base.treinosUltimas4Semanas(), preparatorias,
                base.checkinsUltimos7Dias());

        TreinoHistoricoProvider provider = mock(TreinoHistoricoProvider.class);
        when(provider.prepararContexto(any())).thenReturn(contexto);
        PlanoTreinoPromptBuilder builder = PlanoPromptArquetipos.builder(provider, provaRepository);

        try (MockedStatic<LocalDate> now = mockStatic(LocalDate.class, CALLS_REAL_METHODS)) {
            now.when(LocalDate::now).thenReturn(HOJE);
            return builder.buildOptimizedPrompt(atleta, arquetipo.meta(), provaAlvo, inicioSemana,
                    diasEfetivos, null, null, skeleton, false);
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
