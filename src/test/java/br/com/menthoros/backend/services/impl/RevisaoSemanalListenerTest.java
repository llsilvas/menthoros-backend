package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.enums.OrigemEncerramento;
import br.com.menthoros.backend.events.SemanaEncerradaEvent;
import br.com.menthoros.backend.services.RevisaoSemanalService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RevisaoSemanalListenerTest {

    @Mock
    private RevisaoSemanalService revisaoSemanalService;

    @InjectMocks
    private RevisaoSemanalListener listener;

    @Test
    @DisplayName("delega ao serviço com planoId/tenantId do evento")
    void delega() {
        UUID planoId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        listener.aoEncerrarSemana(new SemanaEncerradaEvent(planoId, UUID.randomUUID(), tenantId, 0,
                OrigemEncerramento.ON_DEMAND));

        verify(revisaoSemanalService).gerarNoEncerramento(planoId, tenantId);
    }

    @Test
    @DisplayName("engole exceção do serviço — não desfaz o encerramento nem quebra outros listeners")
    void engoleExcecao() {
        UUID planoId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        doThrow(new RuntimeException("falha")).when(revisaoSemanalService)
                .gerarNoEncerramento(planoId, tenantId);

        assertThatCode(() -> listener.aoEncerrarSemana(new SemanaEncerradaEvent(planoId, UUID.randomUUID(),
                tenantId, 0, OrigemEncerramento.ON_DEMAND))).doesNotThrowAnyException();
    }
}
