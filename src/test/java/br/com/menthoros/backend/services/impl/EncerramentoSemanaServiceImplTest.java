package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.entity.Assessoria;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.PlanoSemanal;
import br.com.menthoros.backend.entity.TreinoPlanejado;
import br.com.menthoros.backend.enums.OrigemEncerramento;
import br.com.menthoros.backend.enums.PlanoStatus;
import br.com.menthoros.backend.enums.TreinoExecucaoStatus;
import br.com.menthoros.backend.events.SemanaEncerradaEvent;
import br.com.menthoros.backend.exception.DomainNotFoundException;
import br.com.menthoros.backend.multitenancy.TenantContext;
import br.com.menthoros.backend.repository.AtletaRepository;
import br.com.menthoros.backend.repository.PlanoSemanalRepository;
import br.com.menthoros.backend.repository.TreinoPlanejadoRepository;
import br.com.menthoros.backend.services.EncerramentoLoteResultado;
import br.com.menthoros.backend.services.EncerramentoSemanaResultado;
import br.com.menthoros.backend.services.TreinoService;
import org.springframework.transaction.PlatformTransactionManager;
import org.junit.jupiter.api.AfterEach;
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
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private AtletaRepository atletaRepository;
    @Mock
    private PlatformTransactionManager transactionManager;

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

        @Test
        @DisplayName("fecha o plano (CONCLUIDO) quando a semana terminou")
        void planoFechaQuandoSemanaTerminou() {
            LocalDate domingo = LocalDate.of(2026, 7, 5);
            UUID planoId = UUID.randomUUID();
            PlanoSemanal plano = plano(planoId, domingo, PlanoStatus.EM_ANDAMENTO);
            TreinoPlanejado pendente = treino(LocalDate.of(2026, 7, 4), TreinoExecucaoStatus.PENDENTE);
            stubPlano(planoId, plano);
            when(treinoPlanejadoRepository.findPendentesAteHojeDoPlano(eq(planoId), any()))
                    .thenReturn(List.of(pendente));

            EncerramentoSemanaResultado resultado = servico(domingo).encerrarSemana(planoId);

            assertThat(resultado.novoStatus()).isEqualTo(PlanoStatus.CONCLUIDO);
            assertThat(resultado.prontoParaProximaSemana()).isTrue();
            assertThat(resultado.aviso()).isNull();
        }

        @Test
        @DisplayName("plano passado sem treinos elegíveis é fechado (não reprocessa)")
        void planoVazioPassadoFecha() {
            LocalDate hoje = LocalDate.of(2026, 7, 10);
            UUID planoId = UUID.randomUUID();
            PlanoSemanal plano = plano(planoId, LocalDate.of(2026, 7, 5), PlanoStatus.EM_ANDAMENTO);
            stubPlano(planoId, plano);
            when(treinoPlanejadoRepository.findPendentesAteHojeDoPlano(eq(planoId), any()))
                    .thenReturn(List.of());

            EncerramentoSemanaResultado resultado = servico(hoje).encerrarSemana(planoId);

            assertThat(resultado.treinosFinalizados()).isZero();
            assertThat(resultado.novoStatus()).isEqualTo(PlanoStatus.CONCLUIDO);
        }

        @Test
        @DisplayName("publica SemanaEncerradaEvent com os campos obrigatórios e origem ON_DEMAND")
        void publicaSemanaEncerradaEventComCampos() {
            LocalDate domingo = LocalDate.of(2026, 7, 5);
            UUID planoId = UUID.randomUUID();
            PlanoSemanal plano = plano(planoId, domingo, PlanoStatus.EM_ANDAMENTO);
            TreinoPlanejado pendente = treino(LocalDate.of(2026, 7, 4), TreinoExecucaoStatus.PENDENTE);
            stubPlano(planoId, plano);
            when(treinoPlanejadoRepository.findPendentesAteHojeDoPlano(eq(planoId), any()))
                    .thenReturn(List.of(pendente));

            servico(domingo).encerrarSemana(planoId);

            ArgumentCaptor<SemanaEncerradaEvent> eventoCaptor = ArgumentCaptor.forClass(SemanaEncerradaEvent.class);
            verify(eventPublisher).publishEvent(eventoCaptor.capture());
            SemanaEncerradaEvent evento = eventoCaptor.getValue();
            assertThat(evento.planoId()).isEqualTo(planoId);
            assertThat(evento.atletaId()).isEqualTo(plano.getAtleta().getId());
            assertThat(evento.tenantId()).isEqualTo(tenantId);
            assertThat(evento.treinosPerdidos()).isEqualTo(1);
            assertThat(evento.origem()).isEqualTo(OrigemEncerramento.ON_DEMAND);
        }

        @Test
        @DisplayName("persiste origem ON_DEMAND quando o encerramento on-demand fecha o plano")
        void persisteOrigemOnDemand() {
            LocalDate domingo = LocalDate.of(2026, 7, 5);
            UUID planoId = UUID.randomUUID();
            PlanoSemanal plano = plano(planoId, domingo, PlanoStatus.EM_ANDAMENTO);
            stubPlano(planoId, plano);
            when(treinoPlanejadoRepository.findPendentesAteHojeDoPlano(eq(planoId), any()))
                    .thenReturn(List.of());

            servico(domingo).encerrarSemana(planoId);

            assertThat(plano.getOrigemEncerramento()).isEqualTo(OrigemEncerramento.ON_DEMAND);
        }

        @Test
        @DisplayName("persiste origem AUTOMATICO quando o fallback fecha o plano")
        void persisteOrigemAutomatico() {
            LocalDate hoje = LocalDate.of(2026, 7, 10);
            UUID planoId = UUID.randomUUID();
            PlanoSemanal plano = plano(planoId, LocalDate.of(2026, 7, 5), PlanoStatus.EM_ANDAMENTO);
            when(treinoPlanejadoRepository.findPendentesAteHojeDoPlano(eq(planoId), any()))
                    .thenReturn(List.of());

            servico(hoje).encerrarPlano(plano, hoje, OrigemEncerramento.AUTOMATICO);

            assertThat(plano.getOrigemEncerramento()).isEqualTo(OrigemEncerramento.AUTOMATICO);
        }

        @Test
        @DisplayName("não registra origem no meio da semana (plano não fecha)")
        void naoRegistraOrigemNoMeioDaSemana() {
            LocalDate terca = LocalDate.of(2026, 7, 7);
            UUID planoId = UUID.randomUUID();
            PlanoSemanal plano = plano(planoId, LocalDate.of(2026, 7, 12), PlanoStatus.EM_ANDAMENTO);
            stubPlano(planoId, plano);
            when(treinoPlanejadoRepository.findPendentesAteHojeDoPlano(eq(planoId), any()))
                    .thenReturn(List.of());

            servico(terca).encerrarSemana(planoId);

            assertThat(plano.getOrigemEncerramento()).isNull();
        }
    }

    @Nested
    @DisplayName("encerrarSemanaLoteAssessoria")
    class EncerrarLote {

        @Test
        @DisplayName("encerra a semana de todos os atletas e consolida os totais")
        void encerraTodosOsAtletas() {
            LocalDate domingo = LocalDate.of(2026, 7, 5);
            Atleta a1 = atleta();
            Atleta a2 = atleta();
            PlanoSemanal p1 = plano(UUID.randomUUID(), domingo, PlanoStatus.EM_ANDAMENTO);
            PlanoSemanal p2 = plano(UUID.randomUUID(), domingo, PlanoStatus.EM_ANDAMENTO);
            when(atletaRepository.findAllAtletas(tenantId)).thenReturn(List.of(a1, a2));
            when(planoSemanalRepository.findSemanaCorrente(eq(a1.getId()), any(), any())).thenReturn(List.of(p1));
            when(planoSemanalRepository.findSemanaCorrente(eq(a2.getId()), any(), any())).thenReturn(List.of(p2));
            when(planoSemanalRepository.findByIdAndTenantId(p1.getId(), tenantId)).thenReturn(Optional.of(p1));
            when(planoSemanalRepository.findByIdAndTenantId(p2.getId(), tenantId)).thenReturn(Optional.of(p2));
            when(treinoPlanejadoRepository.findPendentesAteHojeDoPlano(eq(p1.getId()), any()))
                    .thenReturn(List.of(treino(LocalDate.of(2026, 7, 4), TreinoExecucaoStatus.PENDENTE)));
            when(treinoPlanejadoRepository.findPendentesAteHojeDoPlano(eq(p2.getId()), any()))
                    .thenReturn(List.of(treino(LocalDate.of(2026, 7, 4), TreinoExecucaoStatus.PENDENTE)));

            EncerramentoLoteResultado resultado = servico(domingo).encerrarSemanaLoteAssessoria();

            assertThat(resultado.atletasProcessados()).isEqualTo(2);
            assertThat(resultado.atletasSemPlano()).isZero();
            assertThat(resultado.planosConcluidos()).isEqualTo(2);
            assertThat(resultado.treinosPerdidosTotal()).isEqualTo(2);
            assertThat(resultado.falhas()).isEmpty();
        }

        @Test
        @DisplayName("atleta sem semana corrente é ignorado (não é falha)")
        void atletaSemPlanoEIgnorado() {
            LocalDate domingo = LocalDate.of(2026, 7, 5);
            Atleta comPlano = atleta();
            Atleta semPlano = atleta();
            PlanoSemanal p1 = plano(UUID.randomUUID(), domingo, PlanoStatus.EM_ANDAMENTO);
            when(atletaRepository.findAllAtletas(tenantId)).thenReturn(List.of(comPlano, semPlano));
            when(planoSemanalRepository.findSemanaCorrente(eq(comPlano.getId()), any(), any())).thenReturn(List.of(p1));
            when(planoSemanalRepository.findSemanaCorrente(eq(semPlano.getId()), any(), any())).thenReturn(List.of());
            when(planoSemanalRepository.findByIdAndTenantId(p1.getId(), tenantId)).thenReturn(Optional.of(p1));
            when(treinoPlanejadoRepository.findPendentesAteHojeDoPlano(eq(p1.getId()), any())).thenReturn(List.of());

            EncerramentoLoteResultado resultado = servico(domingo).encerrarSemanaLoteAssessoria();

            assertThat(resultado.atletasProcessados()).isEqualTo(1);
            assertThat(resultado.atletasSemPlano()).isEqualTo(1);
            assertThat(resultado.falhas()).isEmpty();
        }

        @Test
        @DisplayName("usa a fonte de atletas tenant-scoped do tenant corrente")
        void usaFonteTenantScoped() {
            LocalDate domingo = LocalDate.of(2026, 7, 5);
            when(atletaRepository.findAllAtletas(tenantId)).thenReturn(List.of());

            servico(domingo).encerrarSemanaLoteAssessoria();

            verify(atletaRepository).findAllAtletas(tenantId);
        }

        @Test
        @DisplayName("falha de um atleta não aborta o lote (reportada em falhas)")
        void falhaParcialNaoAbortaOLote() {
            LocalDate domingo = LocalDate.of(2026, 7, 5);
            Atleta ok = atleta();
            Atleta comFalha = atleta();
            PlanoSemanal pOk = plano(UUID.randomUUID(), domingo, PlanoStatus.EM_ANDAMENTO);
            UUID planoFalhaId = UUID.randomUUID();
            when(atletaRepository.findAllAtletas(tenantId)).thenReturn(List.of(ok, comFalha));
            when(planoSemanalRepository.findSemanaCorrente(eq(ok.getId()), any(), any())).thenReturn(List.of(pOk));
            when(planoSemanalRepository.findSemanaCorrente(eq(comFalha.getId()), any(), any()))
                    .thenReturn(List.of(plano(planoFalhaId, domingo, PlanoStatus.EM_ANDAMENTO)));
            when(planoSemanalRepository.findByIdAndTenantId(pOk.getId(), tenantId)).thenReturn(Optional.of(pOk));
            // o plano do atleta com falha não é encontrado ao recarregar → DomainNotFoundException na sua transação
            when(planoSemanalRepository.findByIdAndTenantId(planoFalhaId, tenantId)).thenReturn(Optional.empty());
            when(treinoPlanejadoRepository.findPendentesAteHojeDoPlano(eq(pOk.getId()), any())).thenReturn(List.of());

            EncerramentoLoteResultado resultado = servico(domingo).encerrarSemanaLoteAssessoria();

            assertThat(resultado.atletasProcessados()).isEqualTo(1);
            assertThat(resultado.falhas()).hasSize(1);
            assertThat(resultado.falhas().get(0).atletaId()).isEqualTo(comFalha.getId());
        }
    }

    @Nested
    @DisplayName("previewLoteAssessoria")
    class PreviewLote {

        @Test
        @DisplayName("projeta o impacto sem persistir (não marca nem salva)")
        void projetaSemPersistir() {
            LocalDate domingo = LocalDate.of(2026, 7, 5);
            Atleta a1 = atleta();
            PlanoSemanal p1 = plano(UUID.randomUUID(), domingo, PlanoStatus.EM_ANDAMENTO);
            when(atletaRepository.findAllAtletas(tenantId)).thenReturn(List.of(a1));
            when(planoSemanalRepository.findSemanaCorrente(eq(a1.getId()), any(), any())).thenReturn(List.of(p1));
            when(treinoPlanejadoRepository.findPendentesAteHojeDoPlano(eq(p1.getId()), any()))
                    .thenReturn(List.of(treino(LocalDate.of(2026, 7, 4), TreinoExecucaoStatus.PENDENTE)));

            EncerramentoLoteResultado preview = servico(domingo).previewLoteAssessoria();

            assertThat(preview.atletasProcessados()).isEqualTo(1);
            assertThat(preview.treinosPerdidosTotal()).isEqualTo(1);
            assertThat(preview.planosConcluidos()).isEqualTo(1);
            verify(treinoService, never()).marcarTreinoPerdido(any());
            verify(planoSemanalRepository, never()).save(any());
        }
    }

    // ---- helpers ----

    private EncerramentoSemanaServiceImpl servico(LocalDate hoje) {
        return servicoComClock(Clock.fixed(hoje.atStartOfDay(ZONA).toInstant(), ZONA));
    }

    private EncerramentoSemanaServiceImpl servicoComClock(Clock clock) {
        return new EncerramentoSemanaServiceImpl(
                planoSemanalRepository, treinoPlanejadoRepository, atletaRepository, treinoService,
                eventPublisher, transactionManager, clock);
    }

    private void stubPlano(UUID planoId, PlanoSemanal plano) {
        when(planoSemanalRepository.findByIdAndTenantId(planoId, tenantId)).thenReturn(Optional.of(plano));
    }

    private PlanoSemanal plano(UUID id, LocalDate semanaFim, PlanoStatus status) {
        Assessoria assessoria = Assessoria.builder().id(tenantId).build();
        Atleta atleta = Atleta.builder().id(UUID.randomUUID()).build();
        return PlanoSemanal.builder()
                .id(id)
                .atleta(atleta)
                .assessoria(assessoria)
                .semanaInicio(semanaFim.minusDays(6))
                .semanaFim(semanaFim)
                .status(status)
                .build();
    }

    private Atleta atleta() {
        return Atleta.builder().id(UUID.randomUUID()).build();
    }

    private TreinoPlanejado treino(LocalDate data, TreinoExecucaoStatus status) {
        TreinoPlanejado treino = new TreinoPlanejado();
        treino.setId(UUID.randomUUID());
        treino.setDataTreino(data);
        treino.setStatusTreino(status);
        return treino;
    }
}
