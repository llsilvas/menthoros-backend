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

    @Test
    @DisplayName("deviceName atravessa para o TreinoRealizadoOutputDto (atribuição do Garmin)")
    void expoeDeviceName() {
        TreinoRealizado tr = new TreinoRealizado();
        tr.setId(UUID.randomUUID());
        tr.setDeviceName("Forerunner 970");

        assertThat(mapper.toOutputDto(tr).deviceName()).isEqualTo("Forerunner 970");
        assertThat(mapper.toOutputDto(new TreinoRealizado()).deviceName()).isNull();
    }

    @Test
    @DisplayName("planejado expõe fonte e modelo do realizado vinculado, separados da fonte do plano")
    void planejadoExpoeFonteDoRealizado() {
        br.com.menthoros.backend.entity.TreinoPlanejado tp = new br.com.menthoros.backend.entity.TreinoPlanejado();
        tp.setFonteDados(br.com.menthoros.backend.enums.FonteDados.IA_GERADO);
        TreinoRealizado tr = new TreinoRealizado();
        tr.setId(UUID.randomUUID());
        tr.setFonteDados(br.com.menthoros.backend.enums.FonteDados.GARMIN);
        tr.setDeviceName("Forerunner 970");
        tp.setTreinoRealizado(tr);

        var dto = mapper.toOutputDto(tp);

        assertThat(dto.fonteDados()).isEqualTo(br.com.menthoros.backend.enums.FonteDados.IA_GERADO);
        assertThat(dto.completedSource()).isEqualTo(br.com.menthoros.backend.enums.FonteDados.GARMIN);
        assertThat(dto.completedDeviceName()).isEqualTo("Forerunner 970");
    }

    @Test
    @DisplayName("planejado sem realizado vinculado: fonte e modelo do realizado ausentes")
    void planejadoSemRealizado() {
        var dto = mapper.toOutputDto(new br.com.menthoros.backend.entity.TreinoPlanejado());

        assertThat(dto.completedSource()).isNull();
        assertThat(dto.completedDeviceName()).isNull();
    }
}
