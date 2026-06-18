package br.com.menthoros.backend.services.quality;

import br.com.menthoros.backend.dto.llm.PlanoSemanalLlmDto;
import br.com.menthoros.backend.dto.llm.TreinoPlanejadoLlmDto;
import br.com.menthoros.backend.enums.DiaSemana;
import br.com.menthoros.backend.enums.TipoTreino;
import br.com.menthoros.backend.services.prompt.constraint.Constraint;
import br.com.menthoros.backend.services.prompt.constraint.ConstraintKey;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PlanQualityChecker")
class PlanQualityCheckerTest {

    private MeterRegistry registry;
    private PlanQualityChecker checker;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        checker = new PlanQualityChecker(registry);
    }

    @Nested
    @DisplayName("plano bom")
    class PlanoBom {

        @Test
        @DisplayName("respeita todas as constraints → zero violações")
        void semViolacoes() {
            PlanoSemanalLlmDto plano = plano(
                    treino("SEGUNDA", "CONTINUO", "5:40/km"),
                    treino("QUARTA", "FACIL", "6:30/km"));
            List<Constraint> regras = List.of(
                    Constraint.intervaladoProibido("Sem intervalado."),
                    Constraint.paceTeto("Teto.", java.util.Map.of(TipoTreino.CONTINUO, new BigDecimal("5.5"))),
                    Constraint.diasPermitidos("Dias.", List.of(DiaSemana.SEGUNDA, DiaSemana.QUARTA)),
                    Constraint.maxConsecutivos("Máx 4.", 4));

            assertThat(checker.check(plano, regras)).isEmpty();
        }

        @Test
        @DisplayName("pace exatamente no teto não viola (teto não-inclusivo)")
        void paceNoLimite() {
            PlanoSemanalLlmDto plano = plano(treino("SEGUNDA", "CONTINUO", "5:30/km")); // 5.5 == teto
            var regras = List.of(Constraint.paceTeto("Teto.", java.util.Map.of(TipoTreino.CONTINUO, new BigDecimal("5.5"))));
            assertThat(checker.check(plano, regras)).isEmpty();
        }

        @Test
        @DisplayName("exatamente no máximo de consecutivos não viola")
        void maxNoLimite() {
            PlanoSemanalLlmDto plano = plano(
                    treino("SEGUNDA", "CONTINUO", "6:00/km"), treino("TERCA", "FACIL", "6:00/km"),
                    treino("QUARTA", "CONTINUO", "6:00/km"), treino("QUINTA", "FACIL", "6:00/km"));
            assertThat(checker.check(plano, List.of(Constraint.maxConsecutivos("Máx 4.", 4)))).isEmpty();
        }

        @Test
        @DisplayName("plano nulo → sem violações")
        void planoNulo() {
            assertThat(checker.check(null, List.of(Constraint.intervaladoProibido("x")))).isEmpty();
        }

        @Test
        @DisplayName("tipo de treino desconhecido é ignorado (sem falso positivo)")
        void tipoDesconhecido() {
            PlanoSemanalLlmDto plano = plano(treino("SEGUNDA", "XPTO", "4:00/km"));
            var regras = List.of(Constraint.paceTeto("Teto.", java.util.Map.of(TipoTreino.CONTINUO, new BigDecimal("5.5"))));
            assertThat(checker.check(plano, regras)).isEmpty();
        }
    }

    @Nested
    @DisplayName("plano alucinado")
    class PlanoAlucinado {

        @Test
        @DisplayName("INTERVALADO sob INTERVALADO_PROIBIDO → viola")
        void intervaladoProibido() {
            PlanoSemanalLlmDto plano = plano(treino("TERCA", "INTERVALADO", "4:00/km"));
            var v = checker.check(plano, List.of(Constraint.intervaladoProibido("Sem intervalado.")));
            assertThat(v).extracting(ViolacaoQualidade::key).containsExactly(ConstraintKey.INTERVALADO_PROIBIDO);
            assertThat(registry.find("violacoes_plano").tag("key", "INTERVALADO_PROIBIDO").counter().count()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("TIRO também viola INTERVALADO_PROIBIDO (conjunto intensivo)")
        void tiroProibido() {
            PlanoSemanalLlmDto plano = plano(treino("QUINTA", "TIRO", "4:00/km"));
            var v = checker.check(plano, List.of(Constraint.intervaladoProibido("Sem intensivo.")));
            assertThat(v).extracting(ViolacaoQualidade::key).containsExactly(ConstraintKey.INTERVALADO_PROIBIDO);
        }

        @Test
        @DisplayName("etapa mais rápida que o teto → viola PACE_TETO")
        void paceTeto() {
            PlanoSemanalLlmDto plano = plano(treino("SEGUNDA", "CONTINUO", "5:00/km"));
            var regras = List.of(Constraint.paceTeto("Teto.", java.util.Map.of(TipoTreino.CONTINUO, new BigDecimal("5.5"))));
            var v = checker.check(plano, regras);
            assertThat(v).extracting(ViolacaoQualidade::key).containsExactly(ConstraintKey.PACE_TETO);
        }

        @Test
        @DisplayName("treino fora dos dias permitidos → viola DIAS_PERMITIDOS")
        void diasPermitidos() {
            PlanoSemanalLlmDto plano = plano(treino("TERCA", "FACIL", "6:00/km"));
            var regras = List.of(Constraint.diasPermitidos("Dias.", List.of(DiaSemana.SEGUNDA, DiaSemana.QUARTA)));
            var v = checker.check(plano, regras);
            assertThat(v).extracting(ViolacaoQualidade::key).containsExactly(ConstraintKey.DIAS_PERMITIDOS);
        }

        @Test
        @DisplayName("5 dias seguidos sob máx 4 → viola MAX_CONSECUTIVOS")
        void maxConsecutivos() {
            PlanoSemanalLlmDto plano = plano(
                    treino("SEGUNDA", "CONTINUO", "6:00/km"),
                    treino("TERCA", "FACIL", "6:00/km"),
                    treino("QUARTA", "CONTINUO", "6:00/km"),
                    treino("QUINTA", "FACIL", "6:00/km"),
                    treino("SEXTA", "CONTINUO", "6:00/km"));
            var v = checker.check(plano, List.of(Constraint.maxConsecutivos("Máx 4.", 4)));
            assertThat(v).extracting(ViolacaoQualidade::key).containsExactly(ConstraintKey.MAX_CONSECUTIVOS);
        }

        @Test
        @DisplayName("REGENERATIVO quebra a sequência (não conta como dia consecutivo)")
        void regenerativoQuebraSequencia() {
            PlanoSemanalLlmDto plano = plano(
                    treino("SEGUNDA", "CONTINUO", "6:00/km"),
                    treino("TERCA", "CONTINUO", "6:00/km"),
                    treino("QUARTA", "REGENERATIVO", "7:00/km"),
                    treino("QUINTA", "CONTINUO", "6:00/km"),
                    treino("SEXTA", "CONTINUO", "6:00/km"));
            // maior sequência sem REGENERATIVO = 2 (seg-ter) ou 2 (qui-sex) ≤ 4 → sem violação
            assertThat(checker.check(plano, List.of(Constraint.maxConsecutivos("Máx 4.", 4)))).isEmpty();
        }
    }

    // ===== helpers =====

    private static PlanoSemanalLlmDto plano(TreinoPlanejadoLlmDto... treinos) {
        return new PlanoSemanalLlmDto(0.0, 0.0, null, null, null, null, List.of(treinos));
    }

    private static TreinoPlanejadoLlmDto treino(String dia, String tipo, String ritmo) {
        return new TreinoPlanejadoLlmDto(dia, tipo, null, null, null, null, null, null, null, ritmo, null);
    }
}
