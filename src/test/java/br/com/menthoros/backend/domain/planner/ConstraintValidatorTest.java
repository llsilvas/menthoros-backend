package br.com.menthoros.backend.domain.planner;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ConstraintValidatorTest {

    private final ConstraintValidator validator = new ConstraintValidator();

    @Nested
    @DisplayName("validate — violacao de dias disponiveis")
    class ViolacaoDeDias {

        @Test
        @DisplayName("sessao em dia fora dos diasDisponiveis do atleta gera violacao")
        void sessaoEmDiaIndisponivelGeraViolacao() {
            AthleteConstraints constraints = new AthleteConstraints(
                    List.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY),
                    5, 90, List.of());
            List<SessionSlot> sessoes = List.of(
                    sessao(DayOfWeek.MONDAY),
                    sessao(DayOfWeek.SUNDAY) // fora dos dias disponiveis
            );

            ConstraintValidationResult resultado = validator.validate(constraints, sessoes);

            assertThat(resultado.valid()).isFalse();
            assertThat(resultado.violations()).anyMatch(v -> v.key() == ConstraintViolationKey.DIA_INDISPONIVEL);
        }

        @Test
        @DisplayName("todas as sessoes em dias disponiveis nao gera violacao de dia")
        void todasAsSessoesEmDiasDisponiveisOk() {
            AthleteConstraints constraints = new AthleteConstraints(
                    List.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY), 5, 90, List.of());
            List<SessionSlot> sessoes = List.of(sessao(DayOfWeek.MONDAY), sessao(DayOfWeek.WEDNESDAY));

            ConstraintValidationResult resultado = validator.validate(constraints, sessoes);

            assertThat(resultado.violations()).noneMatch(v -> v.key() == ConstraintViolationKey.DIA_INDISPONIVEL);
        }
    }

    @Nested
    @DisplayName("validate — max sessoes por semana")
    class MaxSessoes {

        @Test
        @DisplayName("numero de sessoes acima do maximo permitido gera violacao")
        void excedeMaxSessoesGeraViolacao() {
            AthleteConstraints constraints = new AthleteConstraints(
                    List.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY),
                    2, 90, List.of());
            List<SessionSlot> sessoes = List.of(
                    sessao(DayOfWeek.MONDAY), sessao(DayOfWeek.TUESDAY), sessao(DayOfWeek.WEDNESDAY));

            ConstraintValidationResult resultado = validator.validate(constraints, sessoes);

            assertThat(resultado.valid()).isFalse();
            assertThat(resultado.violations()).anyMatch(v -> v.key() == ConstraintViolationKey.MAX_SESSOES_EXCEDIDO);
        }
    }

    @Nested
    @DisplayName("validate — duracao maxima por sessao")
    class Duracao {

        @Test
        @DisplayName("sessao acima da duracao maxima do atleta gera violacao")
        void sessaoAcimaDaDuracaoMaximaGeraViolacao() {
            AthleteConstraints constraints = new AthleteConstraints(
                    List.of(DayOfWeek.SATURDAY), 5, 60, List.of());
            List<SessionSlot> sessoes = List.of(
                    new SessionSlot(DayOfWeek.SATURDAY, "LONGO", 150.0, "Z2", true, 120));

            ConstraintValidationResult resultado = validator.validate(constraints, sessoes);

            assertThat(resultado.valid()).isFalse();
            assertThat(resultado.violations()).anyMatch(v -> v.key() == ConstraintViolationKey.DURACAO_MAXIMA_EXCEDIDA);
        }
    }

    @Nested
    @DisplayName("validate — equipamento indisponivel")
    class Equipamento {

        @Test
        @DisplayName("sessao de tipo indisponivel para o atleta gera violacao")
        void sessaoComEquipamentoIndisponivelGeraViolacao() {
            AthleteConstraints constraints = new AthleteConstraints(
                    List.of(DayOfWeek.THURSDAY), 5, 90, List.of("NATACAO"));
            List<SessionSlot> sessoes = List.of(
                    new SessionSlot(DayOfWeek.THURSDAY, "NATACAO", 60.0, "Z2", false, 45));

            ConstraintValidationResult resultado = validator.validate(constraints, sessoes);

            assertThat(resultado.valid()).isFalse();
            assertThat(resultado.violations()).anyMatch(v -> v.key() == ConstraintViolationKey.EQUIPAMENTO_INDISPONIVEL);
        }
    }

    @Nested
    @DisplayName("validate — sem violacoes")
    class SemViolacoes {

        @Test
        @DisplayName("sessoes dentro de todas as constraints resultam em valid=true e violations vazia")
        void tudoDentroDosLimitesResultaValido() {
            AthleteConstraints constraints = new AthleteConstraints(
                    List.of(DayOfWeek.MONDAY, DayOfWeek.THURSDAY), 3, 90, List.of("NATACAO"));
            List<SessionSlot> sessoes = List.of(
                    new SessionSlot(DayOfWeek.MONDAY, "INTERVALADO", 70.0, "Z4", false, 60),
                    new SessionSlot(DayOfWeek.THURSDAY, "LONGO", 100.0, "Z2", true, 80));

            ConstraintValidationResult resultado = validator.validate(constraints, sessoes);

            assertThat(resultado.valid()).isTrue();
            assertThat(resultado.violations()).isEmpty();
        }
    }

    private SessionSlot sessao(DayOfWeek dia) {
        return new SessionSlot(dia, "REGENERATIVO", 40.0, "Z1", false, 45);
    }
}
