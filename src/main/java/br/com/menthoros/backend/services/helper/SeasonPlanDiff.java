package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.dto.output.SeasonPlanDiffDto;
import br.com.menthoros.backend.entity.SeasonWeek;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Diff do ajuste contra a temporada ativa (add-season-plan-version-diff): por {@code weekIndex}, os campos
 * que o treinador e o recálculo mexem. O marco fica de fora — o recálculo não o move.
 */
public final class SeasonPlanDiff {

    private record Campo(String nome, Function<SeasonWeek, @Nullable Object> valor) {
    }

    private static final List<Campo> CAMPOS = List.of(
            new Campo("phase", SeasonWeek::getPhase),
            new Campo("targetVolumeKm", SeasonWeek::getTargetVolumeKm),
            new Campo("targetTss", SeasonWeek::getTargetTss),
            new Campo("longRunKm", SeasonWeek::getLongRunKm),
            new Campo("deload", SeasonWeek::isDeload),
            new Campo("keySessions", SeasonWeek::getKeySessions));

    private SeasonPlanDiff() {
    }

    /** Campos alterados do {@code rascunho} em relação à {@code ativa}, por {@code weekIndex} e na ordem fixa dos campos. */
    public static List<SeasonPlanDiffDto> between(List<SeasonWeek> ativa, List<SeasonWeek> rascunho) {
        if (ativa == null || rascunho == null) {
            throw new IllegalArgumentException("As semanas da ativa e do rascunho não podem ser nulas");
        }
        Map<Integer, SeasonWeek> porIndice = ativa.stream()
                .collect(Collectors.toMap(SeasonWeek::getWeekIndex, Function.identity()));
        List<SeasonPlanDiffDto> diff = new ArrayList<>();
        for (SeasonWeek depois : rascunho.stream().sorted(Comparator.comparing(SeasonWeek::getWeekIndex)).toList()) {
            @Nullable SeasonWeek antes = porIndice.get(depois.getWeekIndex());
            for (Campo campo : CAMPOS) {
                @Nullable Object de = antes == null ? null : campo.valor().apply(antes);
                @Nullable Object para = campo.valor().apply(depois);
                if (!iguais(de, para)) {
                    diff.add(new SeasonPlanDiffDto(depois.getWeekIndex(), campo.nome(), de, para, depois.getValueSource()));
                }
            }
        }
        return List.copyOf(diff);
    }

    /** Semanas distintas com ao menos um campo no diff. */
    public static long changedWeeks(List<SeasonPlanDiffDto> diff) {
        return diff.stream().map(SeasonPlanDiffDto::weekIndex).distinct().count();
    }

    private static boolean iguais(@Nullable Object a, @Nullable Object b) {
        if (a instanceof BigDecimal x && b instanceof BigDecimal y) {
            return x.compareTo(y) == 0;
        }
        return Objects.equals(a, b);
    }
}
