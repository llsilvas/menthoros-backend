package br.com.menthoros.backend.services;

import br.com.menthoros.backend.enums.StatusSincronizacao;
import org.jspecify.annotations.Nullable;

/**
 * Resultado de um push de {@link br.com.menthoros.backend.domain.workout.StructuredWorkout} para
 * um canal externo. Nunca é construído diretamente — use {@link #ok(long)} ou
 * {@link #erro(StatusSincronizacao, String)}.
 */
public record PushResult(boolean sucesso, @Nullable Long eventId, @Nullable StatusSincronizacao statusErro,
                          @Nullable String mensagem) {

    public static PushResult ok(long eventId) {
        return new PushResult(true, eventId, null, null);
    }

    public static PushResult erro(StatusSincronizacao status, String mensagem) {
        if (status == null) {
            throw new IllegalArgumentException("status não pode ser nulo");
        }
        if (mensagem == null || mensagem.isBlank()) {
            throw new IllegalArgumentException("mensagem não pode ser nula ou vazia");
        }
        return new PushResult(false, null, status, mensagem);
    }
}
