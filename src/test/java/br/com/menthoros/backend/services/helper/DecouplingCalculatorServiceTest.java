package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.entity.EtapaRealizada;
import br.com.menthoros.backend.entity.TreinoRealizado;
import br.com.menthoros.backend.enums.MotivoNullDecoupling;
import br.com.menthoros.backend.enums.TipoTreino;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * O gate de aplicabilidade é o alvo prioritário (AC1): cada predicado + fronteiras (BVA).
 * Princípio "na dúvida, null": falso-negativo é aceitável; falso-positivo (número sobre
 * intervalado) não é.
 */
class DecouplingCalculatorServiceTest {

    private final DecouplingCalculatorService service = new DecouplingCalculatorService();

    @Nested
    @DisplayName("calcular(List, TipoTreino)")
    class CalcularComLista {

        // ===== Cálculo (aplicável) =====

        @Test
        @DisplayName("contínuo steady → decoupling positivo exato")
        void deveCalcularDecouplingPositivoEmContinuoSteady() {
            // 4 etapas de 10min (40min). Meia = fim da etapa 2 (sem cruzamento).
            List<EtapaRealizada> etapas = List.of(
                    etapa(1, 10, 150, 12.0),
                    etapa(2, 10, 150, 12.0),
                    etapa(3, 10, 155, 11.5),
                    etapa(4, 10, 155, 11.5)
            );
            // EF1=12/150=0.08 ; EF2=11.5/155=0.074193 ; (EF1-EF2)/EF1*100 = 7.258 -> 7.3
            assertThat(service.calcular(etapas, TipoTreino.CONTINUO)).isEqualTo(7.3);
        }

        @Test
        @DisplayName("eficiência melhora na 2ª metade → negativo")
        void deveRetornarNegativoQuandoEficienciaMelhoraNaSegundaMetade() {
            List<EtapaRealizada> etapas = List.of(
                    etapa(1, 10, 155, 11.5),
                    etapa(2, 10, 155, 11.5),
                    etapa(3, 10, 150, 12.0),
                    etapa(4, 10, 150, 12.0)
            );
            assertThat(service.calcular(etapas, TipoTreino.CONTINUO)).isEqualTo(-7.8);
        }

        @Test
        @DisplayName("segmento que cruza o meio é particionado proporcionalmente")
        void deveParticionarProporcionalmenteOSegmentoQueCruzaOMeio() {
            // 15 / 10 / 15 min (40min, meio em 20min cai no meio da etapa 2).
            // Split proporcional (5min/5min) torna as metades simétricas -> 0.0.
            // Se o cruzamento fosse ignorado, o resultado seria != 0 (prova a partição).
            List<EtapaRealizada> etapas = List.of(
                    etapa(1, 15, 150, 12.0),
                    etapa(2, 10, 160, 11.0),
                    etapa(3, 15, 150, 12.0)
            );
            assertThat(service.calcular(etapas, TipoTreino.CONTINUO)).isEqualTo(0.0);
        }

        @Test
        @DisplayName("pace convertido para velocidade quando velocidadeMedia ausente")
        void deveConverterPaceParaVelocidadeQuandoVelocidadeAusente() {
            // pace 5:00/km == 12 km/h; velocidadeMedia null -> usa a conversão.
            List<EtapaRealizada> etapas = List.of(
                    etapaPace(1, 10, 150, Duration.ofMinutes(5)),
                    etapaPace(2, 10, 150, Duration.ofMinutes(5)),
                    etapaPace(3, 10, 152, Duration.ofMinutes(5)),
                    etapaPace(4, 10, 152, Duration.ofMinutes(5))
            );
            // vel 12 constante; FC 150->152 -> EF1=12/150, EF2=12/152 -> 1.316 -> 1.3
            assertThat(service.calcular(etapas, TipoTreino.CONTINUO)).isEqualTo(1.3);
        }

        // ===== Gate -> null =====

        @ParameterizedTest
        @EnumSource(value = TipoTreino.class, names = {"INTERVALADO", "TIRO", "FARTLEK", "SUBIDA"})
        @DisplayName("tipo não-contínuo → null mesmo com CV baixo (belt-and-suspenders)")
        void deveRetornarNullEmTipoNaoContinuoMesmoComCvBaixo(TipoTreino tipo) {
            List<EtapaRealizada> etapas = List.of(
                    etapa(1, 10, 150, 12.0),
                    etapa(2, 10, 150, 12.0),
                    etapa(3, 10, 155, 11.5),
                    etapa(4, 10, 155, 11.5)
            );
            assertThat(service.calcular(etapas, tipo)).isNull();
        }

        @Test
        @DisplayName("CV(FC) no limite 0.10 → aplica (valor exato)")
        void deveAplicarQuandoCvFcNoLimiteDe010() {
            // FC [135,135,165,165] -> media 150, sd 15, CV = 0.10 (passa, <=). vel constante.
            List<EtapaRealizada> etapas = List.of(
                    etapa(1, 10, 135, 12.0),
                    etapa(2, 10, 135, 12.0),
                    etapa(3, 10, 165, 12.0),
                    etapa(4, 10, 165, 12.0)
            );
            // EF1=12/135, EF2=12/165 -> 18.18 -> 18.2
            assertThat(service.calcular(etapas, TipoTreino.CONTINUO)).isEqualTo(18.2);
        }

        @Test
        @DisplayName("CV(FC) acima de 0.10 → null")
        void deveRetornarNullQuandoCvFcAcimaDe010() {
            // FC [134,134,166,166] -> media 150, sd 16, CV = 0.1067 (> 0.10).
            List<EtapaRealizada> etapas = List.of(
                    etapa(1, 10, 134, 12.0),
                    etapa(2, 10, 134, 12.0),
                    etapa(3, 10, 166, 12.0),
                    etapa(4, 10, 166, 12.0)
            );
            assertThat(service.calcular(etapas, TipoTreino.CONTINUO)).isNull();
        }

        @Test
        @DisplayName("CV(velocidade) acima de 0.15 → null")
        void deveRetornarNullQuandoCvVelocidadeAcimaDe015() {
            // vel [9.5,9.5,13.5,13.5] -> media 11.5, sd 2, CV = 0.1739 (> 0.15). FC constante.
            List<EtapaRealizada> etapas = List.of(
                    etapa(1, 10, 150, 9.5),
                    etapa(2, 10, 150, 9.5),
                    etapa(3, 10, 150, 13.5),
                    etapa(4, 10, 150, 13.5)
            );
            assertThat(service.calcular(etapas, TipoTreino.CONTINUO)).isNull();
        }

        @Test
        @DisplayName("duração total < 20min → null")
        void deveRetornarNullQuandoDuracaoTotalAbaixoDe20min() {
            // 2 x 9min = 18min (< 20), ainda que steady.
            List<EtapaRealizada> etapas = List.of(
                    etapa(1, 9, 150, 12.0),
                    etapa(2, 9, 150, 12.0)
            );
            assertThat(service.calcular(etapas, TipoTreino.CONTINUO)).isNull();
        }

        @Test
        @DisplayName("< 2 segmentos elegíveis → null")
        void deveRetornarNullComMenosDeDoisSegmentosElegiveis() {
            List<EtapaRealizada> etapas = List.of(etapa(1, 25, 150, 12.0));
            assertThat(service.calcular(etapas, TipoTreino.CONTINUO)).isNull();
        }

        @Test
        @DisplayName("laps curtos (<60s) deixam < 2 segmentos para o CV → null")
        void deveRetornarNullQuandoLapsCurtosDeixamMenosDeDoisSegmentosParaOCv() {
            // 1 etapa longa (>=60s) + 2 laps de 40s: soma >= 20min (passa duracao),
            // mas so 1 segmento >= 60s -> CV nao avaliavel -> null.
            List<EtapaRealizada> etapas = List.of(
                    etapa(1, 20, 150, 12.0),
                    etapaSeg(2, 40, 150, 12.0),
                    etapaSeg(3, 40, 150, 12.0)
            );
            assertThat(service.calcular(etapas, TipoTreino.CONTINUO)).isNull();
        }

        @Test
        @DisplayName("aquecimento/desaquecimento rotulados descartados antes do cálculo")
        void deveDescartarAquecimentoEDesaquecimentoRotuladosAntesDoCalculo() {
            // Aquecimento/desaquecimento tem metricas destoantes: se entrassem, o CV reprovaria.
            // Descartados -> restam 2 principais steady -> aplicavel.
            List<EtapaRealizada> etapas = List.of(
                    etapaTipo(1, 5, "AQUECIMENTO", 110, 7.0),
                    etapaTipo(2, 12, "PRINCIPAL", 150, 12.0),
                    etapaTipo(3, 12, "PRINCIPAL", 152, 11.8),
                    etapaTipo(4, 5, "DESAQUECIMENTO", 105, 6.5)
            );
            assertThat(service.calcular(etapas, TipoTreino.CONTINUO)).isNotNull();
        }

        @Test
        @DisplayName("rampa não rotulada eleva o CV → null (rede de segurança)")
        void deveRetornarNullQuandoRampaNaoRotuladaElevaOCv() {
            List<EtapaRealizada> etapas = List.of(
                    etapa(1, 10, 120, 8.0),
                    etapa(2, 10, 140, 10.0),
                    etapa(3, 10, 160, 12.0),
                    etapa(4, 10, 175, 14.0)
            );
            assertThat(service.calcular(etapas, TipoTreino.CONTINUO)).isNull();
        }

        @Test
        @DisplayName("FC zerada/nula → segmentos inelegíveis → null")
        void deveRetornarNullQuandoSegmentosTemFcZeradaOuNula() {
            List<EtapaRealizada> etapas = List.of(
                    etapa(1, 12, 0, 12.0),
                    etapa(2, 12, 0, 12.0)
            );
            assertThat(service.calcular(etapas, TipoTreino.CONTINUO)).isNull();
        }

        @Test
        @DisplayName("etapas vazias ou nulas → null")
        void deveRetornarNullQuandoEtapasVaziasOuNulas() {
            assertThat(service.calcular(List.of(), TipoTreino.CONTINUO)).isNull();
            assertThat(service.calcular(null, TipoTreino.CONTINUO)).isNull();
        }

        @Test
        @DisplayName("elemento nulo dentro da lista é filtrado (sem NPE)")
        void deveIgnorarElementosNulosDentroDaLista() {
            // Lista com um null no meio de segmentos válidos: honra "na dúvida, null" sem estourar NPE.
            List<EtapaRealizada> etapas = Arrays.asList(
                    etapa(1, 10, 150, 12.0),
                    null,
                    etapa(2, 10, 150, 12.0),
                    etapa(3, 10, 155, 11.5),
                    etapa(4, 10, 155, 11.5)
            );
            assertThat(service.calcular(etapas, TipoTreino.CONTINUO)).isEqualTo(7.3);
        }

        // ===== Guarda defensivo (não ocorre no fluxo real: tipoTreino é nullable=false) =====

        @Test
        @DisplayName("tipoTreino nulo → cai no CV e computa valor")
        void deveCairNoCvQuandoTipoTreinoNuloEComputarValor() {
            List<EtapaRealizada> etapas = List.of(
                    etapa(1, 10, 150, 12.0),
                    etapa(2, 10, 150, 12.0),
                    etapa(3, 10, 155, 11.5),
                    etapa(4, 10, 155, 11.5)
            );
            assertThat(service.calcular(etapas, null)).isEqualTo(7.3);
        }
    }

    @Nested
    @DisplayName("calcular(TreinoRealizado)")
    class CalcularComTreino {

        @Test
        @DisplayName("treino nulo → null")
        void deveRetornarNullQuandoTreinoNulo() {
            assertThat(service.calcular((TreinoRealizado) null)).isNull();
        }

        @Test
        @DisplayName("treino sem etapas → null")
        void deveRetornarNullQuandoTreinoSemEtapas() {
            TreinoRealizado treino = new TreinoRealizado();
            treino.setTipoTreino(TipoTreino.CONTINUO);
            assertThat(service.calcular(treino)).isNull();
        }

        @Test
        @DisplayName("delega ao cálculo puro com etapas + tipo do treino")
        void deveDelegarAoCalculoPuro() {
            TreinoRealizado treino = new TreinoRealizado();
            treino.setTipoTreino(TipoTreino.CONTINUO);
            treino.setEtapasRealizadas(List.of(
                    etapa(1, 10, 150, 12.0),
                    etapa(2, 10, 150, 12.0),
                    etapa(3, 10, 155, 11.5),
                    etapa(4, 10, 155, 11.5)
            ));
            assertThat(service.calcular(treino)).isEqualTo(7.3);
        }
    }

    @Nested
    @DisplayName("calcularCompleto (Pa:HR + Pw:HR com motivos de null)")
    class CalcularCompleto {

        @Test
        @DisplayName("treino steady com potência em todas as voltas calcula as duas métricas")
        void calculaAmbasAsMetricas() {
            List<EtapaRealizada> etapas = List.of(
                    etapaPot(1, 10, 150, 12.0, 360),
                    etapaPot(2, 10, 150, 12.0, 360),
                    etapaPot(3, 10, 155, 11.5, 340),
                    etapaPot(4, 10, 155, 11.5, 340)
            );

            DecouplingResultado r = service.calcularCompleto(etapas, TipoTreino.CONTINUO);

            assertThat(r.percentual()).isEqualTo(7.3);
            assertThat(r.motivoNull()).isNull();
            // EF1=360/150=2.4 ; EF2=340/155=2.19355 -> 8.6021 -> 8.6
            assertThat(r.potenciaPercentual()).isEqualTo(8.6);
            assertThat(r.motivoNullPotencia()).isNull();
        }

        @Test
        @DisplayName("cobertura global de potência alta mas concentrada fora de uma metade → Pw:HR null")
        void coberturaConcentradaNumaMetadeReprovaPwHr() {
            // 50min; potência ausente só na L1 (10min) — global 80%, mas 1ª metade (25min) tem 60%.
            List<EtapaRealizada> etapas = List.of(
                    etapaPot(1, 10, 150, 12.0, null),
                    etapaPot(2, 10, 150, 12.0, 360),
                    etapaPot(3, 10, 150, 12.0, 360),
                    etapaPot(4, 10, 151, 12.0, 360),
                    etapaPot(5, 10, 151, 12.0, 360)
            );

            DecouplingResultado r = service.calcularCompleto(etapas, TipoTreino.CONTINUO);

            assertThat(r.percentual()).isNotNull();
            assertThat(r.potenciaPercentual()).isNull();
            assertThat(r.motivoNullPotencia()).isEqualTo(MotivoNullDecoupling.COBERTURA_POTENCIA_INSUFICIENTE);
        }

        @Test
        @DisplayName("cobertura de potência exatamente 80% em cada metade → Pw:HR calculado (BVA)")
        void coberturaNoLimiteDe80PorMetadeCalcula() {
            // 40min; 2ª metade (1200s) = L3(600s) + L4(360s) + L5(240s sem potência) → 960/1200 = 80%.
            List<EtapaRealizada> etapas = List.of(
                    etapaPot(1, 10, 150, 12.0, 360),
                    etapaPot(2, 10, 150, 12.0, 360),
                    etapaPot(3, 10, 150, 12.0, 360),
                    etapaSegPot(4, 360, 150, 12.0, 360),
                    etapaSegPot(5, 240, 150, 12.0, null)
            );

            DecouplingResultado r = service.calcularCompleto(etapas, TipoTreino.CONTINUO);

            assertThat(r.potenciaPercentual()).isEqualTo(0.0);
            assertThat(r.motivoNullPotencia()).isNull();
        }

        @Test
        @DisplayName("cobertura de potência logo abaixo de 80% numa metade → Pw:HR null (BVA)")
        void coberturaAbaixoDe80NumaMetadeReprova() {
            // 2ª metade (1200s) = L3(600s) + L4(350s) + L5(250s sem potência) → 950/1200 = 79,2%.
            List<EtapaRealizada> etapas = List.of(
                    etapaPot(1, 10, 150, 12.0, 360),
                    etapaPot(2, 10, 150, 12.0, 360),
                    etapaPot(3, 10, 150, 12.0, 360),
                    etapaSegPot(4, 350, 150, 12.0, 360),
                    etapaSegPot(5, 250, 150, 12.0, null)
            );

            DecouplingResultado r = service.calcularCompleto(etapas, TipoTreino.CONTINUO);

            assertThat(r.potenciaPercentual()).isNull();
            assertThat(r.motivoNullPotencia()).isEqualTo(MotivoNullDecoupling.COBERTURA_POTENCIA_INSUFICIENTE);
        }

        @Test
        @DisplayName("CV de potência acima de 0.15 → Pw:HR null sem afetar o Pa:HR")
        void cvDePotenciaAltoReprovaSoPwHr() {
            // pot [290,290,410,410] -> media 350, sd 60, CV 0.171 (> 0.15). Vel/FC steady.
            List<EtapaRealizada> etapas = List.of(
                    etapaPot(1, 10, 150, 12.0, 290),
                    etapaPot(2, 10, 150, 12.0, 290),
                    etapaPot(3, 10, 150, 12.0, 410),
                    etapaPot(4, 10, 150, 12.0, 410)
            );

            DecouplingResultado r = service.calcularCompleto(etapas, TipoTreino.CONTINUO);

            assertThat(r.percentual()).isEqualTo(0.0);
            assertThat(r.potenciaPercentual()).isNull();
            assertThat(r.motivoNullPotencia()).isEqualTo(MotivoNullDecoupling.VARIABILIDADE_ALTA);
        }

        @Test
        @DisplayName("voltas sem velocidade mas com potência → Pw:HR calculado com Pa:HR null")
        void pwHrCalculadoSemVelocidade() {
            List<EtapaRealizada> etapas = List.of(
                    etapaPot(1, 10, 150, null, 360),
                    etapaPot(2, 10, 150, null, 360),
                    etapaPot(3, 10, 155, null, 340),
                    etapaPot(4, 10, 155, null, 340)
            );

            DecouplingResultado r = service.calcularCompleto(etapas, TipoTreino.CONTINUO);

            assertThat(r.percentual()).isNull();
            assertThat(r.motivoNull()).isEqualTo(MotivoNullDecoupling.SEGMENTOS_INSUFICIENTES);
            assertThat(r.potenciaPercentual()).isEqualTo(8.6);
        }

        @Test
        @DisplayName("tipo não-contínuo anula as duas métricas com o mesmo motivo")
        void tipoNaoContinuoAnulaAmbas() {
            List<EtapaRealizada> etapas = List.of(
                    etapaPot(1, 10, 150, 12.0, 360),
                    etapaPot(2, 10, 150, 12.0, 360),
                    etapaPot(3, 10, 155, 11.5, 340),
                    etapaPot(4, 10, 155, 11.5, 340)
            );

            DecouplingResultado r = service.calcularCompleto(etapas, TipoTreino.INTERVALADO);

            assertThat(r.percentual()).isNull();
            assertThat(r.motivoNull()).isEqualTo(MotivoNullDecoupling.TIPO_NAO_CONTINUO);
            assertThat(r.potenciaPercentual()).isNull();
            assertThat(r.motivoNullPotencia()).isEqualTo(MotivoNullDecoupling.TIPO_NAO_CONTINUO);
        }

        @Test
        @DisplayName("duração total abaixo de 20min anula as duas com DURACAO_INSUFICIENTE")
        void duracaoInsuficienteAnulaAmbas() {
            List<EtapaRealizada> etapas = List.of(
                    etapaPot(1, 9, 150, 12.0, 360),
                    etapaPot(2, 9, 150, 12.0, 360)
            );

            DecouplingResultado r = service.calcularCompleto(etapas, TipoTreino.CONTINUO);

            assertThat(r.motivoNull()).isEqualTo(MotivoNullDecoupling.DURACAO_INSUFICIENTE);
            assertThat(r.motivoNullPotencia()).isEqualTo(MotivoNullDecoupling.DURACAO_INSUFICIENTE);
        }

        @Test
        @DisplayName("lista vazia ou nula → SEM_ETAPAS nas duas métricas")
        void listaVaziaOuNula() {
            assertThat(service.calcularCompleto(List.of(), TipoTreino.CONTINUO).motivoNull())
                    .isEqualTo(MotivoNullDecoupling.SEM_ETAPAS);
            assertThat(service.calcularCompleto(null, TipoTreino.CONTINUO).motivoNullPotencia())
                    .isEqualTo(MotivoNullDecoupling.SEM_ETAPAS);
        }

        @Test
        @DisplayName("legado calcular() retorna exatamente o percentual do calcularCompleto()")
        void legadoDelegaAoCompleto() {
            List<EtapaRealizada> etapas = List.of(
                    etapa(1, 10, 150, 12.0),
                    etapa(2, 10, 150, 12.0),
                    etapa(3, 10, 155, 11.5),
                    etapa(4, 10, 155, 11.5)
            );

            assertThat(service.calcular(etapas, TipoTreino.CONTINUO))
                    .isEqualTo(service.calcularCompleto(etapas, TipoTreino.CONTINUO).percentual())
                    .isEqualTo(7.3);
        }
    }

    @Nested
    @DisplayName("gate de CV GAP-ajustado (condicionado ao hard gate — GapCalculator.HABILITADO)")
    class GateCvGapAjustado {

        /**
         * Corrida steady em terreno ondulado: velocidade bruta varia com o gradiente
         * (CV 0.19 > 0.15 → reprovado hoje), mas a velocidade GAP-ajustada é constante (12.0).
         */
        private List<EtapaRealizada> onduladoSteady() {
            return List.of(
                    etapaGap(1, 10, 150, 9.45, 30, 0, 1.0),   // subida 3% → ajustada 12.0
                    etapaGap(2, 10, 150, 13.87, 0, 30, 1.0),  // descida 3% → ajustada 12.0
                    etapaGap(3, 10, 150, 9.45, 30, 0, 1.0),
                    etapaGap(4, 10, 150, 13.87, 0, 30, 1.0)
            );
        }

        @Test
        @DisplayName("flag DESLIGADA (produção): ondulado steady continua null — byte a byte com o comportamento atual")
        void flagDesligadaReproduzComportamentoAtual() {
            assertThat(GapCalculator.HABILITADO).isFalse();
            assertThat(service.calcularCompleto(onduladoSteady(), TipoTreino.CONTINUO).percentual()).isNull();
            assertThat(service.calcular(onduladoSteady(), TipoTreino.CONTINUO)).isNull();
        }

        @Test
        @DisplayName("flag ligada: ondulado steady passa a calcular (velocidade GAP-ajustada estabiliza o CV)")
        void flagLigadaDestravaOndulado() {
            DecouplingResultado r = service.calcularCompleto(onduladoSteady(), TipoTreino.CONTINUO, true);

            // ajustadas todas 12.0 com FC constante → decoupling 0.0
            assertThat(r.percentual()).isEqualTo(0.0);
            assertThat(r.motivoNull()).isNull();
        }

        @Test
        @DisplayName("flag ligada em treino plano: resultado idêntico ao da flag desligada")
        void flagLigadaNaoMudaTreinoPlano() {
            List<EtapaRealizada> planas = List.of(
                    etapaGap(1, 10, 150, 12.0, 2, 2, 1.0),
                    etapaGap(2, 10, 150, 12.0, 2, 2, 1.0),
                    etapaGap(3, 10, 155, 11.5, 2, 2, 1.0),
                    etapaGap(4, 10, 155, 11.5, 2, 2, 1.0)
            );

            assertThat(service.calcularCompleto(planas, TipoTreino.CONTINUO, true).percentual())
                    .isEqualTo(service.calcularCompleto(planas, TipoTreino.CONTINUO, false).percentual())
                    .isEqualTo(7.3);
        }

        @Test
        @DisplayName("flag ligada mas alguma volta sem elevação → cai para velocidade bruta (sem GAP parcial)")
        void voltaSemElevacaoCaiParaVelocidadeBruta() {
            List<EtapaRealizada> comBuraco = List.of(
                    etapaGap(1, 10, 150, 9.45, 30, 0, 1.0),
                    etapaGap(2, 10, 150, 13.87, 0, 30, 1.0),
                    etapaGap(3, 10, 150, 9.45, null, null, 1.0), // sem barômetro nesta volta
                    etapaGap(4, 10, 150, 13.87, 0, 30, 1.0)
            );

            // Sem GAP em TODAS as voltas, o gate usa a velocidade bruta → CV alto → null.
            DecouplingResultado r = service.calcularCompleto(comBuraco, TipoTreino.CONTINUO, true);

            assertThat(r.percentual()).isNull();
            assertThat(r.motivoNull()).isEqualTo(MotivoNullDecoupling.VARIABILIDADE_ALTA);
        }
    }

    @Nested
    @DisplayName("golden — fixture real (corrida 15 km, 16 laps)")
    class GoldenFixtureReal {

        /**
         * Characterization test (task 1.1 de fit-lap-derived-metrics): fixa o Pa:HR do treino
         * real ANTES do refactor do extrator de intensidade — qualquer mudança neste valor
         * durante o refactor é regressão, não evolução.
         */
        @Test
        @DisplayName("Pa:HR do treino real permanece byte a byte após refactors")
        void goldenPaHrDaFixtureReal() throws Exception {
            List<EtapaRealizada> etapas = etapasDaFixtureReal();

            // 15.6% = deterioração real de EF (FC 130→~160 bpm com pace estável, dia de 21-24°C)
            assertThat(service.calcular(etapas, TipoTreino.CONTINUO)).isEqualTo(15.6);
        }

        /**
         * Converte os laps da fixture na mesma forma que o FitTreinoPersister persiste
         * (velocidade derivada de distância/duração, 2 casas) — se o persister mudar a
         * derivação, este helper deve mudar junto.
         */
        private List<EtapaRealizada> etapasDaFixtureReal() throws Exception {
            try (var in = getClass().getResourceAsStream("/fit/corrida-15km-16laps.fit")) {
                var dados = new br.com.menthoros.backend.services.impl.FitParseServiceImpl().parse(in);
                return dados.laps().stream()
                        .map(lap -> EtapaRealizada.builder()
                                .ordem(lap.ordem())
                                .duracao(lap.duracao())
                                .fcMedia(lap.fcMedia())
                                .velocidadeMedia(velocidadeDerivada(lap.distanciaKm(), lap.duracao()))
                                .build())
                        .toList();
            }
        }

        private BigDecimal velocidadeDerivada(Double distanciaKm, Duration duracao) {
            if (distanciaKm == null || distanciaKm <= 0 || duracao == null || duracao.isZero()) {
                return null;
            }
            double horas = duracao.toMillis() / 3_600_000.0;
            return BigDecimal.valueOf(distanciaKm / horas).setScale(2, java.math.RoundingMode.HALF_UP);
        }
    }

    // ===== fixtures =====

    private static EtapaRealizada etapa(int ordem, int durMin, Integer fc, Double velKmh) {
        return EtapaRealizada.builder()
                .ordem(ordem)
                .tipoEtapa("PRINCIPAL")
                .duracao(Duration.ofMinutes(durMin))
                .fcMedia(fc)
                .velocidadeMedia(velKmh != null ? BigDecimal.valueOf(velKmh) : null)
                .build();
    }

    private static EtapaRealizada etapaSeg(int ordem, int durSeg, Integer fc, Double velKmh) {
        return EtapaRealizada.builder()
                .ordem(ordem)
                .tipoEtapa("PRINCIPAL")
                .duracao(Duration.ofSeconds(durSeg))
                .fcMedia(fc)
                .velocidadeMedia(velKmh != null ? BigDecimal.valueOf(velKmh) : null)
                .build();
    }

    private static EtapaRealizada etapaTipo(int ordem, int durMin, String tipoEtapa, Integer fc, Double velKmh) {
        return EtapaRealizada.builder()
                .ordem(ordem)
                .tipoEtapa(tipoEtapa)
                .duracao(Duration.ofMinutes(durMin))
                .fcMedia(fc)
                .velocidadeMedia(BigDecimal.valueOf(velKmh))
                .build();
    }

    private static EtapaRealizada etapaGap(int ordem, int durMin, Integer fc, Double velKmh,
                                           Integer subida, Integer descida, Double distKm) {
        return EtapaRealizada.builder()
                .ordem(ordem)
                .tipoEtapa("PRINCIPAL")
                .duracao(Duration.ofMinutes(durMin))
                .fcMedia(fc)
                .velocidadeMedia(velKmh != null ? BigDecimal.valueOf(velKmh) : null)
                .elevacaoGanhoMetros(subida)
                .elevacaoPerdaMetros(descida)
                .distanciaKm(distKm != null ? BigDecimal.valueOf(distKm) : null)
                .build();
    }

    private static EtapaRealizada etapaPot(int ordem, int durMin, Integer fc, Double velKmh, Integer potencia) {
        return EtapaRealizada.builder()
                .ordem(ordem)
                .tipoEtapa("PRINCIPAL")
                .duracao(Duration.ofMinutes(durMin))
                .fcMedia(fc)
                .velocidadeMedia(velKmh != null ? BigDecimal.valueOf(velKmh) : null)
                .potenciaMedia(potencia)
                .build();
    }

    private static EtapaRealizada etapaSegPot(int ordem, int durSeg, Integer fc, Double velKmh, Integer potencia) {
        return EtapaRealizada.builder()
                .ordem(ordem)
                .tipoEtapa("PRINCIPAL")
                .duracao(Duration.ofSeconds(durSeg))
                .fcMedia(fc)
                .velocidadeMedia(velKmh != null ? BigDecimal.valueOf(velKmh) : null)
                .potenciaMedia(potencia)
                .build();
    }

    private static EtapaRealizada etapaPace(int ordem, int durMin, Integer fc, Duration pace) {
        return EtapaRealizada.builder()
                .ordem(ordem)
                .tipoEtapa("PRINCIPAL")
                .duracao(Duration.ofMinutes(durMin))
                .fcMedia(fc)
                .paceMedia(pace)
                .build();
    }
}
