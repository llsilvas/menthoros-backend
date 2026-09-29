package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.dto.MatchingDecision;
import br.com.menthoros.backend.entity.Assessoria;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.TreinoPlanejado;
import br.com.menthoros.backend.entity.TreinoRealizado;
import br.com.menthoros.backend.enums.ReconciliationStatus;
import br.com.menthoros.backend.enums.TipoTreino;
import br.com.menthoros.backend.enums.TreinoExecucaoStatus;
import br.com.menthoros.backend.repository.TreinoPlanejadoRepository;
import br.com.menthoros.backend.repository.TreinoReconciliacaoRepository;
import br.com.menthoros.backend.repository.TreinoRealizadoRepository;
import br.com.menthoros.backend.services.ActivityTypeCompatibilityMatrix;
import br.com.menthoros.backend.services.impl.MatchingDecisionEngineImpl;
import br.com.menthoros.backend.services.impl.MatchingScoreCalculatorImpl;
import br.com.menthoros.backend.services.plano.ProvaResultadoSyncer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.when;

/**
 * Caso real de 2026-09-29 (fix-reconciliation-exclude-realized-candidates) com seletor, score e
 * motor de decisão REAIS — só os repositórios são mockados. Realizado CONTINUO 6,07 km / 40 min;
 * planejado do dia REGENERATIVO 6,1 km / 50 min (score 0,90); planejado da véspera CONTINUO
 * 5 km / 40 min (score 0,86). Diferença 0,04 < 0,10: o empate só é legítimo se a véspera estiver livre.
 */
@ExtendWith(MockitoExtension.class)
class ReconciliacaoCandidatoOcupadoTest {

    private static final LocalDate HOJE = LocalDate.of(2026, 9, 29);

    @Mock private TreinoPlanejadoRepository treinoPlanejadoRepository;
    @Mock private TreinoRealizadoRepository treinoRealizadoRepository;
    @Mock private TreinoReconciliacaoRepository treinoReconciliacaoRepository;
    @Mock private ActivityTypeCompatibilityMatrix activityTypeCompatibilityMatrix;
    @Mock private ProvaResultadoSyncer provaResultadoSyncer;

    private CandidateSelector selector;
    private ReconciliationDecisionExecutor executor;
    private Atleta atleta;
    private UUID tenantId;
    private TreinoRealizado realizado;
    private TreinoPlanejado vespera;
    private TreinoPlanejado doDia;

    @BeforeEach
    void setUp() {
        selector = new CandidateSelector(treinoPlanejadoRepository, activityTypeCompatibilityMatrix, treinoRealizadoRepository);
        executor = new ReconciliationDecisionExecutor(new MatchingScoreCalculatorImpl(), new MatchingDecisionEngineImpl(),
                treinoRealizadoRepository, treinoPlanejadoRepository, treinoReconciliacaoRepository, provaResultadoSyncer);

        tenantId = UUID.randomUUID();
        Assessoria assessoria = new Assessoria();
        assessoria.setId(tenantId);
        atleta = new Atleta();
        atleta.setId(UUID.randomUUID());
        atleta.setAssessoria(assessoria);

        realizado = new TreinoRealizado();
        realizado.setId(UUID.randomUUID());
        realizado.setAtleta(atleta);
        realizado.setDataTreino(HOJE);
        realizado.setTipoTreino(TipoTreino.CONTINUO);
        realizado.setDistanciaKm(new BigDecimal("6.07"));
        realizado.setDuracaoMin(Duration.ofSeconds(2433));

        vespera = planejado(HOJE.minusDays(1), TipoTreino.CONTINUO, "5.00", 40);
        vespera.setStatusTreino(TreinoExecucaoStatus.REALIZADO);
        doDia = planejado(HOJE, TipoTreino.REGENERATIVO, "6.10", 50);

        when(treinoPlanejadoRepository.findByAtletaIdAndDataBetween(any(), any(), any()))
                .thenReturn(List.of(vespera, doDia));
        when(activityTypeCompatibilityMatrix.isCompatible(any(), any())).thenReturn(true);
    }

    @Nested
    @DisplayName("executar sobre os candidatos do seletor")
    class Executar {

        @Test
        @DisplayName("véspera já vinculada a outro realizado: vincula automaticamente ao planejado do dia com 0,90")
        void vesperaOcupadaVinculaAoDoDia() {
            when(treinoRealizadoRepository.findPlanejadoIdsVinculadosAOutroRealizado(anyCollection(), any()))
                    .thenReturn(Set.of(vespera.getId()));

            MatchingDecision decisao = executor.executar(realizado, selector.buscarCandidatos(realizado, tenantId), atleta);

            assertThat(decisao.getStatus()).isEqualTo(ReconciliationStatus.VINCULADO_AUTOMATICO);
            assertThat(decisao.getReasonCode()).isEqualTo("AUTO_MATCH");
            assertThat(decisao.getSelectedScore()).isEqualByComparingTo("0.90");
            assertThat(realizado.getTreinoPlanejado()).isSameAs(doDia);
            assertThat(doDia.getStatusTreino()).isEqualTo(TreinoExecucaoStatus.REALIZADO);
        }

        @Test
        @DisplayName("véspera livre: os dois concorrem e o empate (0,90 vs 0,86) continua indo para revisão manual")
        void vesperaLivreContinuaEmpate() {
            when(treinoRealizadoRepository.findPlanejadoIdsVinculadosAOutroRealizado(anyCollection(), any()))
                    .thenReturn(Set.of());

            MatchingDecision decisao = executor.executar(realizado, selector.buscarCandidatos(realizado, tenantId), atleta);

            assertThat(decisao.getStatus()).isEqualTo(ReconciliationStatus.AMBIGUO);
            assertThat(decisao.getReasonCode()).isEqualTo("TIE_BREAK");
            assertThat(realizado.getTreinoPlanejado()).isNull();
        }
    }

    private TreinoPlanejado planejado(LocalDate data, TipoTreino tipo, String km, int minutos) {
        TreinoPlanejado p = new TreinoPlanejado();
        p.setId(UUID.randomUUID());
        p.setAtleta(atleta);
        p.setDataTreino(data);
        p.setTipoTreino(tipo);
        p.setDistanciaKm(new BigDecimal(km));
        p.setDuracaoMin(Duration.ofMinutes(minutos));
        p.setStatusTreino(TreinoExecucaoStatus.PENDENTE);
        return p;
    }
}
