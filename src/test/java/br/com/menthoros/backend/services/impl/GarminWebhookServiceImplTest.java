package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.IntegracaoExterna;
import br.com.menthoros.backend.enums.FonteDados;
import br.com.menthoros.backend.repository.IntegracaoExternaRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GarminWebhookServiceImplTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private IntegracaoExternaRepository repository;
    private GarminWebhookServiceImpl service;
    private IntegracaoExterna conexao;

    @BeforeEach
    void setUp() {
        repository = mock(IntegracaoExternaRepository.class);
        service = new GarminWebhookServiceImpl(repository);
        conexao = new IntegracaoExterna();
        conexao.setAtleta(Atleta.builder().id(UUID.randomUUID()).build());
        conexao.setAtivo(true);
        conexao.setAccessToken("acc");
        conexao.setRefreshToken("ref");
        conexao.setTokenExpiraEm(Instant.now());
        conexao.setRefreshTokenExpiraEm(Instant.now());
        conexao.setScopes("WORKOUT_IMPORT,ACTIVITY_EXPORT");
        when(repository.findActiveByExternalAthleteIdAndPlataforma("u1", FonteDados.GARMIN))
                .thenReturn(Optional.of(conexao));
    }

    private JsonNode json(String s) throws Exception {
        return mapper.readTree(s);
    }

    @Test
    @DisplayName("deregistrations: desativa e zera tokens e expirações")
    void deregistration() throws Exception {
        service.processar(Map.of("deregistrations", List.of(json("{\"userId\":\"u1\"}"))));

        assertThat(conexao.isAtivo()).isFalse();
        assertThat(conexao.getAccessToken()).isNull();
        assertThat(conexao.getRefreshToken()).isNull();
        assertThat(conexao.getTokenExpiraEm()).isNull();
        assertThat(conexao.getRefreshTokenExpiraEm()).isNull();
        assertThat(conexao.getLastSyncError()).isNotBlank();
        verify(repository).save(conexao);
    }

    @Test
    @DisplayName("userPermissionsChange: regrava scopes e mantém a conexão ativa")
    void permissoes() throws Exception {
        service.processar(Map.of("userPermissionsChange",
                List.of(json("{\"userId\":\"u1\",\"permissions\":[\"ACTIVITY_EXPORT\"]}"))));

        assertThat(conexao.getScopes()).isEqualTo("ACTIVITY_EXPORT");
        assertThat(conexao.isAtivo()).isTrue();
        verify(repository).save(conexao);
    }

    @Test
    @DisplayName("userId desconhecido, ausente ou chave desconhecida: nada é gravado")
    void ignorados() throws Exception {
        service.processar(Map.of("deregistrations", List.of(json("{\"userId\":\"outro\"}"), json("{}"))));
        service.processar(Map.of("activityFiles", List.of(json("{\"userId\":\"u1\"}"))));
        service.processar(null);

        verify(repository, never()).save(any());
        assertThat(conexao.isAtivo()).isTrue();
    }
}
