package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.entity.Assessoria;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.PlanoSemanal;
import br.com.menthoros.backend.entity.TreinoPlanejado;
import br.com.menthoros.backend.enums.OrigemEncerramento;
import br.com.menthoros.backend.enums.PlanoStatus;
import br.com.menthoros.backend.enums.TreinoExecucaoStatus;
import br.com.menthoros.backend.exception.DomainNotFoundException;
import br.com.menthoros.backend.multitenancy.TenantContext;
import br.com.menthoros.backend.repository.AtletaRepository;
import br.com.menthoros.backend.repository.PlanoSemanalRepository;
import br.com.menthoros.backend.repository.TreinoPlanejadoRepository;
import br.com.menthoros.backend.events.SemanaEncerradaEvent;
import br.com.menthoros.backend.services.EncerramentoLoteResultado;
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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.PlatformTransactionManager;

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
    private AtletaRepository atletaRepository;
    @Mock
    private TreinoService treinoService;
    @Mock
    private ApplicationEventPublisher eventPublisher;
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
            PlanoSemanal plano = plano(planoId, domingo, PlanoStatus.EM_ANDAMENTO);
            TreinoPlanejado sabado = treino(LocalDate.of(2026, 7, 4), TreinoExecucaoStatus.PENDENTE);
            TreinoPlanejado longaoDomingo = treino(domingo, TreinoExecucaoStatus.PENDENTE);
            stubPlano(planoId, plano);
            stubPendentes(planoId, List.of(sabado, longaoDomingo));

            EncerramentoSemanaResultado resultado = servico(domingo).encerrarSemana(planoId);

            assertThat(resultado.treinosPerdidos()).containsExactly(sabado.getId(), longaoDomingo.getId());
            assertThat(resultado.treinosFinalizados()).isEqualTo(2);
            verify(treinoService).marcarTreinosPerdidos(eq(plano), eq(List.of(sabado, longaoDomingo)));
        }

        @Test
        @DisplayName("no meio da semana preserva o treino de hoje e avisa que a semana não terminou")
        void noMeioDaSemanaPreservaTreinoDeHoje() {
            LocalDate terca = LocalDate.of(2026, 7, 7);
            UUID planoId = UUID.randomUUID();
            PlanoSemanal plano = plano(planoId, LocalDate.of(2026, 7, 12), PlanoStatus.EM_ANDAMENTO);
            TreinoPlanejado segunda = treino(LocalDate.of(2026, 7, 6), TreinoExecucaoStatus.PENDENTE);
            TreinoPlanejado hoje = treino(terca, TreinoExecucaoStatus.PENDENTE);
            stubPlano(planoId, plano);
            stubPendentes(planoId, List.of(segunda, hoje));

            EncerramentoSemanaResultado resultado = servico(terca).encerrarSemana(planoId);

            assertThat(resultado.treinosPerdidos()).containsExactly(segunda.getId());
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
            stubPendentes(planoId, List.of(pendentePassado, realizado, parcial, futuro));

            EncerramentoSemanaResultado resultado = servico(domingo).encerrarSemana(planoId);

            assertThat(resultado.treinosPerdidos()).containsExactly(pendentePassado.getId());
        }

        @Test
        @DisplayName("é idempotente e NÃO publica evento quando não há nada a fazer")
        void idempotenteQuandoNaoHaPendenteElegivel() {
            LocalDate hoje = LocalDate.of(2026, 7, 10);
            UUID planoId = UUID.randomUUID();
            PlanoSemanal plano = plano(planoId, LocalDate.of(2026, 7, 5), PlanoStatus.CONCLUIDO);
            stubPlano(planoId, plano);
            stubPendentes(planoId, List.of());

            EncerramentoSemanaResultado resultado = servico(hoje).encerrarSemana(planoId);

            assertThat(resultado.treinosFinalizados()).isZero();
            assertThat(resultado.novoStatus()).isEqualTo(PlanoStatus.CONCLUIDO);
            verify(eventPublisher, never()).publishEvent(any());
        }

        @Test
        @DisplayName("resolve a data corrente no fuso America/Sao_Paulo, não no do JVM")
        void usaDataNoFusoSaoPaulo() {
            // Domingo 22h BRT == Segunda 01h UTC. A data corrente deve ser o domingo (05/07), não segunda.
            Clock clockUtc = Clock.fixed(Instant.parse("2026-07-06T01:00:00Z"), ZoneOffset.UTC);
            UUID planoId = UUID.randomUUID();
            PlanoSemanal plano = plano(planoId, LocalDate.of(2026, 7, 5), PlanoStatus.EM_ANDAMENTO);
            stubPlano(planoId, plano);
            when(treinoPlanejadoRepository.findPendentesAteHojeDoPlano(eq(planoId), eq(tenantId), any()))
                    .thenReturn(List.of());

            servicoComClock(clockUtc).encerrarSemana(planoId);

            ArgumentCaptor<LocalDate> hojeCaptor = ArgumentCaptor.forClass(LocalDate.class);
            verify(treinoPlanejadoRepository).findPendentesAteHojeDoPlano(eq(planoId), eq(tenantId), hojeCaptor.capture());
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
            stubPendentes(planoId, List.of(aindaPendente, virouRealizado));

            EncerramentoSemanaResultado resultado = servico(domingo).encerrarSemana(planoId);

            assertThat(resultado.treinosPerdidos()).containsExactly(aindaPendente.getId());
        }

        @Test
        @DisplayName("lança DomainNotFoundException quando o plano não é do tenant corrente")
        void planoDeOutroTenantNaoEncontrado() {
            UUID planoId = UUID.randomUUID();
            when(planoSemanalRepository.findByIdAndTenantId(planoId, tenantId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> servico(LocalDate.of(2026, 7, 5)).encerrarSemana(planoId))
                    .isInstanceOf(DomainNotFoundException.class);

            verify(treinoService, never()).marcarTreinosPerdidos(any(), any());
        }

        @Test
        @DisplayName("fecha o plano (CONCLUIDO) quando a semana terminou")
        void planoFechaQuandoSemanaTerminou() {
            LocalDate domingo = LocalDate.of(2026, 7, 5);
            UUID planoId = UUID.randomUUID();
            PlanoSemanal plano = plano(planoId, domingo, PlanoStatus.EM_ANDAMENTO);
            stubPlano(planoId, plano);
            stubPendentes(planoId, List.of(treino(LocalDate.of(2026, 7, 4), TreinoExecucaoStatus.PENDENTE)));

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
            stubPendentes(planoId, List.of());

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
            stubPlano(planoId, plano);
            stubPendentes(planoId, List.of(treino(LocalDate.of(2026, 7, 4), TreinoExecucaoStatus.PENDENTE)));

            servico(domingo).encerrarSemana(planoId);

            ArgumentCaptor<SemanaEncerradaEvent> captor = ArgumentCaptor.forClass(SemanaEncerradaEvent.class);
            verify(eventPublisher).publishEvent(captor.capture());
            SemanaEncerradaEvent evento = captor.getValue();
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
            stubPendentes(planoId, List.of());

            servico(domingo).encerrarSemana(planoId);

            assertThat(plano.getOrigemEncerramento()).isEqualTo(OrigemEncerramento.ON_DEMAND);
        }

        @Test
        @DisplayName("não registra origem no meio da semana (plano não fecha)")
        void naoRegistraOrigemNoMeioDaSemana() {
            LocalDate terca = LocalDate.of(2026, 7, 7);
            UUID planoId = UUID.randomUUID();
            PlanoSemanal plano = plano(planoId, LocalDate.of(2026, 7, 12), PlanoStatus.EM_ANDAMENTO);
            stubPlano(planoId, plano);
            stubPendentes(planoId, List.of());

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
            PlanoSemanal p1 = plano(UUID.randomUUID(), domingo, PlanoStatus.EM_ANDAMENTO, a1);
            PlanoSemanal p2 = plano(UUID.randomUUID(), domingo, PlanoStatus.EM_ANDAMENTO, a2);
            when(atletaRepository.findAllAtletas(tenantId)).thenReturn(List.of(a1, a2));
            when(planoSemanalRepository.findSemanasCorrentes(eq(tenantId), any())).thenReturn(List.of(p1, p2));
            when(planoSemanalRepository.findByIdAndTenantId(p1.getId(), tenantId)).thenReturn(Optional.of(p1));
            when(planoSemanalRepository.findByIdAndTenantId(p2.getId(), tenantId)).thenReturn(Optional.of(p2));
            stubPendentes(p1.getId(), List.of(treino(LocalDate.of(2026, 7, 4), TreinoExecucaoStatus.PENDENTE)));
            stubPendentes(p2.getId(), List.of(treino(LocalDate.of(2026, 7, 4), TreinoExecucaoStatus.PENDENTE)));

            EncerramentoLoteResultado resultado = servico(domingo).encerrarSemanaLoteAssessoria(List.of());

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
            PlanoSemanal p1 = plano(UUID.randomUUID(), domingo, PlanoStatus.EM_ANDAMENTO, comPlano);
            when(atletaRepository.findAllAtletas(tenantId)).thenReturn(List.of(comPlano, semPlano));
            when(planoSemanalRepository.findSemanasCorrentes(eq(tenantId), any())).thenReturn(List.of(p1));
            when(planoSemanalRepository.findByIdAndTenantId(p1.getId(), tenantId)).thenReturn(Optional.of(p1));
            stubPendentes(p1.getId(), List.of());

            EncerramentoLoteResultado resultado = servico(domingo).encerrarSemanaLoteAssessoria(List.of());

            assertThat(resultado.atletasProcessados()).isEqualTo(1);
            assertThat(resultado.atletasSemPlano()).isEqualTo(1);
            assertThat(resultado.falhas()).isEmpty();
        }

        @Test
        @DisplayName("usa a fonte de atletas tenant-scoped do tenant corrente")
        void usaFonteTenantScoped() {
            LocalDate domingo = LocalDate.of(2026, 7, 5);
            when(atletaRepository.findAllAtletas(tenantId)).thenReturn(List.of());
            when(planoSemanalRepository.findSemanasCorrentes(eq(tenantId), any())).thenReturn(List.of());

            servico(domingo).encerrarSemanaLoteAssessoria(List.of());

            verify(planoSemanalRepository).findSemanasCorrentes(eq(tenantId), any());
            verify(atletaRepository).findAllAtletas(tenantId);
        }

        @Test
        @DisplayName("falha de um atleta não aborta o lote (reportada em falhas)")
        void falhaParcialNaoAbortaOLote() {
            LocalDate domingo = LocalDate.of(2026, 7, 5);
            Atleta ok = atleta();
            Atleta comFalha = atleta();
            PlanoSemanal pOk = plano(UUID.randomUUID(), domingo, PlanoStatus.EM_ANDAMENTO, ok);
            PlanoSemanal pFalha = plano(UUID.randomUUID(), domingo, PlanoStatus.EM_ANDAMENTO, comFalha);
            when(atletaRepository.findAllAtletas(tenantId)).thenReturn(List.of(ok, comFalha));
            when(planoSemanalRepository.findSemanasCorrentes(eq(tenantId), any())).thenReturn(List.of(pOk, pFalha));
            when(planoSemanalRepository.findByIdAndTenantId(pOk.getId(), tenantId)).thenReturn(Optional.of(pOk));
            // o plano do atleta com falha não é encontrado ao recarregar → DomainNotFoundException na sua transação
            when(planoSemanalRepository.findByIdAndTenantId(pFalha.getId(), tenantId)).thenReturn(Optional.empty());
            stubPendentes(pOk.getId(), List.of());

            EncerramentoLoteResultado resultado = servico(domingo).encerrarSemanaLoteAssessoria(List.of());

            assertThat(resultado.atletasProcessados()).isEqualTo(1);
            assertThat(resultado.falhas()).hasSize(1);
            assertThat(resultado.falhas().get(0).atletaId()).isEqualTo(pFalha.getAtleta().getId());
        }

        @Test
        @DisplayName("encerra apenas os atletas informados quando atletaIds é passado")
        void encerraApenasSelecionados() {
            LocalDate domingo = LocalDate.of(2026, 7, 5);
            Atleta a1 = atleta();
            Atleta a2 = atleta();
            PlanoSemanal p1 = plano(UUID.randomUUID(), domingo, PlanoStatus.EM_ANDAMENTO, a1);
            PlanoSemanal p2 = plano(UUID.randomUUID(), domingo, PlanoStatus.EM_ANDAMENTO, a2);
            when(atletaRepository.findAllAtletas(tenantId)).thenReturn(List.of(a1, a2));
            when(planoSemanalRepository.findSemanasCorrentes(eq(tenantId), any())).thenReturn(List.of(p1, p2));
            when(planoSemanalRepository.findByIdAndTenantId(p1.getId(), tenantId)).thenReturn(Optional.of(p1));
            stubPendentes(p1.getId(), List.of());

            EncerramentoLoteResultado resultado = servico(domingo).encerrarSemanaLoteAssessoria(List.of(a1.getId()));

            assertThat(resultado.atletasProcessados()).isEqualTo(1);
            // o atleta não selecionado (a2) não é recarregado nem encerrado
            verify(planoSemanalRepository, never()).findByIdAndTenantId(p2.getId(), tenantId);
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
            PlanoSemanal p1 = plano(UUID.randomUUID(), domingo, PlanoStatus.EM_ANDAMENTO, a1);
            when(atletaRepository.findAllAtletas(tenantId)).thenReturn(List.of(a1));
            when(planoSemanalRepository.findSemanasCorrentes(eq(tenantId), any())).thenReturn(List.of(p1));
            stubPendentes(p1.getId(), List.of(treino(LocalDate.of(2026, 7, 4), TreinoExecucaoStatus.PENDENTE)));

            EncerramentoLoteResultado preview = servico(domingo).previewLoteAssessoria(List.of());

            assertThat(preview.atletasProcessados()).isEqualTo(1);
            assertThat(preview.treinosPerdidosTotal()).isEqualTo(1);
            assertThat(preview.planosConcluidos()).isEqualTo(1);
            verify(treinoService, never()).marcarTreinosPerdidos(any(), any());
            verify(planoSemanalRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("encerrarPlanosElegiveis (fallback)")
    class EncerrarPlanosElegiveis {

        @Test
        @DisplayName("seleciona por hoje - carência e encerra com origem AUTOMATICO")
        void encerraElegiveisComCarencia() {
            LocalDate hoje = LocalDate.of(2026, 7, 10);
            PlanoSemanal plano = plano(UUID.randomUUID(), LocalDate.of(2026, 7, 1), PlanoStatus.EM_ANDAMENTO);
            when(planoSemanalRepository.findElegiveisFallback(tenantId, LocalDate.of(2026, 7, 5)))
                    .thenReturn(List.of(plano));
            when(planoSemanalRepository.findByIdAndTenantId(plano.getId(), tenantId)).thenReturn(Optional.of(plano));
            stubPendentes(plano.getId(), List.of());

            int encerrados = servico(hoje).encerrarPlanosElegiveis(tenantId, hoje, 5);

            assertThat(encerrados).isEqualTo(1);
            verify(planoSemanalRepository).findElegiveisFallback(tenantId, LocalDate.of(2026, 7, 5));
            assertThat(plano.getOrigemEncerramento()).isEqualTo(OrigemEncerramento.AUTOMATICO);
        }

        @Test
        @DisplayName("retorna 0 quando nenhum plano é elegível")
        void nenhumElegivel() {
            LocalDate hoje = LocalDate.of(2026, 7, 10);
            when(planoSemanalRepository.findElegiveisFallback(eq(tenantId), any())).thenReturn(List.of());

            assertThat(servico(hoje).encerrarPlanosElegiveis(tenantId, hoje, 3)).isZero();
        }

        @Test
        @DisplayName("falha num plano não aborta o fallback (continua nos demais)")
        void falhaNaoAbortaOFallback() {
            LocalDate hoje = LocalDate.of(2026, 7, 10);
            PlanoSemanal ok = plano(UUID.randomUUID(), LocalDate.of(2026, 7, 1), PlanoStatus.EM_ANDAMENTO);
            PlanoSemanal falha = plano(UUID.randomUUID(), LocalDate.of(2026, 7, 1), PlanoStatus.EM_ANDAMENTO);
            when(planoSemanalRepository.findElegiveisFallback(eq(tenantId), any())).thenReturn(List.of(falha, ok));
            when(planoSemanalRepository.findByIdAndTenantId(falha.getId(), tenantId)).thenReturn(Optional.empty());
            when(planoSemanalRepository.findByIdAndTenantId(ok.getId(), tenantId)).thenReturn(Optional.of(ok));
            stubPendentes(ok.getId(), List.of());

            int encerrados = servico(hoje).encerrarPlanosElegiveis(tenantId, hoje, 3);

            assertThat(encerrados).isEqualTo(1);
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

    private void stubPendentes(UUID planoId, List<TreinoPlanejado> pendentes) {
        when(treinoPlanejadoRepository.findPendentesAteHojeDoPlano(eq(planoId), eq(tenantId), any()))
                .thenReturn(pendentes);
    }

    private PlanoSemanal plano(UUID id, LocalDate semanaFim, PlanoStatus status) {
        return plano(id, semanaFim, status, atleta());
    }

    private PlanoSemanal plano(UUID id, LocalDate semanaFim, PlanoStatus status, Atleta atleta) {
        Assessoria assessoria = Assessoria.builder().id(tenantId).build();
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
