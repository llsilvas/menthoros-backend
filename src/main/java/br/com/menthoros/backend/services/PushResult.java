package br.com.menthoros.backend.services;

import br.com.menthoros.backend.enums.StatusSincronizacao;
import org.jspecify.annotations.Nullable;

/**
 * Resultado de um push de {@link br.com.menthoros.backend.domain.workout.StructuredWorkout} para
 * um canal externo. Nunca é construído diretamente — use {@link #okCriado(long)},
 * {@link #okAtualizado(long)} ou {@link #erro(StatusSincronizacao, String)}.
 *
 * @param criadoNovo true quando o push CRIOU um evento novo no canal (POST); false em
 *                   atualização de evento existente (PUT) ou erro — insumo do nudge
 *                   anti-debounce do uploader Garmin (intervals-icu-push-hardening, CA2)
 */
public record PushResult(boolean sucesso, @Nullable Long eventId, @Nullable StatusSincronizacao statusErro,
                          @Nullable String mensagem, boolean criadoNovo) {

    public static PushResult okCriado(long eventId) {
        return new PushResult(true, eventId, null, null, true);
    }

    public static PushResult okAtualizado(long eventId) {
        return new PushResult(true, eventId, null, null, false);
    }

    public static PushResult erro(StatusSincronizacao status, String mensagem) {
        if (status == null) {
            throw new IllegalArgumentException("status não pode ser nulo");
        }
        if (mensagem == null || mensagem.isBlank()) {
            throw new IllegalArgumentException("mensagem não pode ser nula ou vazia");
        }
        return new PushResult(false, null, status, mensagem, false);
    }
}
