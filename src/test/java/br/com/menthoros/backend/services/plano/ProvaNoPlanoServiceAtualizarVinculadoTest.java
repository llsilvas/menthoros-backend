package br.com.menthoros.backend.services.plano;

import br.com.menthoros.backend.entity.Assessoria;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.PlanoSemanal;
import br.com.menthoros.backend.entity.Prova;
import br.com.menthoros.backend.entity.TreinoPlanejado;
import br.com.menthoros.backend.enums.DiaSemana;
import br.com.menthoros.backend.enums.DistanciaProva;
import br.com.menthoros.backend.enums.PlanoReviewStatus;
import br.com.menthoros.backend.enums.PlanoStatus;
import br.com.menthoros.backend.enums.ProvaStatus;
import br.com.menthoros.backend.enums.TipoProva;
import br.com.menthoros.backend.enums.TipoTreino;
import br.com.menthoros.backend.enums.TreinoExecucaoStatus;
import br.com.menthoros.backend.mapper.TreinoMapper;
import br.com.menthoros.backend.mapper.TreinoMapperImpl;
import br.com.menthoros.backend.repository.PlanoSemanalRepository;
import br.com.menthoros.backend.repository.ProvaRepository;
import br.com.menthoros.backend.services.PlanoReviewService;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * D5 (prova-no-plano-semanal): {@code atualizarTreinoVinculado} — mudança só de nome/tempo
 * objetivo atualiza o treino vinculado sem reabrir a revisão do coach.
 */
@ExtendWith(MockitoExtension.class)
class ProvaNoPlanoServiceAtualizarVinculadoTest {

    @Mock private ProvaRepository provaRepository;
    @Mock private PlanoSemanalRepository planoSemanalRepository;
    @Mock private PlanoReviewService planoReviewService;
    private final TreinoMapper treinoMapper = new TreinoMapperImpl(null, null);

    private ProvaNoPlanoService service;
    private UUID tenantId;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        service = new ProvaNoPlanoService(provaRepository, treinoMapper, planoSemanalRepository, planoReviewService);
    }

    @Nested
    @DisplayName("atualizarTreinoVinculado")
    class AtualizarTreinoVinculado {

        @Test
        @DisplayName("atualiza descricao/ritmoAlvo/duracaoMin do treino vinculado sem reabrir revisão")
        void atualizaSemReabrir() {
            Atleta atleta = atletaComAssessoria();
            LocalDate dataProva = LocalDate.now().plusDays(3);
            Prova prova = provaCom(atleta, dataProva, "Novo Nome da Prova", Duration.ofHours(2));
            TreinoPlanejado vinculado = treinoVinculado(prova, dataProva, TreinoExecucaoStatus.PENDENTE);
            PlanoSemanal plano = planoCom(atleta, PlanoReviewStatus.APROVADO, vinculado);

            when(planoSemanalRepository.findSemanaAbertaParaProva(atleta.getId(), tenantId, dataProva))
                    .thenReturn(Optional.of(plano));
            when(planoSemanalRepository.save(plano)).thenReturn(plano);

            service.atualizarTreinoVinculado(prova);

            assertThat(vinculado.getDescricao()).isEqualTo("Novo Nome da Prova");
            assertThat(vinculado.getDuracaoMin()).isEqualTo(Duration.ofHours(2));
            verify(planoReviewService, never()).reabrirRevisao(any(), any(), any());
            assertThat(plano.getReviewStatus()).isEqualTo(PlanoReviewStatus.APROVADO);
        }

        @Test
        @DisplayName("treino REALIZADO não é tocado")
        void naoTocaRealizado() {
            Atleta atleta = atletaComAssessoria();
            LocalDate dataProva = LocalDate.now().plusDays(3);
            Prova prova = provaCom(atleta, dataProva, "Novo Nome", Duration.ofHours(2));
            TreinoPlanejado vinculado = treinoVinculado(prova, dataProva, TreinoExecucaoStatus.REALIZADO);
            String descricaoOriginal = vinculado.getDescricao();
            PlanoSemanal plano = planoCom(atleta, PlanoReviewStatus.APROVADO, vinculado);

            when(planoSemanalRepository.findSemanaAbertaParaProva(atleta.getId(), tenantId, dataProva))
                    .thenReturn(Optional.of(plano));

            service.atualizarTreinoVinculado(prova);

            assertThat(vinculado.getDescricao()).isEqualTo(descricaoOriginal);
            verify(planoSemanalRepository, never()).save(any());
        }

        @Test
        @DisplayName("sem semana aberta: no-op")
        void semSemanaNoOp() {
            Atleta atleta = atletaComAssessoria();
            LocalDate dataProva = LocalDate.now().plusDays(3);
            Prova prova = provaCom(atleta, dataProva, "Novo Nome", Duration.ofHours(2));

            when(planoSemanalRepository.findSemanaAbertaParaProva(atleta.getId(), tenantId, dataProva))
                    .thenReturn(Optional.empty());

            service.atualizarTreinoVinculado(prova);

            verify(planoSemanalRepository, never()).save(any());
        }
    }

    // ---- helpers ----

    private Atleta atletaComAssessoria() {
        Assessoria assessoria = new Assessoria();
        assessoria.setId(tenantId);
        Atleta atleta = new Atleta();
        atleta.setId(UUID.randomUUID());
        atleta.setAssessoria(assessoria);
        return atleta;
    }

    private Prova provaCom(Atleta atleta, LocalDate dataProva, String nome, Duration tempoObjetivo) {
        Prova prova = Prova.builder()
                .id(UUID.randomUUID())
                .nomeProva(nome)
                .dataProva(dataProva)
                .distancia(DistanciaProva.KM_10)
                .distanciaKm(BigDecimal.valueOf(10.0))
                .tipoProva(TipoProva.CORRIDA_RUA)
                .statusProva(ProvaStatus.PLANEJADA)
                .tempoObjetivo(tempoObjetivo)
                .build();
        prova.setAtleta(atleta);
        prova.setAssessoria(atleta.getAssessoria());
        return prova;
    }

    private TreinoPlanejado treinoVinculado(Prova prova, LocalDate dataTreino, TreinoExecucaoStatus status) {
        TreinoPlanejado treino = new TreinoPlanejado();
        treino.setDataTreino(dataTreino);
        treino.setDiaSemana(DiaSemana.SEGUNDA);
        treino.setTipoTreino(TipoTreino.PROVA);
        treino.setStatusTreino(status);
        treino.setDuracaoMin(Duration.ofMinutes(50));
        treino.setDistanciaKm(BigDecimal.valueOf(10.0));
        treino.setDescricao("Nome antigo");
        treino.setProva(prova);
        return treino;
    }

    private PlanoSemanal planoCom(Atleta atleta, PlanoReviewStatus reviewStatus, TreinoPlanejado... treinos) {
        PlanoSemanal plano = new PlanoSemanal();
        plano.setId(UUID.randomUUID());
        plano.setAtleta(atleta);
        plano.setAssessoria(atleta.getAssessoria());
        plano.setStatus(PlanoStatus.EM_ANDAMENTO);
        plano.setReviewStatus(reviewStatus);
        plano.setSemanaInicio(LocalDate.now().minusDays(1));
        plano.setSemanaFim(LocalDate.now().plusDays(6));
        plano.setVolumePlanejadoKm(BigDecimal.valueOf(50));
        List<TreinoPlanejado> lista = new ArrayList<>(List.of(treinos));
        plano.setTreinosPlanejados(lista);
        return plano;
    }
}
