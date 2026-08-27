package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.dto.input.TreinoRealizadoInputDto;
import br.com.menthoros.backend.dto.output.ResumoSemanalTreinoDto;
import br.com.menthoros.backend.dto.output.TreinoRealizadoOutputDto;
import br.com.menthoros.backend.entity.Assessoria;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.PlanoSemanal;
import br.com.menthoros.backend.entity.TreinoPlanejado;
import br.com.menthoros.backend.entity.TreinoRealizado;
import br.com.menthoros.backend.enums.FonteDados;
import br.com.menthoros.backend.enums.StatusSincronizacao;
import br.com.menthoros.backend.enums.TreinoExecucaoStatus;
import br.com.menthoros.backend.events.TreinoRegistradoEvent;
import br.com.menthoros.backend.exception.DomainNotFoundException;
import br.com.menthoros.backend.exception.DomainRuleViolationException;
import br.com.menthoros.backend.mapper.PlanoSemanalMapper;
import br.com.menthoros.backend.mapper.TreinoMapper;
import br.com.menthoros.backend.multitenancy.TenantContext;
import br.com.menthoros.backend.repository.AtletaRepository;
import br.com.menthoros.backend.repository.PlanoMetadadosRepository;
import br.com.menthoros.backend.repository.PlanoSemanalRepository;
import br.com.menthoros.backend.repository.TreinoPlanejadoRepository;
import br.com.menthoros.backend.repository.TreinoRealizadoRepository;
import br.com.menthoros.backend.services.helper.TipoTreinoConsistenciaValidator;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Testes unitários do {@link TreinoServiceImpl} com JUnit 5 e Mockito.
 *
 * <p>O {@code @ExtendWith(MockitoExtension.class)} habilita as anotações
 * {@code @Mock}/{@code @InjectMocks}. O {@link TenantContext} é configurado
 * por teste, pois os métodos do service resolvem o tenant via
 * {@code TenantContext.getRequiredTenantId()}.
 *
 * <p>Os casos são agrupados por método do service em classes {@code @Nested}.
 */
@ExtendWith(MockitoExtension.class)
class TreinoServiceImplTest {

    @Mock
    private TreinoMapper treinoMapper;
    @Mock
    private TreinoRealizadoRepository treinoRealizadoRepository;
    @Mock
    private AtletaRepository atletaRepository;
    @Mock
    private PlanoSemanalRepository planoSemanalRepository;
    @Mock
    private PlanoSemanalMapper planoSemanalMapper;
    @Mock
    private TreinoPlanejadoRepository treinoPlanejadoRepository;
    @Mock
    private br.com.menthoros.backend.services.IngestaoTreinoRealizadoService ingestaoTreinoRealizadoService;
    @Mock
    private PlanoMetadadosRepository planoMetaDadosRepository;
    @Mock
    private org.springframework.context.ApplicationEventPublisher eventPublisher;
    @Mock
    private TipoTreinoConsistenciaValidator tipoTreinoConsistenciaValidator;
    @Mock
    private java.time.Clock clock;

    @InjectMocks
    private TreinoServiceImpl treinoService;

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
    @DisplayName("marcarTreinosPerdidos")
    class MarcarTreinosPerdidos {

        @Test
        @DisplayName("lista vazia é no-op (não salva)")
        void listaVaziaNoOp() {
            treinoService.marcarTreinosPerdidos(planoDoTenant(), java.util.List.of());
            org.mockito.Mockito.verify(treinoPlanejadoRepository, org.mockito.Mockito.never())
                    .saveAll(org.mockito.ArgumentMatchers.any());
        }

        @Test
        @DisplayName("ignora treinos não-PENDENTE (não sobrescreve REALIZADO)")
        void ignoraNaoPendente() {
            PlanoSemanal plano = planoDoTenant();
            TreinoPlanejado pendente = planejado(br.com.menthoros.backend.enums.TreinoExecucaoStatus.PENDENTE);
            TreinoPlanejado realizado = planejado(br.com.menthoros.backend.enums.TreinoExecucaoStatus.REALIZADO);

            treinoService.marcarTreinosPerdidos(plano, java.util.List.of(pendente, realizado));

            org.assertj.core.api.Assertions.assertThat(pendente.getStatusTreino())
                    .isEqualTo(br.com.menthoros.backend.enums.TreinoExecucaoStatus.PERDIDO);
            org.assertj.core.api.Assertions.assertThat(realizado.getStatusTreino())
                    .isEqualTo(br.com.menthoros.backend.enums.TreinoExecucaoStatus.REALIZADO);
            org.mockito.Mockito.verify(treinoPlanejadoRepository).saveAll(java.util.List.of(pendente));
        }

        @Test
        @DisplayName("rejeita plano de outro tenant")
        void rejeitaOutroTenant() {
            PlanoSemanal plano = new PlanoSemanal();
            plano.setAssessoria(Assessoria.builder().id(UUID.randomUUID()).build());
            TreinoPlanejado pendente = planejado(br.com.menthoros.backend.enums.TreinoExecucaoStatus.PENDENTE);

            org.assertj.core.api.Assertions.assertThatThrownBy(
                            () -> treinoService.marcarTreinosPerdidos(plano, java.util.List.of(pendente)))
                    .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
            org.mockito.Mockito.verify(treinoPlanejadoRepository, org.mockito.Mockito.never())
                    .saveAll(org.mockito.ArgumentMatchers.any());
        }

        @Test
        @DisplayName("marca em lote e recalcula o status do plano uma vez")
        void marcaERecalcula() {
            PlanoSemanal plano = planoDoTenant();
            TreinoPlanejado pendente = planejado(br.com.menthoros.backend.enums.TreinoExecucaoStatus.PENDENTE);
            plano.setTreinosPlanejados(new java.util.ArrayList<>(java.util.List.of(pendente)));

            treinoService.marcarTreinosPerdidos(plano, java.util.List.of(pendente));

            org.mockito.Mockito.verify(treinoPlanejadoRepository).saveAll(java.util.List.of(pendente));
            org.mockito.Mockito.verify(planoSemanalRepository).save(plano);
        }

        private PlanoSemanal planoDoTenant() {
            PlanoSemanal p = new PlanoSemanal();
            p.setId(UUID.randomUUID());
            p.setAssessoria(Assessoria.builder().id(tenantId).build());
            p.setStatus(br.com.menthoros.backend.enums.PlanoStatus.EM_ANDAMENTO);
            p.setTreinosPlanejados(new java.util.ArrayList<>());
            return p;
        }

        private TreinoPlanejado planejado(br.com.menthoros.backend.enums.TreinoExecucaoStatus status) {
            TreinoPlanejado t = new TreinoPlanejado();
            t.setId(UUID.randomUUID());
            t.setStatusTreino(status);
            return t;
        }
    }

    @Nested
    @DisplayName("marcarTreinoPerdido")
    class MarcarTreinoPerdido {

        @Test
        @DisplayName("lança DomainNotFoundException quando planejado não existe no tenant")
        void lancaQuandoNaoEncontrado() {
            UUID treinoPlanejadoId = UUID.randomUUID();
            when(treinoPlanejadoRepository.findByIdAndTenantId(treinoPlanejadoId, tenantId))
                    .thenReturn(Optional.empty());

            assertThrows(DomainNotFoundException.class,
                    () -> treinoService.marcarTreinoPerdido(treinoPlanejadoId));

            verify(treinoPlanejadoRepository, never()).save(any());
        }

        @Test
        @DisplayName("rejeita treino já REALIZADO")
        void rejeitaRealizado() {
            UUID treinoPlanejadoId = UUID.randomUUID();
            TreinoPlanejado planejado = new TreinoPlanejado();
            planejado.setId(treinoPlanejadoId);
            planejado.setStatusTreino(TreinoExecucaoStatus.REALIZADO);

            when(treinoPlanejadoRepository.findByIdAndTenantId(treinoPlanejadoId, tenantId))
                    .thenReturn(Optional.of(planejado));

            assertThrows(DomainRuleViolationException.class,
                    () -> treinoService.marcarTreinoPerdido(treinoPlanejadoId));

            verify(treinoPlanejadoRepository, never()).save(any());
        }

        @Test
        @DisplayName("idempotente quando já está PERDIDO (não persiste de novo)")
        void idempotenteQuandoJaPerdido() {
            UUID treinoPlanejadoId = UUID.randomUUID();
            TreinoPlanejado planejado = new TreinoPlanejado();
            planejado.setId(treinoPlanejadoId);
            planejado.setStatusTreino(TreinoExecucaoStatus.PERDIDO);

            when(treinoPlanejadoRepository.findByIdAndTenantId(treinoPlanejadoId, tenantId))
                    .thenReturn(Optional.of(planejado));

            treinoService.marcarTreinoPerdido(treinoPlanejadoId);

            verify(treinoPlanejadoRepository, never()).save(any());
            assertEquals(TreinoExecucaoStatus.PERDIDO, planejado.getStatusTreino());
        }

        @Test
        @DisplayName("marca PENDENTE como PERDIDO sem plano semanal vinculado")
        void marcaSemPlanoSemanal() {
            UUID treinoPlanejadoId = UUID.randomUUID();
            TreinoPlanejado planejado = new TreinoPlanejado();
            planejado.setId(treinoPlanejadoId);
            planejado.setStatusTreino(TreinoExecucaoStatus.PENDENTE);
            planejado.setPlanoSemanal(null);

            when(treinoPlanejadoRepository.findByIdAndTenantId(treinoPlanejadoId, tenantId))
                    .thenReturn(Optional.of(planejado));

            treinoService.marcarTreinoPerdido(treinoPlanejadoId);

            assertEquals(TreinoExecucaoStatus.PERDIDO, planejado.getStatusTreino());
            verify(treinoPlanejadoRepository).save(planejado);
            verify(planoSemanalRepository, never()).save(any());
        }

        @Test
        @DisplayName("marca PERDIDO e recalcula status do plano semanal")
        void marcaComPlanoSemanal() {
            UUID treinoPlanejadoId = UUID.randomUUID();

            PlanoSemanal semanal = new PlanoSemanal();
            semanal.setId(UUID.randomUUID());

            TreinoPlanejado planejado = new TreinoPlanejado();
            planejado.setId(treinoPlanejadoId);
            planejado.setStatusTreino(TreinoExecucaoStatus.PENDENTE);
            planejado.setPlanoSemanal(semanal);

            semanal.setTreinosPlanejados(new ArrayList<>(List.of(planejado)));

            when(treinoPlanejadoRepository.findByIdAndTenantId(treinoPlanejadoId, tenantId))
                    .thenReturn(Optional.of(planejado));

            try (MockedStatic<Hibernate> hibernate = mockStatic(Hibernate.class)) {
                hibernate.when(() -> Hibernate.initialize(any())).thenAnswer(inv -> null);

                treinoService.marcarTreinoPerdido(treinoPlanejadoId);
            }

            assertEquals(TreinoExecucaoStatus.PERDIDO, planejado.getStatusTreino());
            verify(treinoPlanejadoRepository).save(planejado);
            verify(planoSemanalRepository).save(semanal);
        }
    }

    @Nested
    @DisplayName("updateTreino")
    class UpdateTreino {

        @Test
        @DisplayName("lança DomainNotFoundException quando treino não existe")
        void lancaQuandoNaoEncontrado() {
            UUID id = UUID.randomUUID();
            TreinoRealizadoInputDto dto = novoInput(UUID.randomUUID(), null, null, null, null);

            when(treinoRealizadoRepository.findByIdAndTenantId(id, tenantId))
                    .thenReturn(Optional.empty());

            assertThrows(DomainNotFoundException.class, () -> treinoService.updateTreino(id, dto));

            verify(treinoRealizadoRepository, never()).save(any());
            verify(eventPublisher, never()).publishEvent(any());
        }

        @Test
        @DisplayName("publica TreinoRegistradoEvent quando percepcaoEsforco está presente")
        void publicaEventoComPercepcao() {
            UUID id = UUID.randomUUID();
            TreinoRealizadoInputDto dto = novoInput(UUID.randomUUID(), 8, 10.5, LocalDate.now(), null);

            TreinoRealizado treino = new TreinoRealizado();
            treino.setId(id);
            treino.setTenantId(tenantId);

            when(treinoRealizadoRepository.findByIdAndTenantId(id, tenantId))
                    .thenReturn(Optional.of(treino));
            when(treinoRealizadoRepository.save(treino)).thenReturn(treino);
            when(tipoTreinoConsistenciaValidator.validarEstrutura(treino)).thenReturn(Optional.empty());
            TreinoRealizadoOutputDto outputStub = outputStub(id);
            when(treinoMapper.toOutputDto(treino)).thenReturn(outputStub);

            TreinoRealizadoOutputDto resultado = treinoService.updateTreino(id, dto);

            assertSame(outputStub, resultado);
            assertEquals(8, treino.getPercepcaoEsforco());

            ArgumentCaptor<TreinoRegistradoEvent> captor = ArgumentCaptor.forClass(TreinoRegistradoEvent.class);
            verify(eventPublisher).publishEvent(captor.capture());
            assertEquals(id, captor.getValue().treinoRealizadoId());
            assertEquals(tenantId, captor.getValue().tenantId());
        }

        @Test
        @DisplayName("não publica evento quando percepcaoEsforco é nula")
        void semPercepcaoNaoPublicaEvento() {
            UUID id = UUID.randomUUID();
            TreinoRealizadoInputDto dto = novoInput(UUID.randomUUID(), null, 10.5, LocalDate.now(), null);

            TreinoRealizado treino = new TreinoRealizado();
            treino.setId(id);
            treino.setTenantId(tenantId);

            when(treinoRealizadoRepository.findByIdAndTenantId(id, tenantId))
                    .thenReturn(Optional.of(treino));
            when(treinoRealizadoRepository.save(treino)).thenReturn(treino);
            when(tipoTreinoConsistenciaValidator.validarEstrutura(treino)).thenReturn(Optional.empty());
            when(treinoMapper.toOutputDto(treino)).thenReturn(outputStub(id));

            treinoService.updateTreino(id, dto);

            verify(eventPublisher, never()).publishEvent(any());
        }

        @Test
        @DisplayName("recalcula a carga do dia via reprocessar (antes nunca tocava TSB/MetricasDiarias)")
        void recalculaCargaViaReprocessar() {
            UUID id = UUID.randomUUID();
            LocalDate data = LocalDate.of(2026, 5, 1);
            TreinoRealizadoInputDto dto = novoInput(UUID.randomUUID(), 6, null, data, null);

            TreinoRealizado treino = new TreinoRealizado();
            treino.setId(id);
            treino.setTenantId(tenantId);
            treino.setDataTreino(data);

            when(treinoRealizadoRepository.findByIdAndTenantId(id, tenantId))
                    .thenReturn(Optional.of(treino));
            when(treinoRealizadoRepository.save(treino)).thenReturn(treino);
            when(tipoTreinoConsistenciaValidator.validarEstrutura(treino)).thenReturn(Optional.empty());
            when(treinoMapper.toOutputDto(treino)).thenReturn(outputStub(id));

            treinoService.updateTreino(id, dto);

            // dataTreino é imutável via updateTreino (não mexido por applyMutableFields,
            // UpdateTreinoIntegrationTest.updateTreino_doesNotOverwriteImmutableFields garante
            // isso) — dataAnterior é sempre null aqui, mas o recálculo em si é o que fecha o gap.
            verify(ingestaoTreinoRealizadoService).reprocessar(id, null);
        }
    }

    @Nested
    @DisplayName("lancarTreino")
    class LancarTreino {

        @Test
        @DisplayName("lança DomainNotFoundException quando atleta não pertence ao tenant")
        void lancaQuandoAtletaNaoEncontrado() {
            UUID atletaId = UUID.randomUUID();
            TreinoRealizadoInputDto dto = novoInput(atletaId, 5, 8.0, LocalDate.now(), null);

            when(atletaRepository.findByIdAndTenantId(atletaId, tenantId)).thenReturn(Optional.empty());

            assertThrows(DomainNotFoundException.class, () -> treinoService.lancarTreino(atletaId, dto));

            verify(treinoRealizadoRepository, never()).save(any());
            verifyNoInteractions(ingestaoTreinoRealizadoService);
        }

        @Test
        @DisplayName("monta a entidade e delega dedup/evento/carga ao seam de ingestão (registrar)")
        void persisteEDelegaAoIngestor() {
            UUID atletaId = UUID.randomUUID();
            LocalDate dataTreino = LocalDate.of(2026, 5, 10);
            TreinoRealizadoInputDto dto = novoInput(atletaId, 7, 12.0, dataTreino, null);

            Atleta atleta = criarAtleta(atletaId);
            TreinoRealizado entidade = new TreinoRealizado();
            TreinoRealizado salvo = new TreinoRealizado();
            salvo.setId(UUID.randomUUID());
            salvo.setTenantId(tenantId);

            when(atletaRepository.findByIdAndTenantId(atletaId, tenantId)).thenReturn(Optional.of(atleta));
            when(treinoMapper.toEntity(dto)).thenReturn(entidade);
            when(ingestaoTreinoRealizadoService.registrar(entidade, null))
                    .thenReturn(new br.com.menthoros.backend.services.helper.TreinoDedupHelper.SaveResult(salvo, true));
            when(tipoTreinoConsistenciaValidator.validarEstrutura(salvo)).thenReturn(Optional.empty());
            when(treinoMapper.toOutputDto(salvo)).thenReturn(outputStub(salvo.getId()));

            treinoService.lancarTreino(atletaId, dto);

            assertEquals(FonteDados.MANUAL, entidade.getFonteDados());
            assertEquals(TreinoExecucaoStatus.REALIZADO, entidade.getStatus());
            assertSame(atleta, entidade.getAtleta());
            assertEquals(tenantId, entidade.getTenantId());
            assertEquals(dataTreino, entidade.getDataTreino());

            verify(ingestaoTreinoRealizadoService).registrar(entidade, null);
            verifyNoInteractions(eventPublisher);
        }
    }

    @Nested
    @DisplayName("addTreino")
    class AddTreino {

        @Test
        @DisplayName("retorna treino existente quando há duplicidade por externalId+atletaId")
        void retornaDuplicado() {
            UUID atletaId = UUID.randomUUID();
            String externalId = "garmin-123";
            TreinoRealizadoInputDto dto = novoInput(atletaId, 6, 9.0, LocalDate.now(), externalId);

            TreinoRealizado existente = new TreinoRealizado();
            existente.setId(UUID.randomUUID());

            when(treinoRealizadoRepository.findByExternalIdAndAtletaId(externalId, atletaId))
                    .thenReturn(Optional.of(existente));

            TreinoRealizado resultado = treinoService.addTreino(null, dto);

            assertSame(existente, resultado);
            verify(atletaRepository, never()).findByIdAndTenantId(any(), any());
            verify(treinoRealizadoRepository, never()).save(any());
        }

        @Test
        @DisplayName("lança DomainNotFoundException quando atleta não pertence ao tenant")
        void lancaQuandoAtletaNaoEncontrado() {
            UUID atletaId = UUID.randomUUID();
            TreinoRealizadoInputDto dto = novoInput(atletaId, 6, 9.0, LocalDate.now(), null);

            when(atletaRepository.findByIdAndTenantId(atletaId, tenantId)).thenReturn(Optional.empty());

            assertThrows(DomainNotFoundException.class, () -> treinoService.addTreino(null, dto));

            verify(treinoRealizadoRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("getResumoSemanal")
    class GetResumoSemanal {

        @Test
        @DisplayName("lança DomainNotFoundException quando atleta não pertence ao tenant")
        void lancaQuandoAtletaNaoEncontrado() {
            UUID atletaId = UUID.randomUUID();
            when(atletaRepository.findByIdAndTenantId(atletaId, tenantId)).thenReturn(Optional.empty());

            assertThrows(DomainNotFoundException.class,
                    () -> treinoService.getResumoSemanal(atletaId, LocalDate.now()));
        }

        @Test
        @DisplayName("retorna resumo zerado quando não há treinos na semana")
        void semTreinos() {
            UUID atletaId = UUID.randomUUID();
            Atleta atleta = criarAtleta(atletaId);

            when(atletaRepository.findByIdAndTenantId(atletaId, tenantId)).thenReturn(Optional.of(atleta));
            when(treinoRealizadoRepository.findRealizedTrainingsByWeek(eq(atletaId), any(), any()))
                    .thenReturn(List.of());

            ResumoSemanalTreinoDto resultado = treinoService.getResumoSemanal(atletaId, LocalDate.of(2026, 5, 6));

            assertNotNull(resultado);
            assertEquals(atletaId, resultado.atletaId());
            assertEquals(atleta.getNome(), resultado.nomeAtleta());
            assertEquals(0, resultado.resumo().totalTreinos());
            assertEquals(0, resultado.resumo().diasComTreino());
            assertEquals(7, resultado.resumo().diasSemTreino());
            assertNull(resultado.resumo().ultimoTreino());
            assertEquals(7, resultado.resumo().diasDaSemana().size());
        }

        @Test
        @DisplayName("D8: cancelado não conta no resumo, NULL conta [task 7.7]")
        void canceladoNaoContaNullConta() {
            UUID atletaId = UUID.randomUUID();
            Atleta atleta = criarAtleta(atletaId);
            LocalDate data = LocalDate.of(2026, 5, 6);

            TreinoRealizado cancelado = new TreinoRealizado();
            cancelado.setDataTreino(data);
            cancelado.setStatusSincronizacao(StatusSincronizacao.CANCELADO);

            TreinoRealizado semStatus = new TreinoRealizado();
            semStatus.setDataTreino(data);
            semStatus.setStatusSincronizacao(null);

            when(atletaRepository.findByIdAndTenantId(atletaId, tenantId)).thenReturn(Optional.of(atleta));
            when(treinoRealizadoRepository.findRealizedTrainingsByWeek(eq(atletaId), any(), any()))
                    .thenReturn(List.of(cancelado, semStatus));

            ResumoSemanalTreinoDto resultado = treinoService.getResumoSemanal(atletaId, data);

            assertEquals(1, resultado.resumo().totalTreinos(),
                    "só o treino sem status (NULL) conta — o CANCELADO fica de fora");
        }
    }

    @Nested
    @DisplayName("getTreinoById")
    class GetTreinoById {

        @Test
        @DisplayName("retorna o DTO de DETALHE (com série de EF) do treino realizado no escopo do tenant")
        void retornaDtoQuandoEncontrado() {
            UUID id = UUID.randomUUID();
            TreinoRealizado treino = new TreinoRealizado();
            treino.setId(id);
            treino.setTenantId(tenantId);
            TreinoRealizadoOutputDto stub = outputStub(id);

            when(treinoRealizadoRepository.findByIdAndTenantId(id, tenantId))
                    .thenReturn(Optional.of(treino));
            when(treinoMapper.toOutputDtoDetalhado(treino)).thenReturn(stub);

            assertSame(stub, treinoService.getTreinoById(id));
            verify(treinoMapper, never()).toOutputDto(any(TreinoRealizado.class));
        }

        @Test
        @DisplayName("lança DomainNotFoundException quando não existe no tenant")
        void lancaQuandoNaoEncontrado() {
            UUID id = UUID.randomUUID();
            when(treinoRealizadoRepository.findByIdAndTenantId(id, tenantId))
                    .thenReturn(Optional.empty());

            assertThrows(DomainNotFoundException.class, () -> treinoService.getTreinoById(id));
            verify(treinoMapper, never()).toOutputDtoDetalhado(any(TreinoRealizado.class));
        }

        @Test
        @DisplayName("id de outro tenant resolve para not-found (isolamento)")
        void isolaCrossTenant() {
            UUID idDeOutroTenant = UUID.randomUUID();
            // O treino existe, mas noutro tenant → a query tenant-scoped não o retorna ao tenant corrente.
            when(treinoRealizadoRepository.findByIdAndTenantId(idDeOutroTenant, tenantId))
                    .thenReturn(Optional.empty());

            assertThrows(DomainNotFoundException.class, () -> treinoService.getTreinoById(idDeOutroTenant));
            verify(treinoMapper, never()).toOutputDto(any(TreinoRealizado.class));
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private Atleta criarAtleta(UUID atletaId) {
        Assessoria assessoria = new Assessoria();
        assessoria.setId(tenantId);

        Atleta atleta = new Atleta();
        atleta.setId(atletaId);
        atleta.setNome("João Silva");
        atleta.setAssessoria(assessoria);
        atleta.setMetricasDiarias(new ArrayList<>());
        return atleta;
    }

    private TreinoRealizadoInputDto novoInput(UUID atletaId, Integer percepcaoEsforco,
                                              Double distanciaKm, LocalDate dataTreino, String externalId) {
        return new TreinoRealizadoInputDto(
                atletaId,          // atletaId
                null,              // planoSemanalId
                null,              // treinoPlanejadoId
                dataTreino,        // dataTreino
                null,              // diaSemana
                null,              // tipoTreino
                null,              // descricao
                null,              // zonaAlvo
                null,              // duracaoMin
                distanciaKm,       // distanciaKm
                null,              // ritmoAlvo
                null,              // ritmoMedio
                null,              // elevacaoGanhoMetros
                null,              // elevacaoPerdaMetros
                null,              // observacao
                null,              // fcMedia
                null,              // fcMax
                null,              // cadenciaMedia
                null,              // potenciaMedia
                null,              // velocidadeMedia
                percepcaoEsforco,  // percepcaoEsforco
                null,              // feedbackAtleta
                null,              // qualidadeSonoNoiteAnterior
                null,              // nivelEstresse
                null,              // fonteDados
                null,              // status
                externalId,        // externalId
                null               // etapasRealizadas
        );
    }

    private TreinoRealizadoOutputDto outputStub(UUID id) {
        return new TreinoRealizadoOutputDto(
                id, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null,
                null, null, // intensidadeReal
                null, // runningDynamics
                null, null, null, null, null, null, // decouplingPercentual..nivelEstresse
                null, null, null, // nivelDor, nivelFadiga, nivelRecuperacao
                null, null, null, null, null, null, null
        );
    }
}
