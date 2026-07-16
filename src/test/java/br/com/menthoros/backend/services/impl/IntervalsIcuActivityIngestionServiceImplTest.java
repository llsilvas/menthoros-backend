package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.dto.intervalsicu.IcuActivityDto;
import br.com.menthoros.backend.dto.output.TreinoRealizadoOutputDto;
import br.com.menthoros.backend.entity.Assessoria;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.IntegracaoExterna;
import br.com.menthoros.backend.entity.TreinoRealizado;
import br.com.menthoros.backend.enums.FonteDados;
import br.com.menthoros.backend.exception.DomainConflictException;
import br.com.menthoros.backend.exception.DomainNotFoundException;
import br.com.menthoros.backend.exception.DomainRuleViolationException;
import br.com.menthoros.backend.exception.IntervalsIcuApiException;
import br.com.menthoros.backend.exception.IntervalsIcuRateLimitException;
import br.com.menthoros.backend.mapper.TreinoMapper;
import br.com.menthoros.backend.repository.AtletaRepository;
import br.com.menthoros.backend.repository.IntegracaoExternaRepository;
import br.com.menthoros.backend.repository.TreinoRealizadoRepository;
import br.com.menthoros.backend.services.IntervalsIcuClient;
import br.com.menthoros.backend.services.IntervalsIcuConnectionService;
import br.com.menthoros.backend.services.helper.IntervalsIcuActivityMapper;
import br.com.menthoros.backend.services.helper.IntervalsIcuActivityPersister;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatusCode;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IntervalsIcuActivityIngestionServiceImplTest {

    @Mock private IntervalsIcuConnectionService intervalsIcuConnectionService;
    @Mock private IntegracaoExternaRepository integracaoExternaRepository;
    @Mock private AtletaRepository atletaRepository;
    @Mock private TreinoRealizadoRepository treinoRealizadoRepository;
    @Mock private IntervalsIcuClient intervalsIcuClient;
    @Mock private IntervalsIcuActivityMapper intervalsIcuActivityMapper;
    @Mock private TreinoMapper treinoMapper;
    @Mock private IntervalsIcuActivityPersister persister;

    private IntervalsIcuActivityIngestionServiceImpl service;

    private UUID atletaId;
    private UUID tenantId;
    private Atleta atleta;
    private static final String ACTIVITY_ID = "i166338796";
    private static final String API_KEY = "chave-secreta";

    @BeforeEach
    void setUp() {
        service = new IntervalsIcuActivityIngestionServiceImpl(
                intervalsIcuConnectionService, integracaoExternaRepository, atletaRepository,
                treinoRealizadoRepository, intervalsIcuClient, intervalsIcuActivityMapper,
                treinoMapper, persister);

        atletaId = UUID.randomUUID();
        tenantId = UUID.randomUUID();
        Assessoria assessoria = new Assessoria();
        assessoria.setId(tenantId);
        atleta = new Atleta();
        atleta.setId(atletaId);
        atleta.setAssessoria(assessoria);
    }

    @Nested
    @DisplayName("importarAtividade — idempotência (CA2, passo 0)")
    class Idempotencia {

        @Test
        @DisplayName("activity já importada retorna o treino existente SEM chamar o client nem checar Strava")
        void activityJaImportadaRetornaExistenteSemChamarClient() {
            TreinoRealizado existente = new TreinoRealizado();
            existente.setId(UUID.randomUUID());
            TreinoRealizadoOutputDto dtoExistente = mockOutputDto();
            when(treinoRealizadoRepository.findByTenantIdAndFonteDadosAndExternalId(tenantId, FonteDados.INTERVALS_ICU, ACTIVITY_ID))
                    .thenReturn(Optional.of(existente));
            when(treinoMapper.toOutputDto(existente)).thenReturn(dtoExistente);

            TreinoRealizadoOutputDto resultado = service.importarAtividade(atletaId, ACTIVITY_ID, tenantId);

            assertThat(resultado).isSameAs(dtoExistente);
            verifyNoInteractions(intervalsIcuClient);
            verify(integracaoExternaRepository, never()).findActiveByAtletaIdAndPlataformaAndTenantId(any(), eq(FonteDados.STRAVA), any());
        }
    }

    @Nested
    @DisplayName("importarAtividade — precondição de pausa Strava (CA11, passo 1)")
    class PrecondicaoStrava {

        @Test
        @DisplayName("Strava ativo e não pausado bloqueia com 409, sem chamada externa")
        void stravaAtivoNaoPausadoBloqueiaCom409() {
            when(treinoRealizadoRepository.findByTenantIdAndFonteDadosAndExternalId(any(), any(), any()))
                    .thenReturn(Optional.empty());
            IntegracaoExterna stravaAtivo = integracaoStrava(false);
            when(integracaoExternaRepository.findActiveByAtletaIdAndPlataformaAndTenantId(atletaId, FonteDados.STRAVA, tenantId))
                    .thenReturn(Optional.of(stravaAtivo));

            assertThatThrownBy(() -> service.importarAtividade(atletaId, ACTIVITY_ID, tenantId))
                    .isInstanceOf(DomainConflictException.class)
                    .hasMessageContaining("pause a sincronização Strava");

            verifyNoInteractions(intervalsIcuClient);
            verifyNoInteractions(intervalsIcuConnectionService);
        }

        @Test
        @DisplayName("sem Strava conectado prossegue além da precondição (chega a checar conexaoAtiva)")
        void semStravaProsseguiAlemDaPrecondicao() {
            when(treinoRealizadoRepository.findByTenantIdAndFonteDadosAndExternalId(any(), any(), any()))
                    .thenReturn(Optional.empty());
            when(integracaoExternaRepository.findActiveByAtletaIdAndPlataformaAndTenantId(atletaId, FonteDados.STRAVA, tenantId))
                    .thenReturn(Optional.empty());
            stubHappyPathAteConexao();

            assertThatThrownBy(() -> service.importarAtividade(atletaId, ACTIVITY_ID, tenantId));

            verify(intervalsIcuConnectionService).conexaoAtiva(atletaId, tenantId);
        }

        @Test
        @DisplayName("Strava pausado prossegue além da precondição (chega a checar conexaoAtiva)")
        void stravaPausadoProsseguiAlemDaPrecondicao() {
            when(treinoRealizadoRepository.findByTenantIdAndFonteDadosAndExternalId(any(), any(), any()))
                    .thenReturn(Optional.empty());
            when(integracaoExternaRepository.findActiveByAtletaIdAndPlataformaAndTenantId(atletaId, FonteDados.STRAVA, tenantId))
                    .thenReturn(Optional.of(integracaoStrava(true)));
            stubHappyPathAteConexao();

            assertThatThrownBy(() -> service.importarAtividade(atletaId, ACTIVITY_ID, tenantId));

            verify(intervalsIcuConnectionService).conexaoAtiva(atletaId, tenantId);
        }
    }

    @Nested
    @DisplayName("importarAtividade — sem conexão intervals.icu (CA4)")
    class SemConexao {

        @Test
        @DisplayName("sem conexão ativa lança 409 e nada é persistido")
        void semConexaoLanca409() {
            when(treinoRealizadoRepository.findByTenantIdAndFonteDadosAndExternalId(any(), any(), any()))
                    .thenReturn(Optional.empty());
            when(integracaoExternaRepository.findActiveByAtletaIdAndPlataformaAndTenantId(any(), eq(FonteDados.STRAVA), any()))
                    .thenReturn(Optional.empty());
            when(intervalsIcuConnectionService.conexaoAtiva(atletaId, tenantId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.importarAtividade(atletaId, ACTIVITY_ID, tenantId))
                    .isInstanceOf(DomainConflictException.class);

            verifyNoInteractions(intervalsIcuClient);
            verifyNoInteractions(persister);
        }
    }

    @Nested
    @DisplayName("importarAtividade — resolução do atleta (pre-mortem #15)")
    class ResolucaoAtleta {

        @Test
        @DisplayName("atleta resolvido via findByIdAndTenantId explícito, não o UUID cru")
        void atletaResolvidoViaFindByIdAndTenantId() {
            IcuActivityDto dto = icuDto();
            when(treinoRealizadoRepository.findByTenantIdAndFonteDadosAndExternalId(any(), any(), any()))
                    .thenReturn(Optional.empty());
            when(integracaoExternaRepository.findActiveByAtletaIdAndPlataformaAndTenantId(any(), eq(FonteDados.STRAVA), any()))
                    .thenReturn(Optional.empty());
            IntegracaoExterna conexao = integracaoIntervalsIcu();
            when(intervalsIcuConnectionService.conexaoAtiva(atletaId, tenantId)).thenReturn(Optional.of(conexao));
            when(atletaRepository.findByIdAndTenantId(atletaId, tenantId)).thenReturn(Optional.of(atleta));
            when(integracaoExternaRepository.findOtherActiveByExternalAthleteIdAndPlataformaAndTenantId(any(), any(), any(), any()))
                    .thenReturn(List.of());
            when(intervalsIcuClient.buscarAtividade(API_KEY, ACTIVITY_ID)).thenReturn(dto);
            when(intervalsIcuActivityMapper.isModalidadeSuportada(dto.type())).thenReturn(true);
            when(persister.persistir(any(), any(), any(), any())).thenReturn(new TreinoRealizado());
            when(treinoMapper.toOutputDto(any(TreinoRealizado.class))).thenReturn(mockOutputDto());

            service.importarAtividade(atletaId, ACTIVITY_ID, tenantId);

            verify(atletaRepository).findByIdAndTenantId(atletaId, tenantId);
        }

        @Test
        @DisplayName("atleta não encontrado no tenant lança 404")
        void atletaNaoEncontradoLanca404() {
            when(treinoRealizadoRepository.findByTenantIdAndFonteDadosAndExternalId(any(), any(), any()))
                    .thenReturn(Optional.empty());
            when(integracaoExternaRepository.findActiveByAtletaIdAndPlataformaAndTenantId(any(), eq(FonteDados.STRAVA), any()))
                    .thenReturn(Optional.empty());
            when(intervalsIcuConnectionService.conexaoAtiva(atletaId, tenantId)).thenReturn(Optional.of(integracaoIntervalsIcu()));
            when(atletaRepository.findByIdAndTenantId(atletaId, tenantId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.importarAtividade(atletaId, ACTIVITY_ID, tenantId))
                    .isInstanceOf(DomainNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("importarAtividade — guard D5.1 (externalAthleteId duplicado no tenant)")
    class GuardExternalAthleteIdDuplicado {

        @Test
        @DisplayName("outra conexão ativa do tenant com o mesmo externalAthleteId bloqueia com 409 sem chamar o client")
        void outraConexaoComMesmoExternalAthleteIdBloqueia() {
            when(treinoRealizadoRepository.findByTenantIdAndFonteDadosAndExternalId(any(), any(), any()))
                    .thenReturn(Optional.empty());
            when(integracaoExternaRepository.findActiveByAtletaIdAndPlataformaAndTenantId(any(), eq(FonteDados.STRAVA), any()))
                    .thenReturn(Optional.empty());
            IntegracaoExterna conexao = integracaoIntervalsIcu();
            when(intervalsIcuConnectionService.conexaoAtiva(atletaId, tenantId)).thenReturn(Optional.of(conexao));
            when(atletaRepository.findByIdAndTenantId(atletaId, tenantId)).thenReturn(Optional.of(atleta));
            IntegracaoExterna outraConexao = new IntegracaoExterna();
            when(integracaoExternaRepository.findOtherActiveByExternalAthleteIdAndPlataformaAndTenantId(
                    conexao.getExternalAthleteId(), FonteDados.INTERVALS_ICU, tenantId, atletaId))
                    .thenReturn(List.of(outraConexao));

            assertThatThrownBy(() -> service.importarAtividade(atletaId, ACTIVITY_ID, tenantId))
                    .isInstanceOf(DomainConflictException.class);

            verifyNoInteractions(intervalsIcuClient);
        }
    }

    @Nested
    @DisplayName("importarAtividade — erros do client (D3.1)")
    class ErrosDoClient {

        @Test
        @DisplayName("404 do client vira DomainNotFoundException")
        void notFoundViraDomainNotFound() {
            stubAteAntesDoClient();
            when(intervalsIcuClient.buscarAtividade(anyString(), anyString()))
                    .thenThrow(new IntervalsIcuApiException(HttpStatusCode.valueOf(404), "não encontrado"));

            assertThatThrownBy(() -> service.importarAtividade(atletaId, ACTIVITY_ID, tenantId))
                    .isInstanceOf(DomainNotFoundException.class);
        }

        @ParameterizedTest
        @ValueSource(ints = {401, 403})
        @DisplayName("401/403 do client vira DomainConflictException (reconexão necessária) — distinto de 404")
        void authInvalidaViraDomainConflict(int status) {
            stubAteAntesDoClient();
            when(intervalsIcuClient.buscarAtividade(anyString(), anyString()))
                    .thenThrow(new IntervalsIcuApiException(HttpStatusCode.valueOf(status), "auth inválida"));

            assertThatThrownBy(() -> service.importarAtividade(atletaId, ACTIVITY_ID, tenantId))
                    .isInstanceOf(DomainConflictException.class);
        }

        @Test
        @DisplayName("422 do provedor vira DomainRuleViolationException")
        void provedor422ViraDomainRuleViolation() {
            stubAteAntesDoClient();
            when(intervalsIcuClient.buscarAtividade(anyString(), anyString()))
                    .thenThrow(new IntervalsIcuApiException(HttpStatusCode.valueOf(422), "rejeitado"));

            assertThatThrownBy(() -> service.importarAtividade(atletaId, ACTIVITY_ID, tenantId))
                    .isInstanceOf(DomainRuleViolationException.class);
        }

        @Test
        @DisplayName("429 do client vira IntervalsIcuRateLimitException — NUNCA 409")
        void rateLimit429ViraIntervalsIcuRateLimitException() {
            stubAteAntesDoClient();
            when(intervalsIcuClient.buscarAtividade(anyString(), anyString()))
                    .thenThrow(new IntervalsIcuApiException(HttpStatusCode.valueOf(429), "rate limit"));

            assertThatThrownBy(() -> service.importarAtividade(atletaId, ACTIVITY_ID, tenantId))
                    .isInstanceOf(IntervalsIcuRateLimitException.class)
                    .isNotInstanceOf(DomainConflictException.class);
        }

        @Test
        @DisplayName("5xx do client vira IntervalsIcuRateLimitException (mesmo tratamento de 429)")
        void erro5xxViraIntervalsIcuRateLimitException() {
            stubAteAntesDoClient();
            when(intervalsIcuClient.buscarAtividade(anyString(), anyString()))
                    .thenThrow(new IntervalsIcuApiException(HttpStatusCode.valueOf(503), "instável"));

            assertThatThrownBy(() -> service.importarAtividade(atletaId, ACTIVITY_ID, tenantId))
                    .isInstanceOf(IntervalsIcuRateLimitException.class);
        }

        @Test
        @DisplayName("falha de transporte (status nulo) vira IntervalsIcuRateLimitException")
        void falhaDeTransporteViraIntervalsIcuRateLimitException() {
            stubAteAntesDoClient();
            when(intervalsIcuClient.buscarAtividade(anyString(), anyString()))
                    .thenThrow(new IntervalsIcuApiException("timeout", new RuntimeException("io")));

            assertThatThrownBy(() -> service.importarAtividade(atletaId, ACTIVITY_ID, tenantId))
                    .isInstanceOf(IntervalsIcuRateLimitException.class);
        }
    }

    @Nested
    @DisplayName("importarAtividade — defesa em profundidade e modalidade (CA5, CA6)")
    class DefesaEModalidade {

        @Test
        @DisplayName("athlete_id divergente da conexão retorna 404 (não vaza existência)")
        void athleteIdDivergenteRetorna404() {
            stubAteAntesDoClient();
            IcuActivityDto dtoDivergente = new IcuActivityDto(
                    ACTIVITY_ID, "i999999", "Run", "Corrida", "2026-07-16T08:00:00",
                    1800, 1850, 5000.0, null, null, null, null, null, null, null, null, null);
            when(intervalsIcuClient.buscarAtividade(anyString(), anyString())).thenReturn(dtoDivergente);

            assertThatThrownBy(() -> service.importarAtividade(atletaId, ACTIVITY_ID, tenantId))
                    .isInstanceOf(DomainNotFoundException.class);

            verifyNoInteractions(persister);
        }

        @Test
        @DisplayName("modalidade não suportada retorna 422, nada persistido")
        void modalidadeNaoSuportadaRetorna422() {
            stubAteAntesDoClient();
            IcuActivityDto dto = icuDto();
            when(intervalsIcuClient.buscarAtividade(anyString(), anyString())).thenReturn(dto);
            when(intervalsIcuActivityMapper.isModalidadeSuportada(dto.type())).thenReturn(false);

            assertThatThrownBy(() -> service.importarAtividade(atletaId, ACTIVITY_ID, tenantId))
                    .isInstanceOf(DomainRuleViolationException.class);

            verifyNoInteractions(persister);
        }
    }

    @Nested
    @DisplayName("importarAtividade — happy path e delegação ao persister (CA1, CA3, CA8)")
    class HappyPath {

        @Test
        @DisplayName("fluxo completo delega ao persister e retorna o DTO mapeado")
        void fluxoCompletoDelegaAoPersisterERetornaDto() {
            stubAteAntesDoClient();
            IcuActivityDto dto = icuDto();
            when(intervalsIcuClient.buscarAtividade(anyString(), anyString())).thenReturn(dto);
            when(intervalsIcuActivityMapper.isModalidadeSuportada(dto.type())).thenReturn(true);
            TreinoRealizado salvo = new TreinoRealizado();
            when(persister.persistir(eq(dto), eq(atleta), eq(tenantId), eq(ACTIVITY_ID))).thenReturn(salvo);
            TreinoRealizadoOutputDto outputDto = mockOutputDto();
            when(treinoMapper.toOutputDto(salvo)).thenReturn(outputDto);

            TreinoRealizadoOutputDto resultado = service.importarAtividade(atletaId, ACTIVITY_ID, tenantId);

            assertThat(resultado).isSameAs(outputDto);
            verify(persister).persistir(dto, atleta, tenantId, ACTIVITY_ID);
        }
    }

    @Nested
    @DisplayName("importarAtividade — normalização de activityId (D5)")
    class NormalizacaoActivityId {

        @Test
        @DisplayName("URL completa colada extrai o segmento final")
        void urlCompletaExtraiSegmentoFinal() {
            when(treinoRealizadoRepository.findByTenantIdAndFonteDadosAndExternalId(tenantId, FonteDados.INTERVALS_ICU, ACTIVITY_ID))
                    .thenReturn(Optional.of(new TreinoRealizado()));
            when(treinoMapper.toOutputDto(any(TreinoRealizado.class))).thenReturn(mockOutputDto());

            service.importarAtividade(atletaId, "https://intervals.icu/activities/" + ACTIVITY_ID, tenantId);

            verify(treinoRealizadoRepository).findByTenantIdAndFonteDadosAndExternalId(tenantId, FonteDados.INTERVALS_ICU, ACTIVITY_ID);
        }

        @ParameterizedTest
        @ValueSource(strings = {"i123/i456", "i123?query=1", "i123%20"})
        @DisplayName("valores soltos com caracteres inválidos são rejeitados antes de chamar o client")
        void valoresInvalidosSaoRejeitados(String activityIdInvalido) {
            assertThatThrownBy(() -> service.importarAtividade(atletaId, activityIdInvalido, tenantId))
                    .isInstanceOf(IllegalArgumentException.class);

            verifyNoInteractions(intervalsIcuClient);
            verifyNoInteractions(treinoRealizadoRepository);
        }
    }

    // ---- helpers ----

    private void stubHappyPathAteConexao() {
        when(intervalsIcuConnectionService.conexaoAtiva(atletaId, tenantId)).thenReturn(Optional.empty());
    }

    private void stubAteAntesDoClient() {
        when(treinoRealizadoRepository.findByTenantIdAndFonteDadosAndExternalId(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(integracaoExternaRepository.findActiveByAtletaIdAndPlataformaAndTenantId(any(), eq(FonteDados.STRAVA), any()))
                .thenReturn(Optional.empty());
        when(intervalsIcuConnectionService.conexaoAtiva(atletaId, tenantId)).thenReturn(Optional.of(integracaoIntervalsIcu()));
        when(atletaRepository.findByIdAndTenantId(atletaId, tenantId)).thenReturn(Optional.of(atleta));
        when(integracaoExternaRepository.findOtherActiveByExternalAthleteIdAndPlataformaAndTenantId(any(), any(), any(), any()))
                .thenReturn(List.of());
    }

    private IntegracaoExterna integracaoStrava(boolean autoSyncPausado) {
        IntegracaoExterna i = new IntegracaoExterna();
        i.setPlataforma(FonteDados.STRAVA);
        i.setAtivo(true);
        i.setAutoSyncPausado(autoSyncPausado);
        return i;
    }

    private IntegracaoExterna integracaoIntervalsIcu() {
        IntegracaoExterna i = new IntegracaoExterna();
        i.setPlataforma(FonteDados.INTERVALS_ICU);
        i.setAtivo(true);
        i.setAccessToken(API_KEY);
        i.setExternalAthleteId("i641775");
        return i;
    }

    private IcuActivityDto icuDto() {
        return new IcuActivityDto(
                ACTIVITY_ID, "i641775", "Run", "Corrida", "2026-07-16T08:00:00",
                1800, 1850, 5000.0, null, null, null, null, null, null, null, null, null);
    }

    private TreinoRealizadoOutputDto mockOutputDto() {
        return org.mockito.Mockito.mock(TreinoRealizadoOutputDto.class);
    }
}
