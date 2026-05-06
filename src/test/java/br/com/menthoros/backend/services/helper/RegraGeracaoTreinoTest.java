package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.enums.DiaSemana;
import br.com.menthoros.backend.enums.ModoGeracaoPlano;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RegraGeracaoTreinoTest {

    @Test
    @DisplayName("Deve identificar meio semana quando for quarta ou posterior")
    void deveIdentificarMeioSemanaQuandoForQuartaOuPosterior() {
        RegraGeracaoTreino regraGeracaoTreino = new RegraGeracaoTreino();

        assertAll(
                () -> assertTrue(regraGeracaoTreino.isMeioSemana(LocalDate.of(2025, 10, 8))), // Quarta-feira
                () -> assertTrue(regraGeracaoTreino.isMeioSemana(LocalDate.of(2025, 10, 9))), // Quinta-feira
                () -> assertTrue(regraGeracaoTreino.isMeioSemana(LocalDate.of(2025, 10, 10))), // Sexta-feira
                () -> assertTrue(regraGeracaoTreino.isMeioSemana(LocalDate.of(2025, 10, 11))), // Sábado
                () -> assertTrue(regraGeracaoTreino.isMeioSemana(LocalDate.of(2025, 10, 12))) // Domingo
        );
    }

    @Test
    @DisplayName("Não deve identificar meio semana quando for segunda ou terça")
    void naoDeveIdentificarMeioSemanaQuandoForSegundaOuTerca() {
        RegraGeracaoTreino regraGeracaoTreino = new RegraGeracaoTreino();
        assertAll(
                () -> assertFalse(regraGeracaoTreino.isMeioSemana(LocalDate.of(2025, 10, 7))), // Segunda-feira
                () -> assertFalse(regraGeracaoTreino.isMeioSemana(LocalDate.of(2025, 10, 6))) // Terça-feira
        );
    }

    @Test
    @DisplayName("Deve retornar todos os dias quando modo for PROXIMA_SEMANA")
    void deveRetornarTodosOsDiasQuandoModoForPROXIMA_SEMANA() {
        RegraGeracaoTreino regraGeracaoTreino = new RegraGeracaoTreino();

        LocalDate hoje = LocalDate.of(2025, 10, 8);

        List<DiaSemana> dias =  List.of(
                DiaSemana.SEGUNDA, DiaSemana.QUARTA, DiaSemana.SEXTA
        );

        List<DiaSemana> resultado = regraGeracaoTreino.filtrarDiasDisponiveis(
                dias, hoje, ModoGeracaoPlano.PROXIMA_SEMANA
        );

        assertEquals(3, resultado.size());
        assertEquals(dias, resultado);

    }

    @Test
    @DisplayName("Deve filtrar dias que ja passaram quando modo for SEMANA_ATUAL")
    void deveFiltrarDiasPassadosQuandoModoForSemanaAtual() {
        RegraGeracaoTreino regraGeracaoTreino = new RegraGeracaoTreino();

        LocalDate hoje = LocalDate.of(2025, 10, 8);

        List<DiaSemana> dias =  List.of(
                DiaSemana.SEGUNDA,
                DiaSemana.TERCA,
                DiaSemana.QUARTA,
                DiaSemana.QUINTA,
                DiaSemana.SEXTA,
                DiaSemana.SABADO
        );

        List<DiaSemana> resultado = regraGeracaoTreino.filtrarDiasDisponiveis(
                dias, hoje, ModoGeracaoPlano.SEMANA_ATUAL
        );

        assertEquals(3, resultado.size());
        assertTrue(resultado.contains(DiaSemana.SEXTA));
        assertTrue(resultado.contains(DiaSemana.SABADO));

    }
}