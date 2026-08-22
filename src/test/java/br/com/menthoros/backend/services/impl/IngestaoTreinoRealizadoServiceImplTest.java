package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.TreinoRealizado;
import br.com.menthoros.backend.exception.DomainRuleViolationException;
import br.com.menthoros.backend.repository.TreinoRealizadoRepository;
import br.com.menthoros.backend.services.TsbService;
import br.com.menthoros.backend.services.helper.TreinoDedupHelper;
import br.com.menthoros.backend.services.helper.TssCalculatorService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Invariantes de {@code registrar} que não precisam de banco real: validação de entrada (CA8) e
 * atomicidade sob falha de um colaborador (CA9) — a garantia real de rollback vem do
 * {@code @Transactional} do método, que este teste unitário não exercita; aqui provamos a
 * pré-condição dela: se um colaborador lança, {@code registrar} propaga em vez de engolir.
 */
@ExtendWith(MockitoExtension.class)
class IngestaoTreinoRealizadoServiceImplTest {

    @Mock private TreinoRealizadoRepository treinoRealizadoRepository;
    @Mock private TreinoDedupHelper treinoDedupHelper;
    @Mock private TssCalculatorService tssCalculatorService;
    @Mock private TsbService tsbService;
    @Mock private ApplicationEventPublisher eventPublisher;
    @InjectMocks private IngestaoTreinoRealizadoServiceImpl service;

    @Nested
    @DisplayName("registrar — validação de entrada [CA8]")
    class Validacao {

        @Test
        @DisplayName("dataTreino nulo lança DomainRuleViolationException e não persiste nada")
        void dataTreinoNuloRejeitado() {
            TreinoRealizado treino = new TreinoRealizado();
            treino.setAtleta(new Atleta());

            assertThatThrownBy(() -> service.registrar(treino, null))
                    .isInstanceOf(DomainRuleViolationException.class)
                    .hasMessageContaining("dataTreino");

            verifyNoInteractions(tssCalculatorService, tsbService, eventPublisher, treinoDedupHelper);
        }

        @Test
        @DisplayName("atleta nulo lança DomainRuleViolationException")
        void atletaNuloRejeitado() {
            TreinoRealizado treino = new TreinoRealizado();
            treino.setDataTreino(LocalDate.now());

            assertThatThrownBy(() -> service.registrar(treino, null))
                    .isInstanceOf(DomainRuleViolationException.class)
                    .hasMessageContaining("atleta");
        }
    }

    @Nested
    @DisplayName("registrar — atomicidade [CA9]")
    class Atomicidade {

        @Test
        @DisplayName("falha no recálculo de carga propaga — não fica engolida, permitindo o rollback do @Transactional")
        void falhaNaCargaPropaga() {
            Atleta atleta = new Atleta();
            atleta.setId(UUID.randomUUID());
            TreinoRealizado treino = new TreinoRealizado();
            treino.setAtleta(atleta);
            treino.setDataTreino(LocalDate.now());

            when(tssCalculatorService.calcularTss(any())).thenReturn(50);
            when(treinoRealizadoRepository.save(treino)).thenReturn(treino);
            RuntimeException falhaSimulada = new RuntimeException("falha simulada no recálculo de TSB");
            doThrow(falhaSimulada).when(tsbService).recalcularDesde(any(), any());

            assertThatThrownBy(() -> service.registrar(treino, null))
                    .isSameAs(falhaSimulada);
        }
    }
}
