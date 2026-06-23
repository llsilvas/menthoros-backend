package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.entity.TreinoRealizado;
import br.com.menthoros.backend.enums.ConfiancaInferencia;
import br.com.menthoros.backend.enums.TipoTreino;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
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
