package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.entity.CheckinProntidao;
import br.com.menthoros.backend.enums.NivelProntidao;
import br.com.menthoros.backend.services.helper.TreinoHistoricoProvider.ContextoTreino;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TreinoHistoricoProviderContextoTreinoTest {

    private static final LocalDate HOJE = LocalDate.of(2026, 7, 2);

    @Nested
    @DisplayName("nivelProntidaoHoje")
    class NivelProntidaoHoje {

        @Test
        @DisplayName("retorna o nível do checkin cuja data é a data de referência")
        void retornaNivelDoDiaAtual() {
            ContextoTreino ctx = contexto(List.of(checkin(HOJE, NivelProntidao.CAUTELOSO, "0.600")));

            assertThat(ctx.nivelProntidaoHoje()).isEqualTo(NivelProntidao.CAUTELOSO);
        }

        @Test
        @DisplayName("retorna null quando não há checkin para a data de referência")
        void retornaNullQuandoAusente() {
            ContextoTreino ctx = contexto(List.of(checkin(HOJE.minusDays(1), NivelProntidao.PRONTO, "0.900")));

            assertThat(ctx.nivelProntidaoHoje()).isNull();
        }
    }

    @Nested
    @DisplayName("readinessScoreHoje")
    class ReadinessScoreHoje {

        @Test
        @DisplayName("retorna o score do checkin de hoje")
        void retornaScoreDoDiaAtual() {
            ContextoTreino ctx = contexto(List.of(checkin(HOJE, NivelProntidao.PRONTO, "0.850")));

            assertThat(ctx.readinessScoreHoje()).isEqualByComparingTo("0.850");
        }

        @Test
        @DisplayName("retorna null quando não há checkin de hoje")
        void retornaNullQuandoAusente() {
            ContextoTreino ctx = contexto(List.of());

            assertThat(ctx.readinessScoreHoje()).isNull();
        }
    }

    @Nested
    @DisplayName("sequenciaUltimos7Dias")
    class SequenciaUltimos7Dias {

        @Test
        @DisplayName("retorna 7 posições, do mais antigo ao mais recente, com null nos dias sem checkin")
        void retorna7PosicoesOrdenadas() {
            ContextoTreino ctx = contexto(List.of(
                    checkin(HOJE, NivelProntidao.PRONTO, "0.900"),
                    checkin(HOJE.minusDays(6), NivelProntidao.DESCANSAR, "0.300")));

            List<NivelProntidao> sequencia = ctx.sequenciaUltimos7Dias();

            assertThat(sequencia).hasSize(7);
            assertThat(sequencia.get(0)).isEqualTo(NivelProntidao.DESCANSAR); // 6 dias atrás
            assertThat(sequencia.get(6)).isEqualTo(NivelProntidao.PRONTO);    // hoje
            assertThat(sequencia.subList(1, 6)).containsOnlyNulls();
        }
    }

    private ContextoTreino contexto(List<CheckinProntidao> checkins) {
        return new ContextoTreino(HOJE, List.of(), List.of(), checkins);
    }

    private CheckinProntidao checkin(LocalDate data, NivelProntidao nivel, String score) {
        return CheckinProntidao.builder()
                .data(data)
                .nivelProntidao(nivel)
                .readinessScore(new BigDecimal(score))
                .build();
    }
}
