package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.dto.output.TreinoRealizadoOutputDto;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.EtapaRealizada;
import br.com.menthoros.backend.entity.TreinoRealizado;
import br.com.menthoros.backend.enums.OrigemCalculo;
import br.com.menthoros.backend.enums.TipoTreino;
import br.com.menthoros.backend.mapper.TreinoMapper;
import br.com.menthoros.backend.mapper.TreinoMapperImpl;
import br.com.menthoros.backend.services.impl.FitParseServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Validação integrada da change fit-lap-derived-metrics (task 5.1): fixture real →
 * etapas como o persister as grava → calculators → mapper de detalhe. Verifica o payload
 * completo que o coach receberia no GET /api/v1/treinos/realizados/{id}.
 */
class FitLapDerivedMetricsIntegrationTest {

    private final TreinoMapper mapper = new TreinoMapperImpl(
            new DecouplingCalculatorService(), new LapEfficiencySeriesCalculator());

    @Test
    @DisplayName("fixture real (16 laps): série completa, envelope Pa:HR+Pw:HR e nenhum paceGap (gate desligado)")
    void payloadDeDetalheDaFixtureReal() throws Exception {
        TreinoRealizado treino = treinoDaFixtureReal();

        TreinoRealizadoOutputDto dto = mapper.toOutputDtoDetalhado(treino);

        // Envelope de decoupling: Pa:HR igual ao golden (15.6%), Pw:HR calculado (potência em 100% das voltas)
        assertThat(dto.decouplingPercentual()).isEqualTo(15.6);
        assertThat(dto.decoupling().percentual()).isEqualTo(15.6);
        assertThat(dto.decoupling().motivoNull()).isNull();
        assertThat(dto.decoupling().potenciaPercentual()).isEqualTo(5.1);
        assertThat(dto.decoupling().motivoNullPotencia()).isNull();
        assertThat(dto.decoupling().origem()).isEqualTo(OrigemCalculo.POR_VOLTA);

        // Série de EF: todas as voltas com FC+velocidade entram; metadados fecham a conta
        var serie = dto.serieEficiencia();
        assertThat(serie).isNotNull();
        assertThat(serie.origem()).isEqualTo(OrigemCalculo.POR_VOLTA);
        assertThat(serie.totalVoltas()).isEqualTo(16);
        assertThat(serie.pontos().size() + serie.voltasOmitidas().size()).isEqualTo(16);
        assertThat(serie.pontos()).allSatisfy(p -> {
            assertThat(p.efPace()).isPositive();
            assertThat(p.efPotencia()).isPositive();
            assertThat(p.wPorKg()).isPositive();
            // hard gate do GAP desligado: nenhum ponto expõe paceGap
            assertThat(p.paceGap()).isNull();
        });
    }

    /** Mesma derivação do FitTreinoPersister (velocidade de distância/duração, 2 casas). */
    private TreinoRealizado treinoDaFixtureReal() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/fit/corrida-15km-16laps.fit")) {
            var dados = new FitParseServiceImpl().parse(in);
            List<EtapaRealizada> etapas = dados.laps().stream()
                    .map(lap -> EtapaRealizada.builder()
                            .ordem(lap.ordem())
                            .duracao(lap.duracao())
                            .distanciaKm(lap.distanciaKm() != null ? BigDecimal.valueOf(lap.distanciaKm()) : null)
                            .fcMedia(lap.fcMedia())
                            .velocidadeMedia(velocidadeDerivada(lap.distanciaKm(), lap.duracao()))
                            .potenciaMedia(lap.potenciaMediaWatts())
                            .elevacaoGanhoMetros(lap.subidaMetros())
                            .elevacaoPerdaMetros(lap.descidaMetros())
                            .build())
                    .toList();

            Atleta atleta = new Atleta();
            atleta.setPesoKg(BigDecimal.valueOf(85.0));

            TreinoRealizado treino = new TreinoRealizado();
            treino.setTipoTreino(TipoTreino.CONTINUO);
            treino.setEtapasRealizadas(etapas);
            treino.setAtleta(atleta);
            return treino;
        }
    }

    private BigDecimal velocidadeDerivada(Double distanciaKm, Duration duracao) {
        if (distanciaKm == null || distanciaKm <= 0 || duracao == null || duracao.isZero()) {
            return null;
        }
        double horas = duracao.toMillis() / 3_600_000.0;
        return BigDecimal.valueOf(distanciaKm / horas).setScale(2, RoundingMode.HALF_UP);
    }
}
