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
import br.com.menthoros.backend.dto.input.ProvaAtletaInputDto;
import br.com.menthoros.backend.enums.MotivoRevisaoProva;
import br.com.menthoros.backend.enums.UserRole;
import br.com.menthoros.backend.exception.ProvaRealizadaImutavelException;
import br.com.menthoros.backend.security.AuthenticatedAtletaResolver;
import br.com.menthoros.backend.security.AuthenticatedPrincipalResolver;
import br.com.menthoros.backend.services.helper.ProvaEnricher;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.mockito.Spy;
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
    @Mock
    private AuthenticatedAtletaResolver atletaResolver;
    @Mock
    private AuthenticatedPrincipalResolver principalResolver;
    @Spy
    private Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
    @Spy
    private java.time.Clock clock = java.time.Clock.systemUTC();

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
                null, null, null, 60, false, 8, true, null, null
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

    @Nested
    @DisplayName("posse do atleta")
    class PosseDoAtleta {

        private final UUID outroAtletaId = UUID.randomUUID();

        @BeforeEach
        void principalAtleta() {
            when(atletaResolver.atuaComoAtleta()).thenReturn(true);
            when(atletaRepository.findByIdAndTenantId(atletaId, tenantId)).thenReturn(Optional.of(atleta));
        }

        @Test
        @DisplayName("atleta lista as próprias provas")
        void listaAsProprias() {
            when(atletaResolver.resolverAtletaIdAtual()).thenReturn(atletaId);
            when(provaRepository.findByAtletaOrderByDataProvaAsc(atleta)).thenReturn(List.of(prova));
            when(provaMapper.toOutputDto(prova)).thenReturn(outputDto);

            assertThat(provaService.listarProvas(atletaId)).hasSize(1);
        }

        @Test
        @DisplayName("atleta com outro atletaId recebe não encontrado em listar, buscar, criar, atualizar e cancelar")
        void outroAtletaIdNaoEncontrado() {
            when(atletaResolver.resolverAtletaIdAtual()).thenReturn(outroAtletaId);

            assertThatThrownBy(() -> provaService.listarProvas(atletaId)).isInstanceOf(ResourceNotFoundException.class);
            assertThatThrownBy(() -> provaService.buscarProvaPorId(atletaId, provaId)).isInstanceOf(ResourceNotFoundException.class);
            assertThatThrownBy(() -> provaService.criarProva(atletaId, inputDto)).isInstanceOf(ResourceNotFoundException.class);
            assertThatThrownBy(() -> provaService.atualizarProva(atletaId, provaId, inputDto)).isInstanceOf(ResourceNotFoundException.class);
            assertThatThrownBy(() -> provaService.cancelarProva(atletaId, provaId)).isInstanceOf(ResourceNotFoundException.class);
            verify(provaRepository, never()).save(any());
            verify(provaRepository, never()).findByIdAndTenantId(any(), any());
        }
    }

    @Nested
    @DisplayName("caminho do atleta")
    class CaminhoDoAtleta {

        @BeforeEach
        void principalAtletaDono() {
            when(atletaResolver.atuaComoAtleta()).thenReturn(true);
            when(atletaResolver.resolverAtletaIdAtual()).thenReturn(atletaId);
            when(atletaRepository.findByIdAndTenantId(atletaId, tenantId)).thenReturn(Optional.of(atleta));
        }

        @Test
        @DisplayName("criar usa o subconjunto do atleta e ignora campos de resultado")
        void criarUsaSubconjunto() {
            ProvaInputDto comResultado = new ProvaInputDto(
                    "Maratona SP", LocalDate.now().plusDays(60), TipoProva.MARATONA, DistanciaProva.KM_42,
                    null, false, ProvaStatus.CONCLUIDA, null, null, null, true,
                    java.time.LocalTime.of(3, 30), null, null, null, null, null, 2, null);
            when(provaMapper.toEntity(any(ProvaAtletaInputDto.class))).thenReturn(prova);
            when(provaRepository.save(prova)).thenReturn(prova);
            when(provaMapper.toOutputDto(prova)).thenReturn(outputDto);

            provaService.criarProva(atletaId, comResultado);

            verify(provaMapper).toEntity(ProvaAtletaInputDto.from(comResultado));
            verify(provaMapper, never()).toEntity(any(ProvaInputDto.class));
        }

        @Test
        @DisplayName("criar com data de hoje é rejeitado com violação em dataProva")
        void criarComDataDeHoje() {
            ProvaInputDto hoje = new ProvaInputDto(
                    "Maratona SP", LocalDate.now(), TipoProva.MARATONA, DistanciaProva.KM_42,
                    null, false, null, null, null, null, null, null, null, null, null, null, null, null, null);

            assertThatThrownBy(() -> provaService.criarProva(atletaId, hoje))
                    .isInstanceOf(ConstraintViolationException.class)
                    .hasMessageContaining("dataProva");
            verify(provaRepository, never()).save(any());
        }

        @Test
        @DisplayName("criar customizada sem quilometragem é rejeitado")
        void criarCustomizadaSemKm() {
            ProvaInputDto semKm = new ProvaInputDto(
                    "Ultra", LocalDate.now().plusDays(90), TipoProva.TRAIL, DistanciaProva.CUSTOMIZADA,
                    null, false, null, null, null, null, null, null, null, null, null, null, null, null, null);

            assertThatThrownBy(() -> provaService.criarProva(atletaId, semKm))
                    .isInstanceOf(ConstraintViolationException.class);
            verify(provaRepository, never()).save(any());
        }

        @Test
        @DisplayName("atualizar aplica só o subconjunto do atleta")
        void atualizarUsaSubconjunto() {
            when(provaRepository.findByIdAndTenantId(provaId, tenantId)).thenReturn(Optional.of(prova));
            when(provaRepository.save(prova)).thenReturn(prova);
            when(provaMapper.toOutputDto(prova)).thenReturn(outputDto);

            provaService.atualizarProva(atletaId, provaId, inputDto);

            verify(provaMapper).updateEntity(ProvaAtletaInputDto.from(inputDto), prova);
            verify(provaMapper, never()).updateEntity(any(ProvaInputDto.class), any());
        }

        @Test
        @DisplayName("atualizar prova realizada responde conflito")
        void atualizarRealizada() {
            prova.setFoiRealizada(true);
            when(provaRepository.findByIdAndTenantId(provaId, tenantId)).thenReturn(Optional.of(prova));

            assertThatThrownBy(() -> provaService.atualizarProva(atletaId, provaId, inputDto))
                    .isInstanceOf(ProvaRealizadaImutavelException.class);
            verify(provaRepository, never()).save(any());
        }

        @Test
        @DisplayName("cancelar marca CANCELADA e preserva a prova")
        void cancelar() {
            when(provaRepository.findByIdAndTenantId(provaId, tenantId)).thenReturn(Optional.of(prova));

            provaService.cancelarProva(atletaId, provaId);

            assertThat(prova.getStatusProva()).isEqualTo(ProvaStatus.CANCELADA);
            verify(provaRepository).save(prova);
            verify(provaRepository, never()).delete(any());
        }

        @Test
        @DisplayName("cancelar prova realizada responde conflito")
        void cancelarRealizada() {
            prova.setFoiRealizada(true);
            when(provaRepository.findByIdAndTenantId(provaId, tenantId)).thenReturn(Optional.of(prova));

            assertThatThrownBy(() -> provaService.cancelarProva(atletaId, provaId))
                    .isInstanceOf(ProvaRealizadaImutavelException.class);
            verify(provaRepository, never()).save(any());
        }

        @Test
        @DisplayName("DELETE do atleta cancela em vez de remover")
        void removerCancela() {
            when(principalResolver.hasRole(UserRole.ADMIN)).thenReturn(false);
            when(provaRepository.findByIdAndTenantId(provaId, tenantId)).thenReturn(Optional.of(prova));

            provaService.removerProva(atletaId, provaId);

            assertThat(prova.getStatusProva()).isEqualTo(ProvaStatus.CANCELADA);
            verify(provaRepository, never()).delete(any());
        }
    }

    @Nested
    @DisplayName("caminho do coach")
    class CaminhoDoCoach {

        @BeforeEach
        void atletaDoTenant() {
            when(atletaRepository.findByIdAndTenantId(atletaId, tenantId)).thenReturn(Optional.of(atleta));
            when(provaRepository.findByIdAndTenantId(provaId, tenantId)).thenReturn(Optional.of(prova));
        }

        @Test
        @DisplayName("coach altera prova realizada e usa o DTO completo")
        void coachAlteraRealizada() {
            prova.setFoiRealizada(true);
            when(provaRepository.save(prova)).thenReturn(prova);
            when(provaMapper.toOutputDto(prova)).thenReturn(outputDto);

            provaService.atualizarProva(atletaId, provaId, inputDto);

            verify(provaMapper).updateEntity(inputDto, prova);
            verify(provaRepository).save(prova);
        }

        @Test
        @DisplayName("ADMIN remove fisicamente")
        void adminRemove() {
            when(principalResolver.hasRole(UserRole.ADMIN)).thenReturn(true);

            provaService.removerProva(atletaId, provaId);

            verify(provaRepository).delete(prova);
            verify(provaRepository, never()).save(any());
        }

        @Test
        @DisplayName("TECNICO cancela, mesmo prova realizada")
        void tecnicoCancela() {
            prova.setFoiRealizada(true);
            when(principalResolver.hasRole(UserRole.ADMIN)).thenReturn(false);

            provaService.removerProva(atletaId, provaId);

            assertThat(prova.getStatusProva()).isEqualTo(ProvaStatus.CANCELADA);
            verify(provaRepository).save(prova);
            verify(provaRepository, never()).delete(any());
        }
    }

    @Nested
    @DisplayName("ciência do coach")
    class CienciaDoCoach {

        @BeforeEach
        void atletaDoTenant() {
            when(atletaRepository.findByIdAndTenantId(atletaId, tenantId)).thenReturn(Optional.of(atleta));
        }

        @Test
        @DisplayName("atleta cria prova → pendente com motivo NOVA")
        void criarZeraFlag() {
            when(atletaResolver.atuaComoAtleta()).thenReturn(true);
            when(atletaResolver.resolverAtletaIdAtual()).thenReturn(atletaId);
            when(provaMapper.toEntity(any(ProvaAtletaInputDto.class))).thenReturn(prova);
            when(provaRepository.save(prova)).thenReturn(prova);
            when(provaMapper.toOutputDto(prova)).thenReturn(outputDto);

            provaService.criarProva(atletaId, inputDto);

            assertThat(prova.isRevisadaPeloCoach()).isFalse();
            assertThat(prova.getMotivoRevisao()).isEqualTo(MotivoRevisaoProva.NOVA);
        }

        @Test
        @DisplayName("atleta cria prova já como alvo substituindo outra → ALVO_TROCADA com o nome da anterior")
        void criarComoAlvoTrocaAlvo() {
            Prova alvoAnterior = provaAlvoExistente("Meia do Rio");
            Prova nova = prova.toBuilder().id(null).provaAlvo(true).build();
            when(atletaResolver.atuaComoAtleta()).thenReturn(true);
            when(atletaResolver.resolverAtletaIdAtual()).thenReturn(atletaId);
            when(provaMapper.toEntity(any(ProvaAtletaInputDto.class))).thenReturn(nova);
            when(provaRepository.findByAtletaAndProvaAlvoTrue(atleta)).thenReturn(List.of(alvoAnterior));
            when(provaRepository.save(any(Prova.class))).thenAnswer(inv -> inv.getArgument(0));
            when(provaMapper.toOutputDto(nova)).thenReturn(outputDto);

            provaService.criarProva(atletaId, novoInput(true));

            assertThat(nova.isRevisadaPeloCoach()).isFalse();
            assertThat(nova.getMotivoRevisao()).isEqualTo(MotivoRevisaoProva.ALVO_TROCADA);
            assertThat(nova.getAlvoAnteriorNome()).isEqualTo("Meia do Rio");
            assertThat(alvoAnterior.isProvaAlvo()).isFalse();
        }

        @Test
        @DisplayName("coach cria prova → continua revisada")
        void coachCriaNaoZera() {
            when(provaMapper.toEntity(inputDto)).thenReturn(prova);
            when(provaRepository.save(prova)).thenReturn(prova);
            when(provaMapper.toOutputDto(prova)).thenReturn(outputDto);

            provaService.criarProva(atletaId, inputDto);

            assertThat(prova.isRevisadaPeloCoach()).isTrue();
            assertThat(prova.getMotivoRevisao()).isNull();
        }

        @Test
        @DisplayName("atleta muda a data → DATA_ALTERADA")
        void mudaData() {
            atletaAtualiza(p -> p.setDataProva(p.getDataProva().plusDays(7)));

            assertThat(prova.isRevisadaPeloCoach()).isFalse();
            assertThat(prova.getMotivoRevisao()).isEqualTo(MotivoRevisaoProva.DATA_ALTERADA);
        }

        @Test
        @DisplayName("atleta muda a distância → DATA_ALTERADA")
        void mudaDistancia() {
            atletaAtualiza(p -> p.setDistancia(DistanciaProva.KM_21));

            assertThat(prova.getMotivoRevisao()).isEqualTo(MotivoRevisaoProva.DATA_ALTERADA);
        }

        @Test
        @DisplayName("atleta muda a quilometragem customizada → DATA_ALTERADA")
        void mudaKm() {
            prova.setDistancia(DistanciaProva.CUSTOMIZADA);
            prova.setDistanciaKm(new java.math.BigDecimal("30"));
            atletaAtualiza(p -> p.setDistanciaKm(new java.math.BigDecimal("35")));

            assertThat(prova.getMotivoRevisao()).isEqualTo(MotivoRevisaoProva.DATA_ALTERADA);
        }

        @Test
        @DisplayName("atleta marca como alvo → ALVO_TROCADA com o nome da alvo anterior")
        void trocaAlvo() {
            Prova alvoAnterior = provaAlvoExistente("Meia do Rio");
            when(provaRepository.findByAtletaAndProvaAlvoTrue(atleta)).thenReturn(List.of(alvoAnterior));
            when(provaRepository.save(any(Prova.class))).thenAnswer(inv -> inv.getArgument(0));

            atletaAtualizaSemStubDeSave(p -> {
                p.setProvaAlvo(true);
                p.setDataProva(p.getDataProva().plusDays(3));
            });

            assertThat(prova.getMotivoRevisao()).isEqualTo(MotivoRevisaoProva.ALVO_TROCADA);
            assertThat(prova.getAlvoAnteriorNome()).isEqualTo("Meia do Rio");
        }

        @Test
        @DisplayName("atleta muda só nome e tempo objetivo → continua revisada")
        void mudaSoNome() {
            atletaAtualiza(p -> {
                p.setNomeProva("Outro nome");
                p.setTempoObjetivo(java.time.LocalTime.of(3, 30));
            });

            assertThat(prova.isRevisadaPeloCoach()).isTrue();
            assertThat(prova.getMotivoRevisao()).isNull();
        }

        @Test
        @DisplayName("coach muda a data → flag intacta")
        void coachMudaData() {
            prova.setRevisadaPeloCoach(true);
            when(provaRepository.findByIdAndTenantId(provaId, tenantId)).thenReturn(Optional.of(prova));
            org.mockito.Mockito.doAnswer(inv -> {
                Prova p = inv.getArgument(1);
                p.setDataProva(p.getDataProva().plusDays(7));
                return null;
            }).when(provaMapper).updateEntity(eq(inputDto), eq(prova));
            when(provaRepository.save(prova)).thenReturn(prova);
            when(provaMapper.toOutputDto(prova)).thenReturn(outputDto);

            provaService.atualizarProva(atletaId, provaId, inputDto);

            assertThat(prova.isRevisadaPeloCoach()).isTrue();
        }

        @Test
        @DisplayName("atleta cancela → CANCELADA pendente")
        void cancelaZera() {
            when(atletaResolver.atuaComoAtleta()).thenReturn(true);
            when(atletaResolver.resolverAtletaIdAtual()).thenReturn(atletaId);
            when(provaRepository.findByIdAndTenantId(provaId, tenantId)).thenReturn(Optional.of(prova));

            provaService.cancelarProva(atletaId, provaId);

            assertThat(prova.isRevisadaPeloCoach()).isFalse();
            assertThat(prova.getMotivoRevisao()).isEqualTo(MotivoRevisaoProva.CANCELADA);
        }

        @Test
        @DisplayName("ciente limpa flag, motivo e alvo anterior e salva")
        void cienteLimpa() {
            prova.setRevisadaPeloCoach(false);
            prova.setMotivoRevisao(MotivoRevisaoProva.ALVO_TROCADA);
            prova.setAlvoAnteriorNome("Meia do Rio");
            when(provaRepository.findByIdAndTenantId(provaId, tenantId)).thenReturn(Optional.of(prova));
            when(provaRepository.save(prova)).thenReturn(prova);
            when(provaMapper.toOutputDto(prova)).thenReturn(outputDto);

            provaService.marcarCiente(atletaId, provaId);

            assertThat(prova.isRevisadaPeloCoach()).isTrue();
            assertThat(prova.getMotivoRevisao()).isNull();
            assertThat(prova.getAlvoAnteriorNome()).isNull();
            verify(provaRepository).save(prova);
        }

        @Test
        @DisplayName("ciente em prova já revisada não grava nada")
        void cienteIdempotente() {
            when(provaRepository.findByIdAndTenantId(provaId, tenantId)).thenReturn(Optional.of(prova));
            when(provaMapper.toOutputDto(prova)).thenReturn(outputDto);

            provaService.marcarCiente(atletaId, provaId);

            verify(provaRepository, never()).save(any());
        }

        @Test
        @DisplayName("ciente em prova de outro tenant → não encontrado")
        void cienteOutroTenant() {
            when(provaRepository.findByIdAndTenantId(provaId, tenantId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> provaService.marcarCiente(atletaId, provaId))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("listarPendentesRevisao lê direto do repositório")
        void listaPendentes() {
            when(provaRepository.findPendentesRevisaoByAtleta(eq(atletaId), eq(tenantId), any(LocalDate.class))).thenReturn(List.of(prova));
            when(provaMapper.toOutputDto(prova)).thenReturn(outputDto);

            assertThat(provaService.listarPendentesRevisao(atletaId)).hasSize(1);
        }

        private void atletaAtualiza(java.util.function.Consumer<Prova> mudanca) {
            when(provaRepository.save(prova)).thenReturn(prova);
            atletaAtualizaSemStubDeSave(mudanca);
        }

        private void atletaAtualizaSemStubDeSave(java.util.function.Consumer<Prova> mudanca) {
            prova.setRevisadaPeloCoach(true);
            when(atletaResolver.atuaComoAtleta()).thenReturn(true);
            when(atletaResolver.resolverAtletaIdAtual()).thenReturn(atletaId);
            when(provaRepository.findByIdAndTenantId(provaId, tenantId)).thenReturn(Optional.of(prova));
            org.mockito.Mockito.doAnswer(inv -> {
                mudanca.accept(inv.getArgument(1));
                return null;
            }).when(provaMapper).updateEntity(any(ProvaAtletaInputDto.class), eq(prova));
            when(provaMapper.toOutputDto(prova)).thenReturn(outputDto);

            provaService.atualizarProva(atletaId, provaId, inputDto);
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
