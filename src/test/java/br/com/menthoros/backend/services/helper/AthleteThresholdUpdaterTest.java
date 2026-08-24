package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.entity.Assessoria;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.PlanoMetaDados;
import br.com.menthoros.backend.entity.Prova;
import br.com.menthoros.backend.entity.TreinoRealizado;
import br.com.menthoros.backend.enums.ConfiancaInferencia;
import br.com.menthoros.backend.enums.FonteLimiarInferencia;
import br.com.menthoros.backend.enums.NivelExperiencia;
import br.com.menthoros.backend.enums.TipoTreino;
import br.com.menthoros.backend.repository.ProvaRepository;
import br.com.menthoros.backend.repository.TreinoRealizadoRepository;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Testes de {@link AthleteThresholdUpdater#atualizarLimiares}, extraído de
 * {@code TsbServiceImpl} — chamada direta ao método público, sem reflection
 * (refactor-threshold-orchestration, CA1/CA2).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AthleteThresholdUpdater — atualizarLimiares")
class AthleteThresholdUpdaterTest {

    @Mock private TreinoRealizadoRepository treinoRealizadoRepository;
    @Mock private ProvaRepository provaRepository;
    @Mock private ThresholdInferenceService thresholdInferenceService;

    @InjectMocks
    private AthleteThresholdUpdater updater;

    private static final LocalDate HOJE = LocalDate.of(2026, 6, 22);

    // =========================================================================

    @Nested
    @DisplayName("inferência de FC")
    class InferirFcLimiar {

        @Test
        @DisplayName("seta fcLimiarEstimado quando FC oficial ausente e inferência retorna valor")
        void setaFcEstimadoQuandoLimiarAusenteEInferenciaRetornaValor() {
            Atleta atleta = atletaBase();
            atleta.setFcLimiar(null);
            atleta.setDataUltimoTesteFc(null);
            PlanoMetaDados metaDados = metaDadosBase(atleta);

            List<TreinoRealizado> treinos = treinos10ComFcMedia(163);
            when(thresholdInferenceService.isFcLimiarDesatualizado(atleta, HOJE)).thenReturn(true);
            when(thresholdInferenceService.isPaceLimiarDesatualizado(atleta, HOJE)).thenReturn(true);
            when(treinoRealizadoRepository.findByAtletaIdAndTenantIdAndDataTreinoBetween(any(), any(), any(), any()))
                    .thenReturn(treinos);
            when(thresholdInferenceService.inferirFcLimiar(treinos, HOJE))
                    .thenReturn(Optional.of(new ThresholdEstimate<>(163, 10, ConfiancaInferencia.ALTA)));
            when(thresholdInferenceService.inferirPaceLimiar(treinos, HOJE))
                    .thenReturn(Optional.empty());

            updater.atualizarLimiares(atleta, metaDados, HOJE);

            assertThat(metaDados.getFcLimiarEstimado()).isEqualTo(163);
            assertThat(metaDados.getConfiancaInferenciaFc()).isEqualTo(ConfiancaInferencia.ALTA);
            assertThat(metaDados.getDataInferenciaLimiar()).isEqualTo(HOJE);
        }

        @Test
        @DisplayName("não seta fcLimiarEstimado quando FC oficial tem teste recente (< 90 dias)")
        void naoSetaFcEstimadoQuandoTesteRecente() {
            Atleta atleta = atletaBase();
            atleta.setFcLimiar(165);
            atleta.setDataUltimoTesteFc(HOJE.minusDays(30));
            atleta.setPaceLimiar(new BigDecimal("5.00"));
            atleta.setDataUltimoTestePace(HOJE.minusDays(30));
            PlanoMetaDados metaDados = metaDadosBase(atleta);

            when(thresholdInferenceService.isFcLimiarDesatualizado(atleta, HOJE)).thenReturn(false);
            when(thresholdInferenceService.isPaceLimiarDesatualizado(atleta, HOJE)).thenReturn(false);

            updater.atualizarLimiares(atleta, metaDados, HOJE);

            assertThat(metaDados.getFcLimiarEstimado()).isNull();
            assertThat(metaDados.getDataInferenciaLimiar()).isNull();
        }

        @Test
        @DisplayName("não seta fcLimiarEstimado quando inferência retorna vazio (< MIN_AMOSTRAS)")
        void naoSetaFcEstimadoQuandoAmostraInsuficiente() {
            Atleta atleta = atletaBase();
            atleta.setFcLimiar(null);
            atleta.setDataUltimoTesteFc(null);
            atleta.setPaceLimiar(null);
            atleta.setDataUltimoTestePace(null);
            PlanoMetaDados metaDados = metaDadosBase(atleta);

            when(thresholdInferenceService.isFcLimiarDesatualizado(atleta, HOJE)).thenReturn(true);
            when(thresholdInferenceService.isPaceLimiarDesatualizado(atleta, HOJE)).thenReturn(true);
            when(treinoRealizadoRepository.findByAtletaIdAndTenantIdAndDataTreinoBetween(any(), any(), any(), any()))
                    .thenReturn(List.of());
            when(thresholdInferenceService.inferirFcLimiar(List.of(), HOJE))
                    .thenReturn(Optional.empty());
            when(thresholdInferenceService.inferirPaceLimiar(List.of(), HOJE))
                    .thenReturn(Optional.empty());

            updater.atualizarLimiares(atleta, metaDados, HOJE);

            assertThat(metaDados.getFcLimiarEstimado()).isNull();
        }

        @Test
        @DisplayName("não sobrescreve atleta.fcLimiar nem atleta.paceLimiar (CA5)")
        void naoAlteraLimiresOficiaisDoAtleta() {
            Atleta atleta = atletaBase();
            atleta.setFcLimiar(null);
            atleta.setDataUltimoTesteFc(null);
            atleta.setPaceLimiar(null);
            atleta.setDataUltimoTestePace(null);
            Integer fcOriginal = atleta.getFcLimiar();
            BigDecimal paceOriginal = atleta.getPaceLimiar();
            PlanoMetaDados metaDados = metaDadosBase(atleta);

            List<TreinoRealizado> treinos = treinos10ComFcMedia(163);
            when(thresholdInferenceService.isFcLimiarDesatualizado(atleta, HOJE)).thenReturn(true);
            when(thresholdInferenceService.isPaceLimiarDesatualizado(atleta, HOJE)).thenReturn(true);
            when(treinoRealizadoRepository.findByAtletaIdAndTenantIdAndDataTreinoBetween(any(), any(), any(), any()))
                    .thenReturn(treinos);
            when(thresholdInferenceService.inferirFcLimiar(treinos, HOJE))
                    .thenReturn(Optional.of(new ThresholdEstimate<>(163, 10, ConfiancaInferencia.ALTA)));
            when(thresholdInferenceService.inferirPaceLimiar(treinos, HOJE))
                    .thenReturn(Optional.of(new ThresholdEstimate<>(new BigDecimal("4.7500"), 8, ConfiancaInferencia.MEDIA)));

            updater.atualizarLimiares(atleta, metaDados, HOJE);

            assertThat(atleta.getFcLimiar()).isEqualTo(fcOriginal);
            assertThat(atleta.getPaceLimiar()).isEqualTo(paceOriginal);
            // metaDados recebe os valores estimados
            assertThat(metaDados.getFcLimiarEstimado()).isEqualTo(163);
            assertThat(metaDados.getPaceLimiarEstimado()).isEqualByComparingTo(new BigDecimal("4.7500"));
        }
    }

    @Nested
    @DisplayName("inferência de pace")
    class InferirPaceLimiar {

        @Test
        @DisplayName("seta paceLimiarEstimado quando pace oficial ausente e inferência retorna valor")
        void setaPaceEstimadoQuandoLimiarAusenteEInferenciaRetornaValor() {
            Atleta atleta = atletaBase();
            atleta.setFcLimiar(165);
            atleta.setDataUltimoTesteFc(HOJE.minusDays(30));
            atleta.setPaceLimiar(null);
            atleta.setDataUltimoTestePace(null);
            PlanoMetaDados metaDados = metaDadosBase(atleta);

            List<TreinoRealizado> treinos = treinos10ComFcMedia(150);
            BigDecimal paceEstimado = new BigDecimal("4.7500");
            when(thresholdInferenceService.isFcLimiarDesatualizado(atleta, HOJE)).thenReturn(false);
            when(thresholdInferenceService.isPaceLimiarDesatualizado(atleta, HOJE)).thenReturn(true);
            when(treinoRealizadoRepository.findByAtletaIdAndTenantIdAndDataTreinoBetween(any(), any(), any(), any()))
                    .thenReturn(treinos);
            when(thresholdInferenceService.inferirPaceLimiar(treinos, HOJE))
                    .thenReturn(Optional.of(new ThresholdEstimate<>(paceEstimado, 6, ConfiancaInferencia.MEDIA)));

            updater.atualizarLimiares(atleta, metaDados, HOJE);

            assertThat(metaDados.getPaceLimiarEstimado()).isEqualByComparingTo(paceEstimado);
            assertThat(metaDados.getConfiancaInferenciaPace()).isEqualTo(ConfiancaInferencia.MEDIA);
            assertThat(metaDados.getDataInferenciaLimiar()).isEqualTo(HOJE);
        }

        @Test
        @DisplayName("não seta paceLimiarEstimado quando pace oficial tem teste recente (< 90 dias)")
        void naoSetaPaceEstimadoQuandoTesteRecente() {
            Atleta atleta = atletaBase();
            atleta.setFcLimiar(165);
            atleta.setDataUltimoTesteFc(HOJE.minusDays(30));
            atleta.setPaceLimiar(new BigDecimal("5.00"));
            atleta.setDataUltimoTestePace(HOJE.minusDays(45));
            PlanoMetaDados metaDados = metaDadosBase(atleta);

            when(thresholdInferenceService.isFcLimiarDesatualizado(atleta, HOJE)).thenReturn(false);
            when(thresholdInferenceService.isPaceLimiarDesatualizado(atleta, HOJE)).thenReturn(false);

            updater.atualizarLimiares(atleta, metaDados, HOJE);

            assertThat(metaDados.getPaceLimiarEstimado()).isNull();
        }

        @Test
        @DisplayName("CA1/CA7: prova válida recente tem precedência sobre o quintil e persiste PROVA_REGISTRADA")
        void provaValidaTemPrecedenciaSobreQuintil() {
            Atleta atleta = atletaBase();
            atleta.setFcLimiar(165);
            atleta.setDataUltimoTesteFc(HOJE.minusDays(30));
            atleta.setPaceLimiar(null);
            atleta.setDataUltimoTestePace(null);
            PlanoMetaDados metaDados = metaDadosBase(atleta);

            Prova provaValida = provaBase();
            BigDecimal paceDaProva = new BigDecimal("4.6333");
            when(thresholdInferenceService.isFcLimiarDesatualizado(atleta, HOJE)).thenReturn(false);
            when(thresholdInferenceService.isPaceLimiarDesatualizado(atleta, HOJE)).thenReturn(true);
            when(treinoRealizadoRepository.findByAtletaIdAndTenantIdAndDataTreinoBetween(any(), any(), any(), any()))
                    .thenReturn(List.of());
            when(provaRepository.findProvasRealizadasRecentes(any(), any(), any()))
                    .thenReturn(List.of(provaValida));
            when(thresholdInferenceService.encontrarProvaValidaMaisRecente(List.of(provaValida)))
                    .thenReturn(Optional.of(provaValida));
            when(thresholdInferenceService.inferirPaceLimiarDeProva(provaValida))
                    .thenReturn(paceDaProva);

            updater.atualizarLimiares(atleta, metaDados, HOJE);

            assertThat(metaDados.getPaceLimiarEstimado()).isEqualByComparingTo(paceDaProva);
            assertThat(metaDados.getConfiancaInferenciaPace()).isEqualTo(ConfiancaInferencia.ALTA);
            assertThat(metaDados.getFonteLimiarPace()).isEqualTo(FonteLimiarInferencia.PROVA_REGISTRADA);
            assertThat(metaDados.getDataInferenciaLimiar()).isEqualTo(HOJE);
            verify(thresholdInferenceService, never()).inferirPaceLimiar(any(), any());
        }

        @Test
        @DisplayName("CA2: sem prova válida, fallback por quintil idêntico ao atual e persiste MEDIA_TREINOS")
        void semProvaValidaFallbackPorQuintilPersisteMediaTreinos() {
            Atleta atleta = atletaBase();
            atleta.setFcLimiar(165);
            atleta.setDataUltimoTesteFc(HOJE.minusDays(30));
            atleta.setPaceLimiar(null);
            atleta.setDataUltimoTestePace(null);
            PlanoMetaDados metaDados = metaDadosBase(atleta);

            List<TreinoRealizado> treinos = treinos10ComFcMedia(150);
            BigDecimal paceEstimado = new BigDecimal("4.7500");
            when(thresholdInferenceService.isFcLimiarDesatualizado(atleta, HOJE)).thenReturn(false);
            when(thresholdInferenceService.isPaceLimiarDesatualizado(atleta, HOJE)).thenReturn(true);
            when(treinoRealizadoRepository.findByAtletaIdAndTenantIdAndDataTreinoBetween(any(), any(), any(), any()))
                    .thenReturn(treinos);
            when(provaRepository.findProvasRealizadasRecentes(any(), any(), any()))
                    .thenReturn(List.of());
            when(thresholdInferenceService.encontrarProvaValidaMaisRecente(List.of()))
                    .thenReturn(Optional.empty());
            when(thresholdInferenceService.inferirPaceLimiar(treinos, HOJE))
                    .thenReturn(Optional.of(new ThresholdEstimate<>(paceEstimado, 6, ConfiancaInferencia.MEDIA)));

            updater.atualizarLimiares(atleta, metaDados, HOJE);

            assertThat(metaDados.getPaceLimiarEstimado()).isEqualByComparingTo(paceEstimado);
            assertThat(metaDados.getConfiancaInferenciaPace()).isEqualTo(ConfiancaInferencia.MEDIA);
            assertThat(metaDados.getFonteLimiarPace()).isEqualTo(FonteLimiarInferencia.MEDIA_TREINOS);
        }
    }

    @Nested
    @DisplayName("sinalização de outlier no recálculo via prova (D5)")
    class SinalizacaoOutlier {

        private ListAppender<ILoggingEvent> logCapture;

        @BeforeEach
        void setUpLogCapture() {
            logCapture = new ListAppender<>();
            logCapture.start();
            ((Logger) LoggerFactory.getLogger(AthleteThresholdUpdater.class)).addAppender(logCapture);
        }

        @AfterEach
        void tearDownLogCapture() {
            ((Logger) LoggerFactory.getLogger(AthleteThresholdUpdater.class)).detachAppender(logCapture);
        }

        @Test
        @DisplayName("delta > 20s/km gera WARN")
        void deltaGrandeGeraWarn() {
            Atleta atleta = atletaBase();
            atleta.setFcLimiar(165);
            atleta.setDataUltimoTesteFc(HOJE.minusDays(30));
            atleta.setPaceLimiar(null);
            atleta.setDataUltimoTestePace(null);
            PlanoMetaDados metaDados = metaDadosBase(atleta);
            metaDados.setPaceLimiarEstimado(new BigDecimal("5.0000")); // pace anterior

            Prova provaValida = provaBase();
            BigDecimal paceNovo = new BigDecimal("4.5000"); // delta = -30s/km
            when(thresholdInferenceService.isFcLimiarDesatualizado(atleta, HOJE)).thenReturn(false);
            when(thresholdInferenceService.isPaceLimiarDesatualizado(atleta, HOJE)).thenReturn(true);
            when(treinoRealizadoRepository.findByAtletaIdAndTenantIdAndDataTreinoBetween(any(), any(), any(), any()))
                    .thenReturn(List.of());
            when(provaRepository.findProvasRealizadasRecentes(any(), any(), any()))
                    .thenReturn(List.of(provaValida));
            when(thresholdInferenceService.encontrarProvaValidaMaisRecente(List.of(provaValida)))
                    .thenReturn(Optional.of(provaValida));
            when(thresholdInferenceService.inferirPaceLimiarDeProva(provaValida))
                    .thenReturn(paceNovo);

            updater.atualizarLimiares(atleta, metaDados, HOJE);

            assertThat(logCapture.list)
                    .anyMatch(evento -> evento.getLevel() == Level.WARN
                            && evento.getFormattedMessage().contains("outlier"));
        }

        @Test
        @DisplayName("delta <= 20s/km gera INFO, sem WARN")
        void deltaPequenoGeraInfo() {
            Atleta atleta = atletaBase();
            atleta.setFcLimiar(165);
            atleta.setDataUltimoTesteFc(HOJE.minusDays(30));
            atleta.setPaceLimiar(null);
            atleta.setDataUltimoTestePace(null);
            PlanoMetaDados metaDados = metaDadosBase(atleta);
            metaDados.setPaceLimiarEstimado(new BigDecimal("5.0000")); // pace anterior

            Prova provaValida = provaBase();
            BigDecimal paceNovo = new BigDecimal("4.9500"); // delta = -3s/km
            when(thresholdInferenceService.isFcLimiarDesatualizado(atleta, HOJE)).thenReturn(false);
            when(thresholdInferenceService.isPaceLimiarDesatualizado(atleta, HOJE)).thenReturn(true);
            when(treinoRealizadoRepository.findByAtletaIdAndTenantIdAndDataTreinoBetween(any(), any(), any(), any()))
                    .thenReturn(List.of());
            when(provaRepository.findProvasRealizadasRecentes(any(), any(), any()))
                    .thenReturn(List.of(provaValida));
            when(thresholdInferenceService.encontrarProvaValidaMaisRecente(List.of(provaValida)))
                    .thenReturn(Optional.of(provaValida));
            when(thresholdInferenceService.inferirPaceLimiarDeProva(provaValida))
                    .thenReturn(paceNovo);

            updater.atualizarLimiares(atleta, metaDados, HOJE);

            assertThat(logCapture.list).noneMatch(evento -> evento.getLevel() == Level.WARN);
            assertThat(logCapture.list)
                    .anyMatch(evento -> evento.getLevel() == Level.INFO
                            && evento.getFormattedMessage().contains("atualizado via prova"));
        }
    }

    @Nested
    @DisplayName("D8: cancelado não conta na carga (achado /qa Bloco 2, ingestao-treino-realizado)")
    class ExclusaoCancelado {

        @Test
        @DisplayName("treinos30d passado às inferências exclui CANCELADO e inclui status null")
        void excluiCanceladoDosTreinosUsadosNaInferencia() {
            Atleta atleta = atletaBase();
            atleta.setFcLimiar(null);
            atleta.setDataUltimoTesteFc(null);
            PlanoMetaDados metaDados = metaDadosBase(atleta);

            List<TreinoRealizado> treinosBrutos = treinos10ComFcMedia(163);
            TreinoRealizado cancelado = new TreinoRealizado();
            cancelado.setDataTreino(HOJE.minusDays(2));
            cancelado.setFcMedia(999);
            cancelado.setDuracaoMin(Duration.ofMinutes(45));
            cancelado.setTipoTreino(TipoTreino.CONTINUO);
            cancelado.setStatusSincronizacao(br.com.menthoros.backend.enums.StatusSincronizacao.CANCELADO);
            List<TreinoRealizado> comCancelado = new ArrayList<>(treinosBrutos);
            comCancelado.add(cancelado);

            when(thresholdInferenceService.isFcLimiarDesatualizado(atleta, HOJE)).thenReturn(true);
            when(thresholdInferenceService.isPaceLimiarDesatualizado(atleta, HOJE)).thenReturn(true);
            when(treinoRealizadoRepository.findByAtletaIdAndTenantIdAndDataTreinoBetween(any(), any(), any(), any()))
                    .thenReturn(comCancelado);
            when(thresholdInferenceService.inferirFcLimiar(treinosBrutos, HOJE))
                    .thenReturn(Optional.of(new ThresholdEstimate<>(163, 10, ConfiancaInferencia.ALTA)));
            when(thresholdInferenceService.inferirPaceLimiar(treinosBrutos, HOJE))
                    .thenReturn(Optional.empty());

            updater.atualizarLimiares(atleta, metaDados, HOJE);

            verify(thresholdInferenceService).inferirFcLimiar(treinosBrutos, HOJE);
            verify(thresholdInferenceService).inferirPaceLimiar(treinosBrutos, HOJE);
            assertThat(metaDados.getFcLimiarEstimado()).isEqualTo(163);
        }
    }

    @Nested
    @DisplayName("casos de borda")
    class CasosDeBorda {

        @Test
        @DisplayName("atleta nulo lança IllegalArgumentException")
        void atletaNuloLancaExcecao() {
            PlanoMetaDados metaDados = metaDadosBase(atletaBase());

            assertThatThrownBy(() -> updater.atualizarLimiares(null, metaDados, HOJE))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("atleta sem assessoria retorna sem erro e sem atualizar metaDados")
        void assessoriaNulaRetornaSemAtualizar() {
            Atleta atleta = atletaBase();
            atleta.setAssessoria(null);
            atleta.setFcLimiar(null);
            atleta.setDataUltimoTesteFc(null);
            PlanoMetaDados metaDados = metaDadosBase(atleta);

            when(thresholdInferenceService.isFcLimiarDesatualizado(atleta, HOJE)).thenReturn(true);
            when(thresholdInferenceService.isPaceLimiarDesatualizado(atleta, HOJE)).thenReturn(false);

            updater.atualizarLimiares(atleta, metaDados, HOJE);

            assertThat(metaDados.getFcLimiarEstimado()).isNull();
            verifyNoInteractions(treinoRealizadoRepository, provaRepository);
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private static Atleta atletaBase() {
        Assessoria assessoria = new Assessoria();
        assessoria.setId(UUID.randomUUID());
        return Atleta.builder()
                .id(UUID.randomUUID())
                .nome("Atleta Teste")
                .objetivo("Teste")
                .nivelExperiencia(NivelExperiencia.INTERMEDIARIO)
                .assessoria(assessoria)
                .build();
    }

    private static PlanoMetaDados metaDadosBase(Atleta atleta) {
        return PlanoMetaDados.builder()
                .atleta(atleta)
                .ctlAtual(40.0)
                .atlAtual(42.0)
                .tsbAtual(-2.0)
                .tsbProntidaoAtual(-2.0)
                .tsbPosCargaAtual(-2.0)
                .rampRateAtual(2.0)
                .diasConsecutivosTreino(0)
                .semanasProgressaoContinua(0)
                .build();
    }

    private static Prova provaBase() {
        Prova prova = new Prova();
        prova.setId(UUID.randomUUID());
        return prova;
    }

    private static List<TreinoRealizado> treinos10ComFcMedia(int fc) {
        List<TreinoRealizado> lista = new ArrayList<>();
        LocalDate base = HOJE.minusDays(5);
        for (int i = 0; i < 10; i++) {
            TreinoRealizado t = new TreinoRealizado();
            t.setDataTreino(base.minusDays(i * 2L));
            t.setFcMedia(fc + i);
            t.setDuracaoMin(Duration.ofMinutes(45));
            t.setTipoTreino(TipoTreino.CONTINUO);
            lista.add(t);
        }
        return lista;
    }
}
