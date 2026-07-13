package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.entity.EtapaRealizada;
import br.com.menthoros.backend.entity.TreinoRealizado;
import br.com.menthoros.backend.enums.TipoTreino;
import lombok.extern.slf4j.Slf4j;
import org.mapstruct.Named;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.function.ToDoubleFunction;

/**
 * Cálculo do decoupling aeróbico (Pa:HR) — deterioração do fator de eficiência
 * {@code velocidade/FC} entre a 1ª e a 2ª metade de um esforço contínuo.
 *
 * <p>Derivado dos segmentos persistidos ({@link EtapaRealizada}); não persistido.
 *
 * <p><b>Gate de aplicabilidade (na dúvida, {@code null}):</b> falso-negativo (esconder
 * num treino talvez elegível) é aceitável; falso-positivo (número sobre intervalado) não.
 * O CV entre segmentos é a defesa primária, robusta e independente da classificação; o
 * belt-and-suspenders por {@link TipoTreino} é rede de segurança para um esforço
 * não-steady mal-segmentado.
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
    /** Duração mínima do esforço elegível como um todo. */
    private static final Duration DURACAO_ESFORCO_MINIMA = Duration.ofMinutes(20);
    /** Duração mínima de um segmento individual para entrar no cálculo do CV. */
    private static final Duration DURACAO_SEGMENTO_MINIMA = Duration.ofSeconds(60);

    /**
     * Tipos que nunca calculam decoupling (esforço não-steady por definição), mesmo com CV baixo.
     * {@code PROVA} e {@code TEMPO_RUN} ficam de fora deliberadamente: são esforços em ritmo
     * sustentado onde o decoupling é justamente útil. Lista revisável pós-release (heurística v1).
     */
    private static final Set<TipoTreino> TIPOS_NAO_CONTINUOS =
            EnumSet.of(TipoTreino.INTERVALADO, TipoTreino.FARTLEK, TipoTreino.TIRO, TipoTreino.SUBIDA);
    private static final Set<String> ETAPAS_DESCARTADAS = Set.of("AQUECIMENTO", "DESAQUECIMENTO", "VOLTA_CALMA");

    /** Segmento elegível com métricas já normalizadas (duração em segundos, velocidade em km/h). */
    private record Segmento(int ordem, double duracaoSeg, double fc, double velocidade) {}

    /** Acumulador ponderado por duração de uma metade do esforço. */
    private static final class Metade {
        private double somaIntensidadePonderada;
        private double somaFcPonderada;
        private double somaPeso;

        void adicionar(double intensidade, double fc, double peso) {
            somaIntensidadePonderada += intensidade * peso;
            somaFcPonderada += fc * peso;
            somaPeso += peso;
        }

        boolean vazia() {
            return somaPeso <= 0;
        }

        /** Fator de eficiência intensidade/FC ponderado por duração. */
        double eficiencia() {
            return (somaIntensidadePonderada / somaPeso) / (somaFcPonderada / somaPeso);
        }
    }

    /**
     * Adaptador para o MapStruct: extrai etapas e tipo do {@link TreinoRealizado} e delega ao cálculo puro.
     *
     * <p>Idempotent: YES — cálculo puro, sem estado. Side Effects: NONE. Tenant-aware: NO.
     */
    @Named("decouplingDeTreino")
    public Double calcular(TreinoRealizado treino) {
        if (treino == null) {
            return null;
        }
        return calcular(treino.getEtapasRealizadas(), treino.getTipoTreino());
    }

    /**
     * Calcula o decoupling aeróbico dos segmentos, ou {@code null} quando não aplicável (gate).
     *
     * <p>Idempotent: YES — cálculo puro, sem estado. Side Effects: NONE. Tenant-aware: NO.
     *
     * @param etapas segmentos realizados (podem conter elementos inválidos/nulos — filtrados)
     * @param tipoTreino tipo do treino, para o belt-and-suspenders (tolera {@code null})
     * @return decoupling em % (1 casa; positivo = piora, negativo = melhora) ou {@code null}
     */
    public Double calcular(List<EtapaRealizada> etapas, TipoTreino tipoTreino) {
        if (etapas == null || etapas.isEmpty()) {
            return null;
        }

        // Gate: tipo não-contínuo (belt-and-suspenders) — o guarda null é defensivo.
        if (tipoTreino != null && TIPOS_NAO_CONTINUOS.contains(tipoTreino)) {
            return null;
        }

        // Gate: elegibilidade — descarta nulos, aquecimento/desaquecimento rotulados e segmentos sem métrica.
        List<Segmento> elegiveis = etapas.stream()
                .filter(Objects::nonNull)
                .filter(DecouplingCalculatorService::naoEhAquecimentoOuDesaquecimento)
                .map(DecouplingCalculatorService::normalizar)
                .filter(Objects::nonNull)
                .sorted(Comparator.comparingInt(Segmento::ordem))
                .toList();
        if (elegiveis.size() < 2) {
            return null;
        }

        // Gate: duração sustentada.
        double duracaoTotal = elegiveis.stream().mapToDouble(Segmento::duracaoSeg).sum();
        if (duracaoTotal < DURACAO_ESFORCO_MINIMA.toSeconds()) {
            return null;
        }

        // Gate: steady por variabilidade (CV apenas sobre segmentos >= 60s).
        List<Segmento> paraCv = elegiveis.stream()
                .filter(s -> s.duracaoSeg() >= DURACAO_SEGMENTO_MINIMA.toSeconds())
                .toList();
        if (paraCv.size() < 2) {
            return null;
        }
        double cvFc = coeficienteVariacao(paraCv.stream().mapToDouble(Segmento::fc).toArray());
        double cvVel = coeficienteVariacao(paraCv.stream().mapToDouble(Segmento::velocidade).toArray());
        if (cvFc > CV_FC_MAX || cvVel > CV_VEL_MAX) {
            return null;
        }

        return deterioracaoPercentual(elegiveis, Segmento::velocidade);
    }

    /**
     * Mecânica compartilhada do decoupling (design D3 de fit-lap-derived-metrics): partição
     * temporal em metades — MESMO corte de tempo para qualquer métrica — com ponderação por
     * duração e deterioração do fator de eficiência {@code intensidade/FC}. A ELEGIBILIDADE
     * (CV, cobertura, duração, tipo) é responsabilidade do chamador, por métrica: este método
     * assume segmentos já filtrados e ordenados.
     *
     * <p>Idempotent: YES — cálculo puro. Side Effects: NONE. Tenant-aware: NO.
     */
    private static Double deterioracaoPercentual(List<Segmento> segmentos, ToDoubleFunction<Segmento> intensidade) {
        double duracaoTotal = segmentos.stream().mapToDouble(Segmento::duracaoSeg).sum();

        // Partição por tempo acumulado; o segmento que cruza o meio é dividido proporcionalmente.
        Metade primeira = new Metade();
        Metade segunda = new Metade();
        double meio = duracaoTotal / 2.0;
        double acumulado = 0.0;
        for (Segmento s : segmentos) {
            double inicio = acumulado;
            double fim = acumulado + s.duracaoSeg();
            double valor = intensidade.applyAsDouble(s);
            if (fim <= meio) {
                primeira.adicionar(valor, s.fc(), s.duracaoSeg());
            } else if (inicio >= meio) {
                segunda.adicionar(valor, s.fc(), s.duracaoSeg());
            } else {
                primeira.adicionar(valor, s.fc(), meio - inicio);
                segunda.adicionar(valor, s.fc(), fim - meio);
            }
            acumulado = fim;
        }

        // Gate: ambas as metades válidas.
        if (primeira.vazia() || segunda.vazia()) {
            return null;
        }

        double ef1 = primeira.eficiencia();
        double ef2 = segunda.eficiencia();
        // Gate: sanidade (EF positivo e finito).
        if (ef1 <= 0 || ef2 <= 0 || !Double.isFinite(ef1) || !Double.isFinite(ef2)) {
            return null;
        }

        double decoupling = (ef1 - ef2) / ef1 * 100.0;
        return Math.round(decoupling * 10.0) / 10.0;
    }

    private static boolean naoEhAquecimentoOuDesaquecimento(EtapaRealizada etapa) {
        String tipo = etapa.getTipoEtapa();
        return tipo == null || !ETAPAS_DESCARTADAS.contains(tipo.trim().toUpperCase(Locale.ROOT));
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
