package br.com.menthoros.backend.services.plano;

import br.com.menthoros.backend.entity.Assessoria;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.PlanoSemanal;
import br.com.menthoros.backend.entity.Prova;
import br.com.menthoros.backend.entity.TreinoPlanejado;
import br.com.menthoros.backend.enums.DiaSemana;
import br.com.menthoros.backend.enums.DistanciaProva;
import br.com.menthoros.backend.enums.MotivoReaberturaRevisao;
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
 * D5 (prova-no-plano-semanal): {@code removerProvaDeSemanaExistente} — prova cancelada ou movida
 * para fora da semana remove só o treino PROVA vinculado a ela, nunca um já REALIZADO.
 */
@ExtendWith(MockitoExtension.class)
class ProvaNoPlanoServiceRemoverTest {

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
    @DisplayName("removerProvaDeSemanaExistente")
    class RemoverProvaDeSemanaExistente {

        @Test
        @DisplayName("remove o PROVA pendente e reabre a revisão (plano estava aprovado)")
        void removeEReabre() {
            Atleta atleta = atletaComAssessoria();
            Prova prova = provaCom(atleta);
            LocalDate dataAntiga = LocalDate.now().plusDays(3);
            TreinoPlanejado vinculado = treinoVinculado(prova, dataAntiga, TreinoExecucaoStatus.PENDENTE);
            PlanoSemanal plano = planoCom(atleta, PlanoReviewStatus.APROVADO, vinculado);

            when(planoSemanalRepository.findSemanaAbertaParaProva(atleta.getId(), tenantId, dataAntiga))
                    .thenReturn(Optional.of(plano));

            Optional<PlanoSemanal> resultado = service.removerProvaDeSemanaExistente(prova, dataAntiga);

            assertThat(resultado).isPresent();
            assertThat(plano.getTreinosPlanejados()).doesNotContain(vinculado);
            verify(planoReviewService).reabrirRevisao(plano, MotivoReaberturaRevisao.PROVA_REMOVIDA, tenantId);
        }

        @Test
        @DisplayName("remove o PROVA PERDIDO (não só PENDENTE)")
        void removePerdido() {
            Atleta atleta = atletaComAssessoria();
            Prova prova = provaCom(atleta);
            LocalDate dataAntiga = LocalDate.now().plusDays(3);
            TreinoPlanejado vinculado = treinoVinculado(prova, dataAntiga, TreinoExecucaoStatus.PERDIDO);
            PlanoSemanal plano = planoCom(atleta, PlanoReviewStatus.AGUARDANDO_REVISAO, vinculado);

            when(planoSemanalRepository.findSemanaAbertaParaProva(atleta.getId(), tenantId, dataAntiga))
                    .thenReturn(Optional.of(plano));
            when(planoSemanalRepository.save(plano)).thenReturn(plano);

            service.removerProvaDeSemanaExistente(prova, dataAntiga);

            assertThat(plano.getTreinosPlanejados()).doesNotContain(vinculado);
        }

        @Test
        @DisplayName("treino REALIZADO não é removido e o plano não muda")
        void naoRemoveRealizado() {
            Atleta atleta = atletaComAssessoria();
            Prova prova = provaCom(atleta);
            LocalDate dataAntiga = LocalDate.now().plusDays(3);
            TreinoPlanejado vinculado = treinoVinculado(prova, dataAntiga, TreinoExecucaoStatus.REALIZADO);
            PlanoSemanal plano = planoCom(atleta, PlanoReviewStatus.APROVADO, vinculado);
            BigDecimal volumeAntes = plano.getVolumePlanejadoKm();

            when(planoSemanalRepository.findSemanaAbertaParaProva(atleta.getId(), tenantId, dataAntiga))
                    .thenReturn(Optional.of(plano));

            service.removerProvaDeSemanaExistente(prova, dataAntiga);

            assertThat(plano.getTreinosPlanejados()).contains(vinculado);
            assertThat(plano.getVolumePlanejadoKm()).isEqualByComparingTo(volumeAntes);
            assertThat(plano.getReviewStatus()).isEqualTo(PlanoReviewStatus.APROVADO);
            verify(planoReviewService, never()).reabrirRevisao(any(), any(), any());
            verify(planoSemanalRepository, never()).save(any());
        }

        @Test
        @DisplayName("outros treinos do dia continuam intactos")
        void outrosTreinosIntactos() {
            Atleta atleta = atletaComAssessoria();
            Prova prova = provaCom(atleta);
            LocalDate dataAntiga = LocalDate.now().plusDays(3);
            TreinoPlanejado vinculado = treinoVinculado(prova, dataAntiga, TreinoExecucaoStatus.PENDENTE);
            TreinoPlanejado outroTreino = new TreinoPlanejado();
            outroTreino.setDataTreino(dataAntiga);
            outroTreino.setDiaSemana(DiaSemana.SEGUNDA);
            outroTreino.setTipoTreino(TipoTreino.CONTINUO);
            outroTreino.setStatusTreino(TreinoExecucaoStatus.PENDENTE);
            outroTreino.setDuracaoMin(Duration.ofMinutes(40));
            outroTreino.setDistanciaKm(BigDecimal.valueOf(6.0));

            PlanoSemanal plano = planoCom(atleta, PlanoReviewStatus.AGUARDANDO_REVISAO, vinculado, outroTreino);

            when(planoSemanalRepository.findSemanaAbertaParaProva(atleta.getId(), tenantId, dataAntiga))
                    .thenReturn(Optional.of(plano));
            when(planoSemanalRepository.save(plano)).thenReturn(plano);

            service.removerProvaDeSemanaExistente(prova, dataAntiga);

            assertThat(plano.getTreinosPlanejados()).containsExactly(outroTreino);
        }

        @Test
        @DisplayName("sem semana aberta na data antiga: no-op")
        void semSemanaNoOp() {
            Atleta atleta = atletaComAssessoria();
            Prova prova = provaCom(atleta);
            LocalDate dataAntiga = LocalDate.now().plusDays(3);

            when(planoSemanalRepository.findSemanaAbertaParaProva(atleta.getId(), tenantId, dataAntiga))
                    .thenReturn(Optional.empty());

            Optional<PlanoSemanal> resultado = service.removerProvaDeSemanaExistente(prova, dataAntiga);

            assertThat(resultado).isEmpty();
            verify(planoReviewService, never()).reabrirRevisao(any(), any(), any());
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

    private Prova provaCom(Atleta atleta) {
        Prova prova = Prova.builder()
                .id(UUID.randomUUID())
                .nomeProva("Prova de teste")
                .dataProva(LocalDate.now().plusDays(3))
                .distancia(DistanciaProva.KM_10)
                .distanciaKm(BigDecimal.valueOf(10.0))
                .tipoProva(TipoProva.CORRIDA_RUA)
                .statusProva(ProvaStatus.CANCELADA)
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
