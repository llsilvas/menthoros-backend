package br.com.menthoros.backend.mapper;

import br.com.menthoros.backend.entity.EtapaRealizada;
import br.com.menthoros.backend.entity.TreinoRealizado;
import br.com.menthoros.backend.enums.TipoTreino;
import br.com.menthoros.backend.services.helper.DecouplingCalculatorService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifica o wiring MapStruct do campo derivado {@code decouplingPercentual}: o mapper
 * deve chamar o {@link DecouplingCalculatorService} sobre a entidade (etapas + tipo).
 * O cálculo em si é coberto por {@code DecouplingCalculatorServiceTest}.
 */
class TreinoMapperDecouplingTest {

    private final TreinoMapper mapper = new TreinoMapperImpl(new DecouplingCalculatorService());

    @Test
    void devePreencherDecouplingParaContinuoSteady() {
        TreinoRealizado treino = treino(TipoTreino.CONTINUO, etapasSteady());

        assertThat(mapper.toOutputDto(treino).decouplingPercentual()).isEqualTo(7.3);
    }

    @Test
    void deveDeixarDecouplingNuloParaIntervalado() {
        TreinoRealizado treino = treino(TipoTreino.INTERVALADO, etapasSteady());

        assertThat(mapper.toOutputDto(treino).decouplingPercentual()).isNull();
    }

    private static TreinoRealizado treino(TipoTreino tipo, List<EtapaRealizada> etapas) {
        TreinoRealizado treino = new TreinoRealizado();
        treino.setTipoTreino(tipo);
        treino.setEtapasRealizadas(etapas);
        return treino;
    }

    private static List<EtapaRealizada> etapasSteady() {
        return List.of(
                etapa(1, 150, 12.0),
                etapa(2, 150, 12.0),
                etapa(3, 155, 11.5),
                etapa(4, 155, 11.5)
        );
    }

    private static EtapaRealizada etapa(int ordem, Integer fc, Double velKmh) {
        return EtapaRealizada.builder()
                .ordem(ordem)
                .tipoEtapa("PRINCIPAL")
                .duracao(Duration.ofMinutes(10))
                .fcMedia(fc)
                .velocidadeMedia(BigDecimal.valueOf(velKmh))
                .build();
    }
}
