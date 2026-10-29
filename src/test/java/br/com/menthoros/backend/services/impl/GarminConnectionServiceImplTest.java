package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.dto.garmin.GarminTokenResponse;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.IntegracaoExterna;
import br.com.menthoros.backend.enums.FonteDados;
import br.com.menthoros.backend.exception.GarminApiException;
import br.com.menthoros.backend.repository.IntegracaoExternaRepository;
import br.com.menthoros.backend.services.GarminClient;
import br.com.menthoros.backend.services.IntervalsIcuConnectionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class GarminConnectionServiceImplTest {

    private static final Instant AGORA = Instant.parse("2026-10-26T12:00:00Z");
    private static final String USER_ID = "d3315b1072421d0dd7c8f6b8e1de4df8";

    private IntegracaoExternaRepository repository;
    private IntervalsIcuConnectionService icuConnection;
    private GarminClient client;
    private GarminConnectionServiceImpl service;

    private UUID atletaId;
    private UUID tenantId;
    private Atleta atleta;

    @BeforeEach
    void setUp() {
        repository = mock(IntegracaoExternaRepository.class);
        icuConnection = mock(IntervalsIcuConnectionService.class);
        client = mock(GarminClient.class);
        service = new GarminConnectionServiceImpl(repository, icuConnection, client,
                Clock.fixed(AGORA, ZoneOffset.UTC));
        atletaId = UUID.randomUUID();
        tenantId = UUID.randomUUID();
        atleta = Atleta.builder().id(atletaId).build();
    }

    private IntegracaoExterna garminAtiva() {
        IntegracaoExterna g = new IntegracaoExterna();
        g.setAtleta(atleta);
        g.setPlataforma(FonteDados.GARMIN);
        g.setAtivo(true);
        g.setAccessToken("acc");
        g.setRefreshToken("ref");
        g.setTokenExpiraEm(AGORA.plusSeconds(100));
        g.setRefreshTokenExpiraEm(AGORA.plusSeconds(1000));
        g.setScopes("WORKOUT_IMPORT");
        g.setExternalAthleteId(USER_ID);
        return g;
    }

    @Nested
    @DisplayName("ativar")
    class Ativar {

        private final GarminTokenResponse token =
                new GarminTokenResponse("acc", "ref", "Bearer", 86400, 7775999L, null);

        @Test
        @DisplayName("linha nova: grava tokens, expirações reais e scopes")
        void linhaNova() {
            when(repository.findByAtletaIdAndPlataformaAndTenantId(atletaId, FonteDados.GARMIN, tenantId))
                    .thenReturn(Optional.empty());

            service.ativar(atleta, tenantId, token, USER_ID, List.of("WORKOUT_IMPORT", "ACTIVITY_EXPORT"));

            ArgumentCaptor<IntegracaoExterna> salvo = ArgumentCaptor.forClass(IntegracaoExterna.class);
            verify(repository).save(salvo.capture());
            IntegracaoExterna g = salvo.getValue();
            assertThat(g.getPlataforma()).isEqualTo(FonteDados.GARMIN);
            assertThat(g.getTenantId()).isEqualTo(tenantId);
            assertThat(g.isAtivo()).isTrue();
            assertThat(g.getExternalAthleteId()).isEqualTo(USER_ID);
            assertThat(g.getTokenExpiraEm()).isEqualTo(AGORA.plusSeconds(86400));
            assertThat(g.getRefreshTokenExpiraEm()).isEqualTo(AGORA.plusSeconds(7775999));
            assertThat(g.getScopes()).isEqualTo("WORKOUT_IMPORT,ACTIVITY_EXPORT");
        }

        @Test
        @DisplayName("linha revogada é reativada, não duplicada")
        void reativaLinhaExistente() {
            IntegracaoExterna revogada = new IntegracaoExterna();
            revogada.setAtivo(false);
            revogada.setLastSyncError("revogada");
            when(repository.findByAtletaIdAndPlataformaAndTenantId(atletaId, FonteDados.GARMIN, tenantId))
                    .thenReturn(Optional.of(revogada));

            service.ativar(atleta, tenantId, token, USER_ID, List.of());

            verify(repository).save(revogada);
            assertThat(revogada.isAtivo()).isTrue();
            assertThat(revogada.getLastSyncError()).isNull();
        }

        @Test
        @DisplayName("exclusividade: desativa o intervals.icu ativo e pausa o Strava")
        void desativaIcuEPausaStrava() {
            IntegracaoExterna icu = new IntegracaoExterna();
            icu.setAtivo(true);
            icu.setAccessToken("icu-token");
            icu.setExternalAthleteId("i123");
            when(repository.findByAtletaIdAndPlataformaAndTenantId(atletaId, FonteDados.GARMIN, tenantId))
                    .thenReturn(Optional.empty());
            when(repository.findActiveByAtletaIdAndPlataformaAndTenantId(atletaId, FonteDados.INTERVALS_ICU, tenantId))
                    .thenReturn(Optional.of(icu));

            service.ativar(atleta, tenantId, token, USER_ID, List.of());

            assertThat(icu.isAtivo()).isFalse();
            assertThat(icu.getAccessToken()).isNull();
            assertThat(icu.getExternalAthleteId()).isNull();
            verify(repository).save(icu);
            verify(icuConnection).pausarStravaAutomaticamente(atletaId, tenantId);
        }

        @Test
        @DisplayName("sem intervals.icu ativo: só o Garmin é salvo e o Strava ainda é pausado")
        void semIcu() {
            when(repository.findByAtletaIdAndPlataformaAndTenantId(atletaId, FonteDados.GARMIN, tenantId))
                    .thenReturn(Optional.empty());
            when(repository.findActiveByAtletaIdAndPlataformaAndTenantId(atletaId, FonteDados.INTERVALS_ICU, tenantId))
                    .thenReturn(Optional.empty());

            service.ativar(atleta, tenantId, token, USER_ID, List.of());

            verify(repository, org.mockito.Mockito.times(1)).save(any());
            verify(icuConnection).pausarStravaAutomaticamente(atletaId, tenantId);
        }
    }

    @Nested
    @DisplayName("desconectar")
    class Desconectar {

        @Test
        @DisplayName("Garmin ok: remove o registro e zera tudo localmente")
        void garminOk() {
            IntegracaoExterna g = garminAtiva();
            when(repository.findActiveByAtletaIdAndPlataformaAndTenantId(atletaId, FonteDados.GARMIN, tenantId))
                    .thenReturn(Optional.of(g));

            service.desconectar(atletaId, tenantId);

            verify(client).removerRegistro("acc");
            assertZerada(g);
            verify(repository).save(g);
        }

        @Test
        @DisplayName("Garmin 500: a desconexão local conclui mesmo assim")
        void garmin500() {
            IntegracaoExterna g = garminAtiva();
            when(repository.findActiveByAtletaIdAndPlataformaAndTenantId(atletaId, FonteDados.GARMIN, tenantId))
                    .thenReturn(Optional.of(g));
            doThrow(new GarminApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Garmin retornou 500"))
                    .when(client).removerRegistro("acc");

            service.desconectar(atletaId, tenantId);

            assertZerada(g);
            verify(repository).save(g);
        }

        @Test
        @DisplayName("sem conexão ativa: no-op")
        void semConexao() {
            when(repository.findActiveByAtletaIdAndPlataformaAndTenantId(atletaId, FonteDados.GARMIN, tenantId))
                    .thenReturn(Optional.empty());

            service.desconectar(atletaId, tenantId);

            verifyNoInteractions(client);
            verify(repository, never()).save(any());
        }

        private void assertZerada(IntegracaoExterna g) {
            assertThat(g.isAtivo()).isFalse();
            assertThat(g.getAccessToken()).isNull();
            assertThat(g.getRefreshToken()).isNull();
            assertThat(g.getTokenExpiraEm()).isNull();
            assertThat(g.getRefreshTokenExpiraEm()).isNull();
            assertThat(g.getScopes()).isNull();
        }
    }

    @Nested
    @DisplayName("status")
    class Status {

        @Test
        @DisplayName("scopes parciais: só workoutImport; userId mascarado")
        void scopesParciais() {
            when(repository.findByAtletaIdAndPlataformaAndTenantId(atletaId, FonteDados.GARMIN, tenantId))
                    .thenReturn(Optional.of(garminAtiva()));

            var dto = service.status(atletaId, tenantId).orElseThrow();

            assertThat(dto.ativo()).isTrue();
            assertThat(dto.workoutImport()).isTrue();
            assertThat(dto.activityExport()).isFalse();
            assertThat(dto.userId()).isEqualTo("d331…4df8").doesNotContain(USER_ID);
        }

        @Test
        @DisplayName("nunca conectou: vazio")
        void nuncaConectou() {
            when(repository.findByAtletaIdAndPlataformaAndTenantId(atletaId, FonteDados.GARMIN, tenantId))
                    .thenReturn(Optional.empty());

            assertThat(service.status(atletaId, tenantId)).isEmpty();
        }

        @Test
        @DisplayName("revogada: ativo=false, sem permissões e sem userId")
        void revogada() {
            IntegracaoExterna g = new IntegracaoExterna();
            g.setAtivo(false);
            g.setLastSyncError("Autorização revogada");
            when(repository.findByAtletaIdAndPlataformaAndTenantId(atletaId, FonteDados.GARMIN, tenantId))
                    .thenReturn(Optional.of(g));

            var dto = service.status(atletaId, tenantId).orElseThrow();

            assertThat(dto.ativo()).isFalse();
            assertThat(dto.workoutImport()).isFalse();
            assertThat(dto.activityExport()).isFalse();
            assertThat(dto.userId()).isNull();
            assertThat(dto.lastSyncError()).isEqualTo("Autorização revogada");
        }
    }

    @Nested
    @DisplayName("conexaoDeCanal")
    class ConexaoDeCanal {

        @Test
        @DisplayName("usa a query por canais e devolve a conexão ativa")
        void devolveAtiva() {
            IntegracaoExterna g = garminAtiva();
            when(repository.findActiveByAtletaIdAndPlataformaInAndTenantId(
                    atletaId, List.of(FonteDados.INTERVALS_ICU, FonteDados.GARMIN), tenantId))
                    .thenReturn(List.of(g));

            assertThat(service.conexaoDeCanal(atletaId, tenantId)).containsSame(g);
        }

        @Test
        @DisplayName("sem canal ativo: vazio")
        void vazio() {
            when(repository.findActiveByAtletaIdAndPlataformaInAndTenantId(
                    atletaId, List.of(FonteDados.INTERVALS_ICU, FonteDados.GARMIN), tenantId))
                    .thenReturn(List.of());

            assertThat(service.conexaoDeCanal(atletaId, tenantId)).isEmpty();
        }
    }
}
