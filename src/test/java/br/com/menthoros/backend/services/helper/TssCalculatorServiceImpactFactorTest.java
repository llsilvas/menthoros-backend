package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.TreinoRealizado;
import br.com.menthoros.backend.enums.DiaSemana;
import br.com.menthoros.backend.enums.TipoTreino;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TssCalculatorServiceImpactFactorTest {

    private final TssCalculatorService service = new TssCalculatorService();

    @Test
    /**
     * Documenta o ISSUE-04 no cenário descrito no arquivo de issue:
     * quando o TSS é calculado por FC, aplicar o fator de impacto "cheio" (ex: 1.4 no INTERVALADO)
     * tende a inflar o valor (dupla contagem), pois a FC já captura grande parte da intensidade.
     *
     * Esperado (opção A do issue): atenuar o componente extra do fator na FC
     * - fator 1.4 -> fator final 1.2 (50% do componente extra)
     */
    void deveAtenuarFatorParaFcEmTreinoIntervalado() {
        Atleta atleta = atletaComFc(195, 50, 175);

        int tssBase = service.calcularTss(treinoComFc(atleta, 170, TipoTreino.FACIL));
        int tssIntervalado = service.calcularTss(treinoComFc(atleta, 170, TipoTreino.INTERVALADO));

        assertEquals((int) Math.round(tssBase * 1.2), tssIntervalado);
    }

    @Test
    /**
     * Simula outro treino de alta exigência com dados de FC (TEMPO_RUN),
     * para garantir que a atenuação da FC se aplica a fatores > 1.0 além do INTERVALADO.
     *
     * Esperado (opção A do issue): fator 1.25 -> componente extra 0.25 -> atenuado 50% = 0.125 -> fator final 1.125
     */
    void deveAtenuarFatorParaFcEmTreinoTempoRun() {
        Atleta atleta = atletaComFc(195, 50, 175);

        int tssBase = service.calcularTss(treinoComFc(atleta, 170, TipoTreino.FACIL));
        int tssTempoRun = service.calcularTss(treinoComFc(atleta, 170, TipoTreino.TEMPO_RUN));

        assertEquals((int) Math.round(tssBase * 1.125), tssTempoRun);
    }

    @Test
    /**
     * Garante que a mudança proposta no ISSUE-04 não altera o comportamento do método PACE:
     * aqui o fator de impacto é desejável para compensar o pace médio "diluído" pela recuperação.
     *
     * Esperado: fator do INTERVALADO continua "cheio" (1.4).
     */
    void deveManterFatorCheioParaPaceEmTreinoIntervalado() {
        Atleta atleta = atletaComPace(4.5);

        int tssBase = service.calcularTss(treinoComPace(atleta, "5:30", TipoTreino.FACIL));
        int tssIntervalado = service.calcularTss(treinoComPace(atleta, "5:30", TipoTreino.INTERVALADO));

        assertEquals((int) Math.round(tssBase * 1.4), tssIntervalado);
    }

    @Test
    /**
     * Simula mais um tipo de treino no método PACE (TEMPO_RUN),
     * para garantir que o fator continua sendo aplicado integralmente fora da FC.
     */
    void deveManterFatorCheioParaPaceEmTreinoTempoRun() {
        Atleta atleta = atletaComPace(4.5);

        int tssBase = service.calcularTss(treinoComPace(atleta, "5:30", TipoTreino.FACIL));
        int tssTempoRun = service.calcularTss(treinoComPace(atleta, "5:30", TipoTreino.TEMPO_RUN));

        assertEquals((int) Math.round(tssBase * 1.25), tssTempoRun);
    }

    @Test
    /**
     * Garante que a mudança do ISSUE-04 não altera o método RPE:
     * a percepção já reflete o tipo de treino, mas o fator pode continuar como correção consistente.
     *
     * Esperado: o fator do INTERVALADO continua "cheio" (1.4) quando o cálculo base é por RPE.
     */
    void deveManterFatorCheioParaRpeEmTreinoIntervalado() {
        int tssBase = service.calcularTss(treinoComRpe(8, TipoTreino.FACIL));
        int tssIntervalado = service.calcularTss(treinoComRpe(8, TipoTreino.INTERVALADO));

        assertEquals((int) Math.round(tssBase * 1.4), tssIntervalado);
    }

    private static Atleta atletaComFc(int fcMax, int fcRepouso, int fcLimiar) {
        return Atleta.builder()
                .nome("Teste")
                .objetivo("Teste")
                .nivelExperiencia(br.com.menthoros.backend.enums.NivelExperiencia.INTERMEDIARIO)
                .fcMaxima(fcMax)
                .fcRepouso(fcRepouso)
                .fcLimiar(fcLimiar)
                .build();
    }

    private static Atleta atletaComPace(double paceLimiarMinPorKm) {
        return Atleta.builder()
                .nome("Teste")
                .objetivo("Teste")
                .nivelExperiencia(br.com.menthoros.backend.enums.NivelExperiencia.INTERMEDIARIO)
                .paceLimiar(BigDecimal.valueOf(paceLimiarMinPorKm))
                .build();
    }

    private static TreinoRealizado treinoComFc(Atleta atleta, int fcMedia, TipoTreino tipoTreino) {
        var treino = new TreinoRealizado();
        treino.setAtleta(atleta);
        treino.setDiaSemana(DiaSemana.SEGUNDA);
        treino.setTipoTreino(tipoTreino);
        treino.setDuracaoMin(Duration.ofMinutes(60));
        treino.setFcMedia(fcMedia);
        return treino;
    }

    private static TreinoRealizado treinoComPace(Atleta atleta, String paceMmSs, TipoTreino tipoTreino) {
        var treino = new TreinoRealizado();
        treino.setAtleta(atleta);
        treino.setDiaSemana(DiaSemana.SEGUNDA);
        treino.setTipoTreino(tipoTreino);
        treino.setDuracaoMin(Duration.ofMinutes(60));
        treino.setFcMedia(null);
        treino.setPaceMedia(parsePaceMinPorKm(paceMmSs));
        return treino;
    }

    private static TreinoRealizado treinoComRpe(int rpe, TipoTreino tipoTreino) {
        var treino = new TreinoRealizado();
        treino.setDiaSemana(DiaSemana.SEGUNDA);
        treino.setTipoTreino(tipoTreino);
        treino.setDuracaoMin(Duration.ofMinutes(60));
        treino.setPercepcaoEsforco(rpe);
        treino.setFcMedia(null);
        treino.setPaceMedia(null);
        return treino;
    }

    private static Duration parsePaceMinPorKm(String mmSs) {
        String[] parts = mmSs.split(":");
        long minutes = Long.parseLong(parts[0]);
        long seconds = Long.parseLong(parts[1]);
        return Duration.ofMinutes(minutes).plusSeconds(seconds);
    }
}

