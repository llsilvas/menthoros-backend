package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.dto.output.ResultadoAnalise;
import br.com.menthoros.backend.entity.Assessoria;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.PlanoMetaDados;
import br.com.menthoros.backend.entity.TreinoRealizado;
import br.com.menthoros.backend.enums.ConfiancaInferencia;
import br.com.menthoros.backend.enums.NivelExperiencia;
import br.com.menthoros.backend.enums.TipoTreino;
import br.com.menthoros.backend.repository.AtletaRepository;
import br.com.menthoros.backend.repository.MetricasDiariasRepository;
import br.com.menthoros.backend.repository.PlanoMetadadosRepository;
import br.com.menthoros.backend.repository.TreinoRealizadoRepository;
import br.com.menthoros.backend.services.helper.ThresholdEstimate;
import br.com.menthoros.backend.services.helper.ThresholdInferenceService;
import br.com.menthoros.backend.services.helper.TssCalculatorService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Testes de integração do método privado atualizarLimiareInferidos em TsbServiceImpl.
 * Acesso via reflection — foco no comportamento de staleness, inferência e CA5.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TsbServiceImpl — atualizarLimiareInferidos")
class TsbServiceImplAtualizarLimiaresTest {

    @Mock private TreinoRealizadoRepository treinoRealizadoRepository;
    @Mock private PlanoMetadadosRepository planoMetadadosRepository;
    @Mock private MetricasDiariasRepository metricasDiariasRepository;
    @Mock private AtletaRepository atletaRepository;
    @Mock private TssCalculatorService tssCalculatorService;
    @Mock private MetricasAlertaService metricasAlertaService;
    @Mock private ThresholdInferenceService thresholdInferenceService;

    @InjectMocks
    private TsbServiceImpl service;

    private static final LocalDate HOJE = LocalDate.of(2026, 6, 22);

    // =========================================================================

    @Nested
    @DisplayName("inferência de FC")
    class InferirFcLimiar {

        @Test
        @DisplayName("seta fcLimiarEstimado quando FC oficial ausente e inferência retorna valor")
        void setaFcEstimadoQuandoLimiarAusenteEInferenciaRetornaValor() throws Exception {
            Atleta atleta = atletaBase();
            atleta.setFcLimiar(null);
            atleta.setDataUltimoTesteFc(null);
            PlanoMetaDados metaDados = metaDadosBase(atleta);

            List<TreinoRealizado> treinos = treinos10ComFcMedia(163);
            when(treinoRealizadoRepository.findByAtletaIdAndDataTreinoBetween(any(), any(), any()))
                    .thenReturn(treinos);
            when(thresholdInferenceService.inferirFcLimiar(treinos, HOJE))
                    .thenReturn(Optional.of(new ThresholdEstimate(163, 10, ConfiancaInferencia.ALTA)));
            when(thresholdInferenceService.inferirPaceLimiar(treinos, HOJE))
                    .thenReturn(Optional.empty());

            invocarAtualizarLimiares(atleta, metaDados, HOJE);

            assertThat(metaDados.getFcLimiarEstimado()).isEqualTo(163);
            assertThat(metaDados.getConfiancaInferenciaFc()).isEqualTo(ConfiancaInferencia.ALTA);
            assertThat(metaDados.getDataInferenciaLimiar()).isEqualTo(HOJE);
        }

        @Test
        @DisplayName("não seta fcLimiarEstimado quando FC oficial tem teste recente (< 90 dias)")
        void naoSetaFcEstimadoQuandoTesteRecente() throws Exception {
            Atleta atleta = atletaBase();
            atleta.setFcLimiar(165);
            atleta.setDataUltimoTesteFc(HOJE.minusDays(30));
            atleta.setPaceLimiar(new BigDecimal("5.00"));
            atleta.setDataUltimoTestePace(HOJE.minusDays(30));
            PlanoMetaDados metaDados = metaDadosBase(atleta);

            invocarAtualizarLimiares(atleta, metaDados, HOJE);

            assertThat(metaDados.getFcLimiarEstimado()).isNull();
            assertThat(metaDados.getDataInferenciaLimiar()).isNull();
        }

        @Test
        @DisplayName("não seta fcLimiarEstimado quando inferência retorna vazio (< MIN_AMOSTRAS)")
        void naoSetaFcEstimadoQuandoAmostraInsuficiente() throws Exception {
            Atleta atleta = atletaBase();
            atleta.setFcLimiar(null);
            atleta.setDataUltimoTesteFc(null);
            atleta.setPaceLimiar(null);
            atleta.setDataUltimoTestePace(null);
            PlanoMetaDados metaDados = metaDadosBase(atleta);

            when(treinoRealizadoRepository.findByAtletaIdAndDataTreinoBetween(any(), any(), any()))
                    .thenReturn(List.of());
            when(thresholdInferenceService.inferirFcLimiar(List.of(), HOJE))
                    .thenReturn(Optional.empty());
            when(thresholdInferenceService.inferirPaceLimiar(List.of(), HOJE))
                    .thenReturn(Optional.empty());

            invocarAtualizarLimiares(atleta, metaDados, HOJE);

            assertThat(metaDados.getFcLimiarEstimado()).isNull();
        }

        @Test
        @DisplayName("não sobrescreve atleta.fcLimiar nem atleta.paceLimiar (CA5)")
        void naoAlteraLimiresOficiaisDoAtleta() throws Exception {
            Atleta atleta = atletaBase();
            atleta.setFcLimiar(null);
            atleta.setDataUltimoTesteFc(null);
            atleta.setPaceLimiar(null);
            atleta.setDataUltimoTestePace(null);
            Integer fcOriginal = atleta.getFcLimiar();
            BigDecimal paceOriginal = atleta.getPaceLimiar();
            PlanoMetaDados metaDados = metaDadosBase(atleta);

            List<TreinoRealizado> treinos = treinos10ComFcMedia(163);
            when(treinoRealizadoRepository.findByAtletaIdAndDataTreinoBetween(any(), any(), any()))
                    .thenReturn(treinos);
            when(thresholdInferenceService.inferirFcLimiar(treinos, HOJE))
                    .thenReturn(Optional.of(new ThresholdEstimate(163, 10, ConfiancaInferencia.ALTA)));
            when(thresholdInferenceService.inferirPaceLimiar(treinos, HOJE))
                    .thenReturn(Optional.of(new ThresholdEstimate(new BigDecimal("4.7500"), 8, ConfiancaInferencia.MEDIA)));

            invocarAtualizarLimiares(atleta, metaDados, HOJE);

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
        void setaPaceEstimadoQuandoLimiarAusenteEInferenciaRetornaValor() throws Exception {
            Atleta atleta = atletaBase();
            atleta.setFcLimiar(165);
            atleta.setDataUltimoTesteFc(HOJE.minusDays(30));
            atleta.setPaceLimiar(null);
            atleta.setDataUltimoTestePace(null);
            PlanoMetaDados metaDados = metaDadosBase(atleta);

            List<TreinoRealizado> treinos = treinos10ComFcMedia(150);
            BigDecimal paceEstimado = new BigDecimal("4.7500");
            when(treinoRealizadoRepository.findByAtletaIdAndDataTreinoBetween(any(), any(), any()))
                    .thenReturn(treinos);
            when(thresholdInferenceService.inferirPaceLimiar(treinos, HOJE))
                    .thenReturn(Optional.of(new ThresholdEstimate(paceEstimado, 6, ConfiancaInferencia.MEDIA)));

            invocarAtualizarLimiares(atleta, metaDados, HOJE);

            assertThat(metaDados.getPaceLimiarEstimado()).isEqualByComparingTo(paceEstimado);
            assertThat(metaDados.getConfiancaInferenciaPace()).isEqualTo(ConfiancaInferencia.MEDIA);
            assertThat(metaDados.getDataInferenciaLimiar()).isEqualTo(HOJE);
        }

        @Test
        @DisplayName("não seta paceLimiarEstimado quando pace oficial tem teste recente (< 90 dias)")
        void naoSetaPaceEstimadoQuandoTesteRecente() throws Exception {
            Atleta atleta = atletaBase();
            atleta.setFcLimiar(165);
            atleta.setDataUltimoTesteFc(HOJE.minusDays(30));
            atleta.setPaceLimiar(new BigDecimal("5.00"));
            atleta.setDataUltimoTestePace(HOJE.minusDays(45));
            PlanoMetaDados metaDados = metaDadosBase(atleta);

            invocarAtualizarLimiares(atleta, metaDados, HOJE);

            assertThat(metaDados.getPaceLimiarEstimado()).isNull();
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private void invocarAtualizarLimiares(Atleta atleta, PlanoMetaDados metaDados, LocalDate hoje)
            throws Exception {
        Method m = TsbServiceImpl.class.getDeclaredMethod(
                "atualizarLimiareInferidos", UUID.class, Atleta.class, PlanoMetaDados.class, LocalDate.class);
        m.setAccessible(true);
        m.invoke(service, atleta.getId(), atleta, metaDados, hoje);
    }

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
