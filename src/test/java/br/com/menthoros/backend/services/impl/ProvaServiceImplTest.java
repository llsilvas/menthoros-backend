package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.dto.input.ProvaInputDto;
import br.com.menthoros.backend.dto.output.ProvaOutputDto;
import br.com.menthoros.backend.entity.Assessoria;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.Prova;
import br.com.menthoros.backend.enums.DistanciaProva;
import br.com.menthoros.backend.enums.ProvaStatus;
import br.com.menthoros.backend.enums.TipoProva;
import br.com.menthoros.backend.exception.ResourceNotFoundException;
import br.com.menthoros.backend.mapper.ProvaMapper;
import br.com.menthoros.backend.multitenancy.TenantContext;
import br.com.menthoros.backend.repository.AssessoriaRepository;
import br.com.menthoros.backend.repository.AtletaRepository;
import br.com.menthoros.backend.repository.ProvaRepository;
import br.com.menthoros.backend.services.helper.ProvaEnricher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProvaServiceImplTest {

    @Mock
    private ProvaRepository provaRepository;
    @Mock
    private AtletaRepository atletaRepository;
    @Mock
    private AssessoriaRepository assessoriaRepository;
    @Mock
    private ProvaMapper provaMapper;
    @Mock
    private ProvaEnricher provaEnricher;

    @InjectMocks
    private ProvaServiceImpl provaService;

    private UUID tenantId;
    private UUID atletaId;
    private UUID provaId;
    private Assessoria assessoria;
    private Atleta atleta;
    private Prova prova;
    private ProvaInputDto inputDto;
    private ProvaOutputDto outputDto;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        TenantContext.setTenantId(tenantId);

        atletaId = UUID.randomUUID();
        provaId = UUID.randomUUID();

        assessoria = new Assessoria();
        assessoria.setId(tenantId);

        atleta = Atleta.builder()
                .id(atletaId)
                .assessoria(assessoria)
                .build();

        prova = Prova.builder()
                .id(provaId)
                .nomeProva("Maratona SP")
                .dataProva(LocalDate.now().plusDays(60))
                .distancia(DistanciaProva.KM_42)
                .statusProva(ProvaStatus.PLANEJADA)
                .foiRealizada(false)
                .atleta(atleta)
                .build();

        inputDto = novoInput(false);
        outputDto = new ProvaOutputDto(
                provaId, "Maratona SP", LocalDate.now().plusDays(60),
                TipoProva.MARATONA, DistanciaProva.KM_42, null, false, ProvaStatus.PLANEJADA,
                null, null, null, false, null, null, null, null, null,
                null, null, null, 60, false, 8
        );
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Nested
    @DisplayName("criarProva")
    class CriarProva {

        @Test
        @DisplayName("cria e retorna a prova quando o atleta pertence ao tenant")
        void criaQuandoAtletaDoTenant() {
            when(atletaRepository.findByIdAndTenantId(atletaId, tenantId)).thenReturn(Optional.of(atleta));
            when(provaMapper.toEntity(inputDto)).thenReturn(prova);
            when(provaRepository.save(prova)).thenReturn(prova);
            when(provaMapper.toOutputDto(prova)).thenReturn(outputDto);

            ProvaOutputDto result = provaService.criarProva(atletaId, inputDto);

            assertThat(result.nomeProva()).isEqualTo("Maratona SP");
            verify(provaRepository).save(prova);
        }

        @Test
        @DisplayName("deriva os campos de preparação antes de salvar")
        void derivaAntesDeSalvar() {
            when(atletaRepository.findByIdAndTenantId(atletaId, tenantId)).thenReturn(Optional.of(atleta));
            when(provaMapper.toEntity(inputDto)).thenReturn(prova);
            when(provaRepository.save(prova)).thenReturn(prova);
            when(provaMapper.toOutputDto(prova)).thenReturn(outputDto);

            provaService.criarProva(atletaId, inputDto);

            InOrder ordem = inOrder(provaEnricher, provaRepository);
            ordem.verify(provaEnricher).aplicarDerivados(prova);
            ordem.verify(provaRepository).save(prova);
        }

        @Test
        @DisplayName("nova prova-alvo desmarca a alvo anterior")
        void novaAlvoDesmarcaAnterior() {
            Prova alvoAnterior = provaAlvoExistente("Meia do Rio");
            Prova nova = prova.toBuilder().id(null).provaAlvo(true).build();
            ProvaInputDto inputAlvo = novoInput(true);
            when(atletaRepository.findByIdAndTenantId(atletaId, tenantId)).thenReturn(Optional.of(atleta));
            when(provaMapper.toEntity(inputAlvo)).thenReturn(nova);
            when(provaRepository.findByAtletaAndProvaAlvoTrue(atleta)).thenReturn(List.of(alvoAnterior));
            when(provaRepository.save(any(Prova.class))).thenAnswer(inv -> inv.getArgument(0));
            when(provaMapper.toOutputDto(nova)).thenReturn(outputDto);

            provaService.criarProva(atletaId, inputAlvo);

            assertThat(alvoAnterior.isProvaAlvo()).isFalse();
            assertThat(nova.isProvaAlvo()).isTrue();
            verify(provaRepository).save(alvoAnterior);
            verify(provaRepository).save(nova);
        }

        @Test
        @DisplayName("prova sem alvo não consulta nem altera as demais")
        void semAlvoNaoTocaOutras() {
            when(atletaRepository.findByIdAndTenantId(atletaId, tenantId)).thenReturn(Optional.of(atleta));
            when(provaMapper.toEntity(inputDto)).thenReturn(prova);
            when(provaRepository.save(prova)).thenReturn(prova);
            when(provaMapper.toOutputDto(prova)).thenReturn(outputDto);

            provaService.criarProva(atletaId, inputDto);

            verify(provaRepository, never()).findByAtletaAndProvaAlvoTrue(any());
            verify(provaRepository).save(prova);
            verifyNoMoreInteractions(provaRepository);
        }

        @Test
        @DisplayName("lança ResourceNotFoundException quando o atleta é de outro tenant")
        void atletaDeOutroTenant() {
            when(atletaRepository.findByIdAndTenantId(atletaId, tenantId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> provaService.criarProva(atletaId, inputDto))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining(atletaId.toString());

            verify(provaRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("listarProvas")
    class ListarProvas {

        @Test
        @DisplayName("retorna as provas do atleta ordenadas por data")
        void retornaLista() {
            when(atletaRepository.findByIdAndTenantId(atletaId, tenantId)).thenReturn(Optional.of(atleta));
            when(provaRepository.findByAtletaOrderByDataProvaAsc(atleta)).thenReturn(List.of(prova));
            when(provaMapper.toOutputDto(prova)).thenReturn(outputDto);

            List<ProvaOutputDto> result = provaService.listarProvas(atletaId);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).nomeProva()).isEqualTo("Maratona SP");
        }

        @Test
        @DisplayName("retorna lista vazia quando o atleta não tem provas")
        void listaVazia() {
            when(atletaRepository.findByIdAndTenantId(atletaId, tenantId)).thenReturn(Optional.of(atleta));
            when(provaRepository.findByAtletaOrderByDataProvaAsc(atleta)).thenReturn(List.of());

            assertThat(provaService.listarProvas(atletaId)).isEmpty();
        }
    }

    @Nested
    @DisplayName("buscarProvaPorId")
    class BuscarProvaPorId {

        @Test
        @DisplayName("retorna a prova quando pertence ao atleta")
        void retornaProva() {
            when(atletaRepository.findByIdAndTenantId(atletaId, tenantId)).thenReturn(Optional.of(atleta));
            when(provaRepository.findByIdAndTenantId(provaId, tenantId)).thenReturn(Optional.of(prova));
            when(provaMapper.toOutputDto(prova)).thenReturn(outputDto);

            ProvaOutputDto result = provaService.buscarProvaPorId(atletaId, provaId);

            assertThat(result.id()).isEqualTo(provaId);
        }

        @Test
        @DisplayName("lança ResourceNotFoundException quando a prova é de outro atleta")
        void provaDeOutroAtleta() {
            Atleta outroAtleta = Atleta.builder().id(UUID.randomUUID()).assessoria(assessoria).build();
            Prova provaDeOutro = Prova.builder().id(provaId).atleta(outroAtleta).build();
            when(atletaRepository.findByIdAndTenantId(atletaId, tenantId)).thenReturn(Optional.of(atleta));
            when(provaRepository.findByIdAndTenantId(provaId, tenantId)).thenReturn(Optional.of(provaDeOutro));

            assertThatThrownBy(() -> provaService.buscarProvaPorId(atletaId, provaId))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining(provaId.toString());
        }
    }

    @Nested
    @DisplayName("atualizarProva")
    class AtualizarProva {

        @Test
        @DisplayName("aplica o DTO, deriva os campos e salva")
        void atualizaEDeriva() {
            when(atletaRepository.findByIdAndTenantId(atletaId, tenantId)).thenReturn(Optional.of(atleta));
            when(provaRepository.findByIdAndTenantId(provaId, tenantId)).thenReturn(Optional.of(prova));
            when(provaRepository.save(prova)).thenReturn(prova);
            when(provaMapper.toOutputDto(prova)).thenReturn(outputDto);

            ProvaOutputDto result = provaService.atualizarProva(atletaId, provaId, inputDto);

            assertThat(result).isNotNull();
            InOrder ordem = inOrder(provaMapper, provaEnricher, provaRepository);
            ordem.verify(provaMapper).updateEntity(inputDto, prova);
            ordem.verify(provaEnricher).aplicarDerivados(prova);
            ordem.verify(provaRepository).save(prova);
        }

        @Test
        @DisplayName("marcar como alvo desmarca a alvo anterior e mantém a própria")
        void marcarAlvoDesmarcaAnterior() {
            Prova alvoAnterior = provaAlvoExistente("Meia do Rio");
            ProvaInputDto inputAlvo = novoInput(true);
            when(atletaRepository.findByIdAndTenantId(atletaId, tenantId)).thenReturn(Optional.of(atleta));
            when(provaRepository.findByIdAndTenantId(provaId, tenantId)).thenReturn(Optional.of(prova));
            doAnswerMarcarAlvo(inputAlvo);
            when(provaRepository.findByAtletaAndProvaAlvoTrue(atleta)).thenReturn(List.of(alvoAnterior, prova));
            when(provaRepository.save(any(Prova.class))).thenAnswer(inv -> inv.getArgument(0));
            when(provaMapper.toOutputDto(prova)).thenReturn(outputDto);

            provaService.atualizarProva(atletaId, provaId, inputAlvo);

            assertThat(alvoAnterior.isProvaAlvo()).isFalse();
            assertThat(prova.isProvaAlvo()).isTrue();
            verify(provaRepository).save(alvoAnterior);
            verify(provaRepository).save(prova);
        }

        private void doAnswerMarcarAlvo(ProvaInputDto input) {
            org.mockito.Mockito.doAnswer(inv -> {
                Prova alvo = inv.getArgument(1);
                alvo.setProvaAlvo(true);
                return null;
            }).when(provaMapper).updateEntity(eq(input), eq(prova));
        }
    }

    @Nested
    @DisplayName("deletarProva")
    class DeletarProva {

        @Test
        @DisplayName("remove a prova quando pertence ao atleta")
        void removeProva() {
            when(atletaRepository.findByIdAndTenantId(atletaId, tenantId)).thenReturn(Optional.of(atleta));
            when(provaRepository.findByIdAndTenantId(provaId, tenantId)).thenReturn(Optional.of(prova));

            provaService.deletarProva(atletaId, provaId);

            verify(provaRepository).delete(prova);
        }
    }

    @Nested
    @DisplayName("getProvasProximas")
    class GetProvasProximas {

        @Test
        @DisplayName("filtra pelo tenant atual — nunca lista provas globais")
        void filtraPeloTenant() {
            Prova provaProxima = Prova.builder()
                    .id(provaId)
                    .nomeProva("Maratona SP")
                    .dataProva(LocalDate.now().plusDays(10))
                    .distancia(DistanciaProva.KM_42)
                    .tipoProva(TipoProva.CORRIDA_RUA)
                    .statusProva(ProvaStatus.PLANEJADA)
                    .atleta(atleta)
                    .build();
            when(provaRepository.findUpcomingProvasNext15DaysByTenant(any(LocalDate.class), eq(tenantId)))
                    .thenReturn(List.of(provaProxima));

            var response = provaService.getProvasProximas();

            assertThat(response.total()).isEqualTo(1);
            verify(provaRepository).findUpcomingProvasNext15DaysByTenant(any(LocalDate.class), eq(tenantId));
            verifyNoMoreInteractions(provaRepository);
        }
    }

    private ProvaInputDto novoInput(boolean provaAlvo) {
        return new ProvaInputDto(
                "Maratona SP",
                LocalDate.now().plusDays(60),
                TipoProva.MARATONA,
                DistanciaProva.KM_42,
                null, provaAlvo, null, null, null, null, null,
                null, null, null, null, null, null, null, null
        );
    }

    private Prova provaAlvoExistente(String nome) {
        return Prova.builder()
                .id(UUID.randomUUID())
                .nomeProva(nome)
                .dataProva(LocalDate.now().plusDays(30))
                .distancia(DistanciaProva.KM_21)
                .provaAlvo(true)
                .statusProva(ProvaStatus.PLANEJADA)
                .atleta(atleta)
                .build();
    }
}
