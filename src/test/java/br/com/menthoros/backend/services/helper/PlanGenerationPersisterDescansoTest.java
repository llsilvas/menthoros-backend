package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.domain.plano.RestDay;
import br.com.menthoros.backend.dto.llm.TreinoPlanejadoLlmDto;
import br.com.menthoros.backend.entity.PlanoSemanal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CA12b (add-descanso-explicito-por-fadiga): {@code garantirProvasNaSemana} roda depois da
 * validação e pode pôr uma prova num dia prescrito como descanso. Ali não há turno de reparo, e um
 * dia com treino <b>e</b> descanso é incoerente nas duas telas — a prova vence.
 */
@DisplayName("PlanGenerationPersister — descanso x treino no mesmo dia")
class PlanGenerationPersisterDescansoTest {

    private final PlanGenerationPersister persister = Mockito.mock(PlanGenerationPersister.class,
            Mockito.withSettings().defaultAnswer(Mockito.CALLS_REAL_METHODS));

    @Test
    @DisplayName("dia que ganhou prova perde o descanso; os demais descansos ficam")
    void provaRemoveDescansoDoDia() {
        PlanoSemanal plano = new PlanoSemanal();
        plano.setRestDays(new ArrayList<>(List.of(
                new RestDay("QUINTA", "check-in de hoje: DESCANSAR"),
                new RestDay("SABADO", "36h desde o último intensivo"))));

        persister.removerDescansosDeDiasComTreino(plano, List.of(treino("SABADO"), treino("SEGUNDA")));

        assertThat(plano.getRestDaysOuVazio()).extracting(RestDay::dayOfWeek).containsExactly("QUINTA");
    }

    @Test
    @DisplayName("sem colisão, a lista de descansos fica intacta")
    void semColisaoNaoMexe() {
        PlanoSemanal plano = new PlanoSemanal();
        var descansos = List.of(new RestDay("QUINTA", "motivo"));
        plano.setRestDays(new ArrayList<>(descansos));

        persister.removerDescansosDeDiasComTreino(plano, List.of(treino("SEGUNDA")));

        assertThat(plano.getRestDaysOuVazio()).isEqualTo(descansos);
    }

    @Test
    @DisplayName("comparação de dia ignora caixa e espaços")
    void comparacaoNormalizada() {
        PlanoSemanal plano = new PlanoSemanal();
        plano.setRestDays(new ArrayList<>(List.of(new RestDay(" quinta ", "motivo"))));

        persister.removerDescansosDeDiasComTreino(plano, List.of(treino("QUINTA")));

        assertThat(plano.getRestDaysOuVazio()).isEmpty();
    }

    @Test
    @DisplayName("plano sem descanso não quebra")
    void semDescanso() {
        PlanoSemanal plano = new PlanoSemanal();

        persister.removerDescansosDeDiasComTreino(plano, List.of(treino("SEGUNDA")));

        assertThat(plano.getRestDaysOuVazio()).isEmpty();
    }

    private static TreinoPlanejadoLlmDto treino(String dia) {
        return new TreinoPlanejadoLlmDto(dia, "CONTINUO", null, null, null, null, null, null, null, null, List.of());
    }
}
