package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.dto.intervalsicu.IcuActivityDto;
import br.com.menthoros.backend.dto.output.BackfillEtapasOutputDto;
import br.com.menthoros.backend.entity.EtapaRealizada;
import br.com.menthoros.backend.entity.IntegracaoExterna;
import br.com.menthoros.backend.entity.TreinoRealizado;
import br.com.menthoros.backend.enums.FonteDados;
import br.com.menthoros.backend.exception.DomainConflictException;
import br.com.menthoros.backend.exception.IntervalsIcuApiException;
import br.com.menthoros.backend.repository.TreinoRealizadoRepository;
import br.com.menthoros.backend.services.IntervalsIcuClient;
import br.com.menthoros.backend.services.IntervalsIcuConnectionService;
import br.com.menthoros.backend.services.helper.IntervalsIcuActivityMapper;
import br.com.menthoros.backend.services.helper.IntervalsIcuLapsBackfillPersister;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IntervalsIcuLapsBackfillServiceImplTest {

    @Mock private IntervalsIcuConnectionService intervalsIcuConnectionService;
    @Mock private TreinoRealizadoRepository treinoRealizadoRepository;
    @Mock private IntervalsIcuClient intervalsIcuClient;
    @Mock private IntervalsIcuActivityMapper intervalsIcuActivityMapper;
    @Mock private IntervalsIcuLapsBackfillPersister persister;

    private IntervalsIcuLapsBackfillServiceImpl service;

    private UUID tenantId;
    private UUID atletaId;

    @BeforeEach
    void setUp() {
        service = new IntervalsIcuLapsBackfillServiceImpl(intervalsIcuConnectionService,
                treinoRealizadoRepository, intervalsIcuClient, intervalsIcuActivityMapper, persister);
        tenantId = UUID.randomUUID();
        atletaId = UUID.randomUUID();
    }

    @Nested
    @DisplayName("backfillEtapas")
    class BackfillEtapas {

        @Test
        @DisplayName("sem conexao intervals.icu ativa lanca 409 e nao consulta candidatos")
        void semConexaoAtivaLanca409() {
            when(intervalsIcuConnectionService.conexaoAtiva(atletaId, tenantId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.backfillEtapas(atletaId, tenantId))
                    .isInstanceOf(DomainConflictException.class);

            verifyNoInteractions(treinoRealizadoRepository, intervalsIcuClient);
        }

        @Test
        @DisplayName("busca candidatos escopados por tenant, atleta e fonte INTERVALS_ICU")
        void candidatosEscopadosPorTenant() {
            conexaoAtiva();
            when(treinoRealizadoRepository.findSemEtapasByAtletaAndFonte(tenantId, atletaId, FonteDados.INTERVALS_ICU))
                    .thenReturn(List.of());

            BackfillEtapasOutputDto resultado = service.backfillEtapas(atletaId, tenantId);

            assertThat(resultado.candidatos()).isZero();
            verify(treinoRealizadoRepository).findSemEtapasByAtletaAndFonte(tenantId, atletaId, FonteDados.INTERVALS_ICU);
            verifyNoInteractions(intervalsIcuClient);
        }

        @Test
        @DisplayName("grava as etapas de cada candidato SEM tocar no summary")
        void gravaEtapasSemTocarNoSummary() {
            conexaoAtiva();
            TreinoRealizado treino = treino("i1");
            when(treinoRealizadoRepository.findSemEtapasByAtletaAndFonte(tenantId, atletaId, FonteDados.INTERVALS_ICU))
                    .thenReturn(List.of(treino));
            IcuActivityDto dto = activity();
            when(intervalsIcuClient.buscarAtividade(anyString(), eq("i1"), eq(true))).thenReturn(dto);
            List<EtapaRealizada> etapas = List.of(new EtapaRealizada(), new EtapaRealizada());
            when(intervalsIcuActivityMapper.mapEtapas(dto)).thenReturn(etapas);

            BackfillEtapasOutputDto resultado = service.backfillEtapas(atletaId, tenantId);

            assertThat(resultado.candidatos()).isEqualTo(1);
            assertThat(resultado.atualizados()).isEqualTo(1);
            // Só as etapas vão para a persistência — o summary do treino nunca é remapeado.
            verify(persister).gravarEtapas(treino.getId(), etapas, tenantId);
            verify(intervalsIcuActivityMapper, never()).map(any(), any());
        }

        @Test
        @DisplayName("activity sem intervalos na fonte nao grava nada e conta em semIntervalos")
        void activitySemIntervalosNaoGrava() {
            conexaoAtiva();
            when(treinoRealizadoRepository.findSemEtapasByAtletaAndFonte(tenantId, atletaId, FonteDados.INTERVALS_ICU))
                    .thenReturn(List.of(treino("i1")));
            IcuActivityDto dto = activity();
            when(intervalsIcuClient.buscarAtividade(anyString(), anyString(), anyBoolean())).thenReturn(dto);
            when(intervalsIcuActivityMapper.mapEtapas(dto)).thenReturn(List.of());

            BackfillEtapasOutputDto resultado = service.backfillEtapas(atletaId, tenantId);

            assertThat(resultado.semIntervalos()).isEqualTo(1);
            assertThat(resultado.atualizados()).isZero();
            verify(persister, never()).gravarEtapas(any(), any(), any());
        }

        @Test
        @DisplayName("falha em um treino nao aborta os demais; ele segue elegivel na proxima execucao")
        void falhaEmUmNaoAbortaOsDemais() {
            conexaoAtiva();
            when(treinoRealizadoRepository.findSemEtapasByAtletaAndFonte(tenantId, atletaId, FonteDados.INTERVALS_ICU))
                    .thenReturn(List.of(treino("i1"), treino("i2"), treino("i3")));
            IcuActivityDto dto = activity();
            when(intervalsIcuClient.buscarAtividade(anyString(), eq("i1"), anyBoolean())).thenReturn(dto);
            when(intervalsIcuClient.buscarAtividade(anyString(), eq("i2"), anyBoolean()))
                    .thenThrow(new IntervalsIcuApiException(HttpStatus.TOO_MANY_REQUESTS, "rate limit"));
            when(intervalsIcuClient.buscarAtividade(anyString(), eq("i3"), anyBoolean())).thenReturn(dto);
            when(intervalsIcuActivityMapper.mapEtapas(dto)).thenReturn(List.of(new EtapaRealizada()));

            BackfillEtapasOutputDto resultado = service.backfillEtapas(atletaId, tenantId);

            assertThat(resultado.candidatos()).isEqualTo(3);
            assertThat(resultado.atualizados()).isEqualTo(2);
            assertThat(resultado.falhas()).isEqualTo(1);
            // Nada marca o treino que falhou: ele continua sem etapas, logo continua candidato.
            verify(persister, never()).gravarEtapas(eq(treino("i2").getId()), any(), any());
        }

        @Test
        @DisplayName("idempotente: sem candidatos, e no-op")
        void semCandidatosEhNoOp() {
            conexaoAtiva();
            when(treinoRealizadoRepository.findSemEtapasByAtletaAndFonte(tenantId, atletaId, FonteDados.INTERVALS_ICU))
                    .thenReturn(List.of());

            BackfillEtapasOutputDto resultado = service.backfillEtapas(atletaId, tenantId);

            assertThat(resultado).isEqualTo(new BackfillEtapasOutputDto(0, 0, 0, 0, 0));
            verifyNoInteractions(persister);
        }
    }

    private void conexaoAtiva() {
        IntegracaoExterna conexao = new IntegracaoExterna();
        conexao.setAccessToken("api-key");
        when(intervalsIcuConnectionService.conexaoAtiva(atletaId, tenantId)).thenReturn(Optional.of(conexao));
    }

    private TreinoRealizado treino(String externalId) {
        TreinoRealizado treino = new TreinoRealizado();
        treino.setId(UUID.nameUUIDFromBytes(externalId.getBytes()));
        treino.setExternalId(externalId);
        treino.setTenantId(tenantId);
        return treino;
    }

    private IcuActivityDto activity() {
        return new IcuActivityDto("i1", "i641775", "Run", "Corrida", "2026-07-16T08:00:00", null,
                1800, 1850, 5000.0, null, null, null, null, null, null, null, null, null, null, null);
    }
}
