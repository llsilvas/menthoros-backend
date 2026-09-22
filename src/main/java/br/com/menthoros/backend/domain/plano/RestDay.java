package br.com.menthoros.backend.domain.plano;

/**
 * Dia de descanso prescrito num plano semanal, como fica persistido em
 * {@code tb_plano_semanal.rest_days} (add-descanso-explicito-por-fadiga).
 *
 * @param dayOfWeek nome do enum {@code DiaSemana} ("SEGUNDA"…"DOMINGO")
 * @param reason    motivo, citando o sinal com valor e limiar
 */
public record RestDay(String dayOfWeek, String reason) {

    /**
     * Normaliza o dia na construção — o valor chega como texto da LLM e é comparado com
     * {@code DiaSemana.name()} em vários pontos (validação, remoção por prova, remoção pelo coach).
     * Normalizar aqui evita que um " quinta " escape para o banco e para a API quando a regra de
     * cobertura está desligada pelo kill-switch (achado do /qa).
     */
    public RestDay {
        dayOfWeek = dayOfWeek == null ? null : dayOfWeek.trim().toUpperCase(java.util.Locale.ROOT);
        reason = reason == null ? null : reason.trim();
    }
}
