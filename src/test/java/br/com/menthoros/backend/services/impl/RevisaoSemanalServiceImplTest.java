package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.entity.Assessoria;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.PlanoSemanal;
import br.com.menthoros.backend.entity.RevisaoSemanal;
import br.com.menthoros.backend.entity.TreinoPlanejado;
import br.com.menthoros.backend.entity.TreinoRealizado;
import br.com.menthoros.backend.enums.NivelAderencia;
import br.com.menthoros.backend.enums.RecommendationType;
import br.com.menthoros.backend.enums.TipoTreino;
import br.com.menthoros.backend.repository.PlanoSemanalRepository;
import br.com.menthoros.backend.repository.RevisaoSemanalRepository;
import br.com.menthoros.backend.repository.TreinoPlanejadoRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Consolidação determinística (bloco 2): conta aderência na janela do plano e delega a decisão
 * ao calculador. O repositório é mockado — a lógica de decisão exaustiva vive em
 * {@code RevisaoSemanalCalculatorTest}.
 */
@ExtendWith(MockitoExtension.class)
class RevisaoSemanalServiceImplTest {

    @Mock
    private TreinoPlanejadoRepository treinoPlanejadoRepository;
    @Mock
    private RevisaoSemanalRepository revisaoSemanalRepository;
    @Mock
    private PlanoSemanalRepository planoSemanalRepository;

    @InjectMocks
    private RevisaoSemanalServiceImpl service;

    @Nested
    @DisplayName("consolidar")
    class Consolidar {

        @Test
        @DisplayName("conta realizados/planejados na janela → MEDIA + MAINTAIN")
        void mediaMaintain() {
            PlanoSemanal plano = plano(new BigDecimal("-5"));
            mockTreinos(List.of(
                    realizado(TipoTreino.FACIL), realizado(TipoTreino.FACIL),
                    realizado(TipoTreino.FACIL), naoRealizado(TipoTreino.FACIL))); // 3/4 = 75%

            RevisaoSemanal r = service.consolidar(plano);

            assertThat(r.getPercentualRealizacao()).isEqualByComparingTo("75.00");
            assertThat(r.getAdherenceStatus()).isEqualTo(NivelAderencia.MEDIA);
            assertThat(r.isDadosSuficientes()).isTrue();
            assertThat(r.getRecommendationType()).isEqualTo(RecommendationType.MAINTAIN);
            assertThat(r.getPlanoSemanal()).isSameAs(plano);
            assertThat(r.getGeradaEm()).isNotNull();
        }

        @Test
        @DisplayName("treino crítico não realizado (LONGO) força BAIXA mesmo com % ok")
        void criticoFaltandoForcaBaixa() {
            PlanoSemanal plano = plano(new BigDecimal("-5"));
            mockTreinos(List.of(
                    realizado(TipoTreino.FACIL), realizado(TipoTreino.FACIL),
                    naoRealizado(TipoTreino.LONGO))); // 2/3 = 66.67%, mas LONGO (1.15) faltando

            RevisaoSemanal r = service.consolidar(plano);

            assertThat(r.getAdherenceStatus()).isEqualTo(NivelAderencia.BAIXA);
            assertThat(r.getRecommendationType()).isEqualTo(RecommendationType.MAINTAIN);
        }

        @Test
        @DisplayName("100% aderência + TSB saudável → PROGRESS")
        void semanaBoaProgress() {
            PlanoSemanal plano = plano(new BigDecimal("0"));
            mockTreinos(List.of(
                    realizado(TipoTreino.FACIL), realizado(TipoTreino.FACIL), realizado(TipoTreino.FACIL)));

            RevisaoSemanal r = service.consolidar(plano);

            assertThat(r.getPercentualRealizacao()).isEqualByComparingTo("100.00");
            assertThat(r.getAdherenceStatus()).isEqualTo(NivelAderencia.ALTA);
            assertThat(r.getRecommendationType()).isEqualTo(RecommendationType.PROGRESS);
        }

        @Test
        @DisplayName("tsbFim nulo → dadosSuficientes false e MAINTAIN")
        void tsbNuloMaintain() {
            PlanoSemanal plano = plano(null);
            mockTreinos(List.of(
                    realizado(TipoTreino.FACIL), realizado(TipoTreino.FACIL), realizado(TipoTreino.FACIL)));

            RevisaoSemanal r = service.consolidar(plano);

            assertThat(r.isDadosSuficientes()).isFalse();
            assertThat(r.getRecommendationType()).isEqualTo(RecommendationType.MAINTAIN);
        }

        @Test
        @DisplayName("recorta a janela — treino após semanaFim (vazado da query sem limite) é ignorado")
        void recortaJanela() {
            PlanoSemanal plano = plano(new BigDecimal("-5")); // janela [now-6, now]
            mockTreinos(List.of(
                    realizado(TipoTreino.FACIL),   // dentro da janela
                    realizado(TipoTreino.FACIL),   // dentro da janela
                    treino(TipoTreino.FACIL, false, LocalDate.now().plusDays(3)))); // futuro → ignorado

            RevisaoSemanal r = service.consolidar(plano);

            // só os 2 dentro da janela contam (ambos realizados) → 100%, não 66,67%
            assertThat(r.getPercentualRealizacao()).isEqualByComparingTo("100.00");
            assertThat(r.getAdherenceStatus()).isEqualTo(NivelAderencia.ALTA);
        }
    }

    // ---- helpers ----

    private void mockTreinos(List<TreinoPlanejado> treinos) {
        when(treinoPlanejadoRepository.findComRealizadoByAtletaAndPeriodo(any(), any(), any()))
                .thenReturn(treinos);
    }

    private PlanoSemanal plano(BigDecimal tsbFim) {
        Assessoria assessoria = new Assessoria();
        Atleta atleta = new Atleta();
        atleta.setAssessoria(assessoria);
        PlanoSemanal plano = new PlanoSemanal();
        plano.setAtleta(atleta);
        plano.setAssessoria(assessoria);
        plano.setSemanaInicio(LocalDate.now().minusDays(6));
        plano.setSemanaFim(LocalDate.now());
        plano.setTsbFim(tsbFim);
        return plano;
    }

    private TreinoPlanejado realizado(TipoTreino tipo) {
        return treino(tipo, true, LocalDate.now().minusDays(3)); // dentro da janela [now-6, now]
    }

    private TreinoPlanejado naoRealizado(TipoTreino tipo) {
        return treino(tipo, false, LocalDate.now().minusDays(3));
    }

    private TreinoPlanejado treino(TipoTreino tipo, boolean realizado, LocalDate dataTreino) {
        TreinoPlanejado t = new TreinoPlanejado();
        t.setTipoTreino(tipo);
        t.setDataTreino(dataTreino);
        if (realizado) {
            t.setTreinoRealizado(new TreinoRealizado());
        }
        return t;
    }
}
