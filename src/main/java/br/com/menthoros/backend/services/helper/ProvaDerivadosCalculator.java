package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.domain.planner.RacePreparationRule;
import br.com.menthoros.backend.entity.Prova;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;

/**
 * Calcula, na leitura, os indicadores de preparação expostos em {@code ProvaOutputDto}
 * ({@code preparacaoCurta}, {@code semanasFaltando}). Usa o {@link Clock} da aplicação para que
 * a data de referência seja controlável em teste.
 */
@Component
@RequiredArgsConstructor
public class ProvaDerivadosCalculator {

    private final Clock clock;

    /** Idempotent: YES · Side Effects: NONE · Tenant-aware: N/A. */
    public boolean preparacaoCurta(Prova prova) {
        if (prova == null) {
            throw new IllegalArgumentException("Prova não pode ser nula");
        }
        return RacePreparationRule.preparacaoCurta(prova.getInicioPreparacao(), LocalDate.now(clock));
    }

    /** Idempotent: YES · Side Effects: NONE · Tenant-aware: N/A. */
    public int semanasFaltando(Prova prova) {
        if (prova == null) {
            throw new IllegalArgumentException("Prova não pode ser nula");
        }
        if (prova.getDataProva() == null) {
            return 0;
        }
        return RacePreparationRule.semanasFaltando(prova.getDataProva(), LocalDate.now(clock));
    }
}
