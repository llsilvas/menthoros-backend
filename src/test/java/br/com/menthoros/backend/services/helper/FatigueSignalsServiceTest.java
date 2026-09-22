package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.config.core.ReadinessProperties;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.PlanoMetaDados;
import br.com.menthoros.backend.entity.TreinoRealizado;
import br.com.menthoros.backend.enums.FatigueSignalType;
import br.com.menthoros.backend.enums.NivelExperiencia;
import br.com.menthoros.backend.enums.NivelProntidao;
import br.com.menthoros.backend.enums.TipoTreino;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link FatigueSignalsService} (add-descanso-explicito-por-fadiga, Decisão 3): os sinais são
 * avaliados <b>todos</b>, independentes da recomendação do intervalado — que retorna no primeiro
 * portão que dispara e por isso perderia sinais.
 *
 * <p>Quem libera descanso e quem não libera está em
 * {@code knowledge/coaching/frequencia-e-descanso-por-fadiga.md}: sinal de semana (TSB, RPE) leva a
 * treino leve; descanso é decisão do dia.</p>
 */
@DisplayName("FatigueSignalsService — sinais de fadiga")
class FatigueSignalsServiceTest {

    private static final LocalDate HOJE = LocalDate.of(2026, 9, 22);
    private static final int MAX_CONSECUTIVOS = 5;

    private FatigueSignalsService service;
    private ReadinessProperties readinessProperties;

    @BeforeEach
    void setUp() {
        readinessProperties = new ReadinessProperties();
        readinessProperties.setEnabled(true);
        service = new FatigueSignalsService(readinessProperties);
    }

    @Nested
    @DisplayName("cada sinal isolado")
    class SinalIsolado {

        @ParameterizedTest(name = "TSB {0} com limiar {1} → {2}")
        @CsvSource({
                "-16.0, INTERVALADO, true",   // Intermediário: limiar -15
                "-15.0, INTERVALADO, false",  // igual ao limiar não dispara
                "-14.9, INTERVALADO, false",
                "-40.0, INTERVALADO, true",   // fadiga extrema continua sendo TSB baixo
                "-11.0, INICIANTE, true",     // Iniciante: limiar -10
                "-9.0,  INICIANTE, false"
        })
        @DisplayName("TSB_BAIXO usa o limiar do nível (BVA no limiar)")
        void tsbBaixo(double tsb, String nivelTeste, boolean esperado) {
            NivelExperiencia nivel = "INICIANTE".equals(nivelTeste)
                    ? NivelExperiencia.INICIANTE : NivelExperiencia.INTERMEDIARIO;

            var sinais = service.avaliar(atleta(nivel), metaDados(tsb, null, false, 0), List.of(),
                    HOJE, null, MAX_CONSECUTIVOS);

            assertThat(contem(sinais, FatigueSignalType.TSB_BAIXO)).isEqualTo(esperado);
        }

        @Test
        @DisplayName("TSB_BAIXO carrega valor e limiar para o motivo citar número e régua")
        void tsbBaixoCarregaValorELimiar() {
            var sinais = service.avaliar(atleta(NivelExperiencia.INTERMEDIARIO),
                    metaDados(-18.0, null, false, 0), List.of(), HOJE, null, MAX_CONSECUTIVOS);

            var sinal = sinais.stream().filter(s -> s.type() == FatigueSignalType.TSB_BAIXO).findFirst().orElseThrow();
            assertThat(sinal.value()).isEqualTo(-18.0);
            assertThat(sinal.threshold()).isEqualTo(-15.0);
        }

        @ParameterizedTest(name = "RPE médio {0} → {1}")
        @CsvSource({"8, true", "7, false", "9, true"})
        @DisplayName("RPE_ALTO dispara com média de 7 dias ≥ 7,5")
        void rpeAlto(int rpe, boolean esperado) {
            var treinos = List.of(treino(HOJE.minusDays(2), TipoTreino.CONTINUO, rpe));

            var sinais = service.avaliar(atleta(NivelExperiencia.INTERMEDIARIO), metaDados(0.0, null, false, 0),
                    treinos, HOJE, null, MAX_CONSECUTIVOS);

            assertThat(contem(sinais, FatigueSignalType.RPE_ALTO)).isEqualTo(esperado);
        }

        @Test
        @DisplayName("RPE de treino fora da janela de 7 dias não conta")
        void rpeForaDaJanela() {
            var treinos = List.of(treino(HOJE.minusDays(10), TipoTreino.CONTINUO, 10));

            var sinais = service.avaliar(atleta(NivelExperiencia.INTERMEDIARIO), metaDados(0.0, null, false, 0),
                    treinos, HOJE, null, MAX_CONSECUTIVOS);

            assertThat(contem(sinais, FatigueSignalType.RPE_ALTO)).isFalse();
        }

        @ParameterizedTest(name = "último intensivo há {0} dias → {1}")
        @CsvSource({"1, true", "2, true", "3, false"})
        @DisplayName("RECUPERACAO_INSUFICIENTE compara horas desde o último intensivo com o mínimo do nível (60h)")
        void recuperacaoInsuficiente(int diasAtras, boolean esperado) {
            var treinos = List.of(treino(HOJE.minusDays(diasAtras), TipoTreino.INTERVALADO, 5));

            var sinais = service.avaliar(atleta(NivelExperiencia.INTERMEDIARIO), metaDados(0.0, null, false, 0),
                    treinos, HOJE, null, MAX_CONSECUTIVOS);

            assertThat(contem(sinais, FatigueSignalType.RECUPERACAO_INSUFICIENTE)).isEqualTo(esperado);
        }

        @Test
        @DisplayName("sem treino intensivo no histórico não há sinal de recuperação")
        void semIntensivo() {
            var treinos = List.of(treino(HOJE.minusDays(1), TipoTreino.CONTINUO, 5));

            var sinais = service.avaliar(atleta(NivelExperiencia.INTERMEDIARIO), metaDados(0.0, null, false, 0),
                    treinos, HOJE, null, MAX_CONSECUTIVOS);

            assertThat(contem(sinais, FatigueSignalType.RECUPERACAO_INSUFICIENTE)).isFalse();
        }

        @Test
        @DisplayName("DIAS_CONSECUTIVOS_LIMITE vem do alerta dos metadados, com o máximo como limiar")
        void diasConsecutivos() {
            var sinais = service.avaliar(atleta(NivelExperiencia.INTERMEDIARIO), metaDados(0.0, null, true, 6),
                    List.of(), HOJE, null, MAX_CONSECUTIVOS);

            var sinal = sinais.stream().filter(s -> s.type() == FatigueSignalType.DIAS_CONSECUTIVOS_LIMITE)
                    .findFirst().orElseThrow();
            assertThat(sinal.value()).isEqualTo(6.0);
            assertThat(sinal.threshold()).isEqualTo(5.0);
        }

        @Test
        @DisplayName("sem alerta de dias consecutivos não há sinal")
        void semAlertaDiasConsecutivos() {
            var sinais = service.avaliar(atleta(NivelExperiencia.INTERMEDIARIO), metaDados(0.0, null, false, 6),
                    List.of(), HOJE, null, MAX_CONSECUTIVOS);

            assertThat(contem(sinais, FatigueSignalType.DIAS_CONSECUTIVOS_LIMITE)).isFalse();
        }

        @ParameterizedTest(name = "prontidão {0}")
        @EnumSource(NivelProntidao.class)
        @DisplayName("READINESS_DESCANSAR só com check-in DESCANSAR")
        void readiness(NivelProntidao prontidao) {
            var sinais = service.avaliar(atleta(NivelExperiencia.INTERMEDIARIO), metaDados(0.0, null, false, 0),
                    List.of(), HOJE, prontidao, MAX_CONSECUTIVOS);

            assertThat(contem(sinais, FatigueSignalType.READINESS_DESCANSAR))
                    .isEqualTo(prontidao == NivelProntidao.DESCANSAR);
        }

        @Test
        @DisplayName("sem check-in não há sinal de prontidão")
        void semCheckin() {
            var sinais = service.avaliar(atleta(NivelExperiencia.INTERMEDIARIO), metaDados(0.0, null, false, 0),
                    List.of(), HOJE, null, MAX_CONSECUTIVOS);

            assertThat(contem(sinais, FatigueSignalType.READINESS_DESCANSAR)).isFalse();
        }

        @Test
        @DisplayName("com app.readiness.enabled=false o check-in é ignorado")
        void readinessDesligado() {
            readinessProperties.setEnabled(false);

            var sinais = service.avaliar(atleta(NivelExperiencia.INTERMEDIARIO), metaDados(0.0, null, false, 0),
                    List.of(), HOJE, NivelProntidao.DESCANSAR, MAX_CONSECUTIVOS);

            assertThat(contem(sinais, FatigueSignalType.READINESS_DESCANSAR)).isFalse();
        }

        @ParameterizedTest(name = "CTL {0} → {1}")
        @CsvSource({"10.0, true", "25.0, false", "30.0, false"})
        @DisplayName("CTL_BAIXO usa o mínimo do nível (Intermediário: 25)")
        void ctlBaixo(double ctl, boolean esperado) {
            var sinais = service.avaliar(atleta(NivelExperiencia.INTERMEDIARIO), metaDados(0.0, ctl, false, 0),
                    List.of(), HOJE, null, MAX_CONSECUTIVOS);

            assertThat(contem(sinais, FatigueSignalType.CTL_BAIXO)).isEqualTo(esperado);
        }
    }

    @Nested
    @DisplayName("avaliação completa — o que a recomendação do intervalado perderia")
    class AvaliacaoCompleta {

        @Test
        @DisplayName("TSB baixo E CTL baixo E recuperação insuficiente: os três vêm juntos")
        void sinaisCombinados() {
            var treinos = List.of(treino(HOJE.minusDays(1), TipoTreino.TIRO, 9));

            var sinais = service.avaliar(atleta(NivelExperiencia.INTERMEDIARIO), metaDados(-18.0, 4.5, true, 6),
                    treinos, HOJE, NivelProntidao.DESCANSAR, MAX_CONSECUTIVOS);

            assertThat(sinais).extracting(FatigueSignal::type).containsExactlyInAnyOrder(
                    FatigueSignalType.TSB_BAIXO,
                    FatigueSignalType.CTL_BAIXO,
                    FatigueSignalType.RECUPERACAO_INSUFICIENTE,
                    FatigueSignalType.RPE_ALTO,
                    FatigueSignalType.DIAS_CONSECUTIVOS_LIMITE,
                    FatigueSignalType.READINESS_DESCANSAR);
        }

        @Test
        @DisplayName("atleta sem fadiga: nenhum sinal")
        void semFadiga() {
            var treinos = List.of(treino(HOJE.minusDays(5), TipoTreino.INTERVALADO, 4));

            var sinais = service.avaliar(atleta(NivelExperiencia.INTERMEDIARIO), metaDados(5.0, 40.0, false, 2),
                    treinos, HOJE, NivelProntidao.PRONTO, MAX_CONSECUTIVOS);

            assertThat(sinais).isEmpty();
        }

        @Test
        @DisplayName("metadados nulos não quebram a avaliação")
        void metadadosNulos() {
            var sinais = service.avaliar(atleta(NivelExperiencia.INTERMEDIARIO), null, null, HOJE, null, MAX_CONSECUTIVOS);

            assertThat(sinais).isEmpty();
        }

        @Test
        @DisplayName("atleta nulo cai no nível Intermediário, sem quebrar")
        void atletaNulo() {
            var sinais = service.avaliar(null, metaDados(-18.0, null, false, 0), List.of(), HOJE, null, MAX_CONSECUTIVOS);

            assertThat(contem(sinais, FatigueSignalType.TSB_BAIXO)).isTrue();
        }
    }

    @Nested
    @DisplayName("quem libera descanso")
    class LiberaDescanso {

        @ParameterizedTest(name = "{0}")
        @EnumSource(FatigueSignalType.class)
        @DisplayName("só sinais do dia e o estrutural liberam descanso; sinais da semana levam a treino leve")
        void liberaDescansoPorTipo(FatigueSignalType tipo) {
            boolean esperado = switch (tipo) {
                case READINESS_DESCANSAR, RECUPERACAO_INSUFICIENTE, DIAS_CONSECUTIVOS_LIMITE,
                     SEQUENCIA_ACIMA_DO_MAXIMO -> true;
                case TSB_BAIXO, RPE_ALTO, CTL_BAIXO -> false;
            };

            assertThat(tipo.liberaDescanso()).isEqualTo(esperado);
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(FatigueSignalType.class)
        @DisplayName("escopo: semana, dia ou sequência")
        void escopoPorTipo(FatigueSignalType tipo) {
            var esperado = switch (tipo) {
                case TSB_BAIXO, RPE_ALTO, CTL_BAIXO -> FatigueSignalType.Escopo.SEMANA;
                case READINESS_DESCANSAR, RECUPERACAO_INSUFICIENTE, DIAS_CONSECUTIVOS_LIMITE -> FatigueSignalType.Escopo.DIA;
                case SEQUENCIA_ACIMA_DO_MAXIMO -> FatigueSignalType.Escopo.SEQUENCIA;
            };

            assertThat(tipo.escopo()).isEqualTo(esperado);
        }
    }

    // ---------- fixtures ----------

    private static boolean contem(List<FatigueSignal> sinais, FatigueSignalType tipo) {
        return sinais.stream().anyMatch(s -> s.type() == tipo);
    }

    private static Atleta atleta(NivelExperiencia nivel) {
        return Atleta.builder().nivelExperiencia(nivel).build();
    }

    private static PlanoMetaDados metaDados(Double tsb, Double ctl, boolean alertaDias, int diasConsecutivos) {
        PlanoMetaDados m = new PlanoMetaDados();
        m.setTsbProntidaoAtual(tsb);
        m.setCtlAtual(ctl);
        m.setAlertaDiasConsecutivos(alertaDias);
        m.setDiasConsecutivosTreino(diasConsecutivos);
        return m;
    }

    private static TreinoRealizado treino(LocalDate data, TipoTreino tipo, Integer rpe) {
        TreinoRealizado t = new TreinoRealizado();
        t.setDataTreino(data);
        t.setTipoTreino(tipo);
        t.setPercepcaoEsforco(rpe);
        return t;
    }
}
