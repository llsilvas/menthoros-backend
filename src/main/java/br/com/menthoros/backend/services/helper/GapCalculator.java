package br.com.menthoros.backend.services.helper;

import java.time.Duration;

/**
 * GAP interno v1 (design D2 de fit-lap-derived-metrics): pace/velocidade ajustados por gradiente
 * médio da volta, com fator de custo linear assimétrico (aproximação de Minetti na faixa
 * |g| ≤ 10%) — subida encarece ~9% por 1% de gradiente; descida devolve menos (4.5%).
 *
 * <p>Funções puras e estáticas — sempre computáveis para calibração/testes. O <b>HARD GATE</b>
 * ({@link #HABILITADO}) é responsabilidade dos CONSUMIDORES: enquanto a matriz de calibração
 * (fixtures em {@code src/test/resources/fit/}) não passar no critério duplo (erro médio
 * ≤ 3 s/km E desvio máximo por volta ≤ 5 s/km na faixa |g| ≤ 3%), nenhum valor GAP é exposto
 * na API nem altera a elegibilidade do decoupling escalar.
 *
 * <p>Idempotent: YES — funções puras. Side Effects: NONE. Tenant-aware: NO.
 */
public final class GapCalculator {

    /**
     * Liga o GAP nos consumidores (série de EF e gate de CV GAP-ajustado do decoupling).
     * SÓ pode virar {@code true} em commit próprio, com a matriz de calibração completa e verde
     * no critério duplo — ver task 3.4 de fit-lap-derived-metrics e o teste executável em
     * {@code GapCalculatorTest.HardGate}.
     */
    public static final boolean HABILITADO = false;

    /** Custo por unidade de gradiente positivo (~9% mais caro por 1% de subida). */
    static final double COEF_SUBIDA = 9.0;
    /** Benefício por unidade de gradiente negativo — menor que o custo da subida (assimetria). */
    static final double COEF_DESCIDA = 4.5;
    /** Gradiente médio máximo suportado pelo modelo linear v1. */
    private static final double GRADIENTE_ABS_MAX = 0.10;
    /** Subida+descida acima desta fração da distância indica barômetro suspeito ou trail. */
    private static final double PROPORCAO_ELEVACAO_MAX = 0.30;

    private GapCalculator() {
    }

    /**
     * Fator de custo relativo do gradiente médio (1.0 = plano), ou {@code null} quando os dados
     * não sustentam o modelo (elevação/distância ausentes, gradiente extremo, elevação
     * desproporcional). Arredondado a 4 casas para estabilidade de asserção/exibição.
     */
    public static Double custoRelativo(Integer subidaMetros, Integer descidaMetros, Double distanciaKm) {
        if (subidaMetros == null || descidaMetros == null || distanciaKm == null || distanciaKm <= 0) {
            return null;
        }
        double distanciaMetros = distanciaKm * 1000.0;
        if (subidaMetros + descidaMetros > distanciaMetros * PROPORCAO_ELEVACAO_MAX) {
            return null;
        }
        double gradiente = (subidaMetros - descidaMetros) / distanciaMetros;
        if (Math.abs(gradiente) > GRADIENTE_ABS_MAX) {
            return null;
        }
        double custo = gradiente >= 0
                ? 1.0 + COEF_SUBIDA * gradiente
                : 1.0 + COEF_DESCIDA * gradiente;
        if (custo <= 0) {
            return null;
        }
        return Math.round(custo * 10_000.0) / 10_000.0;
    }

    /** Pace equivalente no plano: subida líquida produz GAP mais rápido que o pace bruto. */
    public static Duration paceGap(Duration paceBruto, Integer subidaMetros, Integer descidaMetros, Double distanciaKm) {
        if (paceBruto == null || paceBruto.isZero() || paceBruto.isNegative()) {
            return null;
        }
        Double custo = custoRelativo(subidaMetros, descidaMetros, distanciaKm);
        if (custo == null) {
            return null;
        }
        return Duration.ofSeconds(Math.round(paceBruto.toSeconds() / custo));
    }

    /** Velocidade equivalente no plano (km/h, 2 casas): o esforço na subida vale uma velocidade maior. */
    public static Double velocidadeAjustada(Double velocidadeKmh, Integer subidaMetros, Integer descidaMetros, Double distanciaKm) {
        if (velocidadeKmh == null || velocidadeKmh <= 0) {
            return null;
        }
        Double custo = custoRelativo(subidaMetros, descidaMetros, distanciaKm);
        if (custo == null) {
            return null;
        }
        return Math.round(velocidadeKmh * custo * 100.0) / 100.0;
    }
}
