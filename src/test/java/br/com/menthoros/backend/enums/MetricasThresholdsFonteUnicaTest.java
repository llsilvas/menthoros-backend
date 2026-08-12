package br.com.menthoros.backend.enums;

import br.com.menthoros.backend.skills.core.SkillContext;
import br.com.menthoros.backend.skills.core.SkillResult;
import br.com.menthoros.backend.skills.recovery.RecoveryCargaInput;
import br.com.menthoros.backend.skills.recovery.RecoveryCargaPayload;
import br.com.menthoros.backend.skills.recovery.RecoveryCargaSkill;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Amarra os limiares de fadiga a uma fonte única.
 *
 * <p><b>O problema que estes testes impedem de voltar.</b> O mesmo conceito vivia em três lugares:
 * {@link MetricasThresholds}, as constantes privadas de {@link RecoveryCargaSkill} e o
 * {@code TSB_PISO_RECOVERY} de {@link RevisaoSemanalCalculator}. Dois valores eram cópias literais
 * do enum (5.0 e -10.0) e dois não existiam nele (-25 e 7 dias). Mudar o enum não mexia na skill;
 * mudar a skill não mexia na revisão semanal — e nada falhava, porque cada um tinha seu próprio
 * número.
 *
 * <p>As duas asserções sobre o {@code RevisaoSemanalCalculator} vivem em
 * {@code RevisaoSemanalCalculatorTest}: os campos dele são package-private, e alargar a visibilidade
 * só para este teste seria deixar o teste ditar o desenho.
 *
 * <p><b>Por que testar a fronteira e não a constante.</b> As constantes da skill são privadas, e
 * torná-las visíveis só para o teste inverteria a dependência. Em vez disso, cada teste alimenta a
 * skill com um TSB derivado do valor canônico: se alguém reintroduzir um literal na skill, o valor
 * do enum deixa de comandar a fronteira e o teste quebra. É a invariante que interessa — "o enum é
 * quem manda" — e não a igualdade de dois campos.
 */
@DisplayName("Limiares de fadiga — fonte única em MetricasThresholds")
class MetricasThresholdsFonteUnicaTest {

    private final RecoveryCargaSkill skill = new RecoveryCargaSkill();
    private final SkillContext context = SkillContext.of(UUID.randomUUID(), UUID.randomUUID(), LocalDate.now());

    @Nested
    @DisplayName("RecoveryCargaSkill respeita o enum")
    class SkillRespeitaEnum {

        @Test
        @DisplayName("TSB logo abaixo de TSB_PISO_RECOVERY classifica SOBRECARREGADO")
        void pisoDeRecoveryComandaSobrecarregado() {
            var abaixo = classificar(MetricasThresholds.TSB_PISO_RECOVERY - 0.1, 0);
            assertThat(abaixo).isEqualTo("SOBRECARREGADO");
        }

        @Test
        @DisplayName("TSB exatamente em TSB_PISO_RECOVERY ainda é FATIGADO — a regra é estritamente menor")
        void pisoExatoNaoDisparaSobrecarga() {
            var noPiso = classificar(MetricasThresholds.TSB_PISO_RECOVERY, 0);
            assertThat(noPiso).isEqualTo("FATIGADO");
        }

        @Test
        @DisplayName("DIAS_CONSECUTIVOS_RECOVERY dispara SOBRECARREGADO mesmo com TSB saudável")
        void diasConsecutivosComandamSobrecarregado() {
            var noLimite = classificar(0.0, MetricasThresholds.DIAS_CONSECUTIVOS_RECOVERY);
            assertThat(noLimite).isEqualTo("SOBRECARREGADO");

            var umAbaixo = classificar(0.0, MetricasThresholds.DIAS_CONSECUTIVOS_RECOVERY - 1);
            assertThat(umAbaixo).isNotEqualTo("SOBRECARREGADO");
        }

        @Test
        @DisplayName("TSB_ACUMULANDO_FADIGA é a fronteira do FATIGADO")
        void acumulandoFadigaComandaFatigado() {
            var abaixo = classificar(MetricasThresholds.TSB_ACUMULANDO_FADIGA - 0.1, 0);
            assertThat(abaixo).isEqualTo("FATIGADO");
        }
    }

    @Nested
    @DisplayName("a escala canônica permanece ordenada")
    class EscalaOrdenada {

        @Test
        @DisplayName("piso de recovery fica entre fadiga moderada e sobrecarga")
        void pisoEntreModeradaESobrecarga() {
            // O -25 não é cópia errada de -20 nem de -30: é um nível próprio, e a ordem é o que
            // garante que ele continue significando "recomendar recuperação antes do alarme".
            assertThat(MetricasThresholds.TSB_PISO_RECOVERY)
                    .isLessThan(MetricasThresholds.TSB_FADIGA_MODERADA)
                    .isGreaterThan(MetricasThresholds.TSB_SOBRECARGA);
        }

        @Test
        @DisplayName("dias para recovery vêm depois do limite crítico de dias consecutivos")
        void diasRecoveryDepoisDoCritico() {
            assertThat(MetricasThresholds.DIAS_CONSECUTIVOS_RECOVERY)
                    .isGreaterThan(MetricasThresholds.DIAS_CONSECUTIVOS_CRITICO);
        }
    }

    private String classificar(double tsb, int diasConsecutivos) {
        RecoveryCargaInput input = new RecoveryCargaInput(60.0, 70.0, tsb, 0.0, diasConsecutivos, false);
        SkillResult<RecoveryCargaPayload> resultado = skill.execute(input, context);
        return resultado.payload().classificacao();
    }
}
