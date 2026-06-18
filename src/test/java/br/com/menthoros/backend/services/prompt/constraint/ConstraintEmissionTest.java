package br.com.menthoros.backend.services.prompt.constraint;

import br.com.menthoros.backend.enums.CategoriaIntervalado;
import br.com.menthoros.backend.enums.DiaSemana;
import br.com.menthoros.backend.enums.TipoTreino;
import br.com.menthoros.backend.services.helper.RecomendacaoIntervalado;
import br.com.menthoros.backend.services.prompt.DisponibilidadePromptFormatter;
import br.com.menthoros.backend.services.prompt.PaceHistoricoFormatter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Emissão de Constraint pelas fontes")
class ConstraintEmissionTest {

    @Nested
    @DisplayName("RecomendacaoIntervalado.toConstraint")
    class Intervalado {

        @Test
        @DisplayName("Substituido → INTERVALADO_PROIBIDO com descrição contendo a instrução")
        void substituido() {
            var rec = new RecomendacaoIntervalado.Substituido(
                    TipoTreino.REGENERATIVO, "Lesão ativa", "Use apenas regenerativo.");
            Optional<Constraint> c = rec.toConstraint();
            assertThat(c).isPresent();
            assertThat(c.get().key()).isEqualTo(ConstraintKey.INTERVALADO_PROIBIDO);
            assertThat(c.get().descricao()).contains("PROIBIDO").contains("Use apenas regenerativo.");
        }

        @Test
        @DisplayName("Degradado → INTERVALADO_MAX_CATEGORIA com a categoria segura")
        void degradado() {
            var rec = new RecomendacaoIntervalado.Degradado(
                    CategoriaIntervalado.C, "TSB baixo", "Só threshold leve.");
            Optional<Constraint> c = rec.toConstraint();
            assertThat(c).isPresent();
            assertThat(c.get().key()).isEqualTo(ConstraintKey.INTERVALADO_MAX_CATEGORIA);
            assertThat(c.get().params()).containsEntry(Constraint.PARAM_CATEGORIA_SEGURA, "C");
        }

        @Test
        @DisplayName("Elegivel → nenhuma constraint (permissão)")
        void elegivel() {
            var rec = new RecomendacaoIntervalado.Elegivel(
                    CategoriaIntervalado.A, "Apto", "Pode incluir intervalado A.");
            assertThat(rec.toConstraint()).isEmpty();
        }
    }

    @Nested
    @DisplayName("PaceHistoricoFormatter.tetoConstraint")
    class Pace {

        private final PaceHistoricoFormatter formatter = new PaceHistoricoFormatter();

        @Test
        @DisplayName("com tetos → PACE_TETO com params.teto")
        void comTetos() {
            Map<TipoTreino, BigDecimal> teto = Map.of(
                    TipoTreino.CONTINUO, new BigDecimal("5.7167"),
                    TipoTreino.LONGO, new BigDecimal("6.9167"));
            Optional<Constraint> c = formatter.tetoConstraint(teto);
            assertThat(c).isPresent();
            assertThat(c.get().key()).isEqualTo(ConstraintKey.PACE_TETO);
            assertThat(c.get().tetoPorTipo()).containsKeys(TipoTreino.CONTINUO, TipoTreino.LONGO);
            assertThat(c.get().descricao()).contains("ritmoAlvo");
        }

        @Test
        @DisplayName("sem tetos → nenhuma constraint")
        void semTetos() {
            assertThat(formatter.tetoConstraint(Map.of())).isEmpty();
        }
    }

    @Nested
    @DisplayName("DisponibilidadePromptFormatter.diasPermitidosConstraint")
    class Dias {

        private final DisponibilidadePromptFormatter formatter = new DisponibilidadePromptFormatter();

        @Test
        @DisplayName("dias efetivos → DIAS_PERMITIDOS com os dias")
        void comDias() {
            Optional<Constraint> c = formatter.diasPermitidosConstraint(List.of(DiaSemana.SEGUNDA, DiaSemana.QUARTA));
            assertThat(c).isPresent();
            assertThat(c.get().key()).isEqualTo(ConstraintKey.DIAS_PERMITIDOS);
            assertThat(c.get().diasPermitidos()).containsExactly(DiaSemana.SEGUNDA, DiaSemana.QUARTA);
        }

        @Test
        @DisplayName("semana cheia (null) → nenhuma constraint")
        void semanaCheia() {
            assertThat(formatter.diasPermitidosConstraint(null)).isEmpty();
        }
    }
}
