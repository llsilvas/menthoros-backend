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

        @Test
        @DisplayName("bpm literal escrito ao contrário (150-140) sai ordenado: Low > High é inválido nos dois canais")
        void bpmInvertidoSaiOrdenado() {
            IntervalsIcuFcAlvoResolver.Resolucao resolucao =
                    resolver.resolver(new FcAlvoBruto(FcAlvoBruto.Base.BPM, 150, 140), semFcMedida());

            assertThat(resolucao.alvo()).isEqualTo(new HrTarget(140, 150));
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

        @Test
        @DisplayName("bpm literal acima da FC máxima do atleta é descartado, não vira meta impossível no relógio")
        void bpmAcimaDaFcMaximaDescarta() {
            // fcMaxima do atleta é 195 (comLimiar); 223 é fisiologicamente implausível para ele.
            IntervalsIcuFcAlvoResolver.Resolucao resolucao = resolver.resolver(
                    new FcAlvoBruto(FcAlvoBruto.Base.BPM, 197, 223), comLimiar(170));

            assertThat(resolucao.alvo()).isNull();
            assertThat(resolucao.descartadoPorFaltaDeDado()).isTrue();
        }

        @ParameterizedTest
        @CsvSource({
                "39, 50, true",    // abaixo do piso plausível (40): descarta
                "40, 50, false",   // exatamente no piso: aceita
                "100, 195, false", // exatamente na FC máxima do atleta (195): aceita
                "100, 196, true",  // 1 bpm acima da FC máxima: descarta
                "1, 270, true"     // faixa degenerada (piso e teto violados): descarta
        })
        @DisplayName("bpm literal fora da faixa fisiológica plausível (piso ou FC máxima do atleta) é descartado")
        void bpmForaDaFaixaPlausivelDescarta(int inicio, int fim, boolean descartadoEsperado) {
            IntervalsIcuFcAlvoResolver.Resolucao resolucao = resolver.resolver(
                    new FcAlvoBruto(FcAlvoBruto.Base.BPM, inicio, fim), comLimiar(170));

            assertThat(resolucao.descartadoPorFaltaDeDado()).isEqualTo(descartadoEsperado);
            if (descartadoEsperado) {
                assertThat(resolucao.alvo()).isNull();
            } else {
                assertThat(resolucao.alvo()).isEqualTo(new HrTarget(inicio, fim));
            }
        }

        @Test
        @DisplayName("sem atleta, bpm literal só respeita o piso plausível (sem teto de FC máxima pra checar)")
        void bpmSemAtletaIgnoraTeto() {
            IntervalsIcuFcAlvoResolver.Resolucao resolucao = resolver.resolver(
                    new FcAlvoBruto(FcAlvoBruto.Base.BPM, 140, 150), null);

            assertThat(resolucao.alvo()).isEqualTo(new HrTarget(140, 150));
            assertThat(resolucao.descartadoPorFaltaDeDado()).isFalse();
        }
    }

    @Nested
    @DisplayName("antes e depois de cadastrar o limiar de FC (ponte com coach-cadastro-ancoras-zonas)")
    class AntesEDepoisDoCadastro {

        private static final int LIMIAR_CADASTRADO = 168;
        private static final int MAXIMA_CADASTRADA = 186;

        /** Mesmo atleta, mesmo id: só muda o que o PUT de âncoras grava. */
        private Atleta antes() {
            return Atleta.builder().id(UUID.randomUUID()).build();
        }

        private Atleta depois(Atleta antes) {
            return Atleta.builder().id(antes.getId())
                    .fcLimiar(LIMIAR_CADASTRADO).fcMaxima(MAXIMA_CADASTRADA).build();
        }

        private IntervalsIcuFcAlvoResolver.Resolucao resolverZona(String zona, Atleta atleta) {
            return resolver.resolver(IntervalsIcuTargetParser.parseZona(zona).orElseThrow(), atleta);
        }

        private IntervalsIcuFcAlvoResolver.Resolucao resolverFc(String fc, Atleta atleta) {
            return resolver.resolver(IntervalsIcuTargetParser.parseFc(fc).orElseThrow(), atleta);
        }

        @Test
        @DisplayName("zona Z2: descartada sem limiar; depois de cadastrar 168, vira 143-150 bpm")
        void zonaAntesDescartaDepoisResolve() {
            Atleta atleta = antes();

            assertThat(resolverZona("Z2", atleta).descartadoPorFaltaDeDado()).isTrue();
            assertThat(resolverZona("Z2", depois(atleta)).alvo()).isEqualTo(new HrTarget(143, 150));
        }

        @Test
        @DisplayName("percentual 85-89%: descartado sem limiar; depois de cadastrar 168, vira 143-150 bpm")
        void percentualAntesDescartaDepoisResolve() {
            Atleta atleta = antes();

            assertThat(resolverFc("85-89%", atleta).descartadoPorFaltaDeDado()).isTrue();
            assertThat(resolverFc("85-89%", depois(atleta)).alvo()).isEqualTo(new HrTarget(143, 150));
        }

        @Test
        @DisplayName("bpm literal (formato gravado pelo editor): resolve igual antes e depois, sem recalcular")
        void bpmLiteralNaoMuda() {
            Atleta atleta = antes();

            IntervalsIcuFcAlvoResolver.Resolucao semLimiar = resolverFc("143-150 bpm", atleta);
            IntervalsIcuFcAlvoResolver.Resolucao comLimiar = resolverFc("143-150 bpm", depois(atleta));

            assertThat(semLimiar.alvo()).isEqualTo(new HrTarget(143, 150));
            assertThat(comLimiar).isEqualTo(semLimiar);
        }

        @Test
        @DisplayName("bpm literal acima da máxima recém-cadastrada passa a ser descartado (a máxima real vira o teto)")
        void bpmAcimaDaMaximaNovaDescarta() {
            Atleta atleta = antes();

            // Antes: máxima estimada (fallback 190) aceita 188. Depois: máxima real 186 é o teto.
            assertThat(resolverFc("180-188 bpm", atleta).descartadoPorFaltaDeDado()).isFalse();
            assertThat(resolverFc("180-188 bpm", depois(atleta)).descartadoPorFaltaDeDado()).isTrue();
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
