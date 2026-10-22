package br.com.menthoros.backend.services.plano;

import br.com.menthoros.backend.dto.llm.TreinoPlanejadoLlmDto;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.Prova;
import br.com.menthoros.backend.mapper.TreinoMapperImpl;
import br.com.menthoros.backend.repository.ProvaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@code garantirProvasNaSemana} (fix-planner-ignora-prova-na-semana, task 2.6): o cadastro aceita
 * duas provas na mesma data e o javadoc promete um PROVA por prova — mas o laço removia todos os
 * treinos do dia a cada prova, então a segunda apagava a primeira.
 */
@DisplayName("ProvaNoPlanoService.garantirProvasNaSemana")
class ProvaNoPlanoServiceGarantirTest {

    private static final LocalDate INICIO = LocalDate.of(2026, 10, 12);
    private static final LocalDate FIM = LocalDate.of(2026, 10, 18);
    private static final LocalDate SABADO = LocalDate.of(2026, 10, 17);
    private static final LocalDate DOMINGO = LocalDate.of(2026, 10, 18);

    private ProvaRepository provaRepository;
    private ProvaNoPlanoService service;
    private final Atleta atleta = Atleta.builder().paceLimiar(BigDecimal.valueOf(5.0)).build();

    @BeforeEach
    void setUp() {
        provaRepository = mock(ProvaRepository.class);
        service = new ProvaNoPlanoService(provaRepository, new TreinoMapperImpl(null, null), null, null);
    }

    @Test
    @DisplayName("CA11: duas provas no mesmo dia → duas PROVAs, uma por provaId")
    void duasProvasNoMesmoDia() {
        Prova a = prova(DOMINGO, "Prova A");
        Prova b = prova(DOMINGO, "Prova B");
        quandoAsProvasDaSemanaSao(a, b);

        var resultado = service.garantirProvasNaSemana(
                List.of(treino("SEGUNDA", "FACIL"), treino("DOMINGO", "LONGO")), atleta, INICIO, FIM);

        assertThat(resultado).filteredOn(t -> "DOMINGO".equals(t.diaSemana()))
                .extracting(TreinoPlanejadoLlmDto::tipoTreino, TreinoPlanejadoLlmDto::provaId)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("PROVA", a.getId()),
                        org.assertj.core.groups.Tuple.tuple("PROVA", b.getId()));
        assertThat(resultado).filteredOn(t -> "SEGUNDA".equals(t.diaSemana())).hasSize(1);
    }

    @Test
    @DisplayName("CA11: reaplicar sobre a saída não duplica nem perde nenhuma prova")
    void reaplicarEIdempotente() {
        quandoAsProvasDaSemanaSao(prova(DOMINGO, "Prova A"), prova(DOMINGO, "Prova B"));

        var primeira = service.garantirProvasNaSemana(List.of(treino("SEGUNDA", "FACIL")), atleta, INICIO, FIM);
        var segunda = service.garantirProvasNaSemana(primeira, atleta, INICIO, FIM);

        assertThat(segunda).extracting(TreinoPlanejadoLlmDto::provaId)
                .containsExactlyInAnyOrderElementsOf(primeira.stream().map(TreinoPlanejadoLlmDto::provaId).toList());
        assertThat(segunda).filteredOn(t -> "PROVA".equals(t.tipoTreino())).hasSize(2);
    }

    @Test
    @DisplayName("lista que já traz a PROVA (vinda da validação) + segunda prova do dia → continua uma por provaId")
    void entradaJaComProva() {
        Prova a = prova(DOMINGO, "Prova A");
        Prova b = prova(DOMINGO, "Prova B");
        quandoAsProvasDaSemanaSao(a, b);
        var jaComA = List.of(treino("SEGUNDA", "FACIL"), service.construirTreinoProva(a, atleta));

        var resultado = service.garantirProvasNaSemana(jaComA, atleta, INICIO, FIM);

        assertThat(resultado).filteredOn(t -> "PROVA".equals(t.tipoTreino()))
                .extracting(TreinoPlanejadoLlmDto::provaId)
                .containsExactlyInAnyOrder(a.getId(), b.getId());
    }

    @Test
    @DisplayName("provas em dias diferentes: cada uma substitui o treino do seu dia, os demais ficam")
    void provasEmDiasDiferentes() {
        quandoAsProvasDaSemanaSao(prova(SABADO, "Prova A"), prova(DOMINGO, "Prova B"));

        var resultado = service.garantirProvasNaSemana(
                List.of(treino("SEGUNDA", "FACIL"), treino("SABADO", "LONGO"), treino("DOMINGO", "FACIL")),
                atleta, INICIO, FIM);

        assertThat(resultado).extracting(TreinoPlanejadoLlmDto::diaSemana, TreinoPlanejadoLlmDto::tipoTreino)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("SEGUNDA", "FACIL"),
                        org.assertj.core.groups.Tuple.tuple("SABADO", "PROVA"),
                        org.assertj.core.groups.Tuple.tuple("DOMINGO", "PROVA"));
    }

    @Test
    @DisplayName("sem prova na semana, devolve a lista como veio")
    void semProva() {
        quandoAsProvasDaSemanaSao();
        var treinos = List.of(treino("SEGUNDA", "FACIL"));

        assertThat(service.garantirProvasNaSemana(treinos, atleta, INICIO, FIM)).isSameAs(treinos);
    }

    private void quandoAsProvasDaSemanaSao(Prova... provas) {
        when(provaRepository.findByAtletaAndDataProvaBetweenOrderByDataProvaAsc(any(), any(), any()))
                .thenReturn(List.of(provas));
    }

    private static Prova prova(LocalDate data, String nome) {
        Prova p = new Prova();
        p.setId(UUID.randomUUID());
        p.setNomeProva(nome);
        p.setDataProva(data);
        p.setDistanciaKm(new BigDecimal("21.097"));
        return p;
    }

    private static TreinoPlanejadoLlmDto treino(String dia, String tipo) {
        return new TreinoPlanejadoLlmDto(dia, tipo, null, null, null, null, null, null, null, null, List.of());
    }
}
