package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.dto.MatchingScoreResult;
import br.com.menthoros.backend.entity.Assessoria;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.TreinoPlanejado;
import br.com.menthoros.backend.entity.TreinoReconciliacao;
import br.com.menthoros.backend.entity.TreinoRealizado;
import br.com.menthoros.backend.enums.ReconciliationStatus;
import br.com.menthoros.backend.enums.StatusSincronizacao;
import br.com.menthoros.backend.enums.TreinoExecucaoStatus;
import br.com.menthoros.backend.repository.TreinoPlanejadoRepository;
import br.com.menthoros.backend.repository.TreinoReconciliacaoRepository;
import br.com.menthoros.backend.repository.TreinoRealizadoRepository;
import br.com.menthoros.backend.services.MatchingDecisionEngine;
import br.com.menthoros.backend.services.MatchingScoreCalculator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReconciliationDecisionExecutorTest {

    @Mock
    private MatchingScoreCalculator matchingScoreCalculator;
    @Mock
    private MatchingDecisionEngine matchingDecisionEngine;
    @Mock
    private TreinoRealizadoRepository treinoRealizadoRepository;
    @Mock
    private TreinoPlanejadoRepository treinoPlanejadoRepository;
    @Mock
    private TreinoReconciliacaoRepository treinoReconciliacaoRepository;

    private ReconciliationDecisionExecutor executor;
    private Atleta atleta;

    @BeforeEach
    void setUp() {
        executor = new ReconciliationDecisionExecutor(matchingScoreCalculator, matchingDecisionEngine,
                treinoRealizadoRepository, treinoPlanejadoRepository, treinoReconciliacaoRepository);
        Assessoria assessoria = new Assessoria();
        assessoria.setId(UUID.randomUUID());
        atleta = new Atleta();
        atleta.setId(UUID.randomUUID());
        atleta.setAssessoria(assessoria);
    }

    @Nested
    @DisplayName("executar — os quatro desfechos")
    class QuatroDesfechos {

        @Test
        @DisplayName("VINCULADO_AUTOMATICO: muta o planejado, salva EXPLICITAMENTE (corrige achado pre-mortem #7), registra auditoria")
        void vinculadoAutomaticoSalvaPlanejadoExplicitamente() {
            TreinoRealizado realizado = realizadoCompleto();
            TreinoPlanejado planejado = planejadoCompleto();
            when(matchingScoreCalculator.calculate(realizado, planejado, atleta))
                    .thenReturn(scoreCompleto(new BigDecimal("0.90")));
            when(matchingDecisionEngine.decide(any(), any()))
                    .thenReturn(decisao(ReconciliationStatus.VINCULADO_AUTOMATICO, planejado, "AUTO_MATCH", "0.90"));

            executor.executar(realizado, List.of(planejado), atleta);

            assertThat(planejado.getStatusTreino()).isEqualTo(TreinoExecucaoStatus.REALIZADO);
            assertThat(planejado.getStatusSincronizacao()).isEqualTo(StatusSincronizacao.SINCRONIZADO);
            assertThat(realizado.getTreinoPlanejado()).isSameAs(planejado);
            verify(treinoPlanejadoRepository).save(planejado);
            verify(treinoRealizadoRepository).save(realizado);
            verify(treinoReconciliacaoRepository).save(any(TreinoReconciliacao.class));
        }

        @Test
        @DisplayName("VINCULADO_AUTOMATICO sobre um planejado pulado: reverte o pulo (motivo e carimbo saem)")
        void vinculadoAutomaticoRevertePulo() {
            TreinoRealizado realizado = realizadoCompleto();
            TreinoPlanejado planejado = planejadoCompleto();
            planejado.setStatusTreino(TreinoExecucaoStatus.PERDIDO);
            planejado.setMotivoPulo(br.com.menthoros.backend.enums.MotivoPulo.DOR);
            planejado.setPuladoEm(java.time.LocalDateTime.of(2026, 7, 16, 7, 0));
            when(matchingScoreCalculator.calculate(realizado, planejado, atleta))
                    .thenReturn(scoreCompleto(new BigDecimal("0.90")));
            when(matchingDecisionEngine.decide(any(), any()))
                    .thenReturn(decisao(ReconciliationStatus.VINCULADO_AUTOMATICO, planejado, "AUTO_MATCH", "0.90"));

            executor.executar(realizado, List.of(planejado), atleta);

            assertThat(planejado.getStatusTreino()).isEqualTo(TreinoExecucaoStatus.REALIZADO);
            assertThat(planejado.getMotivoPulo()).isNull();
            assertThat(planejado.getPuladoEm()).isNull();
        }

        @Test
        @DisplayName("AMBIGUO por faixa (0.50-0.79): registra estado, NÃO mexe no planejado")
        void ambiguoPorFaixaNaoMexeNoPlanejado() {
            TreinoRealizado realizado = realizadoCompleto();
            TreinoPlanejado planejado = planejadoCompleto();
            when(matchingDecisionEngine.decide(any(), any()))
                    .thenReturn(decisao(ReconciliationStatus.AMBIGUO, planejado, "AMBIGUOUS", "0.60"));

            executor.executar(realizado, List.of(planejado), atleta);

            assertThat(realizado.getReconciliationStatus()).isEqualTo(ReconciliationStatus.AMBIGUO);
            assertThat(planejado.getStatusTreino()).isEqualTo(TreinoExecucaoStatus.PENDENTE);
            verify(treinoPlanejadoRepository, never()).save(any());
        }

        @Test
        @DisplayName("AMBIGUO por tie-break: registra estado, NÃO mexe no planejado")
        void ambiguoPorTieBreakNaoMexeNoPlanejado() {
            TreinoRealizado realizado = realizadoCompleto();
            TreinoPlanejado planejado = planejadoCompleto();
            when(matchingDecisionEngine.decide(any(), any()))
                    .thenReturn(decisao(ReconciliationStatus.AMBIGUO, planejado, "TIE_BREAK", "0.85"));

            executor.executar(realizado, List.of(planejado), atleta);

            assertThat(realizado.getReconciliationStatus()).isEqualTo(ReconciliationStatus.AMBIGUO);
            assertThat(realizado.getReconciliationReasonCode()).isEqualTo("TIE_BREAK");
            verify(treinoPlanejadoRepository, never()).save(any());
        }

        @Test
        @DisplayName("NAO_PLANEJADO: registra estado sem planejado vinculado")
        void naoPlanejadoRegistraSemVinculo() {
            TreinoRealizado realizado = realizadoCompleto();
            when(matchingDecisionEngine.decide(any(), any()))
                    .thenReturn(decisao(ReconciliationStatus.NAO_PLANEJADO, null, "ORPHANED", null));

            executor.executar(realizado, List.of(), atleta);

            assertThat(realizado.getReconciliationStatus()).isEqualTo(ReconciliationStatus.NAO_PLANEJADO);
            assertThat(realizado.getTreinoPlanejado()).isNull();
            verify(treinoPlanejadoRepository, never()).save(any());
        }

        @Test
        @DisplayName("sem candidatos na janela: chama o engine com lista vazia (mesmo comportamento do scheduler)")
        void semCandidatosChamaEngineComListaVazia() {
            TreinoRealizado realizado = realizadoCompleto();
            when(matchingDecisionEngine.decide(realizado, List.of()))
                    .thenReturn(decisao(ReconciliationStatus.NAO_PLANEJADO, null, "NO_CANDIDATES", null));

            executor.executar(realizado, List.of(), atleta);

            verify(matchingScoreCalculator, never()).calculate(any(), any(), any());
            verify(matchingDecisionEngine).decide(realizado, List.of());
        }

        @Test
        @DisplayName("auditoria gravada com beforeStatus=PENDENTE e afterStatus=decision.status")
        void auditoriaGravadaComBeforeEAfterStatus() {
            TreinoRealizado realizado = realizadoCompleto();
            when(matchingDecisionEngine.decide(any(), any()))
                    .thenReturn(decisao(ReconciliationStatus.NAO_PLANEJADO, null, "NO_CANDIDATES", null));

            executor.executar(realizado, List.of(), atleta);

            ArgumentCaptor<TreinoReconciliacao> captor = ArgumentCaptor.forClass(TreinoReconciliacao.class);
            verify(treinoReconciliacaoRepository).save(captor.capture());
            assertThat(captor.getValue().getBeforeStatus()).isEqualTo(ReconciliationStatus.PENDENTE);
            assertThat(captor.getValue().getAfterStatus()).isEqualTo(ReconciliationStatus.NAO_PLANEJADO);
        }
    }

    @Nested
    @DisplayName("guarda absoluta de campos ausentes (Bloco 3.3 — força AMBIGUO independentemente do score)")
    class GuardaCamposAusentes {

        @ParameterizedTest(name = "{0}")
        @EnumSource(CasoAusente.class)
        @DisplayName("VINCULADO_AUTOMATICO decidido pelo engine é rebaixado para AMBIGUO quando duração/distância ausentes de qualquer lado")
        void forcaAmbiguoQuandoCampoAusente(CasoAusente caso) {
            TreinoRealizado realizado = realizadoCompleto();
            TreinoPlanejado planejado = planejadoCompleto();
            caso.aplicar(realizado, planejado);

            // engine decidiu VINCULADO_AUTOMATICO com score artificialmente alto (0.80+)
            when(matchingDecisionEngine.decide(any(), any()))
                    .thenReturn(decisao(ReconciliationStatus.VINCULADO_AUTOMATICO, planejado, "AUTO_MATCH", "0.90"));

            executor.executar(realizado, List.of(planejado), atleta);

            assertThat(realizado.getReconciliationStatus())
                    .as("caso %s deve forçar AMBIGUO mesmo com score alto", caso)
                    .isEqualTo(ReconciliationStatus.AMBIGUO);
            verify(treinoPlanejadoRepository, never()).save(any());
        }

        @Test
        @DisplayName("realizado e planejado completos (nenhum campo ausente): VINCULADO_AUTOMATICO passa normalmente")
        void semCampoAusenteNaoAtivaGuarda() {
            TreinoRealizado realizado = realizadoCompleto();
            TreinoPlanejado planejado = planejadoCompleto();
            when(matchingDecisionEngine.decide(any(), any()))
                    .thenReturn(decisao(ReconciliationStatus.VINCULADO_AUTOMATICO, planejado, "AUTO_MATCH", "0.90"));

            executor.executar(realizado, List.of(planejado), atleta);

            assertThat(realizado.getReconciliationStatus()).isEqualTo(ReconciliationStatus.VINCULADO_AUTOMATICO);
            verify(treinoPlanejadoRepository).save(planejado);
        }
    }

    enum CasoAusente {
        REALIZADO_SEM_DURACAO {
            void aplicar(TreinoRealizado r, TreinoPlanejado p) { r.setDuracaoMin(Duration.ZERO); }
        },
        REALIZADO_SEM_DISTANCIA {
            void aplicar(TreinoRealizado r, TreinoPlanejado p) { r.setDistanciaKm(null); }
        },
        REALIZADO_SEM_DURACAO_E_DISTANCIA {
            void aplicar(TreinoRealizado r, TreinoPlanejado p) { r.setDuracaoMin(Duration.ZERO); r.setDistanciaKm(null); }
        },
        PLANEJADO_SEM_DURACAO {
            void aplicar(TreinoRealizado r, TreinoPlanejado p) { p.setDuracaoMin(Duration.ZERO); }
        },
        PLANEJADO_SEM_DISTANCIA {
            void aplicar(TreinoRealizado r, TreinoPlanejado p) { p.setDistanciaKm(null); }
        },
        PLANEJADO_SEM_DURACAO_E_DISTANCIA {
            void aplicar(TreinoRealizado r, TreinoPlanejado p) { p.setDuracaoMin(Duration.ZERO); p.setDistanciaKm(null); }
        };

        abstract void aplicar(TreinoRealizado r, TreinoPlanejado p);
    }

    // ---- helpers ----

    private TreinoRealizado realizadoCompleto() {
        TreinoRealizado t = new TreinoRealizado();
        t.setId(UUID.randomUUID());
        t.setAtleta(atleta);
        t.setDataTreino(LocalDate.of(2026, 7, 16));
        t.setDuracaoMin(Duration.ofMinutes(30));
        t.setDistanciaKm(BigDecimal.valueOf(5));
        return t;
    }

    private TreinoPlanejado planejadoCompleto() {
        TreinoPlanejado p = new TreinoPlanejado();
        p.setId(UUID.randomUUID());
        p.setAtleta(atleta);
        p.setDataTreino(LocalDate.of(2026, 7, 16));
        p.setDuracaoMin(Duration.ofMinutes(30));
        p.setDistanciaKm(BigDecimal.valueOf(5));
        return p;
    }

    private MatchingScoreResult scoreCompleto(BigDecimal overall) {
        return new MatchingScoreResult(overall, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE);
    }

    private br.com.menthoros.backend.dto.MatchingCandidate candidato(TreinoPlanejado planejado, String score) {
        return new br.com.menthoros.backend.dto.MatchingCandidate(planejado, scoreCompleto(new BigDecimal(score)), 1);
    }

    private br.com.menthoros.backend.dto.MatchingDecision decisao(ReconciliationStatus status, TreinoPlanejado selected,
                                                                     String reasonCode, String score) {
        List<br.com.menthoros.backend.dto.MatchingCandidate> ranked = selected != null
                ? List.of(candidato(selected, score != null ? score : "1.0"))
                : List.of();
        return new br.com.menthoros.backend.dto.MatchingDecision(status, selected, ranked, reasonCode, "razao: " + reasonCode);
    }
}
