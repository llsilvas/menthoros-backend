package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.config.external.StravaProperties;
import br.com.menthoros.backend.dto.output.StravaSyncPauseStatusDto;
import br.com.menthoros.backend.dto.strava.StravaTokenResponse;
import br.com.menthoros.backend.entity.Assessoria;
import br.com.menthoros.backend.entity.Atleta;
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
import org.mockito.Answers;
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
    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
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
    @DisplayName("exchangeCodeForToken — hook D5.2 (Strava nasce pausado quando intervals.icu já está ativo)")
    class ExchangeCodeForTokenHook {

        private Atleta atleta;

        @BeforeEach
        void setUpAtleta() {
            Assessoria assessoria = new Assessoria();
            assessoria.setId(tenantId);
            atleta = new Atleta();
            atleta.setId(atletaId);
            atleta.setAssessoria(assessoria);

            when(stravaProperties.getTokenUri()).thenReturn("https://www.strava.com/oauth/token");
            when(stravaWebClient.post().uri(anyString()).body(any()).retrieve()
                    .bodyToMono(StravaTokenResponse.class).block())
                    .thenReturn(new StravaTokenResponse("Bearer", null, 21600, "refresh-token", "access-token", null));
        }

        @Test
        @DisplayName("intervals.icu ativo: integração Strava nasce com autoSyncPausado=true no MESMO save")
        void nasceComAutoSyncPausadoTrue() {
            when(integracaoExternaRepository.findByAtletaIdAndPlataforma(atletaId, FonteDados.STRAVA))
                    .thenReturn(Optional.empty());
            when(integracaoExternaRepository.findActiveByAtletaIdAndPlataformaAndTenantId(atletaId, FonteDados.INTERVALS_ICU, tenantId))
                    .thenReturn(Optional.of(new IntegracaoExterna()));
            when(integracaoExternaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            IntegracaoExterna resultado = service.exchangeCodeForToken("code-oauth", atleta);

            assertThat(resultado.isAutoSyncPausado()).isTrue();
            verify(integracaoExternaRepository, times(1)).save(any());
        }

        @Test
        @DisplayName("sem intervals.icu conectado: autoSyncPausado fica no default false, sem regressão")
        void semIntervalsIcuMantemDefaultFalse() {
            when(integracaoExternaRepository.findByAtletaIdAndPlataforma(atletaId, FonteDados.STRAVA))
                    .thenReturn(Optional.empty());
            when(integracaoExternaRepository.findActiveByAtletaIdAndPlataformaAndTenantId(atletaId, FonteDados.INTERVALS_ICU, tenantId))
                    .thenReturn(Optional.empty());
            when(integracaoExternaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            IntegracaoExterna resultado = service.exchangeCodeForToken("code-oauth", atleta);

            assertThat(resultado.isAutoSyncPausado()).isFalse();
        }

        @Test
        @DisplayName("reconexão com flag herdada true + intervals.icu já desconectado: permanece true, não reseta")
        void reconexaoPreservaFlagHerdadaSemIntervalsIcu() {
            IntegracaoExterna existente = new IntegracaoExterna();
            existente.setAutoSyncPausado(true);

            when(integracaoExternaRepository.findByAtletaIdAndPlataforma(atletaId, FonteDados.STRAVA))
                    .thenReturn(Optional.of(existente));
            when(integracaoExternaRepository.findActiveByAtletaIdAndPlataformaAndTenantId(atletaId, FonteDados.INTERVALS_ICU, tenantId))
                    .thenReturn(Optional.empty());
            when(integracaoExternaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            IntegracaoExterna resultado = service.exchangeCodeForToken("code-oauth", atleta);

            assertThat(resultado.isAutoSyncPausado()).isTrue();
        }

        @Test
        @DisplayName("reconexão com flag herdada true + intervals.icu ainda ativo: permanece true, idempotente")
        void reconexaoPreservaFlagHerdadaComIntervalsIcuAtivo() {
            IntegracaoExterna existente = new IntegracaoExterna();
            existente.setAutoSyncPausado(true);

            when(integracaoExternaRepository.findByAtletaIdAndPlataforma(atletaId, FonteDados.STRAVA))
                    .thenReturn(Optional.of(existente));
            when(integracaoExternaRepository.findActiveByAtletaIdAndPlataformaAndTenantId(atletaId, FonteDados.INTERVALS_ICU, tenantId))
                    .thenReturn(Optional.of(new IntegracaoExterna()));
            when(integracaoExternaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            IntegracaoExterna resultado = service.exchangeCodeForToken("code-oauth", atleta);

            assertThat(resultado.isAutoSyncPausado()).isTrue();
            verify(integracaoExternaRepository, times(1)).save(any());
        }
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
