package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.dto.intervalsicu.IcuAthleteDto;
import br.com.menthoros.backend.dto.output.IntervalsIcuConnectionStatusDto;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.IntegracaoExterna;
import br.com.menthoros.backend.enums.FonteDados;
import br.com.menthoros.backend.exception.DomainRuleViolationException;
import br.com.menthoros.backend.multitenancy.TenantContext;
import br.com.menthoros.backend.repository.AtletaRepository;
import br.com.menthoros.backend.repository.IntegracaoExternaRepository;
import br.com.menthoros.backend.services.IntervalsIcuClient;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IntervalsIcuConnectionServiceImplTest {

    @Mock private IntervalsIcuClient intervalsIcuClient;
    @Mock private IntegracaoExternaRepository integracaoRepository;
    @Mock private AtletaRepository atletaRepository;
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

    @Nested
    @DisplayName("conectar")
    class Conectar {

        @Test
        @DisplayName("key válida persiste integração com externalAthleteId e não expõe a key no DTO")
        void keyValidaPersiste() {
            when(intervalsIcuClient.validarApiKey("key-ok"))
                    .thenReturn(Optional.of(new IcuAthleteDto("i641775", "Leandro")));
            when(atletaRepository.findByIdAndTenantId(atletaId, tenantId))
                    .thenReturn(Optional.of(Atleta.builder().id(atletaId).build()));
            when(integracaoRepository.findByAtletaIdAndPlataformaAndTenantId(atletaId, FonteDados.INTERVALS_ICU, tenantId))
                    .thenReturn(Optional.empty());
            when(integracaoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            IntervalsIcuConnectionStatusDto dto = service.conectar(atletaId, "key-ok");

            ArgumentCaptor<IntegracaoExterna> captor = ArgumentCaptor.forClass(IntegracaoExterna.class);
            verify(integracaoRepository).save(captor.capture());
            IntegracaoExterna salva = captor.getValue();
            assertThat(salva.getPlataforma()).isEqualTo(FonteDados.INTERVALS_ICU);
            assertThat(salva.getAccessToken()).isEqualTo("key-ok");
            assertThat(salva.getExternalAthleteId()).isEqualTo("i641775");
            assertThat(salva.isAtivo()).isTrue();
            assertThat(dto.conectado()).isTrue();
            assertThat(dto.toString()).doesNotContain("key-ok");
        }

        @Test
        @DisplayName("key inválida lança 422 e NADA é persistido")
        void keyInvalidaNaoPersiste() {
            when(intervalsIcuClient.validarApiKey("key-ruim")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.conectar(atletaId, "key-ruim"))
                    .isInstanceOf(DomainRuleViolationException.class)
                    .hasMessageContaining("API key");

            verify(integracaoRepository, never()).save(any());
        }

        @Test
        @DisplayName("reconectar reusa registro existente da unique (atleta, plataforma)")
        void reconectarReusaRegistroExistente() {
            IntegracaoExterna existente = new IntegracaoExterna();
            existente.setAtivo(false);
            existente.setAccessToken("key-antiga");
            existente.setExternalAthleteId("i000000");
            existente.setLastSyncError("erro anterior");

            when(intervalsIcuClient.validarApiKey("key-nova"))
                    .thenReturn(Optional.of(new IcuAthleteDto("i641775", "Leandro")));
            when(atletaRepository.findByIdAndTenantId(atletaId, tenantId))
                    .thenReturn(Optional.of(Atleta.builder().id(atletaId).build()));
            when(integracaoRepository.findByAtletaIdAndPlataformaAndTenantId(atletaId, FonteDados.INTERVALS_ICU, tenantId))
                    .thenReturn(Optional.of(existente));
            when(integracaoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.conectar(atletaId, "key-nova");

            assertThat(existente.getAccessToken()).isEqualTo("key-nova");
            assertThat(existente.getExternalAthleteId()).isEqualTo("i641775");
            assertThat(existente.isAtivo()).isTrue();
            assertThat(existente.getLastSyncError()).isNull();
            verify(integracaoRepository).save(existente);
        }

        @Test
        @DisplayName("atleta não encontrado no tenant lança exceção e nada é persistido")
        void atletaNaoEncontradoNaoPersiste() {
            when(intervalsIcuClient.validarApiKey("key-ok"))
                    .thenReturn(Optional.of(new IcuAthleteDto("i641775", "Leandro")));
            when(atletaRepository.findByIdAndTenantId(atletaId, tenantId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.conectar(atletaId, "key-ok"))
                    .isInstanceOf(RuntimeException.class);

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

        @Test
        @DisplayName("soft-disconnect zera token e desativa (padrão Strava)")
        void softDisconnect() {
            IntegracaoExterna integracao = new IntegracaoExterna();
            integracao.setAccessToken("key");
            integracao.setAtivo(true);
            when(integracaoRepository.findByAtletaIdAndPlataformaAndTenantId(atletaId, FonteDados.INTERVALS_ICU, tenantId))
                    .thenReturn(Optional.of(integracao));

            service.desconectar(atletaId);

            assertThat(integracao.isAtivo()).isFalse();
            assertThat(integracao.getAccessToken()).isNull();
            assertThat(integracao.getRefreshToken()).isNull();
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
