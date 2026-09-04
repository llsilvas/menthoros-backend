package br.com.menthoros.backend.mapper;

import br.com.menthoros.backend.dto.output.TreinoRealizadoOutputDto;
import br.com.menthoros.backend.entity.TreinoRealizado;
import br.com.menthoros.backend.enums.Sensacao;
import br.com.menthoros.backend.services.helper.DecouplingCalculatorService;
import br.com.menthoros.backend.services.helper.LapEfficiencySeriesCalculator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** O Plano/Home/drilldown do coach leem o feedback pelo mesmo DTO — o wiring MapStruct precisa existir. */
class TreinoMapperFeedbackTest {

    private final TreinoMapper mapper = new TreinoMapperImpl(
            new DecouplingCalculatorService(), new LapEfficiencySeriesCalculator());

    @Test
    @DisplayName("sensacoes e feedbackRegistradoEm atravessam para o TreinoRealizadoOutputDto")
    void expoeFeedback() {
        TreinoRealizado tr = new TreinoRealizado();
        tr.setId(UUID.randomUUID());
        tr.setSensacoes(Set.of(Sensacao.PERNAS_PESADAS, Sensacao.DOR));
        tr.setFeedbackRegistradoEm(LocalDateTime.of(2026, 8, 27, 19, 0));

        TreinoRealizadoOutputDto dto = mapper.toOutputDto(tr);

        assertThat(dto.sensacoes()).containsExactlyInAnyOrder(Sensacao.PERNAS_PESADAS, Sensacao.DOR);
        assertThat(dto.feedbackRegistradoEm()).isEqualTo(LocalDateTime.of(2026, 8, 27, 19, 0));
    }

    @Test
    @DisplayName("sem feedback: campos ausentes, não listas/carimbos inventados")
    void semFeedback() {
        TreinoRealizado tr = new TreinoRealizado();
        tr.setId(UUID.randomUUID());

        TreinoRealizadoOutputDto dto = mapper.toOutputDto(tr);

        assertThat(dto.sensacoes()).isNull();
        assertThat(dto.feedbackRegistradoEm()).isNull();
    }
}
