package br.com.menthoros.backend.domain.planner;

import java.math.BigDecimal;

/**
 * Km de uma semana do histórico usado no volume de partida. {@code weekStart} em ISO (segunda-feira):
 * vai para JSONB e assim fica legível sem depender do formato de data do mapper do Hibernate.
 */
public record WeeklyVolume(String weekStart, BigDecimal km) {
}
