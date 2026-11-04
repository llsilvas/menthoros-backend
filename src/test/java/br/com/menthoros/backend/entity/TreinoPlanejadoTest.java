package br.com.menthoros.backend.entity;

import br.com.menthoros.backend.enums.StatusSincronizacao;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class TreinoPlanejadoTest {

    private static final LocalDateTime AGORA = LocalDateTime.of(2026, 11, 4, 20, 0);

    @Nested
    @DisplayName("reiniciarSincronizacao")
    class ReiniciarSincronizacao {

        @ParameterizedTest
        @EnumSource(value = StatusSincronizacao.class,
                names = {"ERRO_PERMANENTE", "ERRO_AUTENTICACAO", "ERRO_VALIDACAO"})
        @DisplayName("zera tentativas e erro e volta a AGUARDANDO_RETRY")
        void zeraTentativasEVoltaAoRetry(StatusSincronizacao statusErro) {
            TreinoPlanejado treino = emErro(statusErro);

            treino.reiniciarSincronizacao();

            assertThat(treino.getStatusSincronizacao()).isEqualTo(StatusSincronizacao.AGUARDANDO_RETRY);
            assertThat(treino.getTentativasSincronizacao()).isZero();
            assertThat(treino.getUltimaTentativaSincronizacao()).isNull();
            assertThat(treino.getErroSincronizacao()).isNull();
        }

        @Test
        @DisplayName("preserva o vínculo externo para o push seguinte atualizar em vez de criar")
        void preservaVinculoExterno() {
            TreinoPlanejado treino = emErro(StatusSincronizacao.ERRO_PERMANENTE);

            treino.reiniciarSincronizacao();

            assertThat(treino.getExternalId()).isEqualTo("987654");
            assertThat(treino.getExportadoPara()).isEqualTo("[\"GARMIN\"]");
        }
    }

    @Nested
    @DisplayName("envioEmAndamento")
    class EnvioEmAndamento {

        @Test
        @DisplayName("AGUARDANDO_RETRY está em andamento")
        void aguardandoRetry() {
            TreinoPlanejado treino = comStatus(StatusSincronizacao.AGUARDANDO_RETRY, null);

            assertThat(treino.envioEmAndamento(AGORA)).isTrue();
        }

        @Test
        @DisplayName("SINCRONIZANDO com tentativa recente está em andamento")
        void sincronizandoRecente() {
            TreinoPlanejado treino = comStatus(StatusSincronizacao.SINCRONIZANDO, AGORA.minusMinutes(9));

            assertThat(treino.envioEmAndamento(AGORA)).isTrue();
        }

        @Test
        @DisplayName("SINCRONIZANDO com tentativa de 10 min ou mais é tratado como preso")
        void sincronizandoPreso() {
            TreinoPlanejado treino = comStatus(StatusSincronizacao.SINCRONIZANDO, AGORA.minusMinutes(10));

            assertThat(treino.envioEmAndamento(AGORA)).isFalse();
        }

        @Test
        @DisplayName("SINCRONIZANDO sem data de tentativa é tratado como preso")
        void sincronizandoSemData() {
            TreinoPlanejado treino = comStatus(StatusSincronizacao.SINCRONIZANDO, null);

            assertThat(treino.envioEmAndamento(AGORA)).isFalse();
        }

        @ParameterizedTest
        @EnumSource(value = StatusSincronizacao.class,
                names = {"SINCRONIZADO", "ERRO_PERMANENTE", "ERRO_TEMPORARIO", "NAO_SINCRONIZADO"})
        @DisplayName("demais status não estão em andamento")
        void demaisStatus(StatusSincronizacao status) {
            TreinoPlanejado treino = comStatus(status, AGORA.minusMinutes(1));

            assertThat(treino.envioEmAndamento(AGORA)).isFalse();
        }
    }

    private static TreinoPlanejado emErro(StatusSincronizacao statusErro) {
        TreinoPlanejado treino = comStatus(statusErro, AGORA.minusHours(1));
        treino.setTentativasSincronizacao(5);
        treino.setErroSincronizacao("Falha ao enviar");
        treino.setExternalId("987654");
        treino.setExportadoPara("[\"GARMIN\"]");
        return treino;
    }

    private static TreinoPlanejado comStatus(StatusSincronizacao status, LocalDateTime ultimaTentativa) {
        TreinoPlanejado treino = new TreinoPlanejado();
        treino.setStatusSincronizacao(status);
        treino.setUltimaTentativaSincronizacao(ultimaTentativa);
        return treino;
    }
}
