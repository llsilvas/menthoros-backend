package br.com.menthoros.backend.domain.plano;

/**
 * Dia de descanso prescrito num plano semanal, como fica persistido em
 * {@code tb_plano_semanal.rest_days} (add-descanso-explicito-por-fadiga).
 *
 * @param dayOfWeek nome do enum {@code DiaSemana} ("SEGUNDA"…"DOMINGO")
 * @param reason    motivo, citando o sinal com valor e limiar
 */
public record RestDay(String dayOfWeek, String reason) {
}
