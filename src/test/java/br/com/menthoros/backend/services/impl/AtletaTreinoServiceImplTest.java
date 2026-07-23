package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.dto.input.TreinoManualInputDto;
import br.com.menthoros.backend.dto.output.TreinoRealizadoOutputDto;
import br.com.menthoros.backend.entity.Assessoria;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.TreinoPlanejado;
import br.com.menthoros.backend.entity.TreinoRealizado;
import br.com.menthoros.backend.enums.FonteDados;
import br.com.menthoros.backend.enums.TipoTreino;
import br.com.menthoros.backend.enums.TreinoExecucaoStatus;
import br.com.menthoros.backend.events.TreinoRegistradoEvent;
import br.com.menthoros.backend.exception.DomainNotFoundException;
import br.com.menthoros.backend.exception.DomainRuleViolationException;
import br.com.menthoros.backend.entity.PlanoSemanal;
import br.com.menthoros.backend.enums.PlanoStatus;
import br.com.menthoros.backend.mapper.PlanoSemanalMapper;
import br.com.menthoros.backend.mapper.TreinoMapper;
import br.com.menthoros.backend.multitenancy.TenantContext;
import br.com.menthoros.backend.repository.AtletaRepository;
import br.com.menthoros.backend.repository.PlanoMetadadosRepository;
import br.com.menthoros.backend.repository.PlanoSemanalRepository;
import br.com.menthoros.backend.repository.TreinoPlanejadoRepository;
import br.com.menthoros.backend.repository.TreinoRealizadoRepository;
import br.com.menthoros.backend.services.TsbService;
import br.com.menthoros.backend.services.helper.TipoTreinoConsistenciaValidator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentCaptor.forClass;

@ExtendWith(MockitoExtension.class)
@DisplayName("TreinoServiceImpl — registrarTreinoManualAtleta e listarTreinosRecentes")
class AtletaTreinoServiceImplTest {

    @Mock private TreinoMapper treinoMapper;
    @Mock private TreinoRealizadoRepository treinoRealizadoRepository;
    @Mock private AtletaRepository atletaRepository;
    @Mock private PlanoSemanalRepository planoSemanalRepository;
    @Mock private PlanoSemanalMapper planoSemanalMapper;
    @Mock private TreinoPlanejadoRepository treinoPlanejadoRepository;
    @Mock private TsbService tsbService;
    @Mock private PlanoMetadadosRepository planoMetaDadosRepository;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private TipoTreinoConsistenciaValidator tipoTreinoConsistenciaValidator;

    @InjectMocks private TreinoServiceImpl service;

    private UUID tenantId;
    private UUID atletaId;
    private Atleta atleta;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        atletaId = UUID.randomUUID();
        TenantContext.setTenantId(tenantId);

        Assessoria assessoria = new Assessoria();
        assessoria.setId(tenantId);

        atleta = new Atleta();
        atleta.setId(atletaId);
        atleta.setAssessoria(assessoria);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("registrarTreinoManualAtleta")
    class RegistrarTreinoManualAtleta {

        @Test
        @DisplayName("salva treino standalone com fonteDados=MANUAL quando não há planejado correspondente")
        void salvaTreinoStandaloneSemMatch() {
            var input = novoInput(LocalDate.now());
            var treinoSalvo = stubTreinoRealizado();
            var outputDto = stubOutputDto();

            when(atletaRepository.findByIdAndTenantId(atletaId, tenantId)).thenReturn(Optional.of(atleta));
            when(treinoRealizadoRepository.save(any())).thenReturn(treinoSalvo);
            when(treinoPlanejadoRepository.findFirstForManualMatch(any(), any(), any(), any(), any()))
                    .thenReturn(Optional.empty());
            when(treinoMapper.toOutputDto(treinoSalvo)).thenReturn(outputDto);

            var result = service.registrarTreinoManualAtleta(atletaId, input);

            assertThat(result).isEqualTo(outputDto);

            ArgumentCaptor<TreinoRealizado> captor = forClass(TreinoRealizado.class);
            verify(treinoRealizadoRepository).save(captor.capture());
            TreinoRealizado salvo = captor.getValue();
            assertThat(salvo.getFonteDados()).isEqualTo(FonteDados.MANUAL);
            assertThat(salvo.getStatus()).isEqualTo(TreinoExecucaoStatus.REALIZADO);
            assertThat(salvo.getCriadoPor()).isEqualTo("ATLETA");
            assertThat(salvo.getTreinoPlanejado()).isNull();
            assertThat(salvo.getFcMedia()).isNull();
            assertThat(salvo.getPaceMedia()).isNull();

            verify(eventPublisher).publishEvent(any(TreinoRegistradoEvent.class));
            verify(tsbService).atualizarTsbDia(atletaId, input.data());
            verify(treinoPlanejadoRepository, never()).save(any());
        }

        @Test
        @DisplayName("vincula ao TreinoPlanejado PENDENTE e muda seu status para REALIZADO")
        void vinculaAoPlanejadoPendenteQuandoHaMatch() {
            var input = novoInput(LocalDate.now());
            var treinoSalvo = stubTreinoRealizado();
            var planejado = stubTreinoPlanejado(TreinoExecucaoStatus.PENDENTE);
            var outputDto = stubOutputDto();

            when(atletaRepository.findByIdAndTenantId(atletaId, tenantId)).thenReturn(Optional.of(atleta));
            when(treinoRealizadoRepository.save(any())).thenReturn(treinoSalvo);
            when(treinoPlanejadoRepository.findFirstForManualMatch(
                    eq(atletaId), eq(tenantId), eq(input.data()), eq(input.tipo()), any()))
                    .thenReturn(Optional.of(planejado));
            when(treinoPlanejadoRepository.save(planejado)).thenReturn(planejado);
            when(treinoMapper.toOutputDto(treinoSalvo)).thenReturn(outputDto);

            service.registrarTreinoManualAtleta(atletaId, input);

            assertThat(planejado.getStatusTreino()).isEqualTo(TreinoExecucaoStatus.REALIZADO);
            assertThat(planejado.getTreinoRealizado()).isEqualTo(treinoSalvo);
            verify(treinoPlanejadoRepository).save(planejado);
        }

        @Test
        @DisplayName("vincula ao TreinoPlanejado PERDIDO e muda seu status para REALIZADO")
        void vinculaAoPlanejadoPerdidoQuandoHaMatch() {
            var input = novoInput(LocalDate.now());
            var treinoSalvo = stubTreinoRealizado();
            var planejado = stubTreinoPlanejado(TreinoExecucaoStatus.PERDIDO);
            var outputDto = stubOutputDto();

            when(atletaRepository.findByIdAndTenantId(atletaId, tenantId)).thenReturn(Optional.of(atleta));
            when(treinoRealizadoRepository.save(any())).thenReturn(treinoSalvo);
            when(treinoPlanejadoRepository.findFirstForManualMatch(
                    eq(atletaId), eq(tenantId), eq(input.data()), eq(input.tipo()), any()))
                    .thenReturn(Optional.of(planejado));
            when(treinoPlanejadoRepository.save(planejado)).thenReturn(planejado);
            when(treinoMapper.toOutputDto(treinoSalvo)).thenReturn(outputDto);

            service.registrarTreinoManualAtleta(atletaId, input);

            assertThat(planejado.getStatusTreino()).isEqualTo(TreinoExecucaoStatus.REALIZADO);
            verify(treinoPlanejadoRepository).save(planejado);
        }

        @Test
        @DisplayName("ao reverter PERDIDO->REALIZADO, vincula ao realizado e recalcula o status do plano")
        void recalculaPlanoAoReverterPerdido() {
            var input = novoInput(LocalDate.now());
            var treinoSalvo = stubTreinoRealizado();
            var planejado = stubTreinoPlanejado(TreinoExecucaoStatus.PERDIDO);
            PlanoSemanal semanal = new PlanoSemanal();
            semanal.setId(UUID.randomUUID());
            semanal.setStatus(PlanoStatus.CONCLUIDO);
            semanal.setTreinosPlanejados(new ArrayList<>(List.of(planejado)));
            planejado.setPlanoSemanal(semanal);
            var outputDto = stubOutputDto();

            when(atletaRepository.findByIdAndTenantId(atletaId, tenantId)).thenReturn(Optional.of(atleta));
            when(treinoRealizadoRepository.save(any())).thenReturn(treinoSalvo);
            when(treinoPlanejadoRepository.findFirstForManualMatch(
                    eq(atletaId), eq(tenantId), eq(input.data()), eq(input.tipo()), any()))
                    .thenReturn(Optional.of(planejado));
            when(treinoPlanejadoRepository.save(planejado)).thenReturn(planejado);
            when(treinoRealizadoRepository.sumDistanciaByPlanoSemanalId(semanal.getId())).thenReturn(0.0);
            when(treinoMapper.toOutputDto(treinoSalvo)).thenReturn(outputDto);

            service.registrarTreinoManualAtleta(atletaId, input);

            assertThat(planejado.getStatusTreino()).isEqualTo(TreinoExecucaoStatus.REALIZADO);
            assertThat(planejado.getTreinoRealizado()).isEqualTo(treinoSalvo);
            verify(planoSemanalRepository).save(semanal);
        }

        @Test
        @DisplayName("lança DomainRuleViolationException quando data é anterior a 7 dias")
        void lancaExcecaoQuandoDataAnteriorA7Dias() {
            var input = novoInput(LocalDate.now().minusDays(8));

            assertThatThrownBy(() -> service.registrarTreinoManualAtleta(atletaId, input))
                    .isInstanceOf(DomainRuleViolationException.class)
                    .hasMessageContaining("7 dias");

            verifyNoInteractions(atletaRepository, treinoRealizadoRepository, eventPublisher, tsbService);
        }

        @Test
        @DisplayName("aceita data exatamente 7 dias no passado sem lançar exceção")
        void aceitaDataComExatamente7DiasNoPassado() {
            var input = novoInput(LocalDate.now().minusDays(7));
            var treinoSalvo = stubTreinoRealizado();

            when(atletaRepository.findByIdAndTenantId(atletaId, tenantId)).thenReturn(Optional.of(atleta));
            when(treinoRealizadoRepository.save(any())).thenReturn(treinoSalvo);
            when(treinoPlanejadoRepository.findFirstForManualMatch(any(), any(), any(), any(), any()))
                    .thenReturn(Optional.empty());
            when(treinoMapper.toOutputDto(treinoSalvo)).thenReturn(stubOutputDto());

            assertThatCode(() -> service.registrarTreinoManualAtleta(atletaId, input)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("lança DomainNotFoundException quando atletaId não pertence ao tenant")
        void lancaExcecaoQuandoAtletaNaoPertenceAoTenant() {
            var input = novoInput(LocalDate.now());
            when(atletaRepository.findByIdAndTenantId(atletaId, tenantId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.registrarTreinoManualAtleta(atletaId, input))
                    .isInstanceOf(DomainNotFoundException.class);

            verifyNoInteractions(treinoRealizadoRepository, eventPublisher, tsbService);
        }

        @Test
        @DisplayName("evento publicado com id do treino salvo e tenantId corretos")
        void eventPublicadoComIdsCorretos() {
            var input = novoInput(LocalDate.now());
            var treinoSalvo = stubTreinoRealizado();

            when(atletaRepository.findByIdAndTenantId(atletaId, tenantId)).thenReturn(Optional.of(atleta));
            when(treinoRealizadoRepository.save(any())).thenReturn(treinoSalvo);
            when(treinoPlanejadoRepository.findFirstForManualMatch(any(), any(), any(), any(), any()))
                    .thenReturn(Optional.empty());
            when(treinoMapper.toOutputDto(treinoSalvo)).thenReturn(stubOutputDto());

            service.registrarTreinoManualAtleta(atletaId, input);

            ArgumentCaptor<TreinoRegistradoEvent> captor = forClass(TreinoRegistradoEvent.class);
            verify(eventPublisher).publishEvent(captor.capture());
            assertThat(captor.getValue().treinoRealizadoId()).isEqualTo(treinoSalvo.getId());
            assertThat(captor.getValue().tenantId()).isEqualTo(tenantId);
        }

        @Test
        @DisplayName("mapeia nivelDor/nivelFadiga/qualidadeSonoNoiteAnterior/nivelRecuperacao para a entidade quando informados (fase de calibração)")
        void mapeiaCamposDeCalibracaoParaEntidadeQuandoPresentes() {
            var input = novoInputComCalibracao(LocalDate.now(), 3, 4, 7, 6);
            var treinoSalvo = stubTreinoRealizado();

            when(atletaRepository.findByIdAndTenantId(atletaId, tenantId)).thenReturn(Optional.of(atleta));
            when(treinoRealizadoRepository.save(any())).thenReturn(treinoSalvo);
            when(treinoPlanejadoRepository.findFirstForManualMatch(any(), any(), any(), any(), any()))
                    .thenReturn(Optional.empty());
            when(treinoMapper.toOutputDto(treinoSalvo)).thenReturn(stubOutputDto());

            service.registrarTreinoManualAtleta(atletaId, input);

            ArgumentCaptor<TreinoRealizado> captor = forClass(TreinoRealizado.class);
            verify(treinoRealizadoRepository).save(captor.capture());
            TreinoRealizado salvo = captor.getValue();
            assertThat(salvo.getNivelDor()).isEqualTo(3);
            assertThat(salvo.getNivelFadiga()).isEqualTo(4);
            assertThat(salvo.getQualidadeSonoNoiteAnterior()).isEqualTo(7);
            assertThat(salvo.getNivelRecuperacao()).isEqualTo(6);
        }

        @Test
        @DisplayName("mantém nivelDor/nivelFadiga/qualidadeSonoNoiteAnterior/nivelRecuperacao nulos quando ausentes (fora da calibração)")
        void mantemCamposDeCalibracaoNulosQuandoAusentes() {
            var input = novoInput(LocalDate.now());
            var treinoSalvo = stubTreinoRealizado();

            when(atletaRepository.findByIdAndTenantId(atletaId, tenantId)).thenReturn(Optional.of(atleta));
            when(treinoRealizadoRepository.save(any())).thenReturn(treinoSalvo);
            when(treinoPlanejadoRepository.findFirstForManualMatch(any(), any(), any(), any(), any()))
                    .thenReturn(Optional.empty());
            when(treinoMapper.toOutputDto(treinoSalvo)).thenReturn(stubOutputDto());

            service.registrarTreinoManualAtleta(atletaId, input);

            ArgumentCaptor<TreinoRealizado> captor = forClass(TreinoRealizado.class);
            verify(treinoRealizadoRepository).save(captor.capture());
            TreinoRealizado salvo = captor.getValue();
            assertThat(salvo.getNivelDor()).isNull();
            assertThat(salvo.getNivelFadiga()).isNull();
            assertThat(salvo.getQualidadeSonoNoiteAnterior()).isNull();
            assertThat(salvo.getNivelRecuperacao()).isNull();
        }

        @Test
        @DisplayName("consulta findFirstForManualMatch com statuses PENDENTE e PERDIDO")
        void consultaMatchComStatusesElegiveis() {
            var input = novoInput(LocalDate.now());
            var treinoSalvo = stubTreinoRealizado();

            when(atletaRepository.findByIdAndTenantId(atletaId, tenantId)).thenReturn(Optional.of(atleta));
            when(treinoRealizadoRepository.save(any())).thenReturn(treinoSalvo);
            when(treinoPlanejadoRepository.findFirstForManualMatch(any(), any(), any(), any(), any()))
                    .thenReturn(Optional.empty());
            when(treinoMapper.toOutputDto(treinoSalvo)).thenReturn(stubOutputDto());

            service.registrarTreinoManualAtleta(atletaId, input);

            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<TreinoExecucaoStatus>> statusCaptor = forClass(List.class);
            verify(treinoPlanejadoRepository).findFirstForManualMatch(
                    eq(atletaId), eq(tenantId), eq(input.data()), eq(input.tipo()), statusCaptor.capture());
            assertThat(statusCaptor.getValue())
                    .containsExactlyInAnyOrder(TreinoExecucaoStatus.PENDENTE, TreinoExecucaoStatus.PERDIDO);
        }
    }

    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("listarTreinosRecentes")
    class ListarTreinosRecentes {

        @Test
        @DisplayName("retorna treinos do período mapeados pelo mapper")
        void retornaTreinosDoPeríodo() {
            var treino = stubTreinoRealizado();
            var outputDto = stubOutputDto();

            when(treinoRealizadoRepository.findByAtletaIdAndTenantIdAndDataTreinoBetween(eq(atletaId), eq(tenantId), any(), any()))
                    .thenReturn(List.of(treino));
            when(treinoMapper.toOutputDto(treino)).thenReturn(outputDto);

            var result = service.listarTreinosRecentes(atletaId, 7);

            assertThat(result).hasSize(1).containsExactly(outputDto);
        }

        @Test
        @DisplayName("limita a 30 dias quando valor superior é informado")
        void limitaA30DiasQuandoValorSuperiorPassado() {
            when(treinoRealizadoRepository.findByAtletaIdAndTenantIdAndDataTreinoBetween(eq(atletaId), eq(tenantId), any(), any()))
                    .thenReturn(List.of());

            service.listarTreinosRecentes(atletaId, 999);

            ArgumentCaptor<LocalDate> dataInicioCaptor = forClass(LocalDate.class);
            verify(treinoRealizadoRepository).findByAtletaIdAndTenantIdAndDataTreinoBetween(
                    eq(atletaId), eq(tenantId), dataInicioCaptor.capture(), any());

            LocalDate hoje = LocalDate.now();
            assertThat(dataInicioCaptor.getValue()).isAfterOrEqualTo(hoje.minusDays(30));
            assertThat(dataInicioCaptor.getValue()).isBefore(hoje.minusDays(29));
        }

        @Test
        @DisplayName("retorna lista vazia quando não há treinos no período")
        void retornaListaVaziaQuandoSemTreinos() {
            when(treinoRealizadoRepository.findByAtletaIdAndTenantIdAndDataTreinoBetween(eq(atletaId), eq(tenantId), any(), any()))
                    .thenReturn(List.of());

            assertThat(service.listarTreinosRecentes(atletaId, 7)).isEmpty();
        }

        @Test
        @DisplayName("isolamento cross-tenant — tenantId do contexto é sempre passado à query, independente do atletaId")
        void passaTenantIdDoContextoAQuery() {
            UUID atletaIdExterno = UUID.randomUUID();
            when(treinoRealizadoRepository.findByAtletaIdAndTenantIdAndDataTreinoBetween(any(), any(), any(), any()))
                    .thenReturn(List.of());

            service.listarTreinosRecentes(atletaIdExterno, 7);

            verify(treinoRealizadoRepository).findByAtletaIdAndTenantIdAndDataTreinoBetween(
                    eq(atletaIdExterno), eq(tenantId), any(), any());
        }
    }

    // -------------------------------------------------------------------------

    private TreinoManualInputDto novoInput(LocalDate data) {
        return new TreinoManualInputDto(
                TipoTreino.CONTINUO, data, 45, BigDecimal.valueOf(8.5), 6, null,
                null, null, null, null); // nivelDor, nivelFadiga, qualidadeSonoNoiteAnterior, nivelRecuperacao
    }

    private TreinoManualInputDto novoInputComCalibracao(LocalDate data, Integer nivelDor, Integer nivelFadiga,
                                                          Integer qualidadeSonoNoiteAnterior, Integer nivelRecuperacao) {
        return new TreinoManualInputDto(
                TipoTreino.CONTINUO, data, 45, BigDecimal.valueOf(8.5), 6, null,
                nivelDor, nivelFadiga, qualidadeSonoNoiteAnterior, nivelRecuperacao);
    }

    private TreinoRealizado stubTreinoRealizado() {
        var tr = new TreinoRealizado();
        tr.setId(UUID.randomUUID());
        tr.setTenantId(tenantId);
        return tr;
    }

    private TreinoPlanejado stubTreinoPlanejado(TreinoExecucaoStatus status) {
        var tp = new TreinoPlanejado();
        tp.setId(UUID.randomUUID());
        tp.setStatusTreino(status);
        return tp;
    }

    private TreinoRealizadoOutputDto stubOutputDto() {
        return new TreinoRealizadoOutputDto(
                UUID.randomUUID(), null, null, TipoTreino.CONTINUO,
                null, null, "00:45:00", null, null, null,
                null, null, null, null, null, null, null, null,
                6, null, null, null, // percepcaoEsforco..intensidadeReal
                null, // runningDynamics
                null, null, null, null, null, null, // decouplingPercentual..nivelEstresse
                null, null, null, // nivelDor, nivelFadiga, nivelRecuperacao
                FonteDados.MANUAL, TreinoExecucaoStatus.REALIZADO, null, null, null);
    }
}
