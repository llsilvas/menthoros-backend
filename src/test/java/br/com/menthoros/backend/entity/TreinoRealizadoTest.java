package br.com.menthoros.backend.entity;

import br.com.menthoros.backend.enums.TipoTreino;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TreinoRealizadoTest {

    /**
     * Seam do fix de progressão: o tipo do realizado vindo de integração é inferido por heurística
     * de duração/FC e erra em longões curtos com FC alta. Quem decide carga precisa perguntar
     * "cumpriu a sessão prescrita?", e a prescrição está no planejado vinculado.
     */
    @Nested
    @DisplayName("getTipoTreinoEfetivo")
    class GetTipoTreinoEfetivo {

        @Test
        @DisplayName("sem vínculo com planejado, devolve o tipo executado")
        void semVinculoDevolveTipoRealizado() {
            TreinoRealizado realizado = comTipo(TipoTreino.TEMPO_RUN);

            assertThat(realizado.getTipoTreinoEfetivo()).isEqualTo(TipoTreino.TEMPO_RUN);
        }

        @Test
        @DisplayName("com vínculo, a prescrição do coach prevalece sobre o tipo inferido")
        void vinculoPrevaleceSobreInferencia() {
            TreinoRealizado realizado = comTipo(TipoTreino.TEMPO_RUN);
            realizado.setTreinoPlanejado(planejadoComTipo(TipoTreino.LONGO));

            assertThat(realizado.getTipoTreinoEfetivo()).isEqualTo(TipoTreino.LONGO);
        }

        @Test
        @DisplayName("planejado sem tipo definido cai de volta no tipo executado")
        void planejadoSemTipoUsaFallback() {
            TreinoRealizado realizado = comTipo(TipoTreino.LONGO);
            realizado.setTreinoPlanejado(planejadoComTipo(null));

            assertThat(realizado.getTipoTreinoEfetivo()).isEqualTo(TipoTreino.LONGO);
        }

        @Test
        @DisplayName("sem tipo em nenhum dos dois lados, devolve null em vez de lançar")
        void ambosNulosDevolveNull() {
            TreinoRealizado realizado = comTipo(null);
            realizado.setTreinoPlanejado(planejadoComTipo(null));

            assertThat(realizado.getTipoTreinoEfetivo()).isNull();
        }

        @Test
        @DisplayName("o tipo persistido do realizado não é sobrescrito — a divergência prescrito/executado é preservada")
        void naoSobrescreveTipoPersistido() {
            TreinoRealizado realizado = comTipo(TipoTreino.TEMPO_RUN);
            realizado.setTreinoPlanejado(planejadoComTipo(TipoTreino.LONGO));

            realizado.getTipoTreinoEfetivo();

            // TipoTreinoConsistenciaValidator depende de enxergar os dois valores diferentes.
            assertThat(realizado.getTipoTreino()).isEqualTo(TipoTreino.TEMPO_RUN);
        }
    }

    // ── Helpers ─────────────────────────────────────────────────────────────────

    private TreinoRealizado comTipo(TipoTreino tipo) {
        TreinoRealizado realizado = new TreinoRealizado();
        realizado.setTipoTreino(tipo);
        return realizado;
    }

    private TreinoPlanejado planejadoComTipo(TipoTreino tipo) {
        TreinoPlanejado planejado = new TreinoPlanejado();
        planejado.setTipoTreino(tipo);
        return planejado;
    }
}
