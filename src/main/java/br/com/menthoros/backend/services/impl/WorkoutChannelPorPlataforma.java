package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.domain.workout.StructuredWorkout;
import br.com.menthoros.backend.entity.IntegracaoExterna;
import br.com.menthoros.backend.services.PushResult;
import br.com.menthoros.backend.services.WorkoutChannel;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Set;

/**
 * {@link WorkoutChannel} que os chamadores injetam: delega ao adapter da plataforma da conexão
 * (design D1). Os adapters concretos são injetados por tipo, então o {@code @Primary} é o que
 * resolve a injeção de {@link WorkoutChannel}.
 */
@Primary
@Component
@RequiredArgsConstructor
public class WorkoutChannelPorPlataforma implements WorkoutChannel {

    private final IntervalsIcuAdapter intervalsIcu;
    private final GarminTrainingAdapter garmin;

    @Override
    public PushResult push(IntegracaoExterna conexao, StructuredWorkout workout, Long eventIdArmazenado) {
        return canal(conexao).push(conexao, workout, eventIdArmazenado);
    }

    @Override
    public void removerOrfaos(IntegracaoExterna conexao, LocalDate inicio, LocalDate fim,
                               Set<String> externalIdsAtuais, Set<String> idsArmazenadosAtuais) {
        canal(conexao).removerOrfaos(conexao, inicio, fim, externalIdsAtuais, idsArmazenadosAtuais);
    }

    @Override
    public void tocarEvento(IntegracaoExterna conexao, long eventId, String externalIdCanonico) {
        canal(conexao).tocarEvento(conexao, eventId, externalIdCanonico);
    }

    private WorkoutChannel canal(IntegracaoExterna conexao) {
        if (conexao == null) {
            throw new IllegalArgumentException("conexao não pode ser nula");
        }
        return switch (conexao.getPlataforma()) {
            case INTERVALS_ICU -> intervalsIcu;
            case GARMIN -> garmin;
            default -> throw new IllegalArgumentException(
                    "Plataforma sem canal de treinos: " + conexao.getPlataforma());
        };
    }
}
