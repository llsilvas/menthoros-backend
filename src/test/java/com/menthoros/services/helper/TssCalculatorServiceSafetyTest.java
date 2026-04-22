package com.menthoros.services.helper;

import com.menthoros.entity.Atleta;
import com.menthoros.entity.TreinoRealizado;
import com.menthoros.enums.DiaSemana;
import com.menthoros.enums.NivelExperiencia;
import com.menthoros.enums.TipoTreino;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TssCalculatorServiceSafetyTest {

    private final TssCalculatorService service = new TssCalculatorService();

    @Test
    void deveUsarPaceQuandoDadosDeFcForemInconsistentes() {
        Atleta atleta = Atleta.builder()
                .nome("Teste")
                .objetivo("Teste")
                .nivelExperiencia(NivelExperiencia.INTERMEDIARIO)
                .fcMaxima(180)
                .fcRepouso(60)
                .fcLimiar(170)
                .paceLimiar(BigDecimal.valueOf(4.5))
                .build();

        TreinoRealizado treino = treinoBase(atleta, TipoTreino.FACIL);
        treino.setFcMedia(55);
        treino.setPaceMedia(Duration.ofMinutes(5).plusSeconds(30));

        assertEquals(67, service.calcularTss(treino));
    }

    @Test
    void deveEstimarTssPorTipoQuandoNaoHouverNenhumaMetricaObjetiva() {
        TreinoRealizado treino = treinoBase(null, TipoTreino.LONGO);
        treino.setPercepcaoEsforco(null);
        treino.setFcMedia(null);
        treino.setPaceMedia(null);

        assertEquals(64, service.calcularTss(treino));
    }

    @Test
    void devePermitirTssMaisBaixoParaTreinoRegenerativoPorRpe() {
        TreinoRealizado treino = treinoBase(null, TipoTreino.REGENERATIVO);
        treino.setPercepcaoEsforco(1);
        treino.setFcMedia(null);
        treino.setPaceMedia(null);

        assertEquals(17, service.calcularTss(treino));
    }

    private static TreinoRealizado treinoBase(Atleta atleta, TipoTreino tipoTreino) {
        TreinoRealizado treino = new TreinoRealizado();
        treino.setAtleta(atleta);
        treino.setDiaSemana(DiaSemana.SEGUNDA);
        treino.setTipoTreino(tipoTreino);
        treino.setDuracaoMin(Duration.ofMinutes(60));
        return treino;
    }
}
