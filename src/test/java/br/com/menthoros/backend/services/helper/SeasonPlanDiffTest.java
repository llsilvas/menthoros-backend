package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.domain.planner.TrainingPhase;
import br.com.menthoros.backend.dto.output.SeasonPlanDiffDto;
import br.com.menthoros.backend.entity.SeasonWeek;
import br.com.menthoros.backend.enums.WeekValueSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SeasonPlanDiffTest {

    @Nested
    @DisplayName("between")
    class Between {

        @Test
        @DisplayName("rascunho igual à ativa dá lista vazia")
        void vazio() {
            assertThat(SeasonPlanDiff.between(List.of(semana(1), semana(2)), List.of(semana(1), semana(2)))).isEmpty();
        }

        @Test
        @DisplayName("um campo alterado vira uma entrada com antes, depois e a origem da semana do rascunho")
        void umCampo() {
            SeasonWeek ativa = semana(5);
            ativa.setLongRunKm(new BigDecimal("18"));
            SeasonWeek rascunho = semana(5);
            rascunho.setLongRunKm(new BigDecimal("20"));
            rascunho.setValueSource(WeekValueSource.COACH);

            assertThat(SeasonPlanDiff.between(List.of(ativa), List.of(rascunho))).containsExactly(
                    new SeasonPlanDiffDto(5, "longRunKm", new BigDecimal("18"), new BigDecimal("20"), WeekValueSource.COACH));
        }

        @Test
        @DisplayName("vários campos numa semana saem na ordem fixa dos campos, semana a semana")
        void variosCampos() {
            SeasonWeek ativa = semana(3);
            SeasonWeek rascunho = semana(3);
            rascunho.setPhase(TrainingPhase.BUILD);
            rascunho.setTargetVolumeKm(new BigDecimal("45"));
            rascunho.setTargetTss(350.0);
            rascunho.setDeload(true);
            rascunho.setKeySessions("6x1km");
            SeasonWeek outra = semana(4);
            outra.setTargetTss(310.0);

            List<SeasonPlanDiffDto> diff = SeasonPlanDiff.between(List.of(ativa, semana(4)), List.of(outra, rascunho));

            assertThat(diff).extracting(SeasonPlanDiffDto::weekIndex, SeasonPlanDiffDto::field).containsExactly(
                    org.assertj.core.groups.Tuple.tuple(3, "phase"),
                    org.assertj.core.groups.Tuple.tuple(3, "targetVolumeKm"),
                    org.assertj.core.groups.Tuple.tuple(3, "targetTss"),
                    org.assertj.core.groups.Tuple.tuple(3, "deload"),
                    org.assertj.core.groups.Tuple.tuple(3, "keySessions"),
                    org.assertj.core.groups.Tuple.tuple(4, "targetTss"));
            assertThat(diff.get(0).before()).isEqualTo(TrainingPhase.BASE);
            assertThat(diff.get(0).after()).isEqualTo(TrainingPhase.BUILD);
            assertThat(diff.get(4).before()).isNull();
        }

        @Test
        @DisplayName("BigDecimal com escalas diferentes não gera falso positivo")
        void escalas() {
            SeasonWeek ativa = semana(1);
            ativa.setTargetVolumeKm(new BigDecimal("40"));
            ativa.setLongRunKm(new BigDecimal("12.0"));
            SeasonWeek rascunho = semana(1);
            rascunho.setTargetVolumeKm(new BigDecimal("40.000"));
            rascunho.setLongRunKm(new BigDecimal("12"));

            assertThat(SeasonPlanDiff.between(List.of(ativa), List.of(rascunho))).isEmpty();
        }

        @Test
        @DisplayName("o marco fica fora do diff")
        void marcoFora() {
            SeasonWeek rascunho = semana(1);
            rascunho.setMilestoneRaceId(UUID.randomUUID());

            assertThat(SeasonPlanDiff.between(List.of(semana(1)), List.of(rascunho))).isEmpty();
        }

        @Test
        @DisplayName("semana sem par na ativa compara com nulo")
        void semPar() {
            List<SeasonPlanDiffDto> diff = SeasonPlanDiff.between(List.of(), List.of(semana(1)));

            assertThat(diff).extracting(SeasonPlanDiffDto::field)
                    .containsExactly("phase", "targetVolumeKm", "targetTss", "deload");
        }

        @Test
        @DisplayName("listas nulas são recusadas")
        void nulas() {
            assertThatThrownBy(() -> SeasonPlanDiff.between(null, List.of())).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("changedWeeks")
    class ChangedWeeks {

        @Test
        @DisplayName("conta semanas distintas com ao menos um campo alterado")
        void conta() {
            assertThat(SeasonPlanDiff.changedWeeks(List.of(
                    new SeasonPlanDiffDto(3, "phase", null, null, WeekValueSource.ENGINE),
                    new SeasonPlanDiffDto(3, "targetTss", null, null, WeekValueSource.ENGINE),
                    new SeasonPlanDiffDto(5, "deload", null, null, WeekValueSource.COACH)))).isEqualTo(2);
            assertThat(SeasonPlanDiff.changedWeeks(List.of())).isZero();
        }
    }

    private static SeasonWeek semana(int indice) {
        SeasonWeek w = new SeasonWeek();
        w.setWeekIndex(indice);
        w.setPhase(TrainingPhase.BASE);
        w.setTargetVolumeKm(new BigDecimal("40"));
        w.setTargetTss(300.0);
        w.setValueSource(WeekValueSource.ENGINE);
        return w;
    }
}
