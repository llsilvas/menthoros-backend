package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.entity.EtapaRealizada;
import br.com.menthoros.backend.enums.TipoTreino;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Cálculo do decoupling aeróbico (Pa:HR) — deterioração do fator de eficiência
 * {@code velocidade/FC} entre a 1ª e a 2ª metade de um esforço contínuo.
 *
 * <p>Derivado dos segmentos persistidos ({@link EtapaRealizada}); não persistido.
 *
 * <p><b>Gate de aplicabilidade (na dúvida, {@code null}):</b> falso-negativo (esconder
 * num treino talvez elegível) é aceitável; falso-positivo (número sobre intervalado) não.
 * O CV entre segmentos é a defesa primária, robusta e independente da classificação; o
 * belt-and-suspenders por {@link TipoTreino} é rede de segurança para um contínuo
 * mal-segmentado.
 *
 * <p>Thresholds são heurística v1 (constantes nomeadas), calibráveis sem mudar contrato.
 *
 * <p>Idempotent: YES — cálculo puro, sem estado. Side Effects: NONE. Tenant-aware: NO.
 */
@Slf4j
@Component
public class DecouplingCalculatorService {

    private static final double CV_FC_MAX = 0.10;
    private static final double CV_VEL_MAX = 0.15;
    private static final Duration DURACAO_MIN_SEG = Duration.ofMinutes(20);
    private static final Duration MIN_SEG_DURACAO = Duration.ofSeconds(60);

    private static final Set<TipoTreino> TIPOS_NAO_CONTINUOS =
            EnumSet.of(TipoTreino.INTERVALADO, TipoTreino.FARTLEK, TipoTreino.TIRO);
    private static final Set<String> ETAPAS_DESCARTADAS = Set.of("AQUECIMENTO", "DESAQUECIMENTO", "VOLTA_CALMA");

    /** Segmento elegível com métricas já normalizadas (duração em segundos, velocidade em km/h). */
    private record Segmento(int ordem, double duracaoSeg, double fc, double velocidade) {}

    /** Acumulador ponderado por duração de uma metade do esforço. */
    private static final class Metade {
        private double somaVelPonderada;
        private double somaFcPonderada;
        private double somaPeso;

        void adicionar(Segmento s, double peso) {
            somaVelPonderada += s.velocidade() * peso;
            somaFcPonderada += s.fc() * peso;
            somaPeso += peso;
        }

        boolean vazia() {
            return somaPeso <= 0;
        }

        /** Fator de eficiência velocidade/FC ponderado por duração. */
        double eficiencia() {
            return (somaVelPonderada / somaPeso) / (somaFcPonderada / somaPeso);
        }
    }

    public Double calcular(List<EtapaRealizada> etapas, TipoTreino tipoTreino) {
        if (etapas == null || etapas.isEmpty()) {
            return null;
        }

        // Belt-and-suspenders: intervalado/fartlek/tiro nunca calcula (o guarda null é defensivo).
        if (tipoTreino != null && TIPOS_NAO_CONTINUOS.contains(tipoTreino)) {
            return null;
        }

        // Predicado 1 — elegibilidade (descarta aquecimento/desaquecimento rotulados e segmentos sem métrica).
        List<Segmento> elegiveis = etapas.stream()
                .filter(DecouplingCalculatorService::naoEhAquecimentoOuDesaquecimento)
                .map(DecouplingCalculatorService::normalizar)
                .filter(s -> s != null)
                .sorted(Comparator.comparingInt(Segmento::ordem))
                .toList();
        if (elegiveis.size() < 2) {
            return null;
        }

        // Predicado 2 — duração sustentada.
        double duracaoTotal = elegiveis.stream().mapToDouble(Segmento::duracaoSeg).sum();
        if (duracaoTotal < DURACAO_MIN_SEG.toSeconds()) {
            return null;
        }

        // Predicado 4 — steady por variabilidade (CV apenas sobre segmentos >= 60s).
        List<Segmento> paraCv = elegiveis.stream()
                .filter(s -> s.duracaoSeg() >= MIN_SEG_DURACAO.toSeconds())
                .toList();
        if (paraCv.size() < 2) {
            return null;
        }
        double cvFc = coeficienteVariacao(paraCv.stream().mapToDouble(Segmento::fc).toArray());
        double cvVel = coeficienteVariacao(paraCv.stream().mapToDouble(Segmento::velocidade).toArray());
        if (cvFc > CV_FC_MAX || cvVel > CV_VEL_MAX) {
            return null;
        }

        // Partição por tempo acumulado; o segmento que cruza o meio é dividido proporcionalmente.
        Metade primeira = new Metade();
        Metade segunda = new Metade();
        double meio = duracaoTotal / 2.0;
        double acumulado = 0.0;
        for (Segmento s : elegiveis) {
            double inicio = acumulado;
            double fim = acumulado + s.duracaoSeg();
            if (fim <= meio) {
                primeira.adicionar(s, s.duracaoSeg());
            } else if (inicio >= meio) {
                segunda.adicionar(s, s.duracaoSeg());
            } else {
                primeira.adicionar(s, meio - inicio);
                segunda.adicionar(s, fim - meio);
            }
            acumulado = fim;
        }

        // Predicado 3 — ambas as metades válidas.
        if (primeira.vazia() || segunda.vazia()) {
            return null;
        }

        double ef1 = primeira.eficiencia();
        double ef2 = segunda.eficiencia();
        // Predicado 6 — sanidade.
        if (ef1 <= 0 || ef2 <= 0 || !Double.isFinite(ef1) || !Double.isFinite(ef2)) {
            return null;
        }

        double decoupling = (ef1 - ef2) / ef1 * 100.0;
        return Math.round(decoupling * 10.0) / 10.0;
    }

    private static boolean naoEhAquecimentoOuDesaquecimento(EtapaRealizada etapa) {
        String tipo = etapa.getTipoEtapa();
        return tipo == null || !ETAPAS_DESCARTADAS.contains(tipo.trim().toUpperCase());
    }

    /** Converte a etapa em {@link Segmento} normalizado, ou {@code null} se não tiver métrica utilizável. */
    private static Segmento normalizar(EtapaRealizada etapa) {
        Duration duracao = etapa.getDuracao();
        Integer fc = etapa.getFcMedia();
        if (duracao == null || duracao.isZero() || duracao.isNegative() || fc == null || fc <= 0) {
            return null;
        }
        Double velocidade = velocidadeKmh(etapa);
        if (velocidade == null || velocidade <= 0) {
            return null;
        }
        return new Segmento(etapa.getOrdem() != null ? etapa.getOrdem() : 0, duracao.toSeconds(), fc, velocidade);
    }

    /** Velocidade em km/h: direto de {@code velocidadeMedia}, senão convertida de {@code paceMedia}. */
    private static Double velocidadeKmh(EtapaRealizada etapa) {
        if (etapa.getVelocidadeMedia() != null && etapa.getVelocidadeMedia().signum() > 0) {
            return etapa.getVelocidadeMedia().doubleValue();
        }
        Duration pace = etapa.getPaceMedia();
        if (pace != null && !pace.isZero() && !pace.isNegative()) {
            return 3600.0 / pace.toSeconds();
        }
        return null;
    }

    /** CV = desvio padrão (populacional) / média. */
    private static double coeficienteVariacao(double[] valores) {
        double media = 0.0;
        for (double v : valores) {
            media += v;
        }
        media /= valores.length;
        if (media == 0.0) {
            return Double.POSITIVE_INFINITY;
        }
        double variancia = 0.0;
        for (double v : valores) {
            double d = v - media;
            variancia += d * d;
        }
        variancia /= valores.length;
        return Math.sqrt(variancia) / media;
    }
}
