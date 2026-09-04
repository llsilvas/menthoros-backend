package br.com.menthoros.backend.dto.jackson;

import java.time.Duration;
import java.time.format.DateTimeParseException;

/**
 * Formata/parseia {@link Duration} como {@code "HH:mm:ss"} — o contrato JSON de
 * {@code tempoObjetivo}/{@code tempoRealizado} de {@code Prova} (prova-no-plano-semanal, D6),
 * que precisou continuar assim para o front não mudar mesmo com o domínio migrando de
 * {@code LocalTime} para {@code Duration}. Sempre inclui horas, ao contrário de
 * {@code TreinoMapper.durationToString} (que omite "00:" quando não há hora) — os exemplos do
 * contrato ("01:45:00") sempre tiveram os três campos.
 */
public final class DurationHhMmSsFormat {

    private DurationHhMmSsFormat() {
    }

    public static String format(Duration duration) {
        if (duration == null) {
            return null;
        }
        long totalSeconds = duration.getSeconds();
        long horas = totalSeconds / 3600;
        long minutos = (totalSeconds % 3600) / 60;
        long segundos = totalSeconds % 60;
        return String.format("%02d:%02d:%02d", horas, minutos, segundos);
    }

    public static Duration parse(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        String[] partes = valor.trim().split(":");
        if (partes.length != 3) {
            throw new DateTimeParseException(
                    "Esperado \"HH:mm:ss\", recebido: " + valor, valor, 0);
        }
        try {
            long horas = Long.parseLong(partes[0]);
            long minutos = Long.parseLong(partes[1]);
            long segundos = Long.parseLong(partes[2]);
            return Duration.ofHours(horas).plusMinutes(minutos).plusSeconds(segundos);
        } catch (NumberFormatException e) {
            throw new DateTimeParseException(
                    "Esperado \"HH:mm:ss\", recebido: " + valor, valor, 0, e);
        }
    }
}
