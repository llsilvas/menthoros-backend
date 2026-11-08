package br.com.menthoros.backend.mapper;

import br.com.menthoros.backend.entity.TreinoRealizado;
import br.com.menthoros.backend.services.helper.DecouplingCalculatorService;
import br.com.menthoros.backend.services.helper.LapEfficiencySeriesCalculator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** {@code temTrajeto} na listagem (add-activity-gps-trace, D4): só o booleano, nunca o JSON. */
class TreinoMapperGpsTraceTest {

    private final TreinoMapper mapper = new TreinoMapperImpl(
            new DecouplingCalculatorService(), new LapEfficiencySeriesCalculator());

    @Test
    @DisplayName("gps_trace preenchido → temTrajeto=true; nulo → false; o JSON não vai no DTO")
    void temTrajetoDerivadoDaColuna() {
        TreinoRealizado com = new TreinoRealizado();
        com.setGpsTrace("{\"v\":1,\"n\":2,\"polyline\":\"_p~iF~ps|U_ulLnnqC\",\"tSeg\":[0,4],\"fc\":[null,null]}");
        TreinoRealizado sem = new TreinoRealizado();

        assertThat(mapper.toOutputDto(com).temTrajeto()).isTrue();
        assertThat(mapper.toOutputDto(sem).temTrajeto()).isFalse();
        assertThat(mapper.toOutputDtoDetalhado(com).temTrajeto()).isTrue();
    }
}
