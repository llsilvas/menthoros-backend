package br.com.menthoros.backend.services;

import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.PlanoSemanal;
import br.com.menthoros.backend.enums.MotivoReaberturaRevisao;
import br.com.menthoros.backend.enums.OrigemAprovacao;
import br.com.menthoros.backend.enums.PlanoReviewStatus;
import br.com.menthoros.backend.enums.PlanoStatus;
import br.com.menthoros.backend.events.PlanoAprovadoEvent;
import br.com.menthoros.backend.events.PlanoReabertoEvent;
import br.com.menthoros.backend.exception.DomainRuleViolationException;
import br.com.menthoros.backend.mapper.PlanoSemanalMapper;
import br.com.menthoros.backend.repository.AthleteBaselineStateRepository;
import br.com.menthoros.backend.repository.PlanoSemanalRepository;
import br.com.menthoros.backend.services.helper.ConsumedReviewOutcomeResolver;
import br.com.menthoros.backend.services.impl.PlanoReviewServiceImpl;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * D4 (prova-no-plano-semanal): {@code reabrirRevisao} é a exceção deliberada à regra "sem volta"
 * de {@code validarTransicao} — só de {@code APROVADO}, só em semana não encerrada. Aprovar e
 * rejeitar de novo limpam o motivo e o carimbo.
 */
@ExtendWith(MockitoExtension.class)
class PlanoReviewServiceReaberturaTest {

    @Mock private PlanoSemanalRepository planoSemanalRepository;
    @Mock private PlanoSemanalMapper planoSemanalMapper;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private AthleteBaselineStateRepository athleteBaselineStateRepository;
    @Spy private ConsumedReviewOutcomeResolver outcomeResolver = new ConsumedReviewOutcomeResolver();
    @Spy private MeterRegistry meterRegistry = new SimpleMeterRegistry();
    @InjectMocks private PlanoReviewServiceImpl service;

    private UUID tenantId;
    private UUID atletaId;
    private UUID planoId;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        atletaId = UUID.randomUUID();
        planoId = UUID.randomUUID();
    }

    @Nested
    @DisplayName("reabrirRevisao")
    class ReabrirRevisao {

        @Test
        @DisplayName("APROVADO em semana não encerrada volta a AGUARDANDO_REVISAO com motivo e carimbo")
        void aprovadoVoltaComMotivo() {
            PlanoSemanal plano = planoComStatus(PlanoReviewStatus.APROVADO, PlanoStatus.EM_ANDAMENTO);
            when(planoSemanalRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            PlanoSemanal salvo = service.reabrirRevisao(plano, MotivoReaberturaRevisao.PROVA_INSERIDA, tenantId);

            assertThat(salvo.getReviewStatus()).isEqualTo(PlanoReviewStatus.AGUARDANDO_REVISAO);
            assertThat(salvo.getMotivoReabertura()).isEqualTo(MotivoReaberturaRevisao.PROVA_INSERIDA);
            assertThat(salvo.getReabertoEm()).isNotNull();

            verify(eventPublisher).publishEvent(
                    new PlanoReabertoEvent(planoId, atletaId, tenantId, MotivoReaberturaRevisao.PROVA_INSERIDA));
        }

        @Test
        @DisplayName("plano AGUARDANDO_REVISAO nunca aprovado recusa reabertura")
        void aguardandoRevisaoRecusa() {
            PlanoSemanal plano = planoComStatus(PlanoReviewStatus.AGUARDANDO_REVISAO, PlanoStatus.PLANEJADO);

            assertThatThrownBy(() -> service.reabrirRevisao(plano, MotivoReaberturaRevisao.PROVA_INSERIDA, tenantId))
                    .isInstanceOf(DomainRuleViolationException.class);

            verify(planoSemanalRepository, org.mockito.Mockito.never()).save(any());
        }

        @Test
        @DisplayName("plano REJEITADO recusa reabertura")
        void rejeitadoRecusa() {
            PlanoSemanal plano = planoComStatus(PlanoReviewStatus.REJEITADO, PlanoStatus.PLANEJADO);

            assertThatThrownBy(() -> service.reabrirRevisao(plano, MotivoReaberturaRevisao.PROVA_REMOVIDA, tenantId))
                    .isInstanceOf(DomainRuleViolationException.class);
        }

        @Test
        @DisplayName("semana encerrada (CONCLUIDO) recusa reabertura mesmo estando APROVADO")
        void semanaEncerradaRecusa() {
            PlanoSemanal plano = planoComStatus(PlanoReviewStatus.APROVADO, PlanoStatus.CONCLUIDO);

            assertThatThrownBy(() -> service.reabrirRevisao(plano, MotivoReaberturaRevisao.PROVA_INSERIDA, tenantId))
                    .isInstanceOf(DomainRuleViolationException.class);

            verify(planoSemanalRepository, org.mockito.Mockito.never()).save(any());
        }
    }

    @Nested
    @DisplayName("aprovarTransicao limpa a reabertura")
    class AprovarLimpaReabertura {

        @Test
        @DisplayName("aprovar um plano reaberto limpa motivoReabertura e reabertoEm")
        void aprovarLimpaCampos() {
            PlanoSemanal plano = planoComStatus(PlanoReviewStatus.AGUARDANDO_REVISAO, PlanoStatus.EM_ANDAMENTO);
            plano.setMotivoReabertura(MotivoReaberturaRevisao.PROVA_INSERIDA);
            plano.setReabertoEm(LocalDateTime.now());
            when(planoSemanalRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            PlanoSemanal salvo = service.aprovarTransicao(plano, tenantId, OrigemAprovacao.COACH);

            assertThat(salvo.getMotivoReabertura()).isNull();
            assertThat(salvo.getReabertoEm()).isNull();
            verify(eventPublisher).publishEvent(new PlanoAprovadoEvent(planoId, atletaId, tenantId));
        }
    }

    @Nested
    @DisplayName("rejeitarPlano limpa a reabertura")
    class RejeitarLimpaReabertura {

        @Test
        @DisplayName("rejeitar um plano reaberto limpa motivoReabertura e reabertoEm")
        void rejeitarLimpaCampos() {
            PlanoSemanal plano = planoComStatus(PlanoReviewStatus.AGUARDANDO_REVISAO, PlanoStatus.EM_ANDAMENTO);
            plano.setMotivoReabertura(MotivoReaberturaRevisao.PROVA_REMOVIDA);
            plano.setReabertoEm(LocalDateTime.now());

            when(planoSemanalRepository.findByIdAndTenantId(planoId, tenantId)).thenReturn(java.util.Optional.of(plano));
            when(planoSemanalRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.rejeitarPlano(planoId, tenantId, "Volume muito alto");

            assertThat(plano.getMotivoReabertura()).isNull();
            assertThat(plano.getReabertoEm()).isNull();
        }
    }

    private PlanoSemanal planoComStatus(PlanoReviewStatus reviewStatus, PlanoStatus status) {
        Atleta atleta = new Atleta();
        atleta.setId(atletaId);
        return PlanoSemanal.builder()
                .id(planoId)
                .atleta(atleta)
                .status(status)
                .reviewStatus(reviewStatus)
                .semanaInicio(LocalDate.now())
                .semanaFim(LocalDate.now().plusDays(6))
                .build();
    }
}
