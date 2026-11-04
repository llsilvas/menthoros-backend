package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.IntegracaoExterna;
import br.com.menthoros.backend.services.IntervalsIcuClient;
import br.com.menthoros.backend.services.IntervalsIcuConnectionService;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("IntervalsIcuFcSyncer")
class IntervalsIcuFcSyncerTest {

    private final IntervalsIcuClient client = mock(IntervalsIcuClient.class);
    private final IntervalsIcuConnectionService connection = mock(IntervalsIcuConnectionService.class);
    private final IntervalsIcuFcSyncer syncer = new IntervalsIcuFcSyncer(client, connection);

    private final UUID tenantId = UUID.randomUUID();
    private final UUID atletaId = UUID.randomUUID();

    private Atleta atleta(Integer limiar, Integer maxima) {
        return Atleta.builder().id(atletaId).fcLimiar(limiar).fcMaxima(maxima).build();
    }

    private IntegracaoExterna conexao(String token) {
        IntegracaoExterna c = new IntegracaoExterna();
        c.setAccessToken(token);
        c.setExternalAthleteId("i123");
        return c;
    }

    @Nested
    @DisplayName("sincronizarSeConectado")
    class SincronizarSeConectado {

        @Test
        @DisplayName("atleta conectado: manda lthr e max_hr")
        void conectado() {
            when(connection.conexaoAtiva(atletaId, tenantId)).thenReturn(Optional.of(conexao("tok")));

            syncer.sincronizarSeConectado(atleta(168, 186), tenantId);

            ArgumentCaptor<JsonNode> payload = ArgumentCaptor.forClass(JsonNode.class);
            verify(client).atualizarSportSettings(eq("tok"), eq("i123"), eq("Run"), payload.capture());
            assertThat(payload.getValue().get("lthr").asInt()).isEqualTo(168);
            assertThat(payload.getValue().get("max_hr").asInt()).isEqualTo(186);
        }

        @Test
        @DisplayName("sem conexão ativa: não chama o intervals.icu")
        void semConexao() {
            when(connection.conexaoAtiva(atletaId, tenantId)).thenReturn(Optional.empty());

            syncer.sincronizarSeConectado(atleta(168, 186), tenantId);

            verify(client, never()).atualizarSportSettings(any(), any(), any(), any());
        }

        @Test
        @DisplayName("conexão sem token: não chama o intervals.icu")
        void semToken() {
            when(connection.conexaoAtiva(atletaId, tenantId)).thenReturn(Optional.of(conexao(" ")));

            syncer.sincronizarSeConectado(atleta(168, 186), tenantId);

            verify(client, never()).atualizarSportSettings(any(), any(), any(), any());
        }

        @Test
        @DisplayName("falha do intervals.icu não propaga (CA15)")
        void falhaNaoPropaga() {
            when(connection.conexaoAtiva(atletaId, tenantId)).thenReturn(Optional.of(conexao("tok")));
            doThrow(new IllegalStateException("503")).when(client)
                    .atualizarSportSettings(any(), any(), any(), any());

            assertThatCode(() -> syncer.sincronizarSeConectado(atleta(168, 186), tenantId))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("falha ao ler a conexão não propaga")
        void falhaNaLeituraNaoPropaga() {
            when(connection.conexaoAtiva(atletaId, tenantId)).thenThrow(new IllegalStateException("db"));

            assertThatCode(() -> syncer.sincronizarSeConectado(atleta(168, 186), tenantId))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("sincronizar")
    class Sincronizar {

        @Test
        @DisplayName("só limiar: manda só lthr")
        void soLimiar() {
            syncer.sincronizar(atleta(168, null), "tok", "i123");

            ArgumentCaptor<JsonNode> payload = ArgumentCaptor.forClass(JsonNode.class);
            verify(client).atualizarSportSettings(eq("tok"), eq("i123"), eq("Run"), payload.capture());
            assertThat(payload.getValue().has("lthr")).isTrue();
            assertThat(payload.getValue().has("max_hr")).isFalse();
        }

        @Test
        @DisplayName("sem limiar nem máxima: não manda nada")
        void semNada() {
            syncer.sincronizar(atleta(null, null), "tok", "i123");

            verify(client, never()).atualizarSportSettings(any(), any(), any(), any());
        }
    }
}
