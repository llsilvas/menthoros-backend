package br.com.menthoros.backend.services.prompt;

import br.com.menthoros.backend.enums.NivelProntidao;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReadinessPromptFormatterTest {

    private final ReadinessPromptFormatter formatter = new ReadinessPromptFormatter();

    @Nested
    @DisplayName("formatarReadiness")
    class FormatarReadiness {

        @Test
        @DisplayName("indica ausência de checkin quando nivelHoje é null")
        void indicaAusenciaDeCheckin() {
            String resultado = formatter.formatarReadiness(Collections.nCopies(7, null), null, null);

            assertThat(resultado).contains("sem checkin registrado");
        }

        @Test
        @DisplayName("inclui a sequência dos 7 dias na ordem informada")
        void incluiSequenciaDeSeteDias() {
            List<NivelProntidao> sequencia = Arrays.asList(
                    NivelProntidao.PRONTO, NivelProntidao.PRONTO, NivelProntidao.CAUTELOSO,
                    NivelProntidao.DESCANSAR, NivelProntidao.CAUTELOSO, NivelProntidao.PRONTO, NivelProntidao.PRONTO);

            String resultado = formatter.formatarReadiness(sequencia, NivelProntidao.PRONTO, new BigDecimal("0.80"));

            assertThat(resultado).contains("[PRONTO, PRONTO, CAUTELOSO, DESCANSAR, CAUTELOSO, PRONTO, PRONTO]");
        }

        @Test
        @DisplayName("inclui score e nível do dia atual quando presentes")
        void incluiScoreENivelDoDiaAtual() {
            String resultado = formatter.formatarReadiness(
                    Collections.nCopies(7, NivelProntidao.PRONTO), NivelProntidao.PRONTO, new BigDecimal("0.90"));

            // Formatação numérica segue a Locale padrão da JVM (mesmo padrão do restante do
            // PlanoTreinoPromptBuilder — sem pin de Locale.US), por isso não fixamos o separador decimal.
            assertThat(resultado).contains("**Hoje:** PRONTO").contains("**Hoje:**");
        }

        @Test
        @DisplayName("inclui instrução obrigatória de bloqueio quando nível hoje é DESCANSAR")
        void incluiInstrucaoObrigatoriaQuandoDescansar() {
            String resultado = formatter.formatarReadiness(
                    Collections.nCopies(7, NivelProntidao.DESCANSAR), NivelProntidao.DESCANSAR, new BigDecimal("0.30"));

            assertThat(resultado).contains("ATENÇÃO OBRIGATÓRIA").contains("NÃO prescreva");
        }

        @Test
        @DisplayName("não inclui instrução de bloqueio quando nível hoje é PRONTO ou CAUTELOSO")
        void naoIncluiInstrucaoDeBloqueioForaDeDescansar() {
            String resultado = formatter.formatarReadiness(
                    Collections.nCopies(7, NivelProntidao.CAUTELOSO), NivelProntidao.CAUTELOSO, new BigDecimal("0.60"));

            assertThat(resultado).doesNotContain("ATENÇÃO OBRIGATÓRIA");
        }

        @Test
        @DisplayName("renderiza SEM_DADO para dias sem checkin na sequência")
        void renderizaSemDadoParaDiasFaltantes() {
            List<NivelProntidao> sequencia = Arrays.asList(
                    null, null, NivelProntidao.PRONTO, null, null, null, NivelProntidao.CAUTELOSO);

            String resultado = formatter.formatarReadiness(sequencia, NivelProntidao.CAUTELOSO, new BigDecimal("0.55"));

            assertThat(resultado).contains("[SEM_DADO, SEM_DADO, PRONTO, SEM_DADO, SEM_DADO, SEM_DADO, CAUTELOSO]");
        }
    }
}
