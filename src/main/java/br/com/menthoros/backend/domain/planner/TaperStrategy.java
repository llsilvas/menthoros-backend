package br.com.menthoros.backend.domain.planner;

import java.util.List;

/**
 * Reducao exponencial de volume nas semanas pre-prova, preservando intensidade (CA5,
 * proposal.md). Duracao da janela de taper por distancia: 5-10K -&gt; ate 7 dias,
 * 21K -&gt; ate 14 dias, 42K/Ironman -&gt; ate 21 dias.
 *
 * <p>A curva de reducao e continua em {@code diasParaProva}: quanto mais perto da prova, maior
 * a reducao, ate um teto de {@link #MAX_REDUCTION}. {@code RACE_WEEK} (0-6 dias) e o trecho
 * terminal dessa mesma curva, nao um caso a parte.
 */
public class TaperStrategy {

    private static final double MAX_REDUCTION = 0.65;
    private static final double DECAY_TAU_DIAS = 38.0;
    private static final double FAIXA_TOLERANCIA = 0.10;

    public boolean estaNaJanelaDeTaper(double distanciaKm, long diasParaProva) {
        if (diasParaProva < 0) {
            return false;
        }
        return diasParaProva <= duracaoMaximaDias(distanciaKm);
    }

    public double resolverReducaoPercentual(long diasParaProva) {
        long dias = Math.max(diasParaProva, 0);
        return MAX_REDUCTION * Math.exp(-dias / DECAY_TAU_DIAS);
    }

    public WeeklyLoadTarget aplicar(WeeklyLoadTarget picoPreTaper, long diasParaProva) {
        double reducao = resolverReducaoPercentual(diasParaProva);
        double alvo = picoPreTaper.targetTss() * (1 - reducao);
        String rationale = String.format(
                "Taper: reducao de %.0f%% sobre o pico pre-taper (%d dias para a prova)",
                reducao * 100, diasParaProva);
        return new WeeklyLoadTarget(alvo, alvo * (1 - FAIXA_TOLERANCIA), alvo * (1 + FAIXA_TOLERANCIA), rationale);
    }

    /**
     * Reduz o TSS de cada sessao proporcionalmente, preservando dia, tipo e zona de
     * intensidade — a curva do taper afeta volume, nao a qualidade do estimulo.
     */
    public List<SessionSlot> preservarIntensidade(List<SessionSlot> sessoesPreTaper, double reducaoPercentual) {
        return sessoesPreTaper.stream()
                .map(s -> new SessionSlot(
                        s.day(),
                        s.sessionType(),
                        s.targetTss() * (1 - reducaoPercentual),
                        s.intensityZone(),
                        s.chave(),
                        s.durationMinutes()))
                .toList();
    }

    private long duracaoMaximaDias(double distanciaKm) {
        if (distanciaKm <= 10.5) {
            return 7;
        }
        if (distanciaKm <= 21.5) {
            return 14;
        }
        return 21;
    }
}
