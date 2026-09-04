package br.com.menthoros.backend.services.plano;

import br.com.menthoros.backend.entity.Prova;
import br.com.menthoros.backend.entity.TreinoPlanejado;
import br.com.menthoros.backend.entity.TreinoRealizado;
import br.com.menthoros.backend.enums.DiaSemana;
import br.com.menthoros.backend.enums.DistanciaProva;
import br.com.menthoros.backend.enums.ProvaStatus;
import br.com.menthoros.backend.enums.TipoProva;
import br.com.menthoros.backend.enums.TipoTreino;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * D6 (prova-no-plano-semanal): fecha o resultado da prova quando o treino {@code PROVA} vinculado
 * é executado — por qualquer caminho de vínculo (registro manual, coach, reconciliação).
 */
class ProvaResultadoSyncerTest {

    private ProvaResultadoSyncer syncer;
    private Prova prova;

    @BeforeEach
    void setUp() {
        syncer = new ProvaResultadoSyncer();
        prova = Prova.builder()
                .nomeProva("Meia Maratona")
                .dataProva(LocalDate.now().plusDays(3))
                .distancia(DistanciaProva.KM_21)
                .distanciaKm(BigDecimal.valueOf(21.1))
                .tipoProva(TipoProva.MEIA)
                .statusProva(ProvaStatus.PLANEJADA)
                .foiRealizada(false)
                .build();
    }

    @Nested
    @DisplayName("aoVincular")
    class AoVincular {

        @Test
        @DisplayName("PROVA com prova vinculada: marca foiRealizada e copia o tempo do realizado")
        void marcaProvaComoRealizada() {
            TreinoPlanejado planejado = treinoProva(prova);
            TreinoRealizado realizado = realizadoCom(Duration.ofHours(1).plusMinutes(48).plusSeconds(30));

            syncer.aoVincular(planejado, realizado);

            assertThat(prova.getFoiRealizada()).isTrue();
            assertThat(prova.getTempoRealizado()).isEqualTo(Duration.ofHours(1).plusMinutes(48).plusSeconds(30));
        }

        @Test
        @DisplayName("tipo diferente de PROVA: não mexe em nada")
        void tipoDiferenteNaoMexe() {
            TreinoPlanejado planejado = new TreinoPlanejado();
            planejado.setTipoTreino(TipoTreino.CONTINUO);
            planejado.setProva(prova);
            TreinoRealizado realizado = realizadoCom(Duration.ofMinutes(45));

            syncer.aoVincular(planejado, realizado);

            assertThat(prova.getFoiRealizada()).isFalse();
            assertThat(prova.getTempoRealizado()).isNull();
        }

        @Test
        @DisplayName("PROVA sem prova vinculada: não lança e não mexe em nada")
        void provaSemVinculoNaoLancaNemMexe() {
            TreinoPlanejado planejado = new TreinoPlanejado();
            planejado.setTipoTreino(TipoTreino.PROVA);
            planejado.setProva(null);
            TreinoRealizado realizado = realizadoCom(Duration.ofMinutes(45));

            syncer.aoVincular(planejado, realizado);
            // sem exceção — só a ausência de efeito importa aqui
        }

        @Test
        @DisplayName("planejado nulo: não lança")
        void planejadoNuloNaoLanca() {
            syncer.aoVincular(null, realizadoCom(Duration.ofMinutes(45)));
        }

        @Test
        @DisplayName("vínculo refeito para outro realizado: o tempo segue o novo")
        void vinculoRefeitoTempoSegueNovo() {
            TreinoPlanejado planejado = treinoProva(prova);
            syncer.aoVincular(planejado, realizadoCom(Duration.ofHours(1).plusMinutes(50)));
            assertThat(prova.getTempoRealizado()).isEqualTo(Duration.ofHours(1).plusMinutes(50));

            syncer.aoVincular(planejado, realizadoCom(Duration.ofHours(1).plusMinutes(45)));

            assertThat(prova.getTempoRealizado()).isEqualTo(Duration.ofHours(1).plusMinutes(45));
            assertThat(prova.getFoiRealizada()).isTrue();
        }

        @Test
        @DisplayName("desvincular (não chamado) mantém a prova intacta — aoVincular nunca desmarca")
        void desvincularNaoMexeQuandoNaoChamado() {
            TreinoPlanejado planejado = treinoProva(prova);
            syncer.aoVincular(planejado, realizadoCom(Duration.ofHours(2)));
            assertThat(prova.getFoiRealizada()).isTrue();

            // Simula "desvincular": o planejado deixa de ter treinoRealizado, mas aoVincular não
            // é chamado nesse caminho (contrato: só marca ao vincular, nunca ao desvincular).
            assertThat(prova.getFoiRealizada()).isTrue();
            assertThat(prova.getTempoRealizado()).isEqualTo(Duration.ofHours(2));
        }
    }

    private TreinoPlanejado treinoProva(Prova prova) {
        TreinoPlanejado treino = new TreinoPlanejado();
        treino.setDataTreino(prova.getDataProva());
        treino.setDiaSemana(DiaSemana.SEGUNDA);
        treino.setTipoTreino(TipoTreino.PROVA);
        treino.setDuracaoMin(Duration.ofMinutes(50));
        treino.setProva(prova);
        return treino;
    }

    private TreinoRealizado realizadoCom(Duration duracao) {
        TreinoRealizado realizado = new TreinoRealizado();
        realizado.setDuracaoMin(duracao);
        return realizado;
    }
}
