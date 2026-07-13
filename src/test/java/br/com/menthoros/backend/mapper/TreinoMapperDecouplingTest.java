package br.com.menthoros.backend.mapper;

import br.com.menthoros.backend.entity.EtapaRealizada;
import br.com.menthoros.backend.entity.TreinoRealizado;
import br.com.menthoros.backend.enums.TipoTreino;
import br.com.menthoros.backend.services.helper.DecouplingCalculatorService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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

    private final TreinoMapper mapper = new TreinoMapperImpl(
            new DecouplingCalculatorService(),
            new br.com.menthoros.backend.services.helper.LapEfficiencySeriesCalculator());

    @Nested
    @DisplayName("toOutputDto")
    class ToOutputDto {

    @Test
    @DisplayName("contínuo steady → decouplingPercentual preenchido")
    void devePreencherDecouplingParaContinuoSteady() {
        TreinoRealizado treino = treino(TipoTreino.CONTINUO, etapasSteady());

        assertThat(mapper.toOutputDto(treino).decouplingPercentual()).isEqualTo(7.3);
    }

    @Test
    @DisplayName("intervalado → decouplingPercentual nulo (omitido no JSON via NON_NULL)")
    void deveDeixarDecouplingNuloParaIntervalado() {
        TreinoRealizado treino = treino(TipoTreino.INTERVALADO, etapasSteady());

        assertThat(mapper.toOutputDto(treino).decouplingPercentual()).isNull();
    }

    @Test
    @DisplayName("envelope decoupling carrega Pa:HR igual ao legado, origem POR_VOLTA e Pw:HR quando há potência")
    void devePreencherEnvelopeComProveniencia() {
        TreinoRealizado treino = treino(TipoTreino.CONTINUO, etapasSteadyComPotencia());

        var dto = mapper.toOutputDto(treino);

        assertThat(dto.decoupling()).isNotNull();
        assertThat(dto.decoupling().percentual()).isEqualTo(dto.decouplingPercentual()).isEqualTo(7.3);
        assertThat(dto.decoupling().motivoNull()).isNull();
        assertThat(dto.decoupling().potenciaPercentual()).isEqualTo(8.6);
        assertThat(dto.decoupling().origem()).isEqualTo(br.com.menthoros.backend.enums.OrigemCalculo.POR_VOLTA);
    }

    @Test
    @DisplayName("envelope explica o null: intervalado carrega TIPO_NAO_CONTINUO nas duas métricas")
    void deveExplicarNullNoEnvelope() {
        TreinoRealizado treino = treino(TipoTreino.INTERVALADO, etapasSteady());

        var dto = mapper.toOutputDto(treino);

        assertThat(dto.decoupling().percentual()).isNull();
        assertThat(dto.decoupling().motivoNull())
                .isEqualTo(br.com.menthoros.backend.enums.MotivoNullDecoupling.TIPO_NAO_CONTINUO);
        assertThat(dto.decoupling().motivoNullPotencia())
                .isEqualTo(br.com.menthoros.backend.enums.MotivoNullDecoupling.TIPO_NAO_CONTINUO);
    }

    @Test
    @DisplayName("toOutputDto (listagens) NÃO carrega a série de EF; toOutputDtoDetalhado carrega")
    void serieSoNoDetalhe() {
        TreinoRealizado treino = treino(TipoTreino.CONTINUO, etapasSteadyComPotencia());

        assertThat(mapper.toOutputDto(treino).serieEficiencia()).isNull();

        var detalhe = mapper.toOutputDtoDetalhado(treino);
        assertThat(detalhe.serieEficiencia()).isNotNull();
        assertThat(detalhe.serieEficiencia().totalVoltas()).isEqualTo(4);
        assertThat(detalhe.serieEficiencia().pontos()).hasSize(4);
        // demais campos permanecem idênticos ao fluxo comum
        assertThat(detalhe.decouplingPercentual()).isEqualTo(mapper.toOutputDto(treino).decouplingPercentual());
    }
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

    private static List<EtapaRealizada> etapasSteadyComPotencia() {
        return List.of(
                etapaPot(1, 150, 12.0, 360),
                etapaPot(2, 150, 12.0, 360),
                etapaPot(3, 155, 11.5, 340),
                etapaPot(4, 155, 11.5, 340)
        );
    }

    private static EtapaRealizada etapaPot(int ordem, Integer fc, Double velKmh, Integer potencia) {
        return EtapaRealizada.builder()
                .ordem(ordem)
                .tipoEtapa("PRINCIPAL")
                .duracao(Duration.ofMinutes(10))
                .fcMedia(fc)
                .velocidadeMedia(BigDecimal.valueOf(velKmh))
                .potenciaMedia(potencia)
                .build();
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
