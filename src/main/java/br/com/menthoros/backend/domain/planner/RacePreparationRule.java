package br.com.menthoros.backend.domain.planner;

import br.com.menthoros.backend.enums.DistanciaProva;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Tabela de semanas mínimas de preparação por distância de prova (spec prova-preparacao-minima).
 *
 * <p>Distância padrão: 5 km → 8 semanas, 10 km → 10, 21 km → 12, 42 km → 16. Distância livre cai
 * na faixa mais próxima: até 7,5 km → regra de 5 km; até 15 → 10 km; até 30 → 21 km; acima → 42 km.
 * Pura e sem Spring — reutilizável pelo macrociclo.
 */
public final class RacePreparationRule {

    private static final BigDecimal LIMITE_FAIXA_5K = new BigDecimal("7.5");
    private static final BigDecimal LIMITE_FAIXA_10K = new BigDecimal("15");
    private static final BigDecimal LIMITE_FAIXA_21K = new BigDecimal("30");

    private RacePreparationRule() {
    }

    /**
     * Semanas mínimas de preparação. Para distância padrão, {@code distanciaKm} é ignorado; para
     * {@link DistanciaProva#CUSTOMIZADA} ele é obrigatório e positivo.
     */
    public static int minimoSemanas(DistanciaProva distancia, @Nullable BigDecimal distanciaKm) {
        if (distancia == null) {
            throw new IllegalArgumentException("distancia não pode ser nula");
        }
        return switch (distancia) {
            case KM_5 -> 8;
            case KM_10 -> 10;
            case KM_21 -> 12;
            case KM_42 -> 16;
            case CUSTOMIZADA -> minimoSemanasPorFaixa(distanciaKm);
        };
    }

    private static int minimoSemanasPorFaixa(@Nullable BigDecimal distanciaKm) {
        if (distanciaKm == null || distanciaKm.signum() <= 0) {
            throw new IllegalArgumentException("distância customizada exige distanciaKm positivo");
        }
        if (distanciaKm.compareTo(LIMITE_FAIXA_5K) <= 0) return 8;
        if (distanciaKm.compareTo(LIMITE_FAIXA_10K) <= 0) return 10;
        if (distanciaKm.compareTo(LIMITE_FAIXA_21K) <= 0) return 12;
        return 16;
    }

    /** Quilometragem nominal da distância padrão (5 / 10 / 21,1 / 42,2). */
    public static BigDecimal distanciaNominalKm(DistanciaProva distancia) {
        if (distancia == null) {
            throw new IllegalArgumentException("distancia não pode ser nula");
        }
        return switch (distancia) {
            case KM_5 -> new BigDecimal("5");
            case KM_10 -> new BigDecimal("10");
            case KM_21 -> new BigDecimal("21.1");
            case KM_42 -> new BigDecimal("42.2");
            case CUSTOMIZADA -> throw new IllegalArgumentException("distância customizada não tem valor nominal");
        };
    }

    /** Data em que a preparação deveria começar: data da prova menos as semanas mínimas. */
    public static LocalDate inicioPreparacao(LocalDate dataProva, int semanas) {
        if (dataProva == null) {
            throw new IllegalArgumentException("dataProva não pode ser nula");
        }
        return dataProva.minusWeeks(semanas);
    }

    /** Semanas inteiras até a prova ({@code floor(dias / 7)}), nunca negativo. */
    public static int semanasFaltando(LocalDate dataProva, LocalDate hoje) {
        if (dataProva == null || hoje == null) {
            throw new IllegalArgumentException("dataProva e hoje não podem ser nulos");
        }
        long dias = ChronoUnit.DAYS.between(hoje, dataProva);
        return dias <= 0 ? 0 : (int) (dias / 7);
    }

    /** Preparação curta quando o início derivado já ficou para trás. */
    public static boolean preparacaoCurta(@Nullable LocalDate inicioPreparacao, LocalDate hoje) {
        if (hoje == null) {
            throw new IllegalArgumentException("hoje não pode ser nulo");
        }
        return inicioPreparacao != null && inicioPreparacao.isBefore(hoje);
    }
}
