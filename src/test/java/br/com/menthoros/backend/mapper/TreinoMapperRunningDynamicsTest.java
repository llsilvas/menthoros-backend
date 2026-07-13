package br.com.menthoros.backend.mapper;

import br.com.menthoros.backend.entity.EtapaRealizada;
import br.com.menthoros.backend.entity.TreinoRealizado;
import br.com.menthoros.backend.enums.TipoTreino;
import br.com.menthoros.backend.services.helper.DecouplingCalculatorService;
import br.com.menthoros.backend.services.helper.LapEfficiencySeriesCalculator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifica o wiring MapStruct dos campos de running dynamics (design D3 de
 * fit-running-dynamics-ingestion): diferente da série de EF e do envelope de decoupling
 * (restritos ao detalhe), estes são escalares simples e devem aparecer no FLUXO COMUM
 * (listagens) e no detalhe — mesmo padrão de elevacaoGanhoMetros/potenciaMedia.
 */
class TreinoMapperRunningDynamicsTest {

    private final TreinoMapper mapper = new TreinoMapperImpl(
            new DecouplingCalculatorService(), new LapEfficiencySeriesCalculator());

    @Nested
    @DisplayName("toOutputDto (fluxo comum)")
    class ToOutputDto {

        @Test
        @DisplayName("running dynamics da sessão e da etapa aparecem no fluxo comum, não só no detalhe")
        void runningDynamicsNoFluxoComum() {
            TreinoRealizado treino = treinoComRunningDynamics();

            var dto = mapper.toOutputDto(treino);

            assertThat(dto.runningDynamics()).isNotNull();
            assertThat(dto.runningDynamics().tempoMovimento()).isEqualTo("29:10");
            assertThat(dto.runningDynamics().calorias()).isEqualTo(650);
            assertThat(dto.runningDynamics().gctMedioMs()).isEqualTo(252);
            assertThat(dto.runningDynamics().gctEquilibrioPct()).isEqualTo(49.3);
            assertThat(dto.runningDynamics().passadaMediaM()).isEqualTo(1.05);
            assertThat(dto.runningDynamics().oscilacaoVerticalCm()).isEqualTo(8.2);
            assertThat(dto.runningDynamics().proporcaoVerticalPct()).isEqualTo(6.8);
            assertThat(dto.runningDynamics().temperaturaMediaC()).isEqualTo(22.0);

            var etapa = dto.etapasRealizadas().get(0);
            assertThat(etapa.runningDynamics()).isNotNull();
            assertThat(etapa.runningDynamics().tempoMovimento()).isEqualTo("14:10");
            assertThat(etapa.runningDynamics().calorias()).isNull(); // só existe a nível de sessão
            assertThat(etapa.runningDynamics().gctMedioMs()).isEqualTo(255);
            assertThat(etapa.runningDynamics().gctEquilibrioPct()).isEqualTo(48.7);
            assertThat(etapa.runningDynamics().passadaMediaM()).isEqualTo(0.98);
            assertThat(etapa.runningDynamics().oscilacaoVerticalCm()).isEqualTo(9.0);
            assertThat(etapa.runningDynamics().proporcaoVerticalPct()).isEqualTo(7.1);
            assertThat(etapa.runningDynamics().temperaturaMediaC()).isEqualTo(23.0);
        }

        @Test
        @DisplayName("sem running dynamics, envelope fica null (omitido no JSON via NON_NULL)")
        void semRunningDynamicsFicaNull() {
            TreinoRealizado treino = new TreinoRealizado();
            treino.setTipoTreino(TipoTreino.CONTINUO);
            treino.setEtapasRealizadas(List.of());

            var dto = mapper.toOutputDto(treino);

            // O envelope em si nunca é null (mesmo padrão de DecouplingResultadoDto) — cada campo
            // interno é que fica null individualmente, omitido no JSON via @JsonInclude(NON_NULL).
            assertThat(dto.runningDynamics()).isEqualTo(
                    new br.com.menthoros.backend.dto.output.RunningDynamicsOutputDto(
                            null, null, null, null, null, null, null, null));
        }
    }

    @Nested
    @DisplayName("toOutputDtoDetalhado")
    class ToOutputDtoDetalhado {

        @Test
        @DisplayName("running dynamics idênticos ao fluxo comum — não é campo restrito ao detalhe")
        void runningDynamicsIdenticosAoFluxoComum() {
            TreinoRealizado treino = treinoComRunningDynamics();

            var comum = mapper.toOutputDto(treino);
            var detalhe = mapper.toOutputDtoDetalhado(treino);

            assertThat(detalhe.runningDynamics()).isEqualTo(comum.runningDynamics());
        }
    }

    private static TreinoRealizado treinoComRunningDynamics() {
        TreinoRealizado treino = new TreinoRealizado();
        treino.setTipoTreino(TipoTreino.CONTINUO);
        treino.setTempoMovimento(Duration.ofMinutes(29).plusSeconds(10));
        treino.setCalorias(650);
        treino.setGctMedioMs(252);
        treino.setGctEquilibrioPct(new BigDecimal("49.3"));
        treino.setPassadaMediaM(new BigDecimal("1.05"));
        treino.setOscilacaoVerticalCm(new BigDecimal("8.2"));
        treino.setProporcaoVerticalPct(new BigDecimal("6.8"));
        treino.setTemperaturaMediaC(new BigDecimal("22.0"));

        EtapaRealizada etapa = EtapaRealizada.builder()
                .ordem(1)
                .tipoEtapa("PRINCIPAL")
                .duracao(Duration.ofMinutes(15))
                .tempoMovimento(Duration.ofMinutes(14).plusSeconds(10))
                .gctMedioMs(255)
                .gctEquilibrioPct(new BigDecimal("48.7"))
                .passadaMediaM(new BigDecimal("0.98"))
                .oscilacaoVerticalCm(new BigDecimal("9.0"))
                .proporcaoVerticalPct(new BigDecimal("7.1"))
                .temperaturaMediaC(new BigDecimal("23.0"))
                .build();
        treino.setEtapasRealizadas(List.of(etapa));
        return treino;
    }
}
