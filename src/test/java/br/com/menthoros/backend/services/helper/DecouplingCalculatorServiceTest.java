package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.entity.EtapaRealizada;
import br.com.menthoros.backend.enums.TipoTreino;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * O gate de aplicabilidade é o alvo prioritário (AC1): cada predicado + fronteiras (BVA).
 * Princípio "na dúvida, null": falso-negativo é aceitável; falso-positivo (número sobre
 * intervalado) não é.
 */
class DecouplingCalculatorServiceTest {

    private final DecouplingCalculatorService service = new DecouplingCalculatorService();

    // ===== Cálculo (aplicável) =====

    @Test
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
    void deveConverterPaceParaVelocidadeQuandoVelocidadeAusente() {
        // pace 5:00/km == 12 km/h; velocidadeMedia null -> usa a conversão.
        List<EtapaRealizada> etapas = List.of(
                etapaPace(1, 10, 150, Duration.ofMinutes(5)),
                etapaPace(2, 10, 150, Duration.ofMinutes(5)),
                etapaPace(3, 10, 152, Duration.ofMinutes(5)),
                etapaPace(4, 10, 152, Duration.ofMinutes(5))
        );
        assertThat(service.calcular(etapas, TipoTreino.CONTINUO)).isNotNull();
    }

    // ===== Gate -> null =====

    @Test
    void deveRetornarNullEmIntervaladoPorTipoTreinoMesmoComCvBaixo() {
        // Belt-and-suspenders: segmentos steady (CV passaria), mas o tipo é intervalado.
        List<EtapaRealizada> etapas = List.of(
                etapa(1, 10, 150, 12.0),
                etapa(2, 10, 150, 12.0),
                etapa(3, 10, 155, 11.5),
                etapa(4, 10, 155, 11.5)
        );
        assertThat(service.calcular(etapas, TipoTreino.INTERVALADO)).isNull();
        assertThat(service.calcular(etapas, TipoTreino.TIRO)).isNull();
        assertThat(service.calcular(etapas, TipoTreino.FARTLEK)).isNull();
    }

    @Test
    void deveAplicarQuandoCvFcNoLimiteDe010() {
        // FC [135,135,165,165] -> media 150, sd 15, CV = 0.10 (passa, <=).
        List<EtapaRealizada> etapas = List.of(
                etapa(1, 10, 135, 12.0),
                etapa(2, 10, 135, 12.0),
                etapa(3, 10, 165, 12.0),
                etapa(4, 10, 165, 12.0)
        );
        assertThat(service.calcular(etapas, TipoTreino.CONTINUO)).isNotNull();
    }

    @Test
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
    void deveRetornarNullQuandoDuracaoTotalAbaixoDe20min() {
        // 2 x 9min = 18min (< 20), ainda que steady.
        List<EtapaRealizada> etapas = List.of(
                etapa(1, 9, 150, 12.0),
                etapa(2, 9, 150, 12.0)
        );
        assertThat(service.calcular(etapas, TipoTreino.CONTINUO)).isNull();
    }

    @Test
    void deveRetornarNullComMenosDeDoisSegmentosElegiveis() {
        List<EtapaRealizada> etapas = List.of(etapa(1, 25, 150, 12.0));
        assertThat(service.calcular(etapas, TipoTreino.CONTINUO)).isNull();
    }

    @Test
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
    void deveRetornarNullQuandoRampaNaoRotuladaElevaOCv() {
        // Sem rotulos de aquecimento, mas rampa progressiva -> CV alto -> null (rede de seguranca).
        List<EtapaRealizada> etapas = List.of(
                etapa(1, 10, 120, 8.0),
                etapa(2, 10, 140, 10.0),
                etapa(3, 10, 160, 12.0),
                etapa(4, 10, 175, 14.0)
        );
        assertThat(service.calcular(etapas, TipoTreino.CONTINUO)).isNull();
    }

    @Test
    void deveRetornarNullQuandoSegmentosTemFcZeradaOuNula() {
        List<EtapaRealizada> etapas = List.of(
                etapa(1, 12, 0, 12.0),
                etapa(2, 12, 0, 12.0)
        );
        assertThat(service.calcular(etapas, TipoTreino.CONTINUO)).isNull();
    }

    @Test
    void deveRetornarNullQuandoEtapasVaziasOuNulas() {
        assertThat(service.calcular(List.of(), TipoTreino.CONTINUO)).isNull();
        assertThat(service.calcular(null, TipoTreino.CONTINUO)).isNull();
    }

    // ===== Guarda defensivo (nao ocorre no fluxo real: tipoTreino e nullable=false) =====

    @Test
    void deveCairNoCvQuandoTipoTreinoNuloEComputarValor() {
        List<EtapaRealizada> etapas = List.of(
                etapa(1, 10, 150, 12.0),
                etapa(2, 10, 150, 12.0),
                etapa(3, 10, 155, 11.5),
                etapa(4, 10, 155, 11.5)
        );
        assertThat(service.calcular(etapas, null)).isEqualTo(7.3);
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
