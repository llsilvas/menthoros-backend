package com.menthoros.services.helper;

import com.menthoros.dto.llm.TreinoPlanejadoLlmDto;
import com.menthoros.enums.DiaSemana;
import com.menthoros.enums.ModoGeracaoPlano;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedistribuicaoTreinoHelperTest {

    @Mock
    private RegraGeracaoTreino regraGeracaoTreinoMock;

    @InjectMocks
    private RedistribuicaoTreinoHelper redistribuicaoTreinoHelper;

    @Test
    @DisplayName("Deve filtrar treinos Longo e Intervalados no meio da semana")
    void deveFiltrarTreinosIncompativeisNoMeioDaSemana() {
        LocalDate quarta = LocalDate.of(2025, 10, 8);
        LocalDate semanaInicio = quarta.with(DayOfWeek.MONDAY);
        LocalDate semanaFim = semanaInicio.plusDays(6);

        when(regraGeracaoTreinoMock.isMeioSemana(quarta)).thenReturn(true);

        List<TreinoPlanejadoLlmDto> treinos = List.of(
                criarTreinoMock("QUINTA", "LONGO"),
                criarTreinoMock("SEXTA", "INTERVALADO"),
                criarTreinoMock("SABADO", "CONTINUO"),
                criarTreinoMock("DOMINGO", "REGENERATIVO")
        );

        List<DiaSemana> diasDisponiveis = List.of(
                DiaSemana.QUINTA, DiaSemana.SEXTA, DiaSemana.SABADO, DiaSemana.DOMINGO
        );

        var resultado = redistribuicaoTreinoHelper.redistribuirTreinos(
                treinos, diasDisponiveis, quarta, semanaInicio, semanaFim,
                ModoGeracaoPlano.SEMANA_ATUAL
        );

        assertEquals(2, resultado.size());
        assertTrue(resultado.stream().noneMatch(t ->
                t.tipoTreino().equals("LONGO") || t.tipoTreino().equals("INTERVALADO")));
    }

    @Test
    @DisplayName("Deve remover dos treinos os treinos intervalados e longo, caso seja meio da semana")
    void deveRemoverTreinosIncompativeisNoMeioDaSemana() {
        LocalDate quarta = LocalDate.of(2025, 10, 8);
        LocalDate semanaInicio = quarta.with(DayOfWeek.MONDAY);
        LocalDate semanaFim = semanaInicio.plusDays(6);

        when(regraGeracaoTreinoMock.isMeioSemana(quarta)).thenReturn(true);

        List<TreinoPlanejadoLlmDto> treinos = List.of(
                criarTreinoMock("SEGUNDA", "REGENERATIVO"),
                criarTreinoMock("TERCA", "CONTINUO"),
                criarTreinoMock("QUINTA", "INTERVALADO"),
                criarTreinoMock("SABADO", "LONGO")
        );

        List<DiaSemana> diasDisponiveis = List.of(
                DiaSemana.SEGUNDA, DiaSemana.TERCA, DiaSemana.QUINTA, DiaSemana.SABADO);

        var resultado = redistribuicaoTreinoHelper.redistribuirTreinos(
                treinos, diasDisponiveis, quarta, semanaInicio, semanaFim,
                ModoGeracaoPlano.SEMANA_ATUAL
        );

        assertEquals(2, resultado.size());

    }
    @Test
    @DisplayName("Deve retornar lista vazia quando não houver dias disponíveis")
    void deveRetornarListaVaziaQuandoNaoHouverDiasDisponiveis() {
        LocalDate hoje = LocalDate.of(2025, 10, 8);
        LocalDate semanaInicio = hoje.with(DayOfWeek.MONDAY);
        LocalDate semanaFim = semanaInicio.plusDays(6);

        List<TreinoPlanejadoLlmDto> treinos = List.of(
                criarTreinoMock("SEGUNDA", "CONTINUO")
        );

        List<DiaSemana> diasDisponiveis = List.of(); // Sem dias

        var resultado = redistribuicaoTreinoHelper.redistribuirTreinos(
                treinos, diasDisponiveis, hoje, semanaInicio, semanaFim,
                ModoGeracaoPlano.SEMANA_ATUAL
        );

        assertTrue(resultado.isEmpty());
    }

    @Test
    @DisplayName("Deve filtrar dias que já passaram na semana atual")
    void deveFiltrarDiasPassadosNaSemanaAtual() {
        LocalDate quinta = LocalDate.of(2025, 10, 9); // Quinta
        LocalDate semanaInicio = quinta.with(DayOfWeek.MONDAY);
        LocalDate semanaFim = semanaInicio.plusDays(6);

        List<TreinoPlanejadoLlmDto> treinos = List.of(
                criarTreinoMock("SEGUNDA", "CONTINUO"),
                criarTreinoMock("TERCA", "FARTLEK"),
                criarTreinoMock("SEXTA", "LONGO")
        );

        List<DiaSemana> diasDisponiveis = List.of(
                DiaSemana.SEGUNDA, DiaSemana.TERCA, DiaSemana.SEXTA
        );

        var resultado = redistribuicaoTreinoHelper.redistribuirTreinos(
                treinos, diasDisponiveis, quinta, semanaInicio, semanaFim,
                ModoGeracaoPlano.SEMANA_ATUAL
        );

        // Deve considerar apenas Sexta (dias posteriores a Quinta)
        assertTrue(resultado.size() > 0);
        assertTrue(resultado.stream().anyMatch(t -> t.diaSemana().equals("SEXTA")));
    }

    @Test
    @DisplayName("Deve manter todos os dias quando modo for PROXIMA_SEMANA")
    void deveManterTodosDiasQuandoModoForProximaSemana() {
        LocalDate quinta = LocalDate.of(2025, 10, 9);
        LocalDate semanaInicio = quinta.plusWeeks(1).with(DayOfWeek.MONDAY);
        LocalDate semanaFim = semanaInicio.plusDays(6);

        List<TreinoPlanejadoLlmDto> treinos = List.of(
                criarTreinoMock("SEGUNDA", "CONTINUO"),
                criarTreinoMock("TERCA", "FARTLEK"),
                criarTreinoMock("QUARTA", "INTERVALADO")
        );

        List<DiaSemana> diasDisponiveis = List.of(
                DiaSemana.SEGUNDA, DiaSemana.TERCA, DiaSemana.QUARTA
        );

        var resultado = redistribuicaoTreinoHelper.redistribuirTreinos(
                treinos, diasDisponiveis, quinta, semanaInicio, semanaFim,
                ModoGeracaoPlano.PROXIMA_SEMANA
        );

        assertEquals(3, resultado.size());
    }

    @Test
    @DisplayName("Deve lidar com lista de treinos vazia")
    void deveLidarComListaTreinosVazia() {
        LocalDate hoje = LocalDate.now();
        LocalDate semanaInicio = hoje.with(DayOfWeek.MONDAY);
        LocalDate semanaFim = semanaInicio.plusDays(6);

        List<TreinoPlanejadoLlmDto> treinos = List.of();
        List<DiaSemana> diasDisponiveis = List.of(DiaSemana.SEGUNDA, DiaSemana.QUARTA);

        var resultado = redistribuicaoTreinoHelper.redistribuirTreinos(
                treinos, diasDisponiveis, hoje, semanaInicio, semanaFim,
                ModoGeracaoPlano.SEMANA_ATUAL
        );

        assertTrue(resultado.isEmpty());
    }

    @Test
    @DisplayName("Deve lidar com mais treinos que dias disponíveis")
    void deveLidarComMaisTreinosQueDiasDisponiveis() {
        LocalDate segunda = LocalDate.of(2025, 10, 6);
        LocalDate semanaInicio = segunda;
        LocalDate semanaFim = semanaInicio.plusDays(6);

        List<TreinoPlanejadoLlmDto> treinos = List.of(
                criarTreinoMock("TERCA", "CONTINUO"),
                criarTreinoMock("QUARTA", "FARTLEK"),
                criarTreinoMock("QUINTA", "REGENERATIVO"),
                criarTreinoMock("SEXTA", "CONTINUO"),
                criarTreinoMock("SABADO", "LONGO")
        );

        // Apenas 2 dias disponíveis
        List<DiaSemana> diasDisponiveis = List.of(DiaSemana.QUARTA, DiaSemana.SABADO);

        var resultado = redistribuicaoTreinoHelper.redistribuirTreinos(
                treinos, diasDisponiveis, segunda, semanaInicio, semanaFim,
                ModoGeracaoPlano.SEMANA_ATUAL
        );

        // Deve distribuir máximo de 2 treinos
        assertTrue(resultado.size() <= 2);
    }


    private TreinoPlanejadoLlmDto criarTreinoMock(String dia, String tipo) {
        return new TreinoPlanejadoLlmDto(
                dia, tipo, "140-160% FCmáx", 100, 1.0, 7,
                "Treino mock", "60", 10.0, "5:00-5:30/km", null
        );
    }
}