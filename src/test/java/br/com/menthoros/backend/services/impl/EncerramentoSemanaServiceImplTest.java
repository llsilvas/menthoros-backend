package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.entity.PlanoSemanal;
import br.com.menthoros.backend.entity.TreinoPlanejado;
import br.com.menthoros.backend.enums.PlanoStatus;
import br.com.menthoros.backend.enums.TreinoExecucaoStatus;
import br.com.menthoros.backend.exception.DomainNotFoundException;
import br.com.menthoros.backend.multitenancy.TenantContext;
import br.com.menthoros.backend.repository.PlanoSemanalRepository;
import br.com.menthoros.backend.repository.TreinoPlanejadoRepository;
import br.com.menthoros.backend.services.EncerramentoSemanaResultado;
import br.com.menthoros.backend.services.TreinoService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EncerramentoSemanaServiceImplTest {

    private static final ZoneId ZONA = ZoneId.of("America/Sao_Paulo");

    @Mock
    private PlanoSemanalRepository planoSemanalRepository;
    @Mock
    private TreinoPlanejadoRepository treinoPlanejadoRepository;
    @Mock
    private TreinoService treinoService;

    private UUID tenantId;

    @BeforeEach
    void setUpTenant() {
        tenantId = UUID.randomUUID();
        TenantContext.setTenantId(tenantId);
    }

    @AfterEach
    void tearDownTenant() {
        TenantContext.clear();
    }

    @Nested
    @DisplayName("encerrarSemana")
    class EncerrarSemana {

        @Test
        @DisplayName("no domingo marca o longão de sábado e o de domingo (fim da semana)")
        void fechaNoDomingoMarcaLongaoDoDia() {
            LocalDate domingo = LocalDate.of(2026, 7, 5);
            UUID planoId = UUID.randomUUID();
            PlanoSemanal plano = plano(planoId, domingo, PlanoStatus.CONCLUIDO);
            TreinoPlanejado sabado = treino(LocalDate.of(2026, 7, 4), TreinoExecucaoStatus.PENDENTE);
            TreinoPlanejado longaoDomingo = treino(domingo, TreinoExecucaoStatus.PENDENTE);
            stubPlano(planoId, plano);
            when(treinoPlanejadoRepository.findPendentesAteHojeDoPlano(eq(planoId), any()))
                    .thenReturn(List.of(sabado, longaoDomingo));

            EncerramentoSemanaResultado resultado = servico(domingo).encerrarSemana(planoId);

            verify(treinoService).marcarTreinoPerdido(sabado.getId());
            verify(treinoService).marcarTreinoPerdido(longaoDomingo.getId());
            assertThat(resultado.treinosFinalizados()).isEqualTo(2);
            assertThat(resultado.treinosPerdidos()).containsExactly(sabado.getId(), longaoDomingo.getId());
        }

        @Test
        @DisplayName("no meio da semana preserva o treino de hoje e avisa que a semana não terminou")
        void noMeioDaSemanaPreservaTreinoDeHoje() {
            LocalDate terca = LocalDate.of(2026, 7, 7);
            LocalDate domingoFim = LocalDate.of(2026, 7, 12);
            UUID planoId = UUID.randomUUID();
            PlanoSemanal plano = plano(planoId, domingoFim, PlanoStatus.EM_ANDAMENTO);
            TreinoPlanejado segunda = treino(LocalDate.of(2026, 7, 6), TreinoExecucaoStatus.PENDENTE);
            TreinoPlanejado hoje = treino(terca, TreinoExecucaoStatus.PENDENTE);
            stubPlano(planoId, plano);
            when(treinoPlanejadoRepository.findPendentesAteHojeDoPlano(eq(planoId), any()))
                    .thenReturn(List.of(segunda, hoje));

            EncerramentoSemanaResultado resultado = servico(terca).encerrarSemana(planoId);

            verify(treinoService).marcarTreinoPerdido(segunda.getId());
            verify(treinoService, never()).marcarTreinoPerdido(hoje.getId());
            assertThat(resultado.treinosFinalizados()).isEqualTo(1);
            assertThat(resultado.prontoParaProximaSemana()).isFalse();
            assertThat(resultado.aviso()).isNotBlank();
        }

        @Test
        @DisplayName("não toca treino REALIZADO, PARCIAL nem futuro (só PENDENTE elegível)")
        void naoTocaRealizadoParcialNemFuturo() {
            LocalDate domingo = LocalDate.of(2026, 7, 5);
            UUID planoId = UUID.randomUUID();
            PlanoSemanal plano = plano(planoId, domingo, PlanoStatus.EM_ANDAMENTO);
            TreinoPlanejado pendentePassado = treino(LocalDate.of(2026, 7, 4), TreinoExecucaoStatus.PENDENTE);
            TreinoPlanejado realizado = treino(LocalDate.of(2026, 7, 3), TreinoExecucaoStatus.REALIZADO);
            TreinoPlanejado parcial = treino(LocalDate.of(2026, 7, 3), TreinoExecucaoStatus.PARCIAL);
            TreinoPlanejado futuro = treino(LocalDate.of(2026, 7, 10), TreinoExecucaoStatus.PENDENTE);
            stubPlano(planoId, plano);
            when(treinoPlanejadoRepository.findPendentesAteHojeDoPlano(eq(planoId), any()))
                    .thenReturn(List.of(pendentePassado, realizado, parcial, futuro));

            EncerramentoSemanaResultado resultado = servico(domingo).encerrarSemana(planoId);

            verify(treinoService).marcarTreinoPerdido(pendentePassado.getId());
            verify(treinoService, never()).marcarTreinoPerdido(realizado.getId());
            verify(treinoService, never()).marcarTreinoPerdido(parcial.getId());
            verify(treinoService, never()).marcarTreinoPerdido(futuro.getId());
            assertThat(resultado.treinosFinalizados()).isEqualTo(1);
        }

        @Test
        @DisplayName("é idempotente: plano sem PENDENTE elegível não altera nada e retorna zero")
        void idempotenteQuandoNaoHaPendenteElegivel() {
            LocalDate hoje = LocalDate.of(2026, 7, 10);
            UUID planoId = UUID.randomUUID();
            PlanoSemanal plano = plano(planoId, LocalDate.of(2026, 7, 5), PlanoStatus.CONCLUIDO);
            stubPlano(planoId, plano);
            when(treinoPlanejadoRepository.findPendentesAteHojeDoPlano(eq(planoId), any()))
                    .thenReturn(List.of());

            EncerramentoSemanaResultado resultado = servico(hoje).encerrarSemana(planoId);

            verify(treinoService, never()).marcarTreinoPerdido(any());
            assertThat(resultado.treinosFinalizados()).isZero();
            assertThat(resultado.novoStatus()).isEqualTo(PlanoStatus.CONCLUIDO);
        }

        @Test
        @DisplayName("resolve a data corrente no fuso America/Sao_Paulo, não no do JVM")
        void usaDataNoFusoSaoPaulo() {
            // Domingo 22h BRT == Segunda 01h UTC. A data corrente deve ser o domingo (05/07), não segunda.
            Clock clockUtc = Clock.fixed(Instant.parse("2026-07-06T01:00:00Z"), ZoneOffset.UTC);
            UUID planoId = UUID.randomUUID();
            PlanoSemanal plano = plano(planoId, LocalDate.of(2026, 7, 5), PlanoStatus.EM_ANDAMENTO);
            stubPlano(planoId, plano);
            when(treinoPlanejadoRepository.findPendentesAteHojeDoPlano(eq(planoId), any()))
                    .thenReturn(List.of());

            servicoComClock(clockUtc).encerrarSemana(planoId);

            ArgumentCaptor<LocalDate> hojeCaptor = ArgumentCaptor.forClass(LocalDate.class);
            verify(treinoPlanejadoRepository).findPendentesAteHojeDoPlano(eq(planoId), hojeCaptor.capture());
            assertThat(hojeCaptor.getValue()).isEqualTo(LocalDate.of(2026, 7, 5));
        }

        @Test
        @DisplayName("ignora sem lançar um treino que deixou de ser PENDENTE durante o processamento")
        void ignoraTreinoQueDeixouDeSerPendente() {
            LocalDate domingo = LocalDate.of(2026, 7, 5);
            UUID planoId = UUID.randomUUID();
            PlanoSemanal plano = plano(planoId, domingo, PlanoStatus.EM_ANDAMENTO);
            TreinoPlanejado aindaPendente = treino(LocalDate.of(2026, 7, 4), TreinoExecucaoStatus.PENDENTE);
            TreinoPlanejado virouRealizado = treino(LocalDate.of(2026, 7, 4), TreinoExecucaoStatus.REALIZADO);
            stubPlano(planoId, plano);
            when(treinoPlanejadoRepository.findPendentesAteHojeDoPlano(eq(planoId), any()))
                    .thenReturn(List.of(aindaPendente, virouRealizado));

            EncerramentoSemanaResultado resultado = servico(domingo).encerrarSemana(planoId);

            verify(treinoService).marcarTreinoPerdido(aindaPendente.getId());
            verify(treinoService, never()).marcarTreinoPerdido(virouRealizado.getId());
            assertThat(resultado.treinosFinalizados()).isEqualTo(1);
        }

        @Test
        @DisplayName("lança DomainNotFoundException quando o plano não é do tenant corrente")
        void planoDeOutroTenantNaoEncontrado() {
            UUID planoId = UUID.randomUUID();
            when(planoSemanalRepository.findByIdAndTenantId(planoId, tenantId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> servico(LocalDate.of(2026, 7, 5)).encerrarSemana(planoId))
                    .isInstanceOf(DomainNotFoundException.class);

            verify(treinoService, never()).marcarTreinoPerdido(any());
        }
    }

    // ---- helpers ----

    private EncerramentoSemanaServiceImpl servico(LocalDate hoje) {
        return servicoComClock(Clock.fixed(hoje.atStartOfDay(ZONA).toInstant(), ZONA));
    }

    private EncerramentoSemanaServiceImpl servicoComClock(Clock clock) {
        return new EncerramentoSemanaServiceImpl(
                planoSemanalRepository, treinoPlanejadoRepository, treinoService, clock);
    }

    private void stubPlano(UUID planoId, PlanoSemanal plano) {
        when(planoSemanalRepository.findByIdAndTenantId(planoId, tenantId)).thenReturn(Optional.of(plano));
    }

    private PlanoSemanal plano(UUID id, LocalDate semanaFim, PlanoStatus status) {
        return PlanoSemanal.builder()
                .id(id)
                .semanaInicio(semanaFim.minusDays(6))
                .semanaFim(semanaFim)
                .status(status)
                .build();
    }

    private TreinoPlanejado treino(LocalDate data, TreinoExecucaoStatus status) {
        TreinoPlanejado treino = new TreinoPlanejado();
        treino.setId(UUID.randomUUID());
        treino.setDataTreino(data);
        treino.setStatusTreino(status);
        return treino;
    }
}
