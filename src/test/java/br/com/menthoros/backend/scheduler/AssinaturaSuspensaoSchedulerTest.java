package br.com.menthoros.backend.scheduler;

import br.com.menthoros.backend.entity.Assessoria;
import br.com.menthoros.backend.entity.Assinatura;
import br.com.menthoros.backend.enums.StatusAssinatura;
import br.com.menthoros.backend.repository.AssessoriaRepository;
import br.com.menthoros.backend.repository.AssinaturaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Job de carência (CA5). A fronteira de datas (4 dias não / 6 dias sim) é provada contra o schema
 * real em {@code AssinaturaRepositoryTest#filtraInadimplenteAntesDoCorte}; aqui provamos que o job
 * pede a janela certa (corte = agora - 5 dias) e transiciona o que o repositório retorna.
 */
@ExtendWith(MockitoExtension.class)
class AssinaturaSuspensaoSchedulerTest {

    @Mock private AssinaturaRepository assinaturaRepository;
    @Mock private AssessoriaRepository assessoriaRepository;

    @InjectMocks private AssinaturaSuspensaoScheduler scheduler;

    @Test
    @DisplayName("suspende INADIMPLENTE vencido e desativa a assessoria; corte = agora - 5 dias (CA5)")
    void suspendeVencido() {
        UUID assessoriaId = UUID.randomUUID();
        Assinatura assinatura = new Assinatura();
        assinatura.setId(UUID.randomUUID());
        assinatura.setAssessoriaId(assessoriaId);
        assinatura.setStatus(StatusAssinatura.INADIMPLENTE);
        assinatura.setOverdueDesde(Instant.now().minus(6, ChronoUnit.DAYS));

        Assessoria assessoria = new Assessoria();
        assessoria.setId(assessoriaId);
        assessoria.setAtivo(true);

        ArgumentCaptor<Instant> corteCaptor = ArgumentCaptor.forClass(Instant.class);
        when(assinaturaRepository.findByStatusAndOverdueDesdeBefore(
                eq(StatusAssinatura.INADIMPLENTE), corteCaptor.capture()))
                .thenReturn(List.of(assinatura));
        when(assessoriaRepository.findById(assessoriaId)).thenReturn(Optional.of(assessoria));

        scheduler.suspenderInadimplentes();

        assertThat(assinatura.getStatus()).isEqualTo(StatusAssinatura.SUSPENSA);
        assertThat(assessoria.getAtivo()).isFalse();
        verify(assinaturaRepository).save(assinatura);
        verify(assessoriaRepository).save(assessoria);

        Instant esperado = Instant.now().minus(5, ChronoUnit.DAYS);
        assertThat(corteCaptor.getValue()).isCloseTo(esperado, within(1, ChronoUnit.MINUTES));
    }

    @Test
    @DisplayName("sem inadimplentes vencidos: nenhuma suspensão")
    void nenhumVencido() {
        when(assinaturaRepository.findByStatusAndOverdueDesdeBefore(eq(StatusAssinatura.INADIMPLENTE), org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of());

        scheduler.suspenderInadimplentes();

        verify(assinaturaRepository, never()).save(org.mockito.ArgumentMatchers.any());
        verifyNoInteractions(assessoriaRepository);
    }
}
