package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.entity.TreinoPlanejado;
import br.com.menthoros.backend.enums.TreinoExecucaoStatus;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Optional;

/**
 * Regra única de editabilidade de treino em plano aprovado (coach-editar-treino-aprovado, D1,
 * regras 1 e 2). Usada pelo PATCH (autoridade) e pelo perfil do atleta ({@code editable}), para o
 * lápis do front nunca divergir da recusa do backend.
 *
 * <p>{@code hoje} vem de {@link AtletaHojeResolver#hojeDe} — o dia do atleta, não o da JVM.
 * {@code hasWatch} é existir conexão de canal, ativa ou revogada: conexão caída não tira o treino
 * antigo do aparelho.
 *
 * <p><b>Idempotente:</b> YES — função pura. <b>Side Effects:</b> NONE. <b>Tenant-aware:</b> NO.
 */
@Component
public class ApprovedWorkoutEditPolicy {

    public enum EditRejection {
        NOT_PENDING("Treino já realizado, perdido ou pulado não pode ser editado."),
        PAST_DAY("Treino de dia passado não pode ser editado."),
        TODAY_ON_WATCH("O treino de hoje já está no relógio do atleta e não pode ser editado.");

        private final String message;

        EditRejection(String message) {
            this.message = message;
        }

        public String message() {
            return message;
        }
    }

    public Optional<EditRejection> avaliar(TreinoPlanejado treino, LocalDate hoje, boolean hasWatch) {
        if (treino == null) throw new IllegalArgumentException("treino não pode ser nulo");
        if (hoje == null) throw new IllegalArgumentException("hoje não pode ser nulo");

        TreinoExecucaoStatus status = treino.getStatusTreino();
        if (status != null && status != TreinoExecucaoStatus.PENDENTE) {
            return Optional.of(EditRejection.NOT_PENDING);
        }
        LocalDate data = treino.getDataTreino();
        if (data == null || data.isBefore(hoje)) {
            return Optional.of(EditRejection.PAST_DAY);
        }
        if (data.isEqual(hoje) && hasWatch) {
            return Optional.of(EditRejection.TODAY_ON_WATCH);
        }
        return Optional.empty();
    }
}
