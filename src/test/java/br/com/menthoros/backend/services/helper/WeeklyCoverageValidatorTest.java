package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.dto.llm.RestDayLlmDto;
import br.com.menthoros.backend.dto.llm.TreinoPlanejadoLlmDto;
import br.com.menthoros.backend.enums.DiaSemana;
import br.com.menthoros.backend.enums.FatigueSignalType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link WeeklyCoverageValidator} (add-descanso-explicito-por-fadiga, Decisão 2): todo dia
 * disponível recebe treino ou descanso, e descanso só existe com sinal que o libere.
 *
 * <p>Caso que originou a change: Leandro, 4 dias (SEG/TER/QUI/SAB), 5 de 7 gerações de 22/09
 * vieram sem a quinta.</p>
 */
@DisplayName("WeeklyCoverageValidator — cobertura da semana")
class WeeklyCoverageValidatorTest {

    private static final List<DiaSemana> DIAS_LEANDRO =
            List.of(DiaSemana.SEGUNDA, DiaSemana.TERCA, DiaSemana.QUINTA, DiaSemana.SABADO);

    private WeeklyCoverageValidator validator;

    @BeforeEach
    void setUp() {
        validator = new WeeklyCoverageValidator();
    }

    @Nested
    @DisplayName("cobertura dos dias")
    class Cobertura {

        @Test
        @DisplayName("CA1: dia disponível sem treino nem descanso → COBERTURA_DIAS nomeando o dia")
        void diaOmitido() {
            var violacoes = validator.validar(
                    treinos(DiaSemana.SEGUNDA, DiaSemana.TERCA, DiaSemana.SABADO), List.of(), ctxSemSinal());

            assertThat(violacoes).singleElement().satisfies(v -> {
                assertThat(v.key()).isEqualTo("COBERTURA_DIAS");
                assertThat(v.mensagem()).contains("QUINTA");
            });
        }

        @Test
        @DisplayName("todos os dias com treino → sem violação")
        void coberturaCompleta() {
            var violacoes = validator.validar(treinos(DIAS_LEANDRO.toArray(DiaSemana[]::new)), List.of(), ctxSemSinal());

            assertThat(violacoes).isEmpty();
        }

        @Test
        @DisplayName("CA5: dois treinos no mesmo dia → COBERTURA_DIAS (dia duplicado)")
        void diaDuplicadoEntreTreinos() {
            var violacoes = validator.validar(
                    treinos(DiaSemana.SEGUNDA, DiaSemana.SEGUNDA, DiaSemana.TERCA, DiaSemana.QUINTA, DiaSemana.SABADO),
                    List.of(), ctxSemSinal());

            assertThat(violacoes).anySatisfy(v -> {
                assertThat(v.key()).isEqualTo("COBERTURA_DIAS");
                assertThat(v.mensagem()).contains("SEGUNDA").contains("mais de uma vez");
            });
        }

        @Test
        @DisplayName("CA5: mesmo dia com treino e descanso → COBERTURA_DIAS (dia duplicado)")
        void diaEmTreinoEDescanso() {
            var violacoes = validator.validar(treinos(DIAS_LEANDRO.toArray(DiaSemana[]::new)),
                    List.of(descanso(DiaSemana.SEGUNDA)), ctxComCheckin());

            assertThat(violacoes).anySatisfy(v -> assertThat(v.mensagem()).contains("mais de uma vez"));
        }

        @Test
        @DisplayName("CA5: treino em dia fora dos disponíveis → COBERTURA_DIAS (dia não disponível)")
        void diaForaDosEfetivos() {
            var violacoes = validator.validar(
                    treinos(DiaSemana.SEGUNDA, DiaSemana.TERCA, DiaSemana.QUARTA, DiaSemana.QUINTA, DiaSemana.SABADO),
                    List.of(), ctxSemSinal());

            assertThat(violacoes).anySatisfy(v -> {
                assertThat(v.key()).isEqualTo("COBERTURA_DIAS");
                assertThat(v.mensagem()).contains("QUARTA").contains("não está disponível");
            });
        }

        @Test
        @DisplayName("descanso em dia que o atleta nunca treina é descartado, não vira violação")
        void descansoForaDosEfetivosEhIgnorado() {
            // caso real 22/09 17:08: a LLM leu restDays como "os dias de folga da semana" e declarou
            // quarta, sexta e domingo — derrubar o plano por isso custa duas chamadas e um erro ao coach
            var violacoes = validator.validar(treinos(DIAS_LEANDRO.toArray(DiaSemana[]::new)),
                    List.of(descanso(DiaSemana.DOMINGO), descanso(DiaSemana.QUARTA), descanso(DiaSemana.SEXTA)),
                    ctxSemSinal());

            assertThat(violacoes).isEmpty();
        }

        @Test
        @DisplayName("descanso fora dos dias não conta para o teto de 1 por semana")
        void descansoForaNaoContaNoTeto() {
            var violacoes = validator.validar(treinos(DiaSemana.TERCA, DiaSemana.QUINTA, DiaSemana.SABADO),
                    List.of(descanso(DiaSemana.SEGUNDA), descanso(DiaSemana.QUARTA), descanso(DiaSemana.DOMINGO)),
                    ctxComCheckin());

            assertThat(violacoes).isEmpty();
        }

        @Test
        @DisplayName("os dias descartados são reportados para quem persiste")
        void reportaDescansosDescartados() {
            var descartados = validator.descansosForaDosDiasDisponiveis(
                    List.of(descanso(DiaSemana.DOMINGO), descanso(DiaSemana.SEGUNDA)), ctxComCheckin());

            assertThat(descartados).containsExactly(DiaSemana.DOMINGO);
        }

        @Test
        @DisplayName("treino em dia que o atleta não treina continua sendo violação")
        void treinoForaDosEfetivosReprova() {
            var violacoes = validator.validar(
                    treinos(DiaSemana.SEGUNDA, DiaSemana.TERCA, DiaSemana.QUARTA, DiaSemana.QUINTA, DiaSemana.SABADO),
                    List.of(), ctxSemSinal());

            assertThat(violacoes).anySatisfy(v -> assertThat(v.mensagem()).contains("QUARTA"));
        }

        @Test
        @DisplayName("dia com texto inválido no plano → COBERTURA_DIAS, sem quebrar")
        void diaInvalido() {
            var treinos = List.of(treino("SEGUNDA-FEIRA"), treino("TERCA"), treino("QUINTA"), treino("SABADO"));

            var violacoes = validator.validar(treinos, List.of(), ctxSemSinal());

            assertThat(violacoes).anySatisfy(v -> assertThat(v.mensagem()).contains("SEGUNDA-FEIRA"));
        }

        @Test
        @DisplayName("CA6: SEMANA_ATUAL com dias já passados cobre só os dias restantes")
        void semanaAtualSoDiasRestantes() {
            var ctx = contexto(List.of(DiaSemana.QUINTA, DiaSemana.SABADO), List.of(), true);

            var violacoes = validator.validar(treinos(DiaSemana.QUINTA, DiaSemana.SABADO), List.of(), ctx);

            assertThat(violacoes).isEmpty();
        }
    }

    @Nested
    @DisplayName("descanso exige sinal que o libere")
    class SinalDeDescanso {

        @Test
        @DisplayName("CA2: SEMANA_ATUAL, check-in DESCANSAR hoje, descanso no primeiro dia efetivo → passa")
        void checkinLiberaPrimeiroDia() {
            var violacoes = validator.validar(treinos(DiaSemana.TERCA, DiaSemana.QUINTA, DiaSemana.SABADO),
                    List.of(descanso(DiaSemana.SEGUNDA)), ctxComCheckin());

            assertThat(violacoes).isEmpty();
        }

        @Test
        @DisplayName("CA2b: caso do Leandro — TSB baixo não libera descanso; a mensagem pede treino leve")
        void tsbNaoLiberaDescanso() {
            var ctx = contexto(DIAS_LEANDRO, List.of(FatigueSignal.de(FatigueSignalType.TSB_BAIXO, -18.0, -15.0)), true);

            var violacoes = validator.validar(treinos(DiaSemana.SEGUNDA, DiaSemana.TERCA, DiaSemana.SABADO),
                    List.of(descanso(DiaSemana.QUINTA)), ctx);

            assertThat(violacoes).singleElement().satisfies(v -> {
                assertThat(v.key()).isEqualTo("DESCANSO_SEM_SINAL");
                assertThat(v.mensagem()).contains("QUINTA").containsIgnoringCase("treino leve");
            });
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(value = FatigueSignalType.class, names = {"TSB_BAIXO", "RPE_ALTO", "CTL_BAIXO"})
        @DisplayName("CA3: sinal de semana (ou CTL) nunca libera descanso")
        void sinalDeSemanaNaoLibera(FatigueSignalType tipo) {
            var ctx = contexto(DIAS_LEANDRO, List.of(FatigueSignal.de(tipo, 1.0, 2.0)), true);

            var violacoes = validator.validar(treinos(DiaSemana.TERCA, DiaSemana.QUINTA, DiaSemana.SABADO),
                    List.of(descanso(DiaSemana.SEGUNDA)), ctx);

            assertThat(violacoes).anySatisfy(v -> assertThat(v.key()).isEqualTo("DESCANSO_SEM_SINAL"));
        }

        @Test
        @DisplayName("CA3: sem nenhum sinal, qualquer descanso reprova")
        void semSinalNenhum() {
            var violacoes = validator.validar(treinos(DiaSemana.TERCA, DiaSemana.QUINTA, DiaSemana.SABADO),
                    List.of(descanso(DiaSemana.SEGUNDA)), ctxSemSinal());

            assertThat(violacoes).anySatisfy(v -> assertThat(v.key()).isEqualTo("DESCANSO_SEM_SINAL"));
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(value = FatigueSignalType.class,
                names = {"READINESS_DESCANSAR", "RECUPERACAO_INSUFICIENTE", "DIAS_CONSECUTIVOS_LIMITE"})
        @DisplayName("sinal do dia libera o primeiro dia efetivo e só ele")
        void sinalDoDiaLiberaPrimeiroDia(FatigueSignalType tipo) {
            var ctx = contexto(DIAS_LEANDRO, List.of(FatigueSignal.de(tipo, 1.0, 2.0)), true);

            var noPrimeiro = validator.validar(treinos(DiaSemana.TERCA, DiaSemana.QUINTA, DiaSemana.SABADO),
                    List.of(descanso(DiaSemana.SEGUNDA)), ctx);
            var noOutroDia = validator.validar(treinos(DiaSemana.SEGUNDA, DiaSemana.TERCA, DiaSemana.SABADO),
                    List.of(descanso(DiaSemana.QUINTA)), ctx);

            assertThat(noPrimeiro).isEmpty();
            assertThat(noOutroDia).anySatisfy(v -> assertThat(v.key()).isEqualTo("DESCANSO_SEM_SINAL"));
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(value = FatigueSignalType.class,
                names = {"READINESS_DESCANSAR", "RECUPERACAO_INSUFICIENTE", "DIAS_CONSECUTIVOS_LIMITE"})
        @DisplayName("CA4b: em PROXIMA_SEMANA nenhum sinal do dia libera descanso")
        void sinalDoDiaNaoValeNaProximaSemana(FatigueSignalType tipo) {
            var ctx = contexto(DIAS_LEANDRO, List.of(FatigueSignal.de(tipo, 1.0, 2.0)), false);

            var violacoes = validator.validar(treinos(DiaSemana.TERCA, DiaSemana.QUINTA, DiaSemana.SABADO),
                    List.of(descanso(DiaSemana.SEGUNDA)), ctx);

            assertThat(violacoes).anySatisfy(v -> assertThat(v.key()).isEqualTo("DESCANSO_SEM_SINAL"));
        }

        @Test
        @DisplayName("CA4c: em PROXIMA_SEMANA só a sequência acima do máximo libera, e só dentro dela")
        void sequenciaLiberaNaProximaSemana() {
            // SEG a SAB = 6 dias seguidos, máximo 5 → a sequência inteira é elegível
            var seisDias = List.of(DiaSemana.SEGUNDA, DiaSemana.TERCA, DiaSemana.QUARTA,
                    DiaSemana.QUINTA, DiaSemana.SEXTA, DiaSemana.SABADO);
            var ctx = new WeeklyCoverageContext(seisDias, List.of(), false, null, 5);

            var violacoes = validator.validar(
                    treinos(DiaSemana.SEGUNDA, DiaSemana.TERCA, DiaSemana.QUINTA, DiaSemana.SEXTA, DiaSemana.SABADO),
                    List.of(descanso(DiaSemana.QUARTA)), ctx);

            assertThat(violacoes).isEmpty();
        }

        @Test
        @DisplayName("CA12e: sem sequência acima do máximo, o sinal não existe")
        void semSequenciaLonga() {
            var ctx = contexto(DIAS_LEANDRO, List.of(), false);

            var violacoes = validator.validar(treinos(DiaSemana.TERCA, DiaSemana.QUINTA, DiaSemana.SABADO),
                    List.of(descanso(DiaSemana.SEGUNDA)), ctx);

            assertThat(violacoes).anySatisfy(v -> assertThat(v.key()).isEqualTo("DESCANSO_SEM_SINAL"));
        }

        @Test
        @DisplayName("descanso fora da sequência longa não é liberado por ela")
        void descansoForaDaSequencia() {
            // SEG-QUA seguidos (3, máximo 2) e SABADO isolado
            var dias = List.of(DiaSemana.SEGUNDA, DiaSemana.TERCA, DiaSemana.QUARTA, DiaSemana.SABADO);
            var ctx = new WeeklyCoverageContext(dias, List.of(), false, null, 2);

            var violacoes = validator.validar(
                    treinos(DiaSemana.SEGUNDA, DiaSemana.TERCA, DiaSemana.QUARTA), List.of(descanso(DiaSemana.SABADO)), ctx);

            assertThat(violacoes).anySatisfy(v -> assertThat(v.key()).isEqualTo("DESCANSO_SEM_SINAL"));
        }
    }

    @Nested
    @DisplayName("teto de descansos por número de dias")
    class Teto {

        @ParameterizedTest(name = "{0} dias efetivos → descanso com sinal do dia permitido: {1}")
        @CsvSource({"7, true", "5, true", "4, true", "3, false", "2, false", "1, false"})
        @DisplayName("CA4: com 1-3 dias efetivos só o check-in DESCANSAR libera")
        void tetoPorNumeroDeDias(int qtdDias, boolean recuperacaoLibera) {
            var dias = diasSeguidos(qtdDias);
            var ctxRecuperacao = new WeeklyCoverageContext(dias,
                    List.of(FatigueSignal.de(FatigueSignalType.RECUPERACAO_INSUFICIENTE, 36.0, 60.0)), true, null, 7);
            var primeiro = dias.getFirst();

            var violacoes = validator.validar(treinosExceto(dias, primeiro), List.of(descanso(primeiro)), ctxRecuperacao);

            assertThat(violacoes.isEmpty()).isEqualTo(recuperacaoLibera);
        }

        @ParameterizedTest(name = "{0} dias efetivos")
        @ValueSource(ints = {1, 2, 3})
        @DisplayName("CA4: com 1-3 dias, o check-in DESCANSAR libera assim mesmo — o atleta disse que não dá")
        void checkinLiberaMesmoComPoucosDias(int qtdDias) {
            var dias = diasSeguidos(qtdDias);
            var ctx = new WeeklyCoverageContext(dias,
                    List.of(FatigueSignal.de(FatigueSignalType.READINESS_DESCANSAR)), true, null, 7);
            var primeiro = dias.getFirst();

            var violacoes = validator.validar(treinosExceto(dias, primeiro), List.of(descanso(primeiro)), ctx);

            assertThat(violacoes).isEmpty();
        }

        @Test
        @DisplayName("CA4: 2 descansos reprovam mesmo com sinal")
        void doisDescansosReprovam() {
            var ctx = contexto(DIAS_LEANDRO,
                    List.of(FatigueSignal.de(FatigueSignalType.READINESS_DESCANSAR)), true);

            var violacoes = validator.validar(treinos(DiaSemana.QUINTA, DiaSemana.SABADO),
                    List.of(descanso(DiaSemana.SEGUNDA), descanso(DiaSemana.TERCA)), ctx);

            assertThat(violacoes).anySatisfy(v -> assertThat(v.key()).isEqualTo("DESCANSO_ACIMA_DO_LIMITE"));
        }
    }

    @Nested
    @DisplayName("motivo e intensos adjacentes")
    class MotivoEIntensos {

        @ParameterizedTest(name = "motivo=\"{0}\"")
        @CsvSource(value = {"NULL", "''", "'   '"}, nullValues = "NULL")
        @DisplayName("CA7: motivo vazio, branco ou ausente → DESCANSO_SEM_MOTIVO")
        void motivoVazio(String motivo) {
            var violacoes = validator.validar(treinos(DiaSemana.TERCA, DiaSemana.QUINTA, DiaSemana.SABADO),
                    List.of(new RestDayLlmDto("SEGUNDA", motivo)), ctxComCheckin());

            assertThat(violacoes).anySatisfy(v -> assertThat(v.key()).isEqualTo("DESCANSO_SEM_MOTIVO"));
        }

        @Test
        @DisplayName("CA7: motivo com 201 caracteres reprova; com 200 passa")
        void motivoLongo() {
            var ctx = ctxComCheckin();
            var treinos = treinos(DiaSemana.TERCA, DiaSemana.QUINTA, DiaSemana.SABADO);

            var com201 = validator.validar(treinos, List.of(new RestDayLlmDto("SEGUNDA", "x".repeat(201))), ctx);
            var com200 = validator.validar(treinos, List.of(new RestDayLlmDto("SEGUNDA", "x".repeat(200))), ctx);

            assertThat(com201).anySatisfy(v -> assertThat(v.key()).isEqualTo("DESCANSO_SEM_MOTIVO"));
            assertThat(com200).isEmpty();
        }

        @Test
        @DisplayName("CA12d: dois tipos de alta intensidade em dias vizinhos → INTENSOS_ADJACENTES")
        void intensosAdjacentes() {
            var treinos = List.of(
                    treino("SEGUNDA", "INTERVALADO"), treino("TERCA", "LONGO"),
                    treino("QUINTA", "CONTINUO"), treino("SABADO", "REGENERATIVO"));

            var violacoes = validator.validar(treinos, List.of(), ctxSemSinal());

            assertThat(violacoes).singleElement().satisfies(v -> {
                assertThat(v.key()).isEqualTo("INTENSOS_ADJACENTES");
                assertThat(v.mensagem()).contains("SEGUNDA").contains("TERCA");
            });
        }

        @Test
        @DisplayName("intensos separados por um dia sem treino não são adjacentes")
        void intensosNaoAdjacentes() {
            var treinos = List.of(
                    treino("SEGUNDA", "INTERVALADO"), treino("TERCA", "REGENERATIVO"),
                    treino("QUINTA", "LONGO"), treino("SABADO", "CONTINUO"));

            var violacoes = validator.validar(treinos, List.of(), ctxSemSinal());

            assertThat(violacoes).isEmpty();
        }

        @Test
        @DisplayName("descanso num dia não separa intensos vizinhos entre si")
        void descansoNaoSeparaIntensosVizinhos() {
            var dias = List.of(DiaSemana.SEGUNDA, DiaSemana.TERCA, DiaSemana.QUARTA);
            var ctx = new WeeklyCoverageContext(dias,
                    List.of(FatigueSignal.de(FatigueSignalType.READINESS_DESCANSAR)), true, null, 7);

            var violacoes = validator.validar(
                    List.of(treino("TERCA", "INTERVALADO"), treino("QUARTA", "LONGO")),
                    List.of(descanso(DiaSemana.SEGUNDA)), ctx);

            assertThat(violacoes).anySatisfy(v -> assertThat(v.key()).isEqualTo("INTENSOS_ADJACENTES"));
        }
    }

    @Nested
    @DisplayName("entradas degeneradas")
    class Degeneradas {

        @Test
        @DisplayName("sem dias efetivos: nada a cobrir, nenhuma violação")
        void semDiasEfetivos() {
            var ctx = new WeeklyCoverageContext(List.of(), List.of(), true, null, 7);

            assertThat(validator.validar(List.of(), List.of(), ctx)).isEmpty();
        }

        @Test
        @DisplayName("listas nulas de treinos e descansos são tratadas como vazias")
        void listasNulas() {
            var violacoes = validator.validar(null, null, ctxSemSinal());

            assertThat(violacoes).singleElement().satisfies(v -> assertThat(v.key()).isEqualTo("COBERTURA_DIAS"));
        }

        @Test
        @DisplayName("violações se acumulam: dia omitido + descanso sem sinal vêm juntos")
        void violacoesAcumulam() {
            var violacoes = validator.validar(treinos(DiaSemana.TERCA, DiaSemana.QUINTA),
                    List.of(descanso(DiaSemana.SEGUNDA)), ctxSemSinal());

            assertThat(violacoes).extracting(v -> v.key())
                    .contains("COBERTURA_DIAS", "DESCANSO_SEM_SINAL");
        }
    }

    // ---------- fixtures ----------

    private static WeeklyCoverageContext ctxSemSinal() {
        return contexto(DIAS_LEANDRO, List.of(), true);
    }

    private static WeeklyCoverageContext ctxComCheckin() {
        return contexto(DIAS_LEANDRO, List.of(FatigueSignal.de(FatigueSignalType.READINESS_DESCANSAR)), true);
    }

    private static WeeklyCoverageContext contexto(List<DiaSemana> dias, List<FatigueSignal> sinais, boolean semanaAtual) {
        return new WeeklyCoverageContext(dias, sinais, semanaAtual, null, 7);
    }

    /** SEGUNDA, TERCA, ... — dias seguidos a partir de segunda. */
    private static List<DiaSemana> diasSeguidos(int quantidade) {
        return Arrays.stream(new DiaSemana[]{DiaSemana.SEGUNDA, DiaSemana.TERCA, DiaSemana.QUARTA,
                        DiaSemana.QUINTA, DiaSemana.SEXTA, DiaSemana.SABADO, DiaSemana.DOMINGO})
                .limit(quantidade).toList();
    }

    private static List<TreinoPlanejadoLlmDto> treinos(DiaSemana... dias) {
        return Arrays.stream(dias).map(d -> treino(d.name())).toList();
    }

    private static List<TreinoPlanejadoLlmDto> treinosExceto(List<DiaSemana> dias, DiaSemana excluido) {
        return dias.stream().filter(d -> d != excluido).map(d -> treino(d.name())).toList();
    }

    private static TreinoPlanejadoLlmDto treino(String diaSemana) {
        return treino(diaSemana, "CONTINUO");
    }

    private static TreinoPlanejadoLlmDto treino(String diaSemana, String tipo) {
        return new TreinoPlanejadoLlmDto(diaSemana, tipo, null, null, null, null, null, null, null, null, List.of());
    }

    private static RestDayLlmDto descanso(DiaSemana dia) {
        return new RestDayLlmDto(dia.name(), "check-in de hoje: DESCANSAR");
    }
}
