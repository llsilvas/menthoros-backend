package br.com.menthoros.backend.mapper;

import br.com.menthoros.backend.entity.EtapaRealizada;
import br.com.menthoros.backend.entity.TempoEmZonas;
import br.com.menthoros.backend.entity.TreinoRealizado;
import br.com.menthoros.backend.enums.TipoTreino;
import br.com.menthoros.backend.services.helper.DecouplingCalculatorService;
import br.com.menthoros.backend.services.helper.LapEfficiencySeriesCalculator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Campos da tela de execução (coach-ver-treino-realizado, D3) no detalhe do realizado. */
class TreinoMapperExecucaoTest {

    private final TreinoMapper mapper = new TreinoMapperImpl(
            new DecouplingCalculatorService(), new LapEfficiencySeriesCalculator());

    @Test
    @DisplayName("detalhe expõe velocidade máxima, temperaturas, elapsed, tempo em zonas e elevação por volta")
    void detalheExpoeCamposDeExecucao() {
        TreinoRealizado treino = new TreinoRealizado();
        treino.setTipoTreino(TipoTreino.LONGO);
        treino.setVelocidadeMaxKmh(new BigDecimal("12.19"));
        treino.setTemperaturaMinC(new BigDecimal("19.0"));
        treino.setTemperaturaMaxC(new BigDecimal("27.0"));
        treino.setElapsedTimeSeg(6032);
        TempoEmZonas zonas = new TempoEmZonas(TempoEmZonas.FONTE_FIT_RECORDS,
                List.of(new TempoEmZonas.Zona("Z3", 130, 148, 2875)));
        treino.setTempoEmZonas(zonas);
        EtapaRealizada etapa = new EtapaRealizada();
        etapa.setOrdem(1);
        etapa.setDuracao(Duration.ofMinutes(5));
        etapa.setElevacaoGanhoMetros(4);
        etapa.setElevacaoPerdaMetros(2);
        treino.setEtapasRealizadas(List.of(etapa));

        var dto = mapper.toOutputDtoDetalhado(treino);

        assertThat(dto.velocidadeMaxKmh()).isEqualTo(12.19);
        assertThat(dto.temperaturaMinC()).isEqualTo(19.0);
        assertThat(dto.temperaturaMaxC()).isEqualTo(27.0);
        assertThat(dto.elapsedTimeSeg()).isEqualTo(6032);
        assertThat(dto.tempoEmZonas()).isEqualTo(zonas);
        assertThat(dto.etapasRealizadas().get(0).elevacaoGanhoMetros()).isEqualTo(4);
        assertThat(dto.etapasRealizadas().get(0).elevacaoPerdaMetros()).isEqualTo(2);
    }

    @Test
    @DisplayName("ausentes ficam nulos")
    void ausentesFicamNulos() {
        TreinoRealizado treino = new TreinoRealizado();
        treino.setTipoTreino(TipoTreino.CONTINUO);
        treino.setEtapasRealizadas(List.of());

        var dto = mapper.toOutputDtoDetalhado(treino);

        assertThat(dto.velocidadeMaxKmh()).isNull();
        assertThat(dto.temperaturaMinC()).isNull();
        assertThat(dto.temperaturaMaxC()).isNull();
        assertThat(dto.elapsedTimeSeg()).isNull();
        assertThat(dto.tempoEmZonas()).isNull();
    }
}
