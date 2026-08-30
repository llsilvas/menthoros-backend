package br.com.menthoros.backend.controller;

import br.com.menthoros.backend.dto.output.PlanoSemanalOutputDto;
import br.com.menthoros.backend.exception.DomainNotFoundException;
import br.com.menthoros.backend.mapper.PlanoSemanalMapper;
import br.com.menthoros.backend.services.AtletaProgressService;
import br.com.menthoros.backend.services.PlanoService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Dono no GET /planos/{atletaId} (analise-ia-treino-atleta, Codex #5): antes só o tenant
 * filtrava — um atleta lia o plano (e agora as flags de análise) de outro atleta da assessoria.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PlanoTreinoController — dono do plano para ROLE_ATLETA")
class PlanoTreinoControllerOwnerTest {

    @Mock private PlanoService planoService;
    @Mock private PlanoSemanalMapper planoSemanalMapper;
    @Mock private AtletaProgressService atletaProgressService;

    private PlanoTreinoController controller() {
        return new PlanoTreinoController(planoService, atletaProgressService, planoSemanalMapper);
    }

    private static Authentication auth(String role) {
        return new UsernamePasswordAuthenticationToken(
                "user", "n/a", List.of(new SimpleGrantedAuthority(role)));
    }

    @Test
    @DisplayName("atleta lendo o próprio plano: 200")
    void atletaDonoOk() {
        UUID atletaId = UUID.randomUUID();
        when(atletaProgressService.resolverAtletaIdAtual()).thenReturn(atletaId);
        when(planoService.buscarPlanoPorAtleta(atletaId, true))
                .thenReturn(PlanoSemanalOutputDto.builder().build());

        assertNotNull(controller().buscarPlanoSemanal(atletaId, auth("ROLE_ATLETA")).getBody());
    }

    @Test
    @DisplayName("atleta lendo o plano de outro atleta do mesmo tenant: 404")
    void atletaNaoDono404() {
        when(atletaProgressService.resolverAtletaIdAtual()).thenReturn(UUID.randomUUID());

        assertThatThrownBy(() -> controller().buscarPlanoSemanal(UUID.randomUUID(), auth("ROLE_ATLETA")))
                .isInstanceOf(DomainNotFoundException.class);
        verify(planoService, never()).buscarPlanoPorAtleta(any(), eq(true));
    }

    @Test
    @DisplayName("coach segue lendo qualquer atleta do tenant sem checagem de dono")
    void coachSemChecagem() {
        UUID atletaId = UUID.randomUUID();
        when(planoService.buscarPlanoPorAtleta(atletaId, false))
                .thenReturn(PlanoSemanalOutputDto.builder().build());

        assertNotNull(controller().buscarPlanoSemanal(atletaId, auth("ROLE_TECNICO")).getBody());
        verifyNoInteractions(atletaProgressService);
    }
}
