package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.dto.output.IntervalsIcuConnectionStatusDto;
import br.com.menthoros.backend.entity.IntegracaoExterna;
import br.com.menthoros.backend.enums.FonteDados;
import br.com.menthoros.backend.multitenancy.TenantContext;
import br.com.menthoros.backend.repository.IntegracaoExternaRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IntervalsIcuConnectionServiceImplTest {

    @Mock private IntegracaoExternaRepository integracaoRepository;
    @InjectMocks private IntervalsIcuConnectionServiceImpl service;

    private UUID tenantId;
    private UUID atletaId;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        atletaId = UUID.randomUUID();
        TenantContext.setTenantId(tenantId);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // O bloco "conectar" saiu com o fluxo de API key (D6). Os três testes do hook D5.2 que viviam
    // nele foram preservados aqui: o hook não morreu, só mudou de porta de entrada — agora é
    // público e quem o chama é o IntervalsIcuOAuthService, a partir do callback (D9).
    @Nested
    @DisplayName("pausarStravaAutomaticamente")
    class PausarStravaAutomaticamente {

        @Test
        @DisplayName("hook D5.2: Strava ativo → autoSyncPausado=true automaticamente")
        void pausaStravaAtivo() {
            IntegracaoExterna integracaoStrava = new IntegracaoExterna();
            integracaoStrava.setPlataforma(FonteDados.STRAVA);
            integracaoStrava.setAtivo(true);
            integracaoStrava.setAutoSyncPausado(false);

            when(integracaoRepository.findActiveByAtletaIdAndPlataformaAndTenantId(atletaId, FonteDados.STRAVA, tenantId))
                    .thenReturn(Optional.of(integracaoStrava));

            service.pausarStravaAutomaticamente(atletaId, tenantId);

            assertThat(integracaoStrava.isAutoSyncPausado()).isTrue();
            verify(integracaoRepository).save(integracaoStrava);
        }

        @Test
        @DisplayName("hook D5.2: sem Strava conectado é no-op quanto à flag")
        void semStravaNaoFazNada() {
            when(integracaoRepository.findActiveByAtletaIdAndPlataformaAndTenantId(atletaId, FonteDados.STRAVA, tenantId))
                    .thenReturn(Optional.empty());

            service.pausarStravaAutomaticamente(atletaId, tenantId);

            verify(integracaoRepository, never()).save(any());
        }

        @Test
        @DisplayName("hook D5.2 (achado Baixo do 5º pre-mortem): Strava já pausado permanece true, sem save duplicado")
        void stravaJaPausadoEhIdempotente() {
            IntegracaoExterna integracaoStrava = new IntegracaoExterna();
            integracaoStrava.setPlataforma(FonteDados.STRAVA);
            integracaoStrava.setAtivo(true);
            integracaoStrava.setAutoSyncPausado(true);

            when(integracaoRepository.findActiveByAtletaIdAndPlataformaAndTenantId(atletaId, FonteDados.STRAVA, tenantId))
                    .thenReturn(Optional.of(integracaoStrava));

            service.pausarStravaAutomaticamente(atletaId, tenantId);

            assertThat(integracaoStrava.isAutoSyncPausado()).isTrue();
            verify(integracaoRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("status")
    class Status {

        @Test
        @DisplayName("integração existente mapeia para DTO sem expor a key")
        void integracaoExistenteMapeiaDto() {
            IntegracaoExterna integracao = new IntegracaoExterna();
            integracao.setAtivo(true);
            integracao.setExternalAthleteId("i641775");
            integracao.setAccessToken("key-secreta");
            integracao.setUltimaSincronizacao(Instant.parse("2026-07-10T10:00:00Z"));
            integracao.setLastSyncError("Falha ao autenticar");
            when(integracaoRepository.findByAtletaIdAndPlataformaAndTenantId(atletaId, FonteDados.INTERVALS_ICU, tenantId))
                    .thenReturn(Optional.of(integracao));

            Optional<IntervalsIcuConnectionStatusDto> resultado = service.status(atletaId);

            assertThat(resultado).isPresent();
            IntervalsIcuConnectionStatusDto dto = resultado.get();
            assertThat(dto.conectado()).isTrue();
            assertThat(dto.externalAthleteId()).isEqualTo("i641775");
            assertThat(dto.ultimoPush()).isEqualTo(Instant.parse("2026-07-10T10:00:00Z"));
            assertThat(dto.ultimoErro()).isEqualTo("Falha ao autenticar");
            assertThat(dto.toString()).doesNotContain("key-secreta");
        }

        @Test
        @DisplayName("nunca conectado retorna Optional vazio")
        void nuncaConectadoRetornaVazio() {
            when(integracaoRepository.findByAtletaIdAndPlataformaAndTenantId(atletaId, FonteDados.INTERVALS_ICU, tenantId))
                    .thenReturn(Optional.empty());

            Optional<IntervalsIcuConnectionStatusDto> resultado = service.status(atletaId);

            assertThat(resultado).isEmpty();
        }
    }

    @Nested
    @DisplayName("desconectar")
    class Desconectar {

        // D13/CA6: "credenciais zeradas" é campo a campo. A versão anterior limpava só
        // accessToken e refreshToken — e a métrica de sucesso da change conta conexões OAuth por
        // scopes != null, então um atleta desconectado seguiria contado como conectado.
        @Test
        @DisplayName("soft-disconnect zera TODOS os campos OAuth e desativa")
        void softDisconnect() {
            IntegracaoExterna integracao = new IntegracaoExterna();
            integracao.setAccessToken("tok");
            integracao.setRefreshToken("refresh");
            integracao.setScopes("ACTIVITY:READ,CALENDAR:WRITE");
            integracao.setTokenExpiraEm(Instant.parse("2026-09-01T00:00:00Z"));
            integracao.setExternalAthleteId("i641775");
            integracao.setLastSyncError("erro antigo");
            integracao.setAtivo(true);
            when(integracaoRepository.findByAtletaIdAndPlataformaAndTenantId(atletaId, FonteDados.INTERVALS_ICU, tenantId))
                    .thenReturn(Optional.of(integracao));

            service.desconectar(atletaId);

            assertThat(integracao.isAtivo()).isFalse();
            assertThat(integracao.getAccessToken()).isNull();
            assertThat(integracao.getRefreshToken()).isNull();
            assertThat(integracao.getScopes()).isNull();
            assertThat(integracao.getTokenExpiraEm()).isNull();
            assertThat(integracao.getExternalAthleteId()).isNull();
            assertThat(integracao.getLastSyncError()).isNull();
            verify(integracaoRepository).save(integracao);
        }

        @Test
        @DisplayName("desconectar sem integração existente é no-op")
        void desconectarSemIntegracaoNaoFalha() {
            when(integracaoRepository.findByAtletaIdAndPlataformaAndTenantId(atletaId, FonteDados.INTERVALS_ICU, tenantId))
                    .thenReturn(Optional.empty());

            service.desconectar(atletaId);

            verify(integracaoRepository, never()).save(any());
        }

        @Test
        @DisplayName("D5.2 (5º pre-mortem, 'nunca auto-retomar'): Strava pausado permanece autoSyncPausado=true — nenhum save na linha Strava")
        void desconectarNaoTocaNoAutoSyncPausadoDoStrava() {
            IntegracaoExterna integracaoIcu = new IntegracaoExterna();
            integracaoIcu.setAccessToken("key");
            integracaoIcu.setAtivo(true);
            when(integracaoRepository.findByAtletaIdAndPlataformaAndTenantId(atletaId, FonteDados.INTERVALS_ICU, tenantId))
                    .thenReturn(Optional.of(integracaoIcu));

            IntegracaoExterna integracaoStrava = new IntegracaoExterna();
            integracaoStrava.setPlataforma(FonteDados.STRAVA);
            integracaoStrava.setAtivo(true);
            integracaoStrava.setAutoSyncPausado(true);
            when(integracaoRepository.findActiveByAtletaIdAndPlataformaAndTenantId(atletaId, FonteDados.STRAVA, tenantId))
                    .thenReturn(Optional.of(integracaoStrava));

            service.desconectar(atletaId);

            assertThat(integracaoStrava.isAutoSyncPausado()).isTrue();
            // teste negativo explícito: nenhuma chamada de save com a linha Strava como argumento
            verify(integracaoRepository, never()).save(integracaoStrava);
            // único save é o da linha intervals.icu (soft-disconnect)
            verify(integracaoRepository, times(1)).save(any());
        }
    }

    @Nested
    @DisplayName("conexaoAtiva")
    class ConexaoAtiva {

        @Test
        @DisplayName("retorna integração ativa quando existir")
        void retornaIntegracaoAtiva() {
            IntegracaoExterna integracao = new IntegracaoExterna();
            integracao.setAtivo(true);
            when(integracaoRepository.findActiveByAtletaIdAndPlataformaAndTenantId(atletaId, FonteDados.INTERVALS_ICU, tenantId))
                    .thenReturn(Optional.of(integracao));

            Optional<IntegracaoExterna> resultado = service.conexaoAtiva(atletaId, tenantId);

            assertThat(resultado).contains(integracao);
        }

        @Test
        @DisplayName("retorna vazio quando não há conexão ativa")
        void retornaVazioSemConexaoAtiva() {
            when(integracaoRepository.findActiveByAtletaIdAndPlataformaAndTenantId(atletaId, FonteDados.INTERVALS_ICU, tenantId))
                    .thenReturn(Optional.empty());

            Optional<IntegracaoExterna> resultado = service.conexaoAtiva(atletaId, tenantId);

            assertThat(resultado).isEmpty();
        }
    }
}
