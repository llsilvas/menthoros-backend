package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.dto.fit.FitImportResultado;
import br.com.menthoros.backend.dto.fit.FitLapData;
import br.com.menthoros.backend.dto.fit.FitSessionData;
import br.com.menthoros.backend.dto.output.TreinoRealizadoOutputDto;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.TreinoRealizado;
import br.com.menthoros.backend.exception.DomainNotFoundException;
import br.com.menthoros.backend.mapper.TreinoMapper;
import br.com.menthoros.backend.multitenancy.TenantContext;
import br.com.menthoros.backend.repository.AtletaRepository;
import br.com.menthoros.backend.repository.TreinoRealizadoRepository;
import br.com.menthoros.backend.services.FitParseService;
import br.com.menthoros.backend.services.TsbService;
import br.com.menthoros.backend.services.helper.TssCalculatorService;
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
import org.springframework.dao.DataIntegrityViolationException;

import java.io.ByteArrayInputStream;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FitUploadServiceImplTest {

    @Mock private FitParseService fitParseService;
    @Mock private AtletaRepository atletaRepository;
    @Mock private TreinoRealizadoRepository treinoRealizadoRepository;
    @Mock private TreinoMapper treinoMapper;
    @Mock private TsbService tsbService;
    @Mock private TssCalculatorService tssCalculatorService;
    @Mock private ApplicationEventPublisher eventPublisher;

    private FitUploadServiceImpl service;

    private UUID tenantId;
    private UUID atletaId;
    private Atleta atleta;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        atletaId = UUID.randomUUID();
        TenantContext.setTenantId(tenantId);

        service = new FitUploadServiceImpl(fitParseService, atletaRepository, treinoRealizadoRepository,
                treinoMapper, tsbService, tssCalculatorService, eventPublisher);

        atleta = mock(Atleta.class);
        lenient().when(atleta.getId()).thenReturn(atletaId);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private FitSessionData sessionCorrida(Long serial, long startEpoch) {
        return new FitSessionData(serial, LocalDate.of(2026, 7, 1), startEpoch,
                Duration.ofMinutes(30), 5.0, 150, 175, 62, true, "RUNNING",
                List.of(new FitLapData(1, Duration.ofMinutes(15), 2.5, 148, 160)));
    }

    @Nested
    @DisplayName("importar")
    class Importar {

        @BeforeEach
        void stubComuns() {
            lenient().when(atletaRepository.findByIdAndTenantId(atletaId, tenantId)).thenReturn(Optional.of(atleta));
            lenient().when(treinoRealizadoRepository.findByExternalIdAndAtletaId(anyString(), eq(atletaId)))
                    .thenReturn(Optional.empty());
        }

        @Test
        @DisplayName("importa um novo treino e retorna novo=true")
        void importaNovo() {
            FitSessionData dados = sessionCorrida(123456789L, 1751360400L);
            when(fitParseService.parse(any())).thenReturn(dados);
            when(treinoRealizadoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            TreinoRealizadoOutputDto dtoEsperado = mock(TreinoRealizadoOutputDto.class);
            when(treinoMapper.toOutputDto(any(TreinoRealizado.class))).thenReturn(dtoEsperado);

            FitImportResultado resultado = service.importar(atletaId, new ByteArrayInputStream(new byte[0]));

            assertThat(resultado.novo()).isTrue();
            assertThat(resultado.treino()).isSameAs(dtoEsperado);

            ArgumentCaptor<TreinoRealizado> captor = ArgumentCaptor.forClass(TreinoRealizado.class);
            verify(treinoRealizadoRepository).save(captor.capture());
            TreinoRealizado salvo = captor.getValue();
            assertThat(salvo.getDataTreino()).isEqualTo(LocalDate.of(2026, 7, 1));
            assertThat(salvo.getFcMedia()).isEqualTo(150);
            assertThat(salvo.getTssCalculado()).isEqualTo(62);
            assertThat(salvo.getMetodoCalculoTss()).isEqualTo("DISPOSITIVO");
            assertThat(salvo.getEtapasRealizadas()).hasSize(1);
            assertThat(salvo.getExternalId()).isEqualTo(atletaId + "-123456789-1751360400");

            verify(eventPublisher).publishEvent(any(br.com.menthoros.backend.events.TreinoRegistradoEvent.class));
            verify(tsbService).atualizarTsbDia(eq(atletaId), eq(LocalDate.of(2026, 7, 1)));
        }

        @Test
        @DisplayName("re-upload do mesmo .fit (mesmo externalId) retorna novo=false, sem persistir de novo")
        void reuploadRetornaExistente() {
            FitSessionData dados = sessionCorrida(123456789L, 1751360400L);
            when(fitParseService.parse(any())).thenReturn(dados);
            String externalId = atletaId + "-123456789-1751360400";
            TreinoRealizado existente = new TreinoRealizado();
            when(treinoRealizadoRepository.findByExternalIdAndAtletaId(externalId, atletaId))
                    .thenReturn(Optional.of(existente));
            TreinoRealizadoOutputDto dtoExistente = mock(TreinoRealizadoOutputDto.class);
            when(treinoMapper.toOutputDto(existente)).thenReturn(dtoExistente);

            FitImportResultado resultado = service.importar(atletaId, new ByteArrayInputStream(new byte[0]));

            assertThat(resultado.novo()).isFalse();
            assertThat(resultado.treino()).isSameAs(dtoExistente);
            verify(treinoRealizadoRepository, never()).save(any());
            verifyNoInteractions(eventPublisher, tsbService);
        }

        @Test
        @DisplayName("esporte não-corrida usa tipoTreino=CONTINUO e anota o esporte em descricao")
        void esporteNaoCorridaUsaContinuoEDescricao() {
            FitSessionData dados = new FitSessionData(1L, LocalDate.of(2026, 7, 1), 1751360400L,
                    Duration.ofHours(1), 30.0, 140, 165, 80, false, "CYCLING", List.of());
            when(fitParseService.parse(any())).thenReturn(dados);
            when(treinoRealizadoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(treinoMapper.toOutputDto(any(TreinoRealizado.class))).thenReturn(mock(TreinoRealizadoOutputDto.class));

            service.importar(atletaId, new ByteArrayInputStream(new byte[0]));

            ArgumentCaptor<TreinoRealizado> captor = ArgumentCaptor.forClass(TreinoRealizado.class);
            verify(treinoRealizadoRepository).save(captor.capture());
            assertThat(captor.getValue().getTipoTreino().name()).isEqualTo("CONTINUO");
            assertThat(captor.getValue().getDescricao()).contains("CYCLING");
        }

        @Test
        @DisplayName("TSS ausente no .fit é calculado via TssCalculatorService (fallback D0.3)")
        void tssAusenteUsaFallback() {
            FitSessionData dados = new FitSessionData(1L, LocalDate.of(2026, 7, 1), 1751360400L,
                    Duration.ofMinutes(30), 5.0, 150, 175, null, true, "RUNNING", List.of());
            when(fitParseService.parse(any())).thenReturn(dados);
            when(tssCalculatorService.calcularTss(any())).thenReturn(70);
            when(treinoRealizadoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(treinoMapper.toOutputDto(any(TreinoRealizado.class))).thenReturn(mock(TreinoRealizadoOutputDto.class));

            service.importar(atletaId, new ByteArrayInputStream(new byte[0]));

            ArgumentCaptor<TreinoRealizado> captor = ArgumentCaptor.forClass(TreinoRealizado.class);
            verify(treinoRealizadoRepository).save(captor.capture());
            assertThat(captor.getValue().getTssCalculado()).isEqualTo(70);
            assertThat(captor.getValue().getMetodoCalculoTss()).isEqualTo("FC");
        }

        @Test
        @DisplayName("dados parciais (sem distância/FC) não fabrica valores — persiste null")
        void dadosParciaisNaoFabricaValores() {
            FitSessionData dados = new FitSessionData(1L, LocalDate.of(2026, 7, 1), 1751360400L,
                    Duration.ofMinutes(20), null, null, null, 40, true, "RUNNING", List.of());
            when(fitParseService.parse(any())).thenReturn(dados);
            when(treinoRealizadoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(treinoMapper.toOutputDto(any(TreinoRealizado.class))).thenReturn(mock(TreinoRealizadoOutputDto.class));

            service.importar(atletaId, new ByteArrayInputStream(new byte[0]));

            ArgumentCaptor<TreinoRealizado> captor = ArgumentCaptor.forClass(TreinoRealizado.class);
            verify(treinoRealizadoRepository).save(captor.capture());
            assertThat(captor.getValue().getDistanciaKm()).isNull();
            assertThat(captor.getValue().getFcMedia()).isNull();
            assertThat(captor.getValue().getFcMax()).isNull();
        }

        @Test
        @DisplayName("atleta não encontrado no tenant lança DomainNotFoundException")
        void atletaNaoEncontrado() {
            when(fitParseService.parse(any())).thenReturn(sessionCorrida(1L, 1751360400L));
            when(atletaRepository.findByIdAndTenantId(atletaId, tenantId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.importar(atletaId, new ByteArrayInputStream(new byte[0])))
                    .isInstanceOf(DomainNotFoundException.class);

            verifyNoInteractions(treinoRealizadoRepository, eventPublisher, tsbService);
        }

        @Test
        @DisplayName("corrida de concorrência na constraint única retorna o registro que venceu, não propaga erro")
        void concorrenciaRetornaRegistroExistente() {
            FitSessionData dados = sessionCorrida(1L, 1751360400L);
            when(fitParseService.parse(any())).thenReturn(dados);
            String externalId = atletaId + "-1-1751360400";
            when(treinoRealizadoRepository.save(any())).thenThrow(new DataIntegrityViolationException("duplicate"));
            TreinoRealizado ganhador = new TreinoRealizado();
            when(treinoRealizadoRepository.findByExternalIdAndAtletaId(externalId, atletaId))
                    .thenReturn(Optional.empty()) // pré-check inicial: não existe
                    .thenReturn(Optional.of(ganhador)); // retry após a violação: existe
            when(treinoMapper.toOutputDto(ganhador)).thenReturn(mock(TreinoRealizadoOutputDto.class));

            FitImportResultado resultado = service.importar(atletaId, new ByteArrayInputStream(new byte[0]));

            assertThat(resultado.novo()).isTrue(); // decisão de 201 já foi tomada no pré-check
            verify(treinoRealizadoRepository, times(2)).findByExternalIdAndAtletaId(externalId, atletaId);
        }
    }
}
