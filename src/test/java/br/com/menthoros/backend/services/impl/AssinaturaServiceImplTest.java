package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.dto.input.AssinaturaInputDto;
import br.com.menthoros.backend.dto.input.AssinaturaTierInputDto;
import br.com.menthoros.backend.dto.output.AssinaturaOutputDto;
import br.com.menthoros.backend.entity.Assessoria;
import br.com.menthoros.backend.entity.Assinatura;
import br.com.menthoros.backend.enums.PlanoAssessoria;
import br.com.menthoros.backend.enums.StatusAssinatura;
import br.com.menthoros.backend.exception.AsaasIntegrationException;
import br.com.menthoros.backend.exception.DomainConflictException;
import br.com.menthoros.backend.exception.DomainNotFoundException;
import br.com.menthoros.backend.mapper.AssinaturaMapper;
import br.com.menthoros.backend.repository.AssessoriaRepository;
import br.com.menthoros.backend.repository.AssinaturaRepository;
import br.com.menthoros.backend.services.AsaasGateway;
import br.com.menthoros.backend.services.AsaasGateway.AsaasAssinaturaCriada;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssinaturaServiceImplTest {

    @Mock private AssinaturaRepository assinaturaRepository;
    @Mock private AssessoriaRepository assessoriaRepository;
    @Mock private AsaasGateway asaasGateway;
    @Mock private AssinaturaMapper assinaturaMapper;
    @Mock private PlatformTransactionManager transactionManager;

    @InjectMocks private AssinaturaServiceImpl service;

    private UUID assessoriaId;
    private Assessoria assessoria;
    private final AssinaturaOutputDto outputStub =
            new AssinaturaOutputDto(null, null, null, null, null, null, null, null);

    @BeforeEach
    void setUp() {
        assessoriaId = UUID.randomUUID();
        assessoria = new Assessoria();
        assessoria.setId(assessoriaId);
        assessoria.setNome("Team X");
        assessoria.setPlano(PlanoAssessoria.BASIC);
        assessoria.setAtivo(true);
    }

    @Nested
    @DisplayName("criar")
    class Criar {

        @Test
        @DisplayName("sem assinatura prévia: grava PENDENTE, chama Asaas, confirma ATIVA e grava o tier (CA1/CA13)")
        void criaPendenteEConfirmaAtiva() {
            when(assessoriaRepository.findById(assessoriaId)).thenReturn(Optional.of(assessoria));
            when(assinaturaRepository.findByAssessoriaId(assessoriaId)).thenReturn(Optional.empty());
            when(assinaturaRepository.save(any())).thenAnswer(inv -> {
                Assinatura a = inv.getArgument(0);
                if (a.getId() == null) a.setId(UUID.randomUUID());
                return a;
            });
            when(asaasGateway.criarClienteEAssinatura(eq(assessoria), eq("tok_x"), any(), any()))
                    .thenReturn(new AsaasAssinaturaCriada("cus_1", "sub_1", "ACTIVE"));
            when(assinaturaMapper.toOutputDto(any(), eq(PlanoAssessoria.PRO))).thenReturn(outputStub);

            AssinaturaOutputDto result = service.criar(assessoriaId, input("tok_x", PlanoAssessoria.PRO));

            assertThat(result).isSameAs(outputStub);

            // Ordem PENDENTE-first: 1º save (âncora) ANTES do Asaas, 2º save (confirmação) DEPOIS.
            // (O estado PENDENTE do 1º save é provado no teste "asaasFalhaFicaPendente" — aqui o
            // objeto é mutado para ATIVA, então o captor não distingue os dois saves.)
            InOrder ordem = inOrder(assinaturaRepository, asaasGateway);
            ordem.verify(assinaturaRepository).save(any());
            ordem.verify(asaasGateway).criarClienteEAssinatura(any(), any(), any(), any());
            ordem.verify(assinaturaRepository).save(any());

            ArgumentCaptor<Assinatura> captor = ArgumentCaptor.forClass(Assinatura.class);
            verify(assinaturaRepository, org.mockito.Mockito.times(2)).save(captor.capture());
            Assinatura confirmada = captor.getValue();
            assertThat(confirmada.getStatus()).isEqualTo(StatusAssinatura.ATIVA);
            assertThat(confirmada.getAsaasSubscriptionId()).isEqualTo("sub_1");
            assertThat(assessoria.getPlano()).isEqualTo(PlanoAssessoria.PRO);
        }

        @Test
        @DisplayName("Asaas falha: assinatura fica PENDENTE, nada vira ATIVA e o tier não é gravado (CA13)")
        void asaasFalhaFicaPendente() {
            when(assessoriaRepository.findById(assessoriaId)).thenReturn(Optional.of(assessoria));
            when(assinaturaRepository.findByAssessoriaId(assessoriaId)).thenReturn(Optional.empty());
            when(assinaturaRepository.save(any())).thenAnswer(inv -> {
                Assinatura a = inv.getArgument(0);
                if (a.getId() == null) a.setId(UUID.randomUUID());
                return a;
            });
            when(asaasGateway.criarClienteEAssinatura(any(), any(), any(), any()))
                    .thenThrow(new AsaasIntegrationException("Asaas fora do ar"));

            assertThatThrownBy(() -> service.criar(assessoriaId, input("tok_x", PlanoAssessoria.PRO)))
                    .isInstanceOf(AsaasIntegrationException.class);

            ArgumentCaptor<Assinatura> captor = ArgumentCaptor.forClass(Assinatura.class);
            verify(assinaturaRepository).save(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(StatusAssinatura.PENDENTE);
            verify(assessoriaRepository, never()).save(any());
            assertThat(assessoria.getPlano()).isEqualTo(PlanoAssessoria.BASIC);
        }

        @Test
        @DisplayName("retry sobre PENDENTE retoma sem inserir nova linha (CA14)")
        void retryRetomaPendente() {
            Assinatura pendente = new Assinatura();
            pendente.setId(UUID.randomUUID());
            pendente.setAssessoriaId(assessoriaId);
            pendente.setStatus(StatusAssinatura.PENDENTE);
            when(assessoriaRepository.findById(assessoriaId)).thenReturn(Optional.of(assessoria));
            when(assinaturaRepository.findByAssessoriaId(assessoriaId)).thenReturn(Optional.of(pendente));
            when(assinaturaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(asaasGateway.criarClienteEAssinatura(any(), any(), any(), any()))
                    .thenReturn(new AsaasAssinaturaCriada("cus_1", "sub_1", "ACTIVE"));
            when(assinaturaMapper.toOutputDto(any(), any())).thenReturn(outputStub);

            service.criar(assessoriaId, input("tok_x", PlanoAssessoria.PRO));

            // Sem novo insert PENDENTE: todos os saves são na MESMA linha (o id da PENDENTE existente),
            // e o estado final é ATIVA.
            ArgumentCaptor<Assinatura> captor = ArgumentCaptor.forClass(Assinatura.class);
            verify(assinaturaRepository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
            assertThat(captor.getAllValues()).allSatisfy(a -> assertThat(a.getId()).isEqualTo(pendente.getId()));
            assertThat(captor.getValue().getStatus()).isEqualTo(StatusAssinatura.ATIVA);
        }

        @Test
        @DisplayName("retry com valor/nextDueDate corrigidos sincroniza a PENDENTE antes de confirmar (I2)")
        void retrySincronizaValorCorrigido() {
            Assinatura pendente = new Assinatura();
            pendente.setId(UUID.randomUUID());
            pendente.setAssessoriaId(assessoriaId);
            pendente.setStatus(StatusAssinatura.PENDENTE);
            pendente.setValor(new BigDecimal("99.00")); // valor da tentativa anterior
            when(assessoriaRepository.findById(assessoriaId)).thenReturn(Optional.of(assessoria));
            when(assinaturaRepository.findByAssessoriaId(assessoriaId)).thenReturn(Optional.of(pendente));
            when(assinaturaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(asaasGateway.criarClienteEAssinatura(any(), any(), any(), any()))
                    .thenReturn(new AsaasAssinaturaCriada("cus_1", "sub_1", "ACTIVE"));
            when(assinaturaMapper.toOutputDto(any(), any())).thenReturn(outputStub);

            // input corrente com valor diferente do original
            AssinaturaInputDto corrigido = new AssinaturaInputDto(
                    "tok_x", LocalDate.of(2026, 12, 1), new BigDecimal("299.90"), PlanoAssessoria.PRO);
            service.criar(assessoriaId, corrigido);

            assertThat(pendente.getValor()).isEqualByComparingTo("299.90");
            assertThat(pendente.getStatus()).isEqualTo(StatusAssinatura.ATIVA);
        }

        @Test
        @DisplayName("POST sobre assinatura já ATIVA lança conflito, sem chamar o Asaas (CA14)")
        void duploPostAtivaConflita() {
            Assinatura ativa = new Assinatura();
            ativa.setStatus(StatusAssinatura.ATIVA);
            when(assessoriaRepository.findById(assessoriaId)).thenReturn(Optional.of(assessoria));
            when(assinaturaRepository.findByAssessoriaId(assessoriaId)).thenReturn(Optional.of(ativa));

            assertThatThrownBy(() -> service.criar(assessoriaId, input("tok_x", PlanoAssessoria.PRO)))
                    .isInstanceOf(DomainConflictException.class);

            verifyNoInteractions(asaasGateway);
            verify(assinaturaRepository, never()).save(any());
        }

        @Test
        @DisplayName("assessoria inexistente lança 404, sem chamar o Asaas")
        void assessoriaInexistente() {
            when(assessoriaRepository.findById(assessoriaId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.criar(assessoriaId, input("tok_x", PlanoAssessoria.PRO)))
                    .isInstanceOf(DomainNotFoundException.class);

            verifyNoInteractions(asaasGateway);
        }
    }

    @Nested
    @DisplayName("atualizarTier")
    class AtualizarTier {

        @Test
        @DisplayName("atualiza tier local e valor no Asaas, local antes do gateway (CA9/CA15)")
        void atualizaTierEValor() {
            Assinatura ativa = assinaturaAtiva("sub_1");
            when(assessoriaRepository.findById(assessoriaId)).thenReturn(Optional.of(assessoria));
            when(assinaturaRepository.findByAssessoriaId(assessoriaId)).thenReturn(Optional.of(ativa));
            when(assinaturaMapper.toOutputDto(any(), eq(PlanoAssessoria.ENTERPRISE))).thenReturn(outputStub);

            service.atualizarTier(assessoriaId, new AssinaturaTierInputDto(PlanoAssessoria.ENTERPRISE, new BigDecimal("499.90")));

            assertThat(assessoria.getPlano()).isEqualTo(PlanoAssessoria.ENTERPRISE);
            assertThat(ativa.getValor()).isEqualByComparingTo("499.90");
            InOrder ordem = inOrder(assessoriaRepository, assinaturaRepository, asaasGateway);
            ordem.verify(assessoriaRepository).save(assessoria);
            ordem.verify(assinaturaRepository).save(ativa);
            ordem.verify(asaasGateway).atualizarValor(eq("sub_1"), eq(new BigDecimal("499.90")));
        }

        @Test
        @DisplayName("aceita o tier SCALE — plano novo do lançamento do MVP")
        void atualizaTierParaScale() {
            Assinatura ativa = assinaturaAtiva("sub_1");
            when(assessoriaRepository.findById(assessoriaId)).thenReturn(Optional.of(assessoria));
            when(assinaturaRepository.findByAssessoriaId(assessoriaId)).thenReturn(Optional.of(ativa));
            when(assinaturaMapper.toOutputDto(any(), eq(PlanoAssessoria.SCALE))).thenReturn(outputStub);

            service.atualizarTier(assessoriaId, new AssinaturaTierInputDto(PlanoAssessoria.SCALE, new BigDecimal("599.00")));

            assertThat(assessoria.getPlano()).isEqualTo(PlanoAssessoria.SCALE);
            assertThat(ativa.getValor()).isEqualByComparingTo("599.00");
            verify(asaasGateway).atualizarValor(eq("sub_1"), eq(new BigDecimal("599.00")));
        }

        @Test
        @DisplayName("Asaas falha depois das escritas locais: exceção propaga (rollback via @Transactional) — CA15")
        void asaasFalhaPropagaParaRollback() {
            Assinatura ativa = assinaturaAtiva("sub_1");
            when(assessoriaRepository.findById(assessoriaId)).thenReturn(Optional.of(assessoria));
            when(assinaturaRepository.findByAssessoriaId(assessoriaId)).thenReturn(Optional.of(ativa));
            doThrow(new AsaasIntegrationException("Asaas fora do ar"))
                    .when(asaasGateway).atualizarValor(any(), any());

            assertThatThrownBy(() -> service.atualizarTier(assessoriaId,
                    new AssinaturaTierInputDto(PlanoAssessoria.ENTERPRISE, new BigDecimal("499.90"))))
                    .isInstanceOf(AsaasIntegrationException.class);

            InOrder ordem = inOrder(assinaturaRepository, asaasGateway);
            ordem.verify(assinaturaRepository).save(ativa);
            ordem.verify(asaasGateway).atualizarValor(any(), any());
        }

        @Test
        @DisplayName("assinatura sem subscription no Asaas (PENDENTE) lança conflito, sem chamar o Asaas")
        void semSubscriptionConflita() {
            Assinatura pendente = new Assinatura();
            pendente.setStatus(StatusAssinatura.PENDENTE);
            when(assessoriaRepository.findById(assessoriaId)).thenReturn(Optional.of(assessoria));
            when(assinaturaRepository.findByAssessoriaId(assessoriaId)).thenReturn(Optional.of(pendente));

            assertThatThrownBy(() -> service.atualizarTier(assessoriaId,
                    new AssinaturaTierInputDto(PlanoAssessoria.PRO, new BigDecimal("1.00"))))
                    .isInstanceOf(DomainConflictException.class);

            verifyNoInteractions(asaasGateway);
        }

        @Test
        @DisplayName("assinatura CANCELADA não pode trocar tier — conflito, sem chamar o Asaas (I1)")
        void canceladaConflita() {
            Assinatura cancelada = assinaturaAtiva("sub_1");
            cancelada.setStatus(StatusAssinatura.CANCELADA);
            when(assessoriaRepository.findById(assessoriaId)).thenReturn(Optional.of(assessoria));
            when(assinaturaRepository.findByAssessoriaId(assessoriaId)).thenReturn(Optional.of(cancelada));

            assertThatThrownBy(() -> service.atualizarTier(assessoriaId,
                    new AssinaturaTierInputDto(PlanoAssessoria.PRO, new BigDecimal("1.00"))))
                    .isInstanceOf(DomainConflictException.class);

            verifyNoInteractions(asaasGateway);
        }
    }

    @Nested
    @DisplayName("cancelar")
    class Cancelar {

        @Test
        @DisplayName("marca CANCELADA, desativa a assessoria e cancela no Asaas (CA7)")
        void cancelaComSubscription() {
            Assinatura ativa = assinaturaAtiva("sub_1");
            when(assessoriaRepository.findById(assessoriaId)).thenReturn(Optional.of(assessoria));
            when(assinaturaRepository.findByAssessoriaId(assessoriaId)).thenReturn(Optional.of(ativa));

            service.cancelar(assessoriaId);

            assertThat(ativa.getStatus()).isEqualTo(StatusAssinatura.CANCELADA);
            assertThat(assessoria.getAtivo()).isFalse();
            InOrder ordem = inOrder(assinaturaRepository, assessoriaRepository, asaasGateway);
            ordem.verify(assinaturaRepository).save(ativa);
            ordem.verify(assessoriaRepository).save(assessoria);
            ordem.verify(asaasGateway).cancelarAssinatura("sub_1");
        }

        @Test
        @DisplayName("assinatura PENDENTE (sem subscription) cancela local sem chamar o Asaas")
        void cancelaPendenteSemAsaas() {
            Assinatura pendente = new Assinatura();
            pendente.setStatus(StatusAssinatura.PENDENTE);
            when(assessoriaRepository.findById(assessoriaId)).thenReturn(Optional.of(assessoria));
            when(assinaturaRepository.findByAssessoriaId(assessoriaId)).thenReturn(Optional.of(pendente));

            service.cancelar(assessoriaId);

            assertThat(pendente.getStatus()).isEqualTo(StatusAssinatura.CANCELADA);
            assertThat(assessoria.getAtivo()).isFalse();
            verifyNoInteractions(asaasGateway);
        }

        @Test
        @DisplayName("assinatura já CANCELADA é no-op — não rechama o Asaas (I1)")
        void jaCanceladaNoOp() {
            Assinatura cancelada = assinaturaAtiva("sub_1");
            cancelada.setStatus(StatusAssinatura.CANCELADA);
            when(assessoriaRepository.findById(assessoriaId)).thenReturn(Optional.of(assessoria));
            when(assinaturaRepository.findByAssessoriaId(assessoriaId)).thenReturn(Optional.of(cancelada));

            service.cancelar(assessoriaId);

            verifyNoInteractions(asaasGateway);
            verify(assinaturaRepository, never()).save(any());
        }

        @Test
        @DisplayName("assinatura inexistente lança 404")
        void assinaturaInexistente() {
            when(assessoriaRepository.findById(assessoriaId)).thenReturn(Optional.of(assessoria));
            when(assinaturaRepository.findByAssessoriaId(assessoriaId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.cancelar(assessoriaId))
                    .isInstanceOf(DomainNotFoundException.class);

            verifyNoInteractions(asaasGateway);
        }
    }

    private AssinaturaInputDto input(String token, PlanoAssessoria plano) {
        return new AssinaturaInputDto(token, LocalDate.of(2026, 9, 1), new BigDecimal("199.90"), plano);
    }

    private Assinatura assinaturaAtiva(String asaasSubscriptionId) {
        Assinatura a = new Assinatura();
        a.setId(UUID.randomUUID());
        a.setAssessoriaId(assessoriaId);
        a.setStatus(StatusAssinatura.ATIVA);
        a.setAsaasSubscriptionId(asaasSubscriptionId);
        a.setValor(new BigDecimal("199.90"));
        return a;
    }
}
