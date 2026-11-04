package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.IntegracaoExterna;
import br.com.menthoros.backend.entity.PlanoSemanal;
import br.com.menthoros.backend.entity.TreinoPlanejado;
import br.com.menthoros.backend.enums.FonteDados;
import br.com.menthoros.backend.enums.StatusSincronizacao;
import br.com.menthoros.backend.enums.TreinoExecucaoStatus;
import br.com.menthoros.backend.enums.WorkoutEditReason;
import br.com.menthoros.backend.events.WorkoutEditedAfterApprovalEvent;
import br.com.menthoros.backend.exception.DomainConflictException;
import br.com.menthoros.backend.exception.DomainRuleViolationException;
import br.com.menthoros.backend.repository.IntegracaoExternaRepository;
import br.com.menthoros.backend.services.GarminConnectionService;
import br.com.menthoros.backend.services.helper.ApprovedWorkoutEditPolicy;
import br.com.menthoros.backend.services.helper.AtletaHojeResolver;
import br.com.menthoros.backend.services.helper.IntervalsIcuWorkoutConverter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
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

@ExtendWith(MockitoExtension.class)
class PostApprovalEditCoordinatorTest {

    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");
    private static final Instant AGORA = Instant.parse("2026-11-04T15:00:00Z");
    private static final LocalDate HOJE = LocalDate.of(2026, 11, 4);

    @Mock private AtletaHojeResolver atletaHojeResolver;
    @Mock private IntegracaoExternaRepository integracaoExternaRepository;
    @Mock private GarminConnectionService garminConnectionService;
    @Mock private IntervalsIcuWorkoutConverter converter;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private PostApprovalEditRecorder recorder;

    private PostApprovalEditCoordinator coordinator;
    private final Clock clock = Clock.fixed(AGORA, FUSO);
    private final UUID tenantId = UUID.randomUUID();
    private Atleta atleta;
    private PlanoSemanal plano;

    @BeforeEach
    void setUp() {
        coordinator = new PostApprovalEditCoordinator(atletaHojeResolver, new ApprovedWorkoutEditPolicy(),
                integracaoExternaRepository, garminConnectionService, converter, eventPublisher, recorder, clock);
        atleta = new Atleta();
        atleta.setId(UUID.randomUUID());
        plano = new PlanoSemanal();
        plano.setId(UUID.randomUUID());
        plano.setAtleta(atleta);
    }

    @Nested
    @DisplayName("checkBeforePatch")
    class CheckBeforePatch {

        @Test
        @DisplayName("treino pendente de amanhã passa")
        void amanhaPassa() {
            when(atletaHojeResolver.hojeDe(atleta)).thenReturn(HOJE);
            stubRelogio(true);

            when(recorder.snapshot(any())).thenReturn("{antes}");

            assertThat(coordinator.checkBeforePatch(plano, treino(HOJE.plusDays(1)))).isEqualTo("{antes}");
        }

        @Test
        @DisplayName("treino realizado: 422 (CA2)")
        void realizadoRecusa() {
            when(atletaHojeResolver.hojeDe(atleta)).thenReturn(HOJE);
            stubRelogio(false);
            TreinoPlanejado treino = treino(HOJE.plusDays(1));
            treino.setStatusTreino(TreinoExecucaoStatus.REALIZADO);

            assertThatThrownBy(() -> coordinator.checkBeforePatch(plano, treino))
                    .isInstanceOf(DomainRuleViolationException.class)
                    .hasMessageContaining("realizado");
        }

        @Test
        @DisplayName("treino de ontem no fuso do atleta: 422 (CA3)")
        void ontemRecusa() {
            when(atletaHojeResolver.hojeDe(atleta)).thenReturn(HOJE);
            stubRelogio(false);

            assertThatThrownBy(() -> coordinator.checkBeforePatch(plano, treino(HOJE.minusDays(1))))
                    .isInstanceOf(DomainRuleViolationException.class)
                    .hasMessageContaining("passado");
        }

        @Test
        @DisplayName("treino de hoje com conexão revogada conta como relógio: 422 (CA3a)")
        void hojeComConexaoRevogadaRecusa() {
            when(atletaHojeResolver.hojeDe(atleta)).thenReturn(HOJE);
            IntegracaoExterna revogada = new IntegracaoExterna();
            revogada.setAtivo(false);
            when(integracaoExternaRepository.findFirstByAtletaIdAndPlataformaInAndTenantIdOrderByAtivoDescAtualizadoEmDesc(
                    atleta.getId(), List.of(FonteDados.INTERVALS_ICU, FonteDados.GARMIN), tenantId))
                    .thenReturn(Optional.of(revogada));

            assertThatThrownBy(() -> coordinator.checkBeforePatch(plano, treino(HOJE)))
                    .isInstanceOf(DomainRuleViolationException.class)
                    .hasMessageContaining("relógio");
        }

        @Test
        @DisplayName("treino de hoje de atleta que nunca conectou passa (CA3a)")
        void hojeSemRelogioPassa() {
            when(atletaHojeResolver.hojeDe(atleta)).thenReturn(HOJE);
            stubRelogio(false);

            when(recorder.snapshot(any())).thenReturn("{antes}");

            assertThat(coordinator.checkBeforePatch(plano, treino(HOJE))).isEqualTo("{antes}");
        }

        @Test
        @DisplayName("SINCRONIZANDO dentro da janela: 409 (CA3c)")
        void sincronizandoRecenteConflita() {
            when(atletaHojeResolver.hojeDe(atleta)).thenReturn(HOJE);
            stubRelogio(true);
            TreinoPlanejado treino = treino(HOJE.plusDays(1));
            treino.setStatusSincronizacao(StatusSincronizacao.SINCRONIZANDO);
            treino.setUltimaTentativaSincronizacao(agoraLocal().minusMinutes(1));

            assertThatThrownBy(() -> coordinator.checkBeforePatch(plano, treino))
                    .isInstanceOf(DomainConflictException.class)
                    .hasMessageContaining("andamento");
        }

        @Test
        @DisplayName("SINCRONIZANDO preso além da janela não bloqueia (R3)")
        void sincronizandoPresoPassa() {
            when(atletaHojeResolver.hojeDe(atleta)).thenReturn(HOJE);
            stubRelogio(true);
            TreinoPlanejado treino = treino(HOJE.plusDays(1));
            treino.setStatusSincronizacao(StatusSincronizacao.SINCRONIZANDO);
            treino.setUltimaTentativaSincronizacao(agoraLocal().minus(TreinoPlanejado.JANELA_SINCRONIZANDO));

            when(recorder.snapshot(treino)).thenReturn("{antes}");

            assertThat(coordinator.checkBeforePatch(plano, treino)).isEqualTo("{antes}");
        }

        @Test
        @DisplayName("AGUARDANDO_RETRY recente não bloqueia: segunda edição seguida passa")
        void aguardandoRetryPassa() {
            when(atletaHojeResolver.hojeDe(atleta)).thenReturn(HOJE);
            stubRelogio(true);
            TreinoPlanejado treino = treino(HOJE.plusDays(1));
            treino.setStatusSincronizacao(StatusSincronizacao.AGUARDANDO_RETRY);
            treino.setUltimaTentativaSincronizacao(agoraLocal());

            when(recorder.snapshot(treino)).thenReturn("{antes}");

            assertThat(coordinator.checkBeforePatch(plano, treino)).isEqualTo("{antes}");
        }
    }

    @Nested
    @DisplayName("applyAfterPatch")
    class ApplyAfterPatch {

        @Test
        @DisplayName("com canal: reinicia a sincronização, mantém externalId, marca a edição e publica o evento (CA1)")
        void comCanal() {
            TreinoPlanejado treino = treino(HOJE.plusDays(1));
            treino.setStatusSincronizacao(StatusSincronizacao.SINCRONIZADO);
            treino.setExternalId("123");
            when(converter.converter(treino)).thenReturn(Optional.of(exportavel()));
            when(garminConnectionService.conexaoDeCanal(atleta.getId(), tenantId))
                    .thenReturn(Optional.of(new IntegracaoExterna()));

            coordinator.applyAfterPatch(plano, treino, "{antes}", WorkoutEditReason.LOAD);

            assertThat(treino.getStatusSincronizacao()).isEqualTo(StatusSincronizacao.AGUARDANDO_RETRY);
            assertThat(treino.getTentativasSincronizacao()).isZero();
            assertThat(treino.getExternalId()).isEqualTo("123");
            assertThat(treino.getEditedAfterApprovalAt()).isEqualTo(AGORA);
            verify(recorder).record(plano, treino, "{antes}", WorkoutEditReason.LOAD, AGORA);
            ArgumentCaptor<WorkoutEditedAfterApprovalEvent> evento =
                    ArgumentCaptor.forClass(WorkoutEditedAfterApprovalEvent.class);
            verify(eventPublisher).publishEvent(evento.capture());
            assertThat(evento.getValue()).isEqualTo(new WorkoutEditedAfterApprovalEvent(
                    treino.getId(), plano.getId(), atleta.getId(), tenantId));
        }

        @Test
        @DisplayName("sem canal: marca a edição, não toca na sincronização e não publica (CA8)")
        void semCanal() {
            TreinoPlanejado treino = treino(HOJE.plusDays(1));
            treino.setStatusSincronizacao(StatusSincronizacao.SINCRONIZADO);
            treino.setExternalId("123");
            treino.setTentativasSincronizacao(1);
            when(converter.converter(treino)).thenReturn(Optional.of(exportavel()));
            when(garminConnectionService.conexaoDeCanal(atleta.getId(), tenantId)).thenReturn(Optional.empty());

            coordinator.applyAfterPatch(plano, treino, "{antes}", WorkoutEditReason.LOAD);

            assertThat(treino.getStatusSincronizacao()).isEqualTo(StatusSincronizacao.SINCRONIZADO);
            assertThat(treino.getTentativasSincronizacao()).isEqualTo(1);
            assertThat(treino.getEditedAfterApprovalAt()).isEqualTo(AGORA);
            verify(recorder).record(plano, treino, "{antes}", WorkoutEditReason.LOAD, AGORA);
            verify(eventPublisher, never()).publishEvent(any());
        }

        @Test
        @DisplayName("resultado não exportável: 422 sem tocar na sincronização nem publicar (CA3b)")
        void naoExportavelRecusa() {
            TreinoPlanejado treino = treino(HOJE.plusDays(1));
            treino.setStatusSincronizacao(StatusSincronizacao.SINCRONIZADO);
            when(converter.converter(treino)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> coordinator.applyAfterPatch(plano, treino, "{antes}", null))
                    .isInstanceOf(DomainRuleViolationException.class)
                    .hasMessageContaining("relógio");

            assertThat(treino.getStatusSincronizacao()).isEqualTo(StatusSincronizacao.SINCRONIZADO);
            assertThat(treino.getEditedAfterApprovalAt()).isNull();
            verifyNoInteractions(garminConnectionService, eventPublisher, recorder);
        }
    }

    private void stubRelogio(boolean temConexao) {
        when(integracaoExternaRepository.findFirstByAtletaIdAndPlataformaInAndTenantIdOrderByAtivoDescAtualizadoEmDesc(
                atleta.getId(), List.of(FonteDados.INTERVALS_ICU, FonteDados.GARMIN), tenantId))
                .thenReturn(temConexao ? Optional.of(new IntegracaoExterna()) : Optional.empty());
    }

    private LocalDateTime agoraLocal() {
        return LocalDateTime.now(clock);
    }

    private TreinoPlanejado treino(LocalDate data) {
        TreinoPlanejado treino = new TreinoPlanejado();
        treino.setId(UUID.randomUUID());
        treino.setTenantId(tenantId);
        treino.setPlanoSemanal(plano);
        treino.setAtleta(atleta);
        treino.setStatusTreino(TreinoExecucaoStatus.PENDENTE);
        treino.setDataTreino(data);
        return treino;
    }

    private static IntervalsIcuWorkoutConverter.ResultadoConversao exportavel() {
        return new IntervalsIcuWorkoutConverter.ResultadoConversao(null, false, false);
    }
}
