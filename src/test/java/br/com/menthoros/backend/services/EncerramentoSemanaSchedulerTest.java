package br.com.menthoros.backend.services;

import br.com.menthoros.backend.entity.Assessoria;
import br.com.menthoros.backend.multitenancy.TenantContext;
import br.com.menthoros.backend.repository.AssessoriaRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EncerramentoSemanaSchedulerTest {

    @Mock
    private AssessoriaRepository assessoriaRepository;
    @Mock
    private EncerramentoSemanaService encerramentoSemanaService;

    private EncerramentoSemanaScheduler scheduler;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-07-10T12:00:00Z"), ZoneOffset.UTC);
        scheduler = new EncerramentoSemanaScheduler(assessoriaRepository, encerramentoSemanaService, clock);
        ReflectionTestUtils.setField(scheduler, "carenciaDias", 3);
        ReflectionTestUtils.setField(scheduler, "enabled", true);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("isola os tenants: uma iteração que lança não impede a seguinte, e o contexto é limpo")
    void isolaTenantsELimpaContexto() {
        UUID t1 = UUID.randomUUID();
        UUID t2 = UUID.randomUUID();
        when(assessoriaRepository.findByAtivoTrue())
                .thenReturn(List.of(Assessoria.builder().id(t1).build(), Assessoria.builder().id(t2).build()));
        when(encerramentoSemanaService.encerrarPlanosElegiveis(eq(t1), any(), eq(3)))
                .thenThrow(new RuntimeException("boom no tenant 1"));
        List<UUID> contextoNaChamada = new ArrayList<>();
        when(encerramentoSemanaService.encerrarPlanosElegiveis(eq(t2), any(), eq(3)))
                .thenAnswer(inv -> {
                    contextoNaChamada.add(TenantContext.getTenantId());
                    return 1;
                });

        scheduler.encerrarSemanasVencidas();

        verify(encerramentoSemanaService).encerrarPlanosElegiveis(eq(t1), any(), eq(3));
        verify(encerramentoSemanaService).encerrarPlanosElegiveis(eq(t2), any(), eq(3));
        // o tenant 2 rodou com o SEU próprio contexto (sem vazamento do tenant 1 que lançou)
        assertThat(contextoNaChamada).containsExactly(t2);
        // contexto limpo ao final
        assertThat(TenantContext.hasTenant()).isFalse();
    }

    @Test
    @DisplayName("desabilitado (enabled=false) não faz nada")
    void desabilitadoNaoFazNada() {
        ReflectionTestUtils.setField(scheduler, "enabled", false);

        scheduler.encerrarSemanasVencidas();

        verifyNoInteractions(assessoriaRepository, encerramentoSemanaService);
    }
}
