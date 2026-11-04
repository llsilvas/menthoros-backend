package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.domain.workout.StructuredWorkout;
import br.com.menthoros.backend.domain.workout.WorkoutStep;
import br.com.menthoros.backend.dto.garmin.GarminSchedule;
import br.com.menthoros.backend.entity.IntegracaoExterna;
import br.com.menthoros.backend.enums.StatusSincronizacao;
import br.com.menthoros.backend.exception.GarminApiException;
import br.com.menthoros.backend.services.GarminClient;
import br.com.menthoros.backend.services.GarminOAuthService;
import br.com.menthoros.backend.services.PushResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GarminTrainingAdapterTest {

    private static final String TOKEN = "token-secreto-nao-logavel";
    private static final String TOKEN_NOVO = "token-renovado";
    private static final LocalDate DATA = LocalDate.of(2026, 11, 4);

    @Mock
    private GarminClient client;
    @Mock
    private GarminOAuthService oauth;

    private GarminTrainingAdapter adapter;
    private IntegracaoExterna conexao;

    @BeforeEach
    void setUp() {
        adapter = new GarminTrainingAdapter(client, oauth, new GarminWorkoutPayloadMapper(new ObjectMapper()));
        conexao = new IntegracaoExterna();
    }

    private static StructuredWorkout treino(LocalDate data) {
        return new StructuredWorkout("menthoros-1", "Longao", null, data, null,
                List.of(WorkoutStep.simples("Rodagem", 3600, null, null)));
    }

    private static JsonNode workoutNoGarmin(long workoutId, long ownerId) {
        return new ObjectMapper().createObjectNode()
                .put("workoutId", workoutId)
                .put("ownerId", ownerId)
                .put("workoutProvider", "Menthoros");
    }

    private static GarminApiException erro(int status) {
        return new GarminApiException(HttpStatus.valueOf(status), "Garmin retornou " + status);
    }

    @Nested
    @DisplayName("push sem id armazenado (criacao)")
    class Criacao {

        @Test
        @DisplayName("CA1: POST workout e POST schedule na data; resultado e criado com o workoutId")
        void criaWorkoutESchedule() {
            when(oauth.tokenValido(conexao)).thenReturn(TOKEN);
            when(client.criarWorkout(eq(TOKEN), any(JsonNode.class))).thenReturn(1000042L);
            when(client.criarSchedule(TOKEN, 1000042L, DATA)).thenReturn(77L);

            PushResult r = adapter.push(conexao, treino(DATA), null);

            assertThat(r.sucesso()).isTrue();
            assertThat(r.eventId()).isEqualTo(1000042L);
            assertThat(r.criadoNovo()).isTrue();
        }

        @Test
        @DisplayName("schedule falha depois do workout criado: apaga o workout para nao deixar duplicata no retry")
        void limpaWorkoutQuandoScheduleFalha() {
            when(oauth.tokenValido(conexao)).thenReturn(TOKEN);
            when(client.criarWorkout(eq(TOKEN), any(JsonNode.class))).thenReturn(1000042L);
            when(client.criarSchedule(TOKEN, 1000042L, DATA)).thenThrow(erro(429));

            PushResult r = adapter.push(conexao, treino(DATA), null);

            assertThat(r.sucesso()).isFalse();
            assertThat(r.statusErro()).isEqualTo(StatusSincronizacao.ERRO_LIMITE_RATE);
            verify(client).deletarWorkout(TOKEN, 1000042L);
        }

        @Test
        @DisplayName("falha na limpeza nao esconde o erro original")
        void falhaNaLimpezaMantemErroOriginal() {
            when(oauth.tokenValido(conexao)).thenReturn(TOKEN);
            when(client.criarWorkout(eq(TOKEN), any(JsonNode.class))).thenReturn(1000042L);
            when(client.criarSchedule(TOKEN, 1000042L, DATA)).thenThrow(erro(429));
            org.mockito.Mockito.doThrow(erro(500)).when(client).deletarWorkout(TOKEN, 1000042L);

            PushResult r = adapter.push(conexao, treino(DATA), null);

            assertThat(r.statusErro()).isEqualTo(StatusSincronizacao.ERRO_LIMITE_RATE);
        }

        @Test
        @DisplayName("mais de 100 steps: ERRO_VALIDACAO sem nenhuma chamada ao Garmin")
        void treinoGrandeDemais() {
            List<WorkoutStep> muitos = new ArrayList<>();
            for (int i = 0; i < 101; i++) {
                muitos.add(WorkoutStep.simples("s", 60, null, null));
            }
            StructuredWorkout grande = new StructuredWorkout("menthoros-1", "Grande", null, DATA, null, muitos);

            PushResult r = adapter.push(conexao, grande, null);

            assertThat(r.statusErro()).isEqualTo(StatusSincronizacao.ERRO_VALIDACAO);
            verifyNoInteractions(client);
        }
    }

    @Nested
    @DisplayName("push com id armazenado (atualizacao)")
    class Atualizacao {

        @Test
        @DisplayName("CA3: PUT workout; schedule do dia ja aponta para o workout, entao nao mexe nele")
        void atualizaSoOWorkout() {
            when(oauth.tokenValido(conexao)).thenReturn(TOKEN);
            when(client.buscarWorkout(TOKEN, 1000042L)).thenReturn(workoutNoGarmin(1000042L, 12345L));
            when(client.listarSchedules(TOKEN, DATA, DATA))
                    .thenReturn(List.of(new GarminSchedule(77L, 1000042L, DATA)));

            PushResult r = adapter.push(conexao, treino(DATA), 1000042L);

            assertThat(r.sucesso()).isTrue();
            assertThat(r.eventId()).isEqualTo(1000042L);
            assertThat(r.criadoNovo()).isFalse();
            verify(client).atualizarWorkout(eq(TOKEN), eq(1000042L), any(JsonNode.class));
            verify(client, never()).criarWorkout(any(), any());
            verify(client, never()).criarSchedule(any(), anyLong(), any());
            verify(client, never()).atualizarSchedule(any(), anyLong(), anyLong(), any());
        }

        @Test
        @DisplayName("CA3: data mudou; acha o schedule na janela e o move, sem segundo workout")
        void movePeloSchedule() {
            LocalDate antiga = DATA.minusDays(2);
            when(oauth.tokenValido(conexao)).thenReturn(TOKEN);
            when(client.buscarWorkout(TOKEN, 1000042L)).thenReturn(workoutNoGarmin(1000042L, 12345L));
            when(client.listarSchedules(TOKEN, DATA, DATA)).thenReturn(List.of());
            when(client.listarSchedules(TOKEN, DATA.minusDays(14), DATA.plusDays(14)))
                    .thenReturn(List.of(new GarminSchedule(77L, 1000042L, antiga),
                            new GarminSchedule(78L, 999L, antiga)));

            PushResult r = adapter.push(conexao, treino(DATA), 1000042L);

            assertThat(r.sucesso()).isTrue();
            verify(client).atualizarSchedule(TOKEN, 77L, 1000042L, DATA);
            verify(client, never()).criarWorkout(any(), any());
        }

        @Test
        @DisplayName("schedule sumiu do Garmin: recria o schedule, mantem o workout")
        void recriaScheduleAusente() {
            when(oauth.tokenValido(conexao)).thenReturn(TOKEN);
            when(client.buscarWorkout(TOKEN, 1000042L)).thenReturn(workoutNoGarmin(1000042L, 12345L));
            when(client.listarSchedules(any(), any(), any())).thenReturn(List.of());
            when(client.criarSchedule(TOKEN, 1000042L, DATA)).thenReturn(80L);

            PushResult r = adapter.push(conexao, treino(DATA), 1000042L);

            assertThat(r.sucesso()).isTrue();
            assertThat(r.eventId()).isEqualTo(1000042L);
            verify(client).criarSchedule(TOKEN, 1000042L, DATA);
        }

        @Test
        @DisplayName("workout apagado pelo atleta (PUT 404): cria um novo e devolve o id novo")
        void recriaQuandoWorkoutSumiu() {
            when(oauth.tokenValido(conexao)).thenReturn(TOKEN);
            when(client.buscarWorkout(TOKEN, 1000042L)).thenReturn(workoutNoGarmin(1000042L, 12345L));
            org.mockito.Mockito.doThrow(erro(404)).when(client)
                    .atualizarWorkout(eq(TOKEN), eq(1000042L), any(JsonNode.class));
            when(client.criarWorkout(eq(TOKEN), any(JsonNode.class))).thenReturn(2000001L);
            when(client.criarSchedule(TOKEN, 2000001L, DATA)).thenReturn(81L);

            PushResult r = adapter.push(conexao, treino(DATA), 1000042L);

            assertThat(r.sucesso()).isTrue();
            assertThat(r.eventId()).isEqualTo(2000001L);
            assertThat(r.criadoNovo()).isTrue();
        }

        @Test
        @DisplayName("CA1: PUT leva ownerId e workoutId do workout lido no Garmin (Training API §3.1)")
        void putLevaOwnerIdEWorkoutId() {
            when(oauth.tokenValido(conexao)).thenReturn(TOKEN);
            when(client.buscarWorkout(TOKEN, 1000042L)).thenReturn(workoutNoGarmin(1000042L, 12345L));
            when(client.listarSchedules(TOKEN, DATA, DATA))
                    .thenReturn(List.of(new GarminSchedule(77L, 1000042L, DATA)));
            ArgumentCaptor<JsonNode> corpo = ArgumentCaptor.forClass(JsonNode.class);

            adapter.push(conexao, treino(DATA), 1000042L);

            verify(client).atualizarWorkout(eq(TOKEN), eq(1000042L), corpo.capture());
            assertThat(corpo.getValue().path("ownerId").asLong()).isEqualTo(12345L);
            assertThat(corpo.getValue().path("workoutId").asLong()).isEqualTo(1000042L);
            assertThat(corpo.getValue().path("workoutName").asText()).isEqualTo("Longao");
        }

        @Test
        @DisplayName("CA2: GET 404 (workout apagado pelo atleta): cria de novo sem tentar o PUT")
        void get404RecriaSemPut() {
            when(oauth.tokenValido(conexao)).thenReturn(TOKEN);
            when(client.buscarWorkout(TOKEN, 1000042L)).thenThrow(erro(404));
            when(client.criarWorkout(eq(TOKEN), any(JsonNode.class))).thenReturn(2000001L);
            when(client.criarSchedule(TOKEN, 2000001L, DATA)).thenReturn(81L);

            PushResult r = adapter.push(conexao, treino(DATA), 1000042L);

            assertThat(r.sucesso()).isTrue();
            assertThat(r.eventId()).isEqualTo(2000001L);
            assertThat(r.criadoNovo()).isTrue();
            verify(client, never()).atualizarWorkout(any(), anyLong(), any());
        }

        @ParameterizedTest(name = "GET {0} -> {1}, sem PUT")
        @CsvSource({
                "403, ERRO_AUTENTICACAO",
                "412, ERRO_AUTENTICACAO",
                "429, ERRO_LIMITE_RATE",
                "500, ERRO_TEMPORARIO"
        })
        @DisplayName("CA3: erro no GET segue o mapeamento do push e nao faz PUT")
        void erroNoGetNaoFazPut(int http, StatusSincronizacao esperado) {
            when(oauth.tokenValido(conexao)).thenReturn(TOKEN);
            when(client.buscarWorkout(TOKEN, 1000042L)).thenThrow(erro(http));

            PushResult r = adapter.push(conexao, treino(DATA), 1000042L);

            assertThat(r.statusErro()).isEqualTo(esperado);
            verify(client, never()).atualizarWorkout(any(), anyLong(), any());
            verify(client, never()).criarWorkout(any(), any());
        }

        @Test
        @DisplayName("GET sem ownerId utilizável (ausente, objeto ou corpo vazio): não faz o PUT condenado, erro temporário")
        void getSemOwnerIdNaoFazPut() {
            JsonNode ownerObjeto = new ObjectMapper().createObjectNode().put("workoutId", 1000042L)
                    .set("ownerId", new ObjectMapper().createObjectNode());
            for (JsonNode resposta : java.util.Arrays.asList(
                    new ObjectMapper().createObjectNode().put("workoutId", 1000042L), ownerObjeto, null)) {
                org.mockito.Mockito.reset(client, oauth);
                when(oauth.tokenValido(conexao)).thenReturn(TOKEN);
                when(client.buscarWorkout(TOKEN, 1000042L)).thenReturn(resposta);

                PushResult r = adapter.push(conexao, treino(DATA), 1000042L);

                assertThat(r.statusErro()).isEqualTo(StatusSincronizacao.ERRO_TEMPORARIO);
                verify(client, never()).atualizarWorkout(any(), anyLong(), any());
                verify(client, never()).criarWorkout(any(), any());
            }
        }

        @Test
        @DisplayName("CA3: GET 401 renova o token e repete; 401 de novo vira ERRO_AUTENTICACAO sem PUT")
        void get401RenovaERepete() {
            when(oauth.tokenValido(conexao)).thenReturn(TOKEN);
            when(oauth.renovar(conexao)).thenReturn(TOKEN_NOVO);
            when(client.buscarWorkout(TOKEN, 1000042L)).thenThrow(erro(401));
            when(client.buscarWorkout(TOKEN_NOVO, 1000042L)).thenThrow(erro(401));

            PushResult r = adapter.push(conexao, treino(DATA), 1000042L);

            assertThat(r.statusErro()).isEqualTo(StatusSincronizacao.ERRO_AUTENTICACAO);
            verify(client).buscarWorkout(TOKEN_NOVO, 1000042L);
            verify(client, never()).atualizarWorkout(any(), anyLong(), any());
        }
    }

    @Nested
    @DisplayName("mapeamento de erro (D6)")
    class Erros {

        @ParameterizedTest(name = "HTTP {0} -> {1}")
        @CsvSource({
                "412, ERRO_AUTENTICACAO",
                "403, ERRO_AUTENTICACAO",
                "400, ERRO_VALIDACAO",
                "429, ERRO_LIMITE_RATE",
                "500, ERRO_TEMPORARIO",
                "503, ERRO_TEMPORARIO"
        })
        void mapeiaStatus(int http, StatusSincronizacao esperado) {
            when(oauth.tokenValido(conexao)).thenReturn(TOKEN);
            when(client.criarWorkout(eq(TOKEN), any(JsonNode.class))).thenThrow(erro(http));

            PushResult r = adapter.push(conexao, treino(DATA), null);

            assertThat(r.sucesso()).isFalse();
            assertThat(r.statusErro()).isEqualTo(esperado);
            assertThat(r.mensagem()).doesNotContain(TOKEN);
        }

        @Test
        @DisplayName("CA6: 412 pede a autorizacao no Garmin Connect e nao tenta renovar token")
        void erro412() {
            when(oauth.tokenValido(conexao)).thenReturn(TOKEN);
            when(client.criarWorkout(eq(TOKEN), any(JsonNode.class))).thenThrow(erro(412));

            PushResult r = adapter.push(conexao, treino(DATA), null);

            assertThat(r.mensagem()).contains("Autorize o envio de treinos no Garmin Connect");
            verify(oauth, never()).renovar(any());
        }

        @Test
        @DisplayName("falha de transporte (sem status) e erro temporario")
        void falhaDeTransporte() {
            when(oauth.tokenValido(conexao)).thenReturn(TOKEN);
            when(client.criarWorkout(eq(TOKEN), any(JsonNode.class)))
                    .thenThrow(new GarminApiException("Falha de transporte ao criar workout", new RuntimeException()));

            PushResult r = adapter.push(conexao, treino(DATA), null);

            assertThat(r.statusErro()).isEqualTo(StatusSincronizacao.ERRO_TEMPORARIO);
        }

        @Test
        @DisplayName("CA8: 401 renova o token e repete uma vez")
        void erro401RenovaERepete() {
            when(oauth.tokenValido(conexao)).thenReturn(TOKEN);
            when(client.criarWorkout(eq(TOKEN), any(JsonNode.class))).thenThrow(erro(401));
            when(oauth.renovar(conexao)).thenReturn(TOKEN_NOVO);
            when(client.criarWorkout(eq(TOKEN_NOVO), any(JsonNode.class))).thenReturn(1000042L);
            when(client.criarSchedule(TOKEN_NOVO, 1000042L, DATA)).thenReturn(77L);

            PushResult r = adapter.push(conexao, treino(DATA), null);

            assertThat(r.sucesso()).isTrue();
            assertThat(r.eventId()).isEqualTo(1000042L);
        }

        @Test
        @DisplayName("CA8: 401 de novo depois da renovacao vira ERRO_AUTENTICACAO")
        void erro401Persistente() {
            when(oauth.tokenValido(conexao)).thenReturn(TOKEN);
            when(client.criarWorkout(any(), any(JsonNode.class))).thenThrow(erro(401));
            when(oauth.renovar(conexao)).thenReturn(TOKEN_NOVO);

            PushResult r = adapter.push(conexao, treino(DATA), null);

            assertThat(r.statusErro()).isEqualTo(StatusSincronizacao.ERRO_AUTENTICACAO);
            verify(oauth).renovar(conexao);
        }

        @Test
        @DisplayName("refresh recusado (conexao inativa): ERRO_AUTENTICACAO pedindo para reautorizar")
        void refreshRecusado() {
            when(oauth.tokenValido(conexao)).thenThrow(new IllegalStateException("refresh recusado"));

            PushResult r = adapter.push(conexao, treino(DATA), null);

            assertThat(r.statusErro()).isEqualTo(StatusSincronizacao.ERRO_AUTENTICACAO);
            assertThat(r.mensagem()).contains("Garmin");
            verifyNoInteractions(client);
        }
    }

    @Nested
    @DisplayName("removerOrfaos")
    class Orfaos {

        private final LocalDate fim = DATA.plusDays(6);

        private JsonNode workoutDe(String provedor) {
            return new ObjectMapper().createObjectNode().put("workoutProvider", provedor);
        }

        @Test
        @DisplayName("CA4: schedule fora do conjunto, de workout do Menthoros: apaga schedule e workout")
        void apagaOrfaoDoMenthoros() {
            when(oauth.tokenValido(conexao)).thenReturn(TOKEN);
            when(client.listarSchedules(TOKEN, DATA, fim)).thenReturn(List.of(
                    new GarminSchedule(5L, 1000042L, DATA), new GarminSchedule(6L, 1000043L, DATA)));
            when(client.buscarWorkout(TOKEN, 1000043L)).thenReturn(workoutDe("Menthoros"));

            adapter.removerOrfaos(conexao, DATA, fim, Set.of(), Set.of("1000042"));

            verify(client).deletarSchedule(TOKEN, 6L);
            verify(client).deletarWorkout(TOKEN, 1000043L);
            verify(client, never()).deletarSchedule(TOKEN, 5L);
            verify(client, never()).buscarWorkout(TOKEN, 1000042L);
        }

        @Test
        @DisplayName("workout criado pelo atleta (outro provedor) nunca e apagado")
        void naoTocaWorkoutDoAtleta() {
            when(oauth.tokenValido(conexao)).thenReturn(TOKEN);
            when(client.listarSchedules(TOKEN, DATA, fim)).thenReturn(List.of(new GarminSchedule(6L, 1000043L, DATA)));
            when(client.buscarWorkout(TOKEN, 1000043L)).thenReturn(workoutDe("Garmin"));

            adapter.removerOrfaos(conexao, DATA, fim, Set.of(), Set.of());

            verify(client, never()).deletarSchedule(any(), anyLong());
            verify(client, never()).deletarWorkout(any(), anyLong());
        }

        @Test
        @DisplayName("404 no delete do schedule e tolerado e o workout ainda e apagado")
        void toleraNaoEncontrado() {
            when(oauth.tokenValido(conexao)).thenReturn(TOKEN);
            when(client.listarSchedules(TOKEN, DATA, fim)).thenReturn(List.of(new GarminSchedule(6L, 1000043L, DATA)));
            when(client.buscarWorkout(TOKEN, 1000043L)).thenReturn(workoutDe("Menthoros"));
            doThrow(erro(404)).when(client).deletarSchedule(TOKEN, 6L);

            adapter.removerOrfaos(conexao, DATA, fim, Set.of(), Set.of());

            verify(client).deletarWorkout(TOKEN, 1000043L);
        }

        @Test
        @DisplayName("falha num orfao nao aborta os demais")
        void falhaIsoladaPorOrfao() {
            when(oauth.tokenValido(conexao)).thenReturn(TOKEN);
            when(client.listarSchedules(TOKEN, DATA, fim)).thenReturn(List.of(
                    new GarminSchedule(6L, 1000043L, DATA), new GarminSchedule(7L, 1000044L, DATA)));
            when(client.buscarWorkout(TOKEN, 1000043L)).thenThrow(erro(500));
            when(client.buscarWorkout(TOKEN, 1000044L)).thenReturn(workoutDe("Menthoros"));

            adapter.removerOrfaos(conexao, DATA, fim, Set.of(), Set.of());

            verify(client).deletarWorkout(TOKEN, 1000044L);
        }

        @Test
        @DisplayName("falha ao listar nao propaga: reconciliacao e best-effort")
        void naoPropagaFalhaDeListagem() {
            when(oauth.tokenValido(conexao)).thenReturn(TOKEN);
            when(client.listarSchedules(TOKEN, DATA, fim)).thenThrow(erro(503));

            adapter.removerOrfaos(conexao, DATA, fim, Set.of(), Set.of());

            verify(client, never()).deletarWorkout(any(), anyLong());
        }
    }

    @Test
    @DisplayName("tocarEvento nao faz nada: nao ha uploader intermediario")
    void tocarEventoENoOp() {
        adapter.tocarEvento(conexao, 1000042L, "menthoros-1");

        verifyNoInteractions(client, oauth);
    }
}
