package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.TreinoPlanejado;
import br.com.menthoros.backend.enums.TreinoExecucaoStatus;
import br.com.menthoros.backend.services.helper.ApprovedWorkoutEditPolicy.EditRejection;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class ApprovedWorkoutEditPolicyTest {

    private static final LocalDate HOJE = LocalDate.of(2026, 11, 4);

    private final ApprovedWorkoutEditPolicy policy = new ApprovedWorkoutEditPolicy();

    @Nested
    @DisplayName("avaliar")
    class Avaliar {

        @Test
        @DisplayName("treino pendente de amanhã é editável, com ou sem relógio")
        void amanhaEditavel() {
            assertThat(policy.avaliar(treino(TreinoExecucaoStatus.PENDENTE, HOJE.plusDays(1)), HOJE, true)).isEmpty();
            assertThat(policy.avaliar(treino(TreinoExecucaoStatus.PENDENTE, HOJE.plusDays(1)), HOJE, false)).isEmpty();
        }

        @Test
        @DisplayName("status de execução nulo conta como pendente")
        void statusNuloEditavel() {
            assertThat(policy.avaliar(treino(null, HOJE.plusDays(2)), HOJE, true)).isEmpty();
        }

        @ParameterizedTest
        @EnumSource(value = TreinoExecucaoStatus.class, names = "PENDENTE", mode = EnumSource.Mode.EXCLUDE)
        @DisplayName("qualquer execução registrada trava, mesmo em dia futuro")
        void execucaoRegistradaTrava(TreinoExecucaoStatus status) {
            assertThat(policy.avaliar(treino(status, HOJE.plusDays(1)), HOJE, false))
                    .contains(EditRejection.NOT_PENDING);
        }

        @Test
        @DisplayName("treino pulado hoje pelo atleta (PERDIDO de hoje) trava")
        void puladoHojeTrava() {
            assertThat(policy.avaliar(treino(TreinoExecucaoStatus.PERDIDO, HOJE), HOJE, false))
                    .contains(EditRejection.NOT_PENDING);
        }

        @Test
        @DisplayName("treino de ontem trava")
        void ontemTrava() {
            assertThat(policy.avaliar(treino(TreinoExecucaoStatus.PENDENTE, HOJE.minusDays(1)), HOJE, false))
                    .contains(EditRejection.PAST_DAY);
        }

        @Test
        @DisplayName("treino de hoje com relógio trava")
        void hojeComRelogioTrava() {
            assertThat(policy.avaliar(treino(TreinoExecucaoStatus.PENDENTE, HOJE), HOJE, true))
                    .contains(EditRejection.TODAY_ON_WATCH);
        }

        @Test
        @DisplayName("treino de hoje sem relógio é editável")
        void hojeSemRelogioEditavel() {
            assertThat(policy.avaliar(treino(TreinoExecucaoStatus.PENDENTE, HOJE), HOJE, false)).isEmpty();
        }

        @Test
        @DisplayName("23h30 em São Paulo (02h30 UTC do dia seguinte): o treino do dia seguinte em SP segue editável")
        void viradaDeDiaNoFusoDoAtleta() {
            Clock clock = Clock.fixed(Instant.parse("2026-11-05T02:30:00Z"), ZoneOffset.UTC);
            Atleta atleta = new Atleta();
            atleta.setTimezone("America/Sao_Paulo");
            LocalDate hojeSp = new AtletaHojeResolver(clock).hojeDe(atleta);

            assertThat(hojeSp).isEqualTo(HOJE);
            assertThat(policy.avaliar(treino(TreinoExecucaoStatus.PENDENTE, HOJE.plusDays(1)), hojeSp, true)).isEmpty();
            assertThat(policy.avaliar(treino(TreinoExecucaoStatus.PENDENTE, HOJE.minusDays(1)), hojeSp, false))
                    .contains(EditRejection.PAST_DAY);
        }
    }

    private static TreinoPlanejado treino(TreinoExecucaoStatus status, LocalDate data) {
        TreinoPlanejado treino = new TreinoPlanejado();
        treino.setStatusTreino(status);
        treino.setDataTreino(data);
        return treino;
    }
}
