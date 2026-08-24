package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.TreinoRealizado;
import br.com.menthoros.backend.enums.StatusSincronizacao;
import br.com.menthoros.backend.multitenancy.TenantContext;
import br.com.menthoros.backend.repository.CheckinProntidaoRepository;
import br.com.menthoros.backend.repository.ProvaRepository;
import br.com.menthoros.backend.repository.TreinoRealizadoRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * D8 (`ingestao-treino-realizado`, task 7.7): {@code prepararContexto} alimenta toda a árvore de
 * formatters de prompt do planner (`PlanoTreinoPromptBuilder`/`VariabilidadePromptFormatter` e
 * demais) via {@code treinosUltimas4Semanas} — cancelado não pode contar na carga aqui.
 */
@ExtendWith(MockitoExtension.class)
class TreinoHistoricoProviderTest {

    @Mock private TreinoRealizadoRepository treinoRealizadoRepository;
    @Mock private ProvaRepository provaRepository;
    @Mock private CheckinProntidaoRepository checkinProntidaoRepository;

    private TreinoHistoricoProvider provider;
    private final LocalDate hoje = LocalDate.of(2026, 8, 24);

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(hoje.atStartOfDay(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault());
        provider = new TreinoHistoricoProvider(treinoRealizadoRepository, provaRepository, checkinProntidaoRepository, clock);
        TenantContext.setTenantId(UUID.randomUUID());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("D8: cancelado não conta em treinosUltimas4Semanas, NULL conta")
    void canceladoNaoContaNullConta() {
        Atleta atleta = new Atleta();
        atleta.setId(UUID.randomUUID());

        TreinoRealizado cancelado = new TreinoRealizado();
        cancelado.setDataTreino(hoje.minusDays(1));
        cancelado.setStatusSincronizacao(StatusSincronizacao.CANCELADO);

        TreinoRealizado semStatus = new TreinoRealizado();
        semStatus.setDataTreino(hoje.minusDays(2));
        semStatus.setStatusSincronizacao(null);

        when(treinoRealizadoRepository
                .findByAtletaAndDataTreinoGreaterThanEqualOrderByDataTreinoDesc(any(), any()))
                .thenReturn(List.of(cancelado, semStatus));
        when(provaRepository.findByAtletaAndDataProvaBetweenOrderByDataProvaAsc(any(), any(), any()))
                .thenReturn(List.of());
        when(checkinProntidaoRepository.findByAtletaIdAndDataBetween(any(), any(), any(), any()))
                .thenReturn(List.of());

        TreinoHistoricoProvider.ContextoTreino ctx = provider.prepararContexto(atleta);

        assertThat(ctx.treinosUltimas4Semanas())
                .as("só o treino sem status (NULL) conta — o CANCELADO fica de fora")
                .containsExactly(semStatus);
    }
}
