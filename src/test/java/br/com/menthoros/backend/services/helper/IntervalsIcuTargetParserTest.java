package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.domain.workout.HrTarget;
import br.com.menthoros.backend.domain.workout.PaceTarget;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class IntervalsIcuTargetParserTest {

    @Nested
    @DisplayName("parsePace")
    class ParsePace {

        @ParameterizedTest
        @CsvSource({
                "5:30-5:45/km, 330, 345",   // canônico do planner (schema LLM)
                "4:30-4:45/km, 270, 285",   // fixture do gate CA0 (eco da API: start=270 end=285)
                "5:30/km, 330, 330",        // valor único com sufixo
                "5:30, 330, 330",           // valor único tolerante (editado à mão)
                "10:05-10:30/km, 605, 630"  // dois dígitos de minuto
        })
        @DisplayName("formatos canônicos e tolerantes viram secs/km")
        void formatosValidos(String entrada, int inicio, int fim) {
            assertThat(IntervalsIcuTargetParser.parsePace(entrada))
                    .contains(new PaceTarget(inicio, fim));
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"  ", "ritmo confortável", "5,30/km", "Z2", "abc-def/km", "5:99/km", "5:60-5:70/km"})
        @DisplayName("não parseável retorna vazio sem lançar")
        void naoParseavelRetornaVazio(String entrada) {
            assertThat(IntervalsIcuTargetParser.parsePace(entrada)).isEmpty();
        }
    }

    @Nested
    @DisplayName("parseFc")
    class ParseFc {

        @ParameterizedTest
        @CsvSource({
                "140-150 bpm, BPM, 140, 150",    // canônico do planner
                "140-150bpm, BPM, 140, 150",     // tolerante sem espaço
                "70-80% FCmáx, PERCENT, 70, 80", // tolerante percentual
                "70-80%, PERCENT, 70, 80"
        })
        @DisplayName("bpm absoluto e percentual")
        void formatosValidos(String entrada, HrTarget.Unidade unidade, int inicio, int fim) {
            assertThat(IntervalsIcuTargetParser.parseFc(entrada))
                    .contains(new HrTarget(unidade, inicio, fim));
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"forte", "150", "bpm"})
        @DisplayName("não parseável retorna vazio (número solto NÃO vira alvo — ambíguo)")
        void naoParseavelRetornaVazio(String entrada) {
            assertThat(IntervalsIcuTargetParser.parseFc(entrada)).isEmpty();
        }
    }

    @Nested
    @DisplayName("parseZona")
    class ParseZona {

        @Test
        @DisplayName("Z2 vira zona 2; faixa z2-z3 vira zona inferior (conservador)")
        void zonas() {
            assertThat(IntervalsIcuTargetParser.parseZona("Z2"))
                    .contains(new HrTarget(HrTarget.Unidade.ZONE, 2, 2));
            assertThat(IntervalsIcuTargetParser.parseZona("z2-z3"))
                    .contains(new HrTarget(HrTarget.Unidade.ZONE, 2, 2));
        }
    }
}
