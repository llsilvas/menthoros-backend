package com.menthoros.services.helper;

import com.menthoros.entity.Atleta;
import com.menthoros.entity.EtapaRealizada;
import com.menthoros.entity.TreinoRealizado;
import com.menthoros.enums.DiaSemana;
import com.menthoros.enums.NivelExperiencia;
import com.menthoros.enums.TipoTreino;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TssCalculatorServiceEtapasTest {

    private final TssCalculatorService service = new TssCalculatorService();

    @Test
    void deveCalcularIntervaladoPorEtapasSemDiluirPelaMediaGlobal() {
        Atleta atleta = atletaComFc();

        TreinoRealizado treinoPorEtapas = treinoBase(atleta, TipoTreino.INTERVALADO);
        treinoPorEtapas.setFcMedia(145);
        treinoPorEtapas.setEtapasRealizadas(List.of(
                etapaFc(1, 20, 130),
                etapaFc(2, 20, 175),
                etapaFc(3, 20, 130)
        ));

        TreinoRealizado treinoPorMedia = treinoBase(atleta, TipoTreino.INTERVALADO);
        treinoPorMedia.setFcMedia(145);

        int tssPorEtapas = service.calcularTss(treinoPorEtapas);
        int tssPorMedia = service.calcularTss(treinoPorMedia);

        assertEquals(79, tssPorEtapas);
        assertEquals(76, tssPorMedia);
        assertTrue(tssPorEtapas > tssPorMedia);
    }

    @Test
    void deveFazerFallbackParaCalculoGlobalQuandoEtapasNaoForemValidas() {
        Atleta atleta = atletaComPace(4.5);

        TreinoRealizado treinoComEtapasInvalidas = treinoBase(atleta, TipoTreino.FACIL);
        treinoComEtapasInvalidas.setPaceMedia(Duration.ofMinutes(5).plusSeconds(30));
        treinoComEtapasInvalidas.setEtapasRealizadas(List.of(
                EtapaRealizada.builder().ordem(1).duracao(Duration.ZERO).paceMedia(Duration.ofMinutes(4)).build(),
                EtapaRealizada.builder().ordem(2).duracao(null).percepcaoEsforco(8).build()
        ));

        TreinoRealizado treinoGlobal = treinoBase(atleta, TipoTreino.FACIL);
        treinoGlobal.setPaceMedia(Duration.ofMinutes(5).plusSeconds(30));

        assertEquals(service.calcularTss(treinoGlobal), service.calcularTss(treinoComEtapasInvalidas));
    }

    @Test
    void deveUsarTipoEtapaQuandoNaoHouverMetricasNaEtapa() {
        TreinoRealizado treino = treinoBase(null, TipoTreino.INTERVALADO);
        treino.setEtapasRealizadas(List.of(
                etapaPorTipo(1, "AQUECIMENTO", 10),
                etapaPorTipo(2, "INTERVALADO", 20),
                etapaPorTipo(3, "RECUPERACAO", 10),
                etapaPorTipo(4, "DESAQUECIMENTO", 10)
        ));

        assertEquals(54, service.calcularTss(treino));
    }

    @Test
    void devePriorizarRpeDaEtapaQuandoDisponivel() {
        TreinoRealizado treino = treinoBase(null, TipoTreino.INTERVALADO);
        treino.setEtapasRealizadas(List.of(
                etapaPorTipoComRpe(1, "INTERVALADO", 20, 8),
                etapaPorTipo(2, "RECUPERACAO", 10)
        ));

        assertEquals(43, service.calcularTss(treino));
    }

    private static Atleta atletaComFc() {
        return Atleta.builder()
                .nome("Teste")
                .objetivo("Teste")
                .nivelExperiencia(NivelExperiencia.INTERMEDIARIO)
                .fcMaxima(190)
                .fcRepouso(50)
                .fcLimiar(170)
                .build();
    }

    private static Atleta atletaComPace(double paceLimiarMinPorKm) {
        return Atleta.builder()
                .nome("Teste")
                .objetivo("Teste")
                .nivelExperiencia(NivelExperiencia.INTERMEDIARIO)
                .paceLimiar(BigDecimal.valueOf(paceLimiarMinPorKm))
                .build();
    }

    private static TreinoRealizado treinoBase(Atleta atleta, TipoTreino tipoTreino) {
        TreinoRealizado treino = new TreinoRealizado();
        treino.setAtleta(atleta);
        treino.setDiaSemana(DiaSemana.SEGUNDA);
        treino.setTipoTreino(tipoTreino);
        treino.setDuracaoMin(Duration.ofMinutes(60));
        return treino;
    }

    private static EtapaRealizada etapaFc(int ordem, int minutos, int fcMedia) {
        return EtapaRealizada.builder()
                .ordem(ordem)
                .duracao(Duration.ofMinutes(minutos))
                .fcMedia(fcMedia)
                .build();
    }

    private static EtapaRealizada etapaPorTipo(int ordem, String tipoEtapa, int minutos) {
        return EtapaRealizada.builder()
                .ordem(ordem)
                .tipoEtapa(tipoEtapa)
                .duracao(Duration.ofMinutes(minutos))
                .build();
    }

    private static EtapaRealizada etapaPorTipoComRpe(int ordem, String tipoEtapa, int minutos, int rpe) {
        return EtapaRealizada.builder()
                .ordem(ordem)
                .tipoEtapa(tipoEtapa)
                .duracao(Duration.ofMinutes(minutos))
                .percepcaoEsforco(rpe)
                .build();
    }
}
