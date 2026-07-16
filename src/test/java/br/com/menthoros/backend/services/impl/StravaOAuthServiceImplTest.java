package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.config.external.StravaProperties;
import br.com.menthoros.backend.dto.output.StravaSyncPauseStatusDto;
import br.com.menthoros.backend.entity.IntegracaoExterna;
import br.com.menthoros.backend.enums.FonteDados;
import br.com.menthoros.backend.exception.ResourceNotFoundException;
import br.com.menthoros.backend.multitenancy.TenantContext;
import br.com.menthoros.backend.repository.AtletaRepository;
import br.com.menthoros.backend.repository.IntegracaoExternaRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StravaOAuthServiceImplTest {

    @Mock
    private StravaProperties stravaProperties;
    @Mock
    private AtletaRepository atletaRepository;
    @Mock
    private IntegracaoExternaRepository integracaoExternaRepository;
    @Mock
    private WebClient stravaWebClient;

    private StravaOAuthServiceImpl service;
    private UUID atletaId;
    private UUID tenantId;

    @BeforeEach
    void setUp() {
        service = new StravaOAuthServiceImpl(stravaProperties, atletaRepository, integracaoExternaRepository, stravaWebClient);
        atletaId = UUID.randomUUID();
        tenantId = UUID.randomUUID();
        TenantContext.setTenantId(tenantId);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Nested
    @DisplayName("pausarSync")
    class PausarSync {

        @Test
        @DisplayName("integração presente: seta autoSyncPausado=true e salva")
        void integracaoPresenteSetaTrue() {
            IntegracaoExterna integracao = integracaoStrava(false);
            when(integracaoExternaRepository.findByAtletaIdAndPlataformaAndTenantId(atletaId, FonteDados.STRAVA, tenantId))
                    .thenReturn(Optional.of(integracao));
            when(integracaoExternaRepository.save(any(IntegracaoExterna.class))).thenAnswer(inv -> inv.getArgument(0));

            StravaSyncPauseStatusDto resultado = service.pausarSync(atletaId, tenantId);

            assertThat(resultado.autoSyncPausado()).isTrue();
            assertThat(integracao.isAutoSyncPausado()).isTrue();
            verify(integracaoExternaRepository).save(integracao);
        }

        @Test
        @DisplayName("integração ausente: lança ResourceNotFoundException (404)")
        void integracaoAusenteLancaExcecao() {
            when(integracaoExternaRepository.findByAtletaIdAndPlataformaAndTenantId(atletaId, FonteDados.STRAVA, tenantId))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.pausarSync(atletaId, tenantId))
                    .isInstanceOf(ResourceNotFoundException.class);
            verify(integracaoExternaRepository, never()).save(any());
        }

        @Test
        @DisplayName("idempotente: chamar duas vezes com já pausado não lança erro")
        void idempotente() {
            IntegracaoExterna integracao = integracaoStrava(true);
            when(integracaoExternaRepository.findByAtletaIdAndPlataformaAndTenantId(atletaId, FonteDados.STRAVA, tenantId))
                    .thenReturn(Optional.of(integracao));
            when(integracaoExternaRepository.save(any(IntegracaoExterna.class))).thenAnswer(inv -> inv.getArgument(0));

            StravaSyncPauseStatusDto resultado = service.pausarSync(atletaId, tenantId);

            assertThat(resultado.autoSyncPausado()).isTrue();
        }
    }

    @Nested
    @DisplayName("retomarSync")
    class RetomarSync {

        @Test
        @DisplayName("integração presente: seta autoSyncPausado=false e salva")
        void integracaoPresenteSetaFalse() {
            IntegracaoExterna integracao = integracaoStrava(true);
            when(integracaoExternaRepository.findByAtletaIdAndPlataformaAndTenantId(atletaId, FonteDados.STRAVA, tenantId))
                    .thenReturn(Optional.of(integracao));
            when(integracaoExternaRepository.save(any(IntegracaoExterna.class))).thenAnswer(inv -> inv.getArgument(0));

            StravaSyncPauseStatusDto resultado = service.retomarSync(atletaId, tenantId);

            assertThat(resultado.autoSyncPausado()).isFalse();
            assertThat(integracao.isAutoSyncPausado()).isFalse();
        }

        @Test
        @DisplayName("integração ausente: lança ResourceNotFoundException (404)")
        void integracaoAusenteLancaExcecao() {
            when(integracaoExternaRepository.findByAtletaIdAndPlataformaAndTenantId(atletaId, FonteDados.STRAVA, tenantId))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.retomarSync(atletaId, tenantId))
                    .isInstanceOf(ResourceNotFoundException.class);
            verify(integracaoExternaRepository, never()).save(any());
        }

        @Test
        @DisplayName("idempotente: chamar duas vezes com já retomado não lança erro")
        void idempotente() {
            IntegracaoExterna integracao = integracaoStrava(false);
            when(integracaoExternaRepository.findByAtletaIdAndPlataformaAndTenantId(atletaId, FonteDados.STRAVA, tenantId))
                    .thenReturn(Optional.of(integracao));
            when(integracaoExternaRepository.save(any(IntegracaoExterna.class))).thenAnswer(inv -> inv.getArgument(0));

            StravaSyncPauseStatusDto resultado = service.retomarSync(atletaId, tenantId);

            assertThat(resultado.autoSyncPausado()).isFalse();
        }
    }

    private IntegracaoExterna integracaoStrava(boolean autoSyncPausado) {
        IntegracaoExterna integracao = new IntegracaoExterna();
        integracao.setPlataforma(FonteDados.STRAVA);
        integracao.setAtivo(true);
        integracao.setAutoSyncPausado(autoSyncPausado);
        integracao.setAtualizadoEm(LocalDateTime.now());
        return integracao;
    }
}
