package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.enums.DiaSemana;

import java.time.LocalDate;

/**
 * Prova no dia anterior ao início da semana do plano: o primeiro dia do plano é o dia seguinte a
 * ela. O plano é segunda a domingo, então uma prova no domingo tem o D+1 na segunda do plano
 * seguinte, que não a enxerga (fix-planner-ignora-prova-na-semana, Decisão 4b).
 *
 * @param diaDoPlano dia do plano que segue a prova (o {@code DiaSemana} de {@code inicioSemana})
 * @param nomeProva  nome cadastrado da prova
 * @param data       data da prova ({@code inicioSemana − 1})
 */
public record PosProvaSemanaAnterior(DiaSemana diaDoPlano, String nomeProva, LocalDate data) {
}
