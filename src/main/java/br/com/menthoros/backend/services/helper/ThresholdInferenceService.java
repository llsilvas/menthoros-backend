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

    private static final Set<TipoTreino> TIPOS_CONTINUOS =
            Set.of(TipoTreino.CONTINUO, TipoTreino.LONGO, TipoTreino.TEMPO_RUN, TipoTreino.FARTLEK);

    /**
     * Idempotent: YES · Side Effects: NONE · Tenant-aware: YES (lista já filtrada pelo caller)
     */
    public Optional<ThresholdEstimate> inferirFcLimiar(List<TreinoRealizado> treinos, LocalDate hoje) {
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

        return Optional.of(new ThresholdEstimate(mediana, fcValores.size(), confianca(fcValores.size())));
    }

    /**
     * Idempotent: YES · Side Effects: NONE · Tenant-aware: YES (lista já filtrada pelo caller)
     */
    public Optional<ThresholdEstimate> inferirPaceLimiar(List<TreinoRealizado> treinos, LocalDate hoje) {
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

        return Optional.of(new ThresholdEstimate(paceLimiar, paceSegundos.size(), confianca(paceSegundos.size())));
    }

    private boolean dentroJanela(TreinoRealizado t, LocalDate hoje) {
        return t.getDataTreino() != null && t.getDataTreino().isAfter(hoje.minusDays(30));
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
