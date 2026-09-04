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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * D5 (prova-no-plano-semanal): {@code aplicarProvaEmSemanaExistente} — a exceção deliberada a
 * "prova não altera plano existente": insere o treino PROVA no dia, recalcula o volume e reabre
 * a revisão quando o plano já estava aprovado.
 */
@ExtendWith(MockitoExtension.class)
class ProvaNoPlanoServiceAplicarTest {

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
    @DisplayName("aplicarProvaEmSemanaExistente")
    class AplicarProvaEmSemanaExistente {

        @Test
        @DisplayName("semana aprovada: substitui o treino do dia e reabre a revisão")
        void semanaAprovadaSubstitueReabre() {
            Atleta atleta = atletaComAssessoria();
            Prova prova = provaCom(atleta, LocalDate.now().plusDays(3), BigDecimal.valueOf(10.0));
            TreinoPlanejado longoNoDia = treinoPendente(prova.getDataProva(), TipoTreino.LONGO, 15.0);
            PlanoSemanal plano = planoCom(atleta, PlanoReviewStatus.APROVADO, PlanoStatus.EM_ANDAMENTO, longoNoDia);

            when(planoSemanalRepository.findSemanaAbertaParaProva(atleta.getId(), tenantId, prova.getDataProva()))
                    .thenReturn(Optional.of(plano));

            Optional<PlanoSemanal> resultado = service.aplicarProvaEmSemanaExistente(prova);

            assertThat(resultado).isPresent();
            assertThat(plano.getTreinosPlanejados()).hasSize(1);
            assertThat(plano.getTreinosPlanejados().getFirst().getTipoTreino()).isEqualTo(TipoTreino.PROVA);
            assertThat(plano.getVolumePlanejadoKm()).isEqualByComparingTo(BigDecimal.valueOf(10.0));

            verify(planoReviewService).reabrirRevisao(plano, MotivoReaberturaRevisao.PROVA_INSERIDA, tenantId);
        }

        @Test
        @DisplayName("semana AGUARDANDO_REVISAO nunca aprovada: substitui sem mudar o status")
        void semanaAguardandoNuncaAprovadaSubstituiSemMudarStatus() {
            Atleta atleta = atletaComAssessoria();
            Prova prova = provaCom(atleta, LocalDate.now().plusDays(3), BigDecimal.valueOf(10.0));
            PlanoSemanal plano = planoCom(atleta, PlanoReviewStatus.AGUARDANDO_REVISAO, PlanoStatus.PLANEJADO);

            when(planoSemanalRepository.findSemanaAbertaParaProva(atleta.getId(), tenantId, prova.getDataProva()))
                    .thenReturn(Optional.of(plano));
            when(planoSemanalRepository.save(plano)).thenReturn(plano);

            service.aplicarProvaEmSemanaExistente(prova);

            assertThat(plano.getReviewStatus()).isEqualTo(PlanoReviewStatus.AGUARDANDO_REVISAO);
            assertThat(plano.getTreinosPlanejados()).hasSize(1);
            verify(planoReviewService, never()).reabrirRevisao(any(), any(), any());
            verify(planoSemanalRepository).save(plano);
        }

        @Test
        @DisplayName("mantém o treino PROVA de outra prova no mesmo dia (caso raro, sem regra especial)")
        void mantemProvaDeOutraProvaNoMesmoDia() {
            Atleta atleta = atletaComAssessoria();
            Prova prova = provaCom(atleta, LocalDate.now().plusDays(3), BigDecimal.valueOf(10.0));
            Prova outraProva = provaCom(atleta, prova.getDataProva(), BigDecimal.valueOf(21.1));
            TreinoPlanejado treinoOutraProva = treinoPendente(prova.getDataProva(), TipoTreino.PROVA, 21.1);
            treinoOutraProva.setProva(outraProva);
            PlanoSemanal plano = planoCom(atleta, PlanoReviewStatus.AGUARDANDO_REVISAO, PlanoStatus.PLANEJADO, treinoOutraProva);

            when(planoSemanalRepository.findSemanaAbertaParaProva(atleta.getId(), tenantId, prova.getDataProva()))
                    .thenReturn(Optional.of(plano));
            when(planoSemanalRepository.save(plano)).thenReturn(plano);

            service.aplicarProvaEmSemanaExistente(prova);

            assertThat(plano.getTreinosPlanejados()).hasSize(2);
            assertThat(plano.getTreinosPlanejados()).contains(treinoOutraProva);
        }

        @Test
        @DisplayName("semana sem plano: no-op")
        void semanaSemPlanoNoOp() {
            Atleta atleta = atletaComAssessoria();
            Prova prova = provaCom(atleta, LocalDate.now().plusDays(3), BigDecimal.valueOf(10.0));

            when(planoSemanalRepository.findSemanaAbertaParaProva(atleta.getId(), tenantId, prova.getDataProva()))
                    .thenReturn(Optional.empty());

            Optional<PlanoSemanal> resultado = service.aplicarProvaEmSemanaExistente(prova);

            assertThat(resultado).isEmpty();
            verify(planoSemanalRepository, never()).save(any());
            verify(planoReviewService, never()).reabrirRevisao(any(), any(), any());
        }

        @Test
        @DisplayName("semana encerrada ou plano rejeitado: no-op (a query nem devolve a semana)")
        void semanaEncerradaOuRejeitadaNoOp() {
            Atleta atleta = atletaComAssessoria();
            Prova prova = provaCom(atleta, LocalDate.now().plusDays(3), BigDecimal.valueOf(10.0));

            when(planoSemanalRepository.findSemanaAbertaParaProva(atleta.getId(), tenantId, prova.getDataProva()))
                    .thenReturn(Optional.empty());

            Optional<PlanoSemanal> resultado = service.aplicarProvaEmSemanaExistente(prova);

            assertThat(resultado).isEmpty();
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

    private Prova provaCom(Atleta atleta, LocalDate dataProva, BigDecimal distanciaKm) {
        Prova prova = Prova.builder()
                .id(UUID.randomUUID())
                .nomeProva("Prova de teste")
                .dataProva(dataProva)
                .distancia(DistanciaProva.KM_10)
                .distanciaKm(distanciaKm)
                .tipoProva(TipoProva.CORRIDA_RUA)
                .statusProva(ProvaStatus.PLANEJADA)
                .tempoObjetivo(Duration.ofMinutes(50))
                .build();
        prova.setAtleta(atleta);
        prova.setAssessoria(atleta.getAssessoria());
        return prova;
    }

    private TreinoPlanejado treinoPendente(LocalDate dataTreino, TipoTreino tipo, double distanciaKm) {
        TreinoPlanejado treino = new TreinoPlanejado();
        treino.setDataTreino(dataTreino);
        treino.setDiaSemana(DiaSemana.SEGUNDA);
        treino.setTipoTreino(tipo);
        treino.setStatusTreino(TreinoExecucaoStatus.PENDENTE);
        treino.setDuracaoMin(Duration.ofMinutes(60));
        treino.setDistanciaKm(BigDecimal.valueOf(distanciaKm));
        return treino;
    }

    private PlanoSemanal planoCom(Atleta atleta, PlanoReviewStatus reviewStatus, PlanoStatus status,
                                  TreinoPlanejado... treinos) {
        PlanoSemanal plano = new PlanoSemanal();
        plano.setId(UUID.randomUUID());
        plano.setAtleta(atleta);
        plano.setAssessoria(atleta.getAssessoria());
        plano.setStatus(status);
        plano.setReviewStatus(reviewStatus);
        plano.setSemanaInicio(LocalDate.now().minusDays(1));
        plano.setSemanaFim(LocalDate.now().plusDays(6));
        plano.setVolumePlanejadoKm(BigDecimal.valueOf(40));
        List<TreinoPlanejado> lista = new ArrayList<>(List.of(treinos));
        plano.setTreinosPlanejados(lista);
        return plano;
    }
}
