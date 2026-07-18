package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.Prova;
import br.com.menthoros.backend.entity.TreinoRealizado;
import br.com.menthoros.backend.enums.ConfiancaInferencia;
import br.com.menthoros.backend.enums.TipoTreino;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Component
public class ThresholdInferenceService {

    static final int MIN_AMOSTRAS = 3;
    static final long MIN_DURACAO_MIN = 20;
    private static final double FATOR_QUINTIL = 0.20;

    /** Dias sem teste oficial para considerar o limiar desatualizado. */
    public static final long DIAS_LIMIAR_DESATUALIZACAO = 90;

    /** Expoente de Riegel — mesmo valor de RiegelCalculator.DEFAULT_EXPONENT, duplicado
     * deliberadamente para não acoplar com o pipeline de projeção (ver design.md D2). */
    static final double EXPONENTE_RIEGEL = 1.06;

    /** Offset somado ao pace de 10K equivalente para estimar o pace de limiar (design.md D2). */
    static final int OFFSET_LIMIAR_SEC_KM = 8;

    /** Faixa de distância válida (metros) para uma prova ser usada como base do limiar (design.md D2). */
    private static final int DISTANCIA_MINIMA_VALIDA_M = 5000;
    private static final int DISTANCIA_MAXIMA_VALIDA_M = 21097;

    private static final Set<TipoTreino> TIPOS_CONTINUOS =
            Set.of(TipoTreino.CONTINUO, TipoTreino.LONGO, TipoTreino.TEMPO_RUN, TipoTreino.FARTLEK);

    /**
     * Infere FC limiar a partir da mediana do quintil superior de fcMedia
     * dos treinos fornecidos na janela de 30 dias.
     *
     * Idempotent: YES · Side Effects: NONE
     * Tenant-aware: NO — o caller é responsável por passar apenas treinos do tenant correto.
     */
    public Optional<ThresholdEstimate<Integer>> inferirFcLimiar(List<TreinoRealizado> treinos, LocalDate hoje) {
        if (treinos == null || treinos.isEmpty()) return Optional.empty();

        List<Integer> fcValores = treinos.stream()
                .filter(t -> dentroJanela(t, hoje))
                .filter(t -> t.getFcMedia() != null && t.getFcMedia() > 0)
                .filter(t -> t.getDuracaoMin() != null && t.getDuracaoMin().toMinutes() > MIN_DURACAO_MIN)
                .map(TreinoRealizado::getFcMedia)
                .sorted(Comparator.reverseOrder())
                .toList();

        if (fcValores.size() < MIN_AMOSTRAS) return Optional.empty();

        int topN = Math.max(1, (int) Math.ceil(fcValores.size() * FATOR_QUINTIL));
        List<Integer> quintil = fcValores.subList(0, topN);
        int mediana = mediana(quintil);

        return Optional.of(new ThresholdEstimate<>(mediana, fcValores.size(), confianca(fcValores.size())));
    }

    /**
     * Infere pace limiar a partir da mediana do quintil mais rápido de paceMedia
     * em treinos contínuos dos últimos 30 dias.
     *
     * Idempotent: YES · Side Effects: NONE
     * Tenant-aware: NO — o caller é responsável por passar apenas treinos do tenant correto.
     */
    public Optional<ThresholdEstimate<BigDecimal>> inferirPaceLimiar(List<TreinoRealizado> treinos, LocalDate hoje) {
        if (treinos == null || treinos.isEmpty()) return Optional.empty();

        List<Long> paceSegundos = treinos.stream()
                .filter(t -> dentroJanela(t, hoje))
                .filter(t -> TIPOS_CONTINUOS.contains(t.getTipoTreino()))
                .filter(t -> t.getPaceMedia() != null && t.getPaceMedia().getSeconds() > 0)
                .filter(t -> t.getDuracaoMin() != null && t.getDuracaoMin().toMinutes() > MIN_DURACAO_MIN)
                .map(t -> t.getPaceMedia().getSeconds())
                .sorted()
                .toList();

        if (paceSegundos.size() < MIN_AMOSTRAS) return Optional.empty();

        int topN = Math.max(1, (int) Math.ceil(paceSegundos.size() * FATOR_QUINTIL));
        List<Long> quintil = paceSegundos.subList(0, topN);
        long medianaSegundos = medianaLong(quintil);

        BigDecimal paceLimiar = BigDecimal.valueOf(medianaSegundos)
                .divide(BigDecimal.valueOf(60), 4, RoundingMode.HALF_UP);

        return Optional.of(new ThresholdEstimate<>(paceLimiar, paceSegundos.size(), confianca(paceSegundos.size())));
    }

    /**
     * Resolve a distância em metros de uma prova, priorizando `distanciaKm` (customizada) sobre
     * o enum `DistanciaProva`. Duplica isoladamente `RaceProjectionServiceImpl.resolveDistanceM`
     * (linhas 202-212) em vez de extrair/importar — método `private` lá, fora do escopo desta
     * change tocar esse arquivo por um ganho de reuso pequeno (ver design.md D2b).
     *
     * Idempotent: YES · Side Effects: NONE
     */
    int resolverDistanciaMetros(Prova prova) {
        if (prova.getDistanciaKm() != null) {
            return prova.getDistanciaKm().multiply(BigDecimal.valueOf(1000)).intValue();
        }
        return switch (prova.getDistancia()) {
            case KM_5 -> 5000;
            case KM_10 -> 10000;
            case KM_21 -> 21097;
            case KM_42 -> 42195;
        };
    }

    /**
     * Deriva `paceLimiarEstimado` (decimal minutos/km, mesmo formato de {@link #inferirPaceLimiar})
     * a partir do resultado de uma prova válida. Fórmula de Riegel isolada, sem chamar
     * `RiegelCalculator.calculate()` — exige `RegressionResult` do pipeline de projeção, não é
     * função pura reaproveitável aqui (ver design.md D2). Normaliza o tempo da prova para o
     * pace-equivalente de 10K (`t_10k = t_prova * (10000 / distancia_prova_m) ^ 1.06`), converte
     * para pace por km e soma o offset de limiar.
     *
     * Idempotent: YES · Side Effects: NONE
     */
    public BigDecimal inferirPaceLimiarDeProva(Prova provaValida) {
        int distanciaM = resolverDistanciaMetros(provaValida);
        long tempoProvaSegundos = provaValida.getTempoRealizado().toSecondOfDay();

        double tempo10kEquivalenteSegundos =
                tempoProvaSegundos * Math.pow(10000.0 / distanciaM, EXPONENTE_RIEGEL);
        double paceLimiarSegundosPorKm = (tempo10kEquivalenteSegundos / 10.0) + OFFSET_LIMIAR_SEC_KM;

        return BigDecimal.valueOf(paceLimiarSegundosPorKm / 60.0).setScale(4, RoundingMode.HALF_UP);
    }

    /**
     * Filtra a lista de provas candidatas (já ordenada por `dataProva DESC`, ver
     * `ProvaRepository.findProvasRealizadasRecentes`) e retorna a primeira com distância
     * resolvida dentro da faixa válida [5000, 21097]m, ou vazio se nenhuma (design.md D2/D2b).
     *
     * Idempotent: YES · Side Effects: NONE
     */
    public Optional<Prova> encontrarProvaValidaMaisRecente(List<Prova> provasCandidatas) {
        if (provasCandidatas == null) return Optional.empty();
        return provasCandidatas.stream()
                .filter(p -> {
                    int distanciaM = resolverDistanciaMetros(p);
                    return distanciaM >= DISTANCIA_MINIMA_VALIDA_M && distanciaM <= DISTANCIA_MAXIMA_VALIDA_M;
                })
                .findFirst();
    }

    /**
     * FC limiar oficial desatualizado: ausente, sem data de teste, ou teste há mais de
     * {@link #DIAS_LIMIAR_DESATUALIZACAO} dias. Unifica a expressão antes duplicada em
     * {@code TsbServiceImpl}/{@code AthleteThresholdUpdater}, {@code CoachAthleteProfileServiceImpl}
     * e {@code ThresholdConstraintFormatter} (refactor-threshold-orchestration, CA3).
     *
     * Idempotent: YES · Side Effects: NONE
     */
    public boolean isFcLimiarDesatualizado(Atleta atleta, LocalDate hoje) {
        if (atleta == null) return false;
        if (atleta.getFcLimiar() == null || atleta.getDataUltimoTesteFc() == null) return true;
        return ChronoUnit.DAYS.between(atleta.getDataUltimoTesteFc(), hoje) > DIAS_LIMIAR_DESATUALIZACAO;
    }

    /**
     * Pace limiar oficial desatualizado — mesma regra de {@link #isFcLimiarDesatualizado}, para o
     * campo de pace.
     *
     * Idempotent: YES · Side Effects: NONE
     */
    public boolean isPaceLimiarDesatualizado(Atleta atleta, LocalDate hoje) {
        if (atleta == null) return false;
        if (atleta.getPaceLimiar() == null || atleta.getDataUltimoTestePace() == null) return true;
        return ChronoUnit.DAYS.between(atleta.getDataUltimoTestePace(), hoje) > DIAS_LIMIAR_DESATUALIZACAO;
    }

    /** Converte pace decimal (minutos) para formato "mm:ss/km". Ex: 4.7500 → "4:45/km". */
    public static String formatarPace(BigDecimal paceDecimal) {
        if (paceDecimal == null) return "N/A";
        int totalSegundos = paceDecimal.multiply(BigDecimal.valueOf(60)).intValue();
        int minutos = totalSegundos / 60;
        int segundos = totalSegundos % 60;
        return String.format("%d:%02d/km", minutos, segundos);
    }

    // Inclui o dia exato do limite (30 dias atrás), compatível com "últimos 30 dias"
    private boolean dentroJanela(TreinoRealizado t, LocalDate hoje) {
        return t.getDataTreino() != null && !t.getDataTreino().isBefore(hoje.minusDays(30));
    }

    private ConfiancaInferencia confianca(int amostras) {
        if (amostras >= 10) return ConfiancaInferencia.ALTA;
        if (amostras >= 5)  return ConfiancaInferencia.MEDIA;
        return ConfiancaInferencia.BAIXA;
    }

    private int mediana(List<Integer> lista) {
        int n = lista.size();
        if (n % 2 == 0) return lista.get(n / 2 - 1); // conservador: abaixo do centro
        return lista.get(n / 2);
    }

    private long medianaLong(List<Long> lista) {
        int n = lista.size();
        if (n % 2 == 0) return lista.get(n / 2 - 1);
        return lista.get(n / 2);
    }
}
