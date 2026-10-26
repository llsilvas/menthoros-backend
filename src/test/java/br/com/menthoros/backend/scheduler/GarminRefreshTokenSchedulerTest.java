package br.com.menthoros.backend.scheduler;

import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.IntegracaoExterna;
import br.com.menthoros.backend.enums.FonteDados;
import br.com.menthoros.backend.repository.IntegracaoExternaRepository;
import br.com.menthoros.backend.services.GarminOAuthService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GarminRefreshTokenSchedulerTest {

    private static final Instant AGORA = Instant.parse("2026-10-26T03:30:00Z");

    private final IntegracaoExternaRepository repository = mock(IntegracaoExternaRepository.class);
    private final GarminOAuthService oauth = mock(GarminOAuthService.class);
    private final GarminRefreshTokenScheduler scheduler =
            new GarminRefreshTokenScheduler(repository, oauth, Clock.fixed(AGORA, ZoneOffset.UTC));

    private IntegracaoExterna conexao() {
        IntegracaoExterna c = new IntegracaoExterna();
        c.setAtleta(Atleta.builder().id(UUID.randomUUID()).build());
        return c;
    }

    @Test
    @DisplayName("consulta GARMIN com limite de agora + 30 dias e renova cada conexão devolvida")
    void renovaAsConexoesDaJanela() {
        IntegracaoExterna a = conexao();
        IntegracaoExterna b = conexao();
        when(repository.findActiveByPlataformaAndRefreshTokenExpiraEmBefore(
                FonteDados.GARMIN, Instant.parse("2026-11-25T03:30:00Z"))).thenReturn(List.of(a, b));

        scheduler.renovarRefreshTokensProximosDoVencimento();

        verify(oauth).renovar(a);
        verify(oauth).renovar(b);
    }

    @Test
    @DisplayName("falha numa conexão não impede a renovação das demais")
    void umaFalhaNaoParaAsOutras() {
        IntegracaoExterna ruim = conexao();
        IntegracaoExterna boa = conexao();
        when(repository.findActiveByPlataformaAndRefreshTokenExpiraEmBefore(
                FonteDados.GARMIN, Instant.parse("2026-11-25T03:30:00Z"))).thenReturn(List.of(ruim, boa));
        doThrow(new IllegalStateException("revogada")).when(oauth).renovar(ruim);

        scheduler.renovarRefreshTokensProximosDoVencimento();

        verify(oauth).renovar(boa);
    }
}
