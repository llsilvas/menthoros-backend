package br.com.menthoros.backend.services.prompt.constraint;

import br.com.menthoros.backend.enums.DiaSemana;
import br.com.menthoros.backend.enums.TipoTreino;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Constraint")
class ConstraintTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Nested
    @DisplayName("factories")
    class Factories {

        @Test
        @DisplayName("intervaladoProibido: key correta, params vazios (regra de presença)")
        void intervaladoProibido() {
            Constraint c = Constraint.intervaladoProibido("Nenhum intervalado esta semana.");
            assertThat(c.key()).isEqualTo(ConstraintKey.INTERVALADO_PROIBIDO);
            assertThat(c.descricao()).isNotBlank();
            assertThat(c.params()).isEmpty();
        }

        @Test
        @DisplayName("paceTeto: params.teto mapeia tipo→pace")
        void paceTeto() {
            Map<TipoTreino, BigDecimal> teto = Map.of(
                    TipoTreino.CONTINUO, new BigDecimal("5.7167"),
                    TipoTreino.LONGO, new BigDecimal("6.9167"));
            Constraint c = Constraint.paceTeto("Não ultrapassar o teto.", teto);
            assertThat(c.key()).isEqualTo(ConstraintKey.PACE_TETO);
            assertThat(c.params()).containsKey(Constraint.PARAM_TETO);
        }

        @Test
        @DisplayName("diasPermitidos: params.dias lista os dias")
        void diasPermitidos() {
            Constraint c = Constraint.diasPermitidos("Treine só nos dias listados.",
                    List.of(DiaSemana.SEGUNDA, DiaSemana.QUARTA));
            assertThat(c.key()).isEqualTo(ConstraintKey.DIAS_PERMITIDOS);
            assertThat(c.diasPermitidos()).containsExactly(DiaSemana.SEGUNDA, DiaSemana.QUARTA);
        }

        @Test
        @DisplayName("maxConsecutivos: params.n guarda o limite")
        void maxConsecutivos() {
            Constraint c = Constraint.maxConsecutivos("No máximo 4 dias seguidos.", 4);
            assertThat(c.key()).isEqualTo(ConstraintKey.MAX_CONSECUTIVOS);
            assertThat(c.maxConsecutivos()).isEqualTo(4);
        }

        @Test
        @DisplayName("descrição em branco é rejeitada")
        void descricaoEmBranco() {
            assertThatThrownBy(() -> Constraint.intervaladoProibido("  "))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("serialização")
    class Serializacao {

        @Test
        @DisplayName("round-trip JSON preserva key/descrição/params (compatível com persistência)")
        void roundTrip() throws Exception {
            Constraint original = Constraint.maxConsecutivos("máx 4", 4);
            String json = mapper.writeValueAsString(original);
            Constraint back = mapper.readValue(json, Constraint.class);
            assertThat(back.key()).isEqualTo(ConstraintKey.MAX_CONSECUTIVOS);
            assertThat(back.descricao()).isEqualTo("máx 4");
            assertThat(back.params()).containsEntry(Constraint.PARAM_N, 4);
        }
    }
}
