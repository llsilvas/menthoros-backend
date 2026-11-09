package br.com.menthoros.backend.mapper;

import br.com.menthoros.backend.entity.TreinoRealizado;
import br.com.menthoros.backend.services.helper.DecouplingCalculatorService;
import br.com.menthoros.backend.services.helper.LapEfficiencySeriesCalculator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** {@code esteira} no contrato de treino realizado (add-activity-treadmill-flag, CA4). */
class TreinoMapperEsteiraTest {

    private final TreinoMapper mapper = new TreinoMapperImpl(
            new DecouplingCalculatorService(), new LapEfficiencySeriesCalculator());

    @Test
    @DisplayName("esteira da entidade vai para a listagem e o detalhe; default false")
    void esteiraCopiadaDaEntidade() {
        TreinoRealizado esteira = new TreinoRealizado();
        esteira.setEsteira(true);
        TreinoRealizado rua = new TreinoRealizado();

        assertThat(mapper.toOutputDto(esteira).esteira()).isTrue();
        assertThat(mapper.toOutputDtoDetalhado(esteira).esteira()).isTrue();
        assertThat(mapper.toOutputDto(rua).esteira()).isFalse();
    }
}
