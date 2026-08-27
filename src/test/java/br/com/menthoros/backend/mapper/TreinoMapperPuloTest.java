package br.com.menthoros.backend.mapper;

import br.com.menthoros.backend.dto.output.TreinoPlanejadoOutputDto;
import br.com.menthoros.backend.entity.TreinoPlanejado;
import br.com.menthoros.backend.enums.MotivoPulo;
import br.com.menthoros.backend.enums.TreinoExecucaoStatus;
import br.com.menthoros.backend.services.helper.DecouplingCalculatorService;
import br.com.menthoros.backend.services.helper.LapEfficiencySeriesCalculator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * O Plano do atleta e o detalhe do coach leem o pulo pelo mesmo DTO do planejado — o wiring
 * MapStruct dos dois campos novos precisa existir, senão o pulo é invisível fora do modo treino.
 */
class TreinoMapperPuloTest {

    private final TreinoMapper mapper = new TreinoMapperImpl(
            new DecouplingCalculatorService(), new LapEfficiencySeriesCalculator());

    @Test
    @DisplayName("motivoPulo e puladoEm atravessam para o TreinoPlanejadoOutputDto")
    void expoePulo() {
        TreinoPlanejado tp = new TreinoPlanejado();
        tp.setId(UUID.randomUUID());
        tp.setStatusTreino(TreinoExecucaoStatus.PERDIDO);
        tp.setMotivoPulo(MotivoPulo.DOR);
        tp.setPuladoEm(LocalDateTime.of(2026, 8, 27, 7, 0));

        TreinoPlanejadoOutputDto dto = mapper.toOutputDto(tp);

        assertThat(dto.motivoPulo()).isEqualTo(MotivoPulo.DOR);
        assertThat(dto.puladoEm()).isEqualTo(LocalDateTime.of(2026, 8, 27, 7, 0));
    }
}
