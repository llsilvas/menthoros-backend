package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.domain.workout.HrTarget;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.services.helper.IntervalsIcuTargetParser.FcAlvoBruto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class IntervalsIcuFcAlvoResolverTest {

    private final ZonaTreinoService zonaTreinoService = new ZonaTreinoService();
    private final IntervalsIcuFcAlvoResolver resolver = new IntervalsIcuFcAlvoResolver(zonaTreinoService);

    @Nested
    @DisplayName("resolver")
    class Resolver {

        @Test
        @DisplayName("alvo já em bpm atravessa intacto, sem depender do atleta")
        void bpmAtravessaIntacto() {
            IntervalsIcuFcAlvoResolver.Resolucao resolucao =
                    resolver.resolver(new FcAlvoBruto(FcAlvoBruto.Base.BPM, 140, 150), semFcMedida());

            assertThat(resolucao.alvo()).isEqualTo(new HrTarget(140, 150));
            assertThat(resolucao.descartadoPorFaltaDeDado()).isFalse();
        }

        @ParameterizedTest
        @CsvSource({
                // limiar, %inicio, %fim, bpmInicio, bpmFim — inflação medida na task 0.1
                "170, 60, 70, 102, 119",
                "142, 60, 70,  85,  99",
                "150, 90, 95, 135, 143"
        })
        @DisplayName("percentual é lido na base do domínio (%LTHR), nunca na FC máxima")
        void percentualUsaLimiar(int fcLimiar, int inicio, int fim, int bpmInicio, int bpmFim) {
            IntervalsIcuFcAlvoResolver.Resolucao resolucao = resolver.resolver(
                    new FcAlvoBruto(FcAlvoBruto.Base.PERCENT, inicio, fim), comLimiar(fcLimiar));

            assertThat(resolucao.alvo()).isEqualTo(new HrTarget(bpmInicio, bpmFim));
        }

        @ParameterizedTest
        @CsvSource({"1", "2", "3", "4", "5"})
        @DisplayName("zona resolve exatamente para a faixa do ZonaTreinoService, sem recalcular")
        void zonaReusaOServico(int numeroZona) {
            Atleta atleta = comLimiar(170);

            IntervalsIcuFcAlvoResolver.Resolucao resolucao = resolver.resolver(
                    new FcAlvoBruto(FcAlvoBruto.Base.ZONE, numeroZona, numeroZona), atleta);

            ZonaTreinoService.ZonaFC esperada = zonaTreinoService
                    .calcularZonasFC(atleta.getFcMaxima(), 170).get(numeroZona - 1);
            assertThat(resolucao.alvo()).isEqualTo(new HrTarget(esperada.fcMin(), esperada.fcMax()));
        }

        @Test
        @DisplayName("Z2 do atleta de limiar 170 é 145-151 bpm")
        void zonaValorAbsoluto() {
            IntervalsIcuFcAlvoResolver.Resolucao resolucao = resolver.resolver(
                    new FcAlvoBruto(FcAlvoBruto.Base.ZONE, 2, 2), comLimiar(170));

            assertThat(resolucao.alvo()).isEqualTo(new HrTarget(145, 151));
        }

        @Test
        @DisplayName("sem FC de limiar medida, descarta em vez de estimar por idade")
        void semLimiarDescarta() {
            IntervalsIcuFcAlvoResolver.Resolucao resolucao = resolver.resolver(
                    new FcAlvoBruto(FcAlvoBruto.Base.PERCENT, 60, 70), semFcMedida());

            assertThat(resolucao.alvo()).isNull();
            assertThat(resolucao.descartadoPorFaltaDeDado()).isTrue();
        }

        @Test
        @DisplayName("o fallback etário NÃO vira meta: getFcLimiarCalculada devolve número, e ele é ignorado")
        void fallbackEtarioNaoViraMeta() {
            Atleta atleta = semFcMedida();
            // O atleta responde 0,85 × (220 - idade) — número plausível e sem base medida. Se o
            // resolver usasse o getter "calculada", este teste passaria com uma meta inventada.
            assertThat(atleta.getFcLimiarCalculada()).isNotNull();

            IntervalsIcuFcAlvoResolver.Resolucao resolucao = resolver.resolver(
                    new FcAlvoBruto(FcAlvoBruto.Base.ZONE, 2, 2), atleta);

            assertThat(resolucao.alvo()).isNull();
            assertThat(resolucao.descartadoPorFaltaDeDado()).isTrue();
        }

        @Test
        @DisplayName("etapa sem alvo de FC não é descarte: é escolha do treinador")
        void semAlvoNaoEDescarte() {
            IntervalsIcuFcAlvoResolver.Resolucao resolucao = resolver.resolver(null, comLimiar(170));

            assertThat(resolucao.alvo()).isNull();
            assertThat(resolucao.descartadoPorFaltaDeDado()).isFalse();
        }
    }

    private Atleta comLimiar(int fcLimiar) {
        return Atleta.builder().id(UUID.randomUUID()).fcLimiar(fcLimiar).fcMaxima(195).build();
    }

    private Atleta semFcMedida() {
        return Atleta.builder()
                .id(UUID.randomUUID())
                .dataNascimento(LocalDate.of(1990, 1, 1))
                .build();
    }
}
