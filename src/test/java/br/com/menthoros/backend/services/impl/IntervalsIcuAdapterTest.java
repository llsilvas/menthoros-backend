package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.domain.workout.HrTarget;
import br.com.menthoros.backend.domain.workout.IntensityTarget;
import br.com.menthoros.backend.domain.workout.PaceTarget;
import br.com.menthoros.backend.domain.workout.StructuredWorkout;
import br.com.menthoros.backend.domain.workout.WorkoutStep;
import br.com.menthoros.backend.dto.intervalsicu.IcuEventDto;
import br.com.menthoros.backend.entity.IntegracaoExterna;
import br.com.menthoros.backend.enums.StatusSincronizacao;
import br.com.menthoros.backend.exception.IntervalsIcuApiException;
import br.com.menthoros.backend.services.IntervalsIcuClient;
import br.com.menthoros.backend.services.PushResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IntervalsIcuAdapterTest {

    private static final String API_KEY = "chave-secreta-123";
    private static final String ATHLETE_ID = "i12345";
    private static final LocalDate DATA = LocalDate.of(2026, 7, 15);

    @Mock
    private IntervalsIcuClient client;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private IntervalsIcuAdapter adapter;
    private IntegracaoExterna conexao;

    @BeforeEach
    void setUp() {
        adapter = new IntervalsIcuAdapter(client, objectMapper);
        conexao = new IntegracaoExterna();
        conexao.setAccessToken(API_KEY);
        conexao.setExternalAthleteId(ATHLETE_ID);
    }

    @Nested
    @DisplayName("push")
    class Push {

        @Test
        @DisplayName("monta workout_doc correto: sem description no evento, steps com pace/hr/bloco")
        void montaPayloadCorreto() {
            List<WorkoutStep> steps = List.of(
                    WorkoutStep.simples("Aquecimento", 600, null, new HrTarget(140, 150)),
                    WorkoutStep.simples(null, null, 1000, new PaceTarget(240, 260)),
                    WorkoutStep.simples(null, 300, null, new PaceTarget(270, 270)),
                    WorkoutStep.simples(null, 600, null, IntensityTarget.SEM_OBJETIVO),
                    WorkoutStep.bloco("Tiros", 4, List.of(
                            WorkoutStep.simples("Tiro", 180, null, new PaceTarget(230, 230))))
            );
            StructuredWorkout workout = new StructuredWorkout(
                    "menthoros-1", "CONTINUO 15/07", null, DATA, "Treino continuo de base", steps);

            when(client.buscarLthrCorrida(API_KEY, ATHLETE_ID)).thenReturn(Optional.of(LTHR_PROVEDOR));
            when(client.listarEventos(API_KEY, ATHLETE_ID, DATA, DATA)).thenReturn(List.of());
            when(client.criarEvento(eq(API_KEY), eq(ATHLETE_ID), any()))
                    .thenReturn(new IcuEventDto(999L, "menthoros-1", "CONTINUO 15/07", "2026-07-15T00:00:00"));

            PushResult resultado = adapter.push(conexao, workout, null);

            assertThat(resultado.sucesso()).isTrue();
            assertThat(resultado.eventId()).isEqualTo(999L);

            ArgumentCaptor<JsonNode> captor = ArgumentCaptor.forClass(JsonNode.class);
            verify(client).criarEvento(eq(API_KEY), eq(ATHLETE_ID), captor.capture());
            JsonNode payload = captor.getValue();

            assertThat(payload.get("category").asText()).isEqualTo("WORKOUT");
            assertThat(payload.get("type").asText()).isEqualTo("Run");
            assertThat(payload.get("start_date_local").asText()).isEqualTo("2026-07-15T00:00:00");
            assertThat(payload.get("external_id").asText()).isEqualTo("menthoros-1");
            assertThat(payload.get("name").asText()).isEqualTo("CONTINUO 15/07");
            assertThat(payload.has("description")).isFalse();

            JsonNode doc = payload.get("workout_doc");
            assertThat(doc.get("description").asText()).isEqualTo("Treino continuo de base");

            JsonNode nodeSteps = doc.get("steps");
            assertThat(nodeSteps).hasSize(5);

            // FC vai como %lthr do provedor: o que importa é o bpm em que ELE resolve (trunca).
            JsonNode hrStep = nodeSteps.get(0);
            assertThat(hrStep.get("text").asText()).isEqualTo("Aquecimento");
            assertThat(hrStep.get("duration").asInt()).isEqualTo(600);
            assertThat(hrStep.get("hr").get("units").asText()).isEqualTo("%lthr");
            assertThat(hrStep.get("hr").has("start")).isTrue();
            assertThat(hrStep.get("hr").has("end")).isTrue();
            assertThat(resolvidoPeloProvedor(hrStep.get("hr").get("start").asDouble(), LTHR_PROVEDOR)).isEqualTo(140);
            assertThat(resolvidoPeloProvedor(hrStep.get("hr").get("end").asDouble(), LTHR_PROVEDOR)).isEqualTo(150);

            JsonNode paceRange = nodeSteps.get(1);
            assertThat(paceRange.get("distance").asInt()).isEqualTo(1000);
            assertThat(paceRange.get("pace").get("units").asText()).isEqualTo("secs/km");
            assertThat(paceRange.get("pace").get("start").asInt()).isEqualTo(240);
            assertThat(paceRange.get("pace").get("end").asInt()).isEqualTo(260);

            JsonNode paceValor = nodeSteps.get(2);
            assertThat(paceValor.get("pace").has("value")).isTrue();
            assertThat(paceValor.get("pace").get("value").asInt()).isEqualTo(270);
            assertThat(paceValor.get("pace").has("start")).isFalse();

            // "Sem objetivo" é escolha de primeira classe: o step vai sem meta nenhuma.
            JsonNode semObjetivo = nodeSteps.get(3);
            assertThat(semObjetivo.has("hr")).isFalse();
            assertThat(semObjetivo.has("pace")).isFalse();

            JsonNode bloco = nodeSteps.get(4);
            assertThat(bloco.get("reps").asInt()).isEqualTo(4);
            assertThat(bloco.get("text").asText()).isEqualTo("Tiros");
            assertThat(bloco.get("steps")).hasSize(1);
            JsonNode sub = bloco.get("steps").get(0);
            assertThat(sub.get("text").asText()).isEqualTo("Tiro");
            assertThat(sub.get("duration").asInt()).isEqualTo(180);
        }

        @Test
        @DisplayName("sem LTHR no perfil, usa o workout_doc.lthr de um evento resolvido perto da data")
        void lthrViaReadBackResolvido() {
            StructuredWorkout workout = workoutComFc("menthoros-lthr-rb", 107, 121);
            when(client.buscarLthrCorrida(API_KEY, ATHLETE_ID)).thenReturn(Optional.empty());
            when(client.buscarLthrResolvido(API_KEY, ATHLETE_ID, DATA.minusDays(14), DATA.plusDays(14)))
                    .thenReturn(Optional.of(168));
            when(client.atualizarEvento(eq(API_KEY), eq(ATHLETE_ID), eq(5L), any()))
                    .thenReturn(new IcuEventDto(5L, "menthoros-lthr-rb", "X", "2026-07-15T00:00:00"));

            PushResult resultado = adapter.push(conexao, workout, 5L);

            assertThat(resultado.sucesso()).isTrue();
            ArgumentCaptor<JsonNode> captor = ArgumentCaptor.forClass(JsonNode.class);
            verify(client).atualizarEvento(eq(API_KEY), eq(ATHLETE_ID), eq(5L), captor.capture());
            JsonNode hr = captor.getValue().get("workout_doc").get("steps").get(0).get("hr");
            assertThat(hr.get("units").asText()).isEqualTo("%lthr");
            // O caso real de 2026-10-01: 63,69% de 168 truncava em 106; o ponto médio resolve em 107.
            assertThat(resolvidoPeloProvedor(hr.get("start").asDouble(), 168)).isEqualTo(107);
            assertThat(resolvidoPeloProvedor(hr.get("end").asDouble(), 168)).isEqualTo(121);
        }

        @Test
        @DisplayName("sem LTHR em lugar nenhum, falha com ERRO_VALIDACAO e não cria nem atualiza o evento")
        void semLthrFalhaSemTocarNoCanal() {
            StructuredWorkout workout = workoutComFc("menthoros-sem-lthr", 140, 150);
            when(client.buscarLthrCorrida(API_KEY, ATHLETE_ID)).thenReturn(Optional.empty());
            when(client.buscarLthrResolvido(eq(API_KEY), eq(ATHLETE_ID), any(), any())).thenReturn(Optional.empty());

            PushResult resultado = adapter.push(conexao, workout, 5L);

            assertThat(resultado.sucesso()).isFalse();
            assertThat(resultado.statusErro()).isEqualTo(StatusSincronizacao.ERRO_VALIDACAO);
            assertThat(resultado.mensagem()).contains("LTHR");
            verify(client, never()).criarEvento(any(), any(), any());
            verify(client, never()).atualizarEvento(any(), any(), anyLong(), any());
        }

        @Test
        @DisplayName("treino sem alvo de FC não consulta LTHR nenhum")
        void semFcNaoConsultaLthr() {
            StructuredWorkout workout = workoutSimples("menthoros-sem-fc");
            when(client.atualizarEvento(eq(API_KEY), eq(ATHLETE_ID), eq(5L), any()))
                    .thenReturn(new IcuEventDto(5L, "menthoros-sem-fc", "X", "2026-07-15T00:00:00"));

            PushResult resultado = adapter.push(conexao, workout, 5L);

            assertThat(resultado.sucesso()).isTrue();
            verify(client, never()).buscarLthrCorrida(any(), any());
            verify(client, never()).buscarLthrResolvido(any(), any(), any(), any());
        }

        @Test
        @DisplayName("FC dentro de bloco de repetição também exige LTHR e sai como %lthr")
        void fcDentroDeBlocoUsaLthr() {
            StructuredWorkout workout = new StructuredWorkout("menthoros-bloco", "FARTLEK", null, DATA, "d",
                    List.of(WorkoutStep.bloco(null, 5, List.of(
                            WorkoutStep.simples("Tiro", null, 300, new HrTarget(136, 146)),
                            WorkoutStep.simples("Rec", null, 200, new HrTarget(121, 126))))));
            when(client.buscarLthrCorrida(API_KEY, ATHLETE_ID)).thenReturn(Optional.of(168));
            when(client.atualizarEvento(eq(API_KEY), eq(ATHLETE_ID), eq(5L), any()))
                    .thenReturn(new IcuEventDto(5L, "menthoros-bloco", "X", "2026-07-15T00:00:00"));

            adapter.push(conexao, workout, 5L);

            ArgumentCaptor<JsonNode> captor = ArgumentCaptor.forClass(JsonNode.class);
            verify(client).atualizarEvento(eq(API_KEY), eq(ATHLETE_ID), eq(5L), captor.capture());
            JsonNode sub = captor.getValue().get("workout_doc").get("steps").get(0).get("steps");
            assertThat(resolvidoPeloProvedor(sub.get(0).get("hr").get("start").asDouble(), 168)).isEqualTo(136);
            assertThat(resolvidoPeloProvedor(sub.get(0).get("hr").get("end").asDouble(), 168)).isEqualTo(146);
            assertThat(resolvidoPeloProvedor(sub.get(1).get("hr").get("start").asDouble(), 168)).isEqualTo(121);
            assertThat(resolvidoPeloProvedor(sub.get(1).get("hr").get("end").asDouble(), 168)).isEqualTo(126);
        }

        @Test
        @DisplayName("falha de API ao ler o LTHR vira PushResult.erro, nunca exceção")
        void falhaAoLerLthrViraErro() {
            StructuredWorkout workout = workoutComFc("menthoros-lthr-500", 140, 150);
            when(client.buscarLthrCorrida(API_KEY, ATHLETE_ID))
                    .thenThrow(new IntervalsIcuApiException(HttpStatus.INTERNAL_SERVER_ERROR, "erro 500"));

            PushResult resultado = adapter.push(conexao, workout, 5L);

            assertThat(resultado.sucesso()).isFalse();
            assertThat(resultado.statusErro()).isEqualTo(StatusSincronizacao.ERRO_TEMPORARIO);
        }

        @Test
        @DisplayName("primeiro push sem match na janela cria evento novo via POST")
        void primeiroPushSemMatchCriaViaPost() {
            StructuredWorkout workout = workoutSimples("menthoros-2");
            when(client.listarEventos(API_KEY, ATHLETE_ID, DATA, DATA))
                    .thenReturn(List.of(new IcuEventDto(1L, "outro-id", "X", "2026-07-15T00:00:00")));
            when(client.criarEvento(eq(API_KEY), eq(ATHLETE_ID), any()))
                    .thenReturn(new IcuEventDto(42L, "menthoros-2", "CONTINUO 15/07", "2026-07-15T00:00:00"));

            PushResult resultado = adapter.push(conexao, workout, null);

            assertThat(resultado.sucesso()).isTrue();
            assertThat(resultado.eventId()).isEqualTo(42L);
            verify(client, never()).atualizarEvento(any(), any(), anyLong(), any());
        }

        @Test
        @DisplayName("guarda defensiva: janela com o mesmo external_id adota o id e faz PUT, nunca duplica")
        void guardaDefensivaAdotaIdExistente() {
            StructuredWorkout workout = workoutSimples("menthoros-3");
            when(client.listarEventos(API_KEY, ATHLETE_ID, DATA, DATA))
                    .thenReturn(List.of(new IcuEventDto(55L, "menthoros-3", "X", "2026-07-15T00:00:00")));
            when(client.atualizarEvento(eq(API_KEY), eq(ATHLETE_ID), eq(55L), any()))
                    .thenReturn(new IcuEventDto(55L, "menthoros-3", "X", "2026-07-15T00:00:00"));

            PushResult resultado = adapter.push(conexao, workout, null);

            assertThat(resultado.sucesso()).isTrue();
            assertThat(resultado.eventId()).isEqualTo(55L);
            verify(client, never()).criarEvento(any(), any(), any());
            verify(client).atualizarEvento(eq(API_KEY), eq(ATHLETE_ID), eq(55L), any());
        }

        @Test
        @DisplayName("re-push com eventIdArmazenado faz PUT direto, sem listar")
        void repushComIdArmazenadoFazPutDireto() {
            StructuredWorkout workout = workoutSimples("menthoros-4");
            when(client.atualizarEvento(eq(API_KEY), eq(ATHLETE_ID), eq(77L), any()))
                    .thenReturn(new IcuEventDto(77L, "menthoros-4", "X", "2026-07-15T00:00:00"));

            PushResult resultado = adapter.push(conexao, workout, 77L);

            assertThat(resultado.sucesso()).isTrue();
            assertThat(resultado.eventId()).isEqualTo(77L);
            verify(client, never()).listarEventos(any(), any(), any(), any());
            verify(client, never()).criarEvento(any(), any(), any());
        }

        @Test
        @DisplayName("PUT 404 recria via POST e retorna o id NOVO")
        void put404RecriaViaPost() {
            StructuredWorkout workout = workoutSimples("menthoros-5");
            when(client.atualizarEvento(eq(API_KEY), eq(ATHLETE_ID), eq(77L), any()))
                    .thenThrow(new IntervalsIcuApiException(HttpStatus.NOT_FOUND, "evento não encontrado"));
            when(client.criarEvento(eq(API_KEY), eq(ATHLETE_ID), any()))
                    .thenReturn(new IcuEventDto(88L, "menthoros-5", "X", "2026-07-15T00:00:00"));

            PushResult resultado = adapter.push(conexao, workout, 77L);

            assertThat(resultado.sucesso()).isTrue();
            assertThat(resultado.eventId()).isEqualTo(88L);
            verify(client).criarEvento(eq(API_KEY), eq(ATHLETE_ID), any());
        }

        @ParameterizedTest(name = "status {0} -> {1}")
        @MethodSource("br.com.menthoros.backend.services.impl.IntervalsIcuAdapterTest#statusParaErro")
        @DisplayName("mapeia status HTTP para StatusSincronizacao sem lançar")
        void mapeiaErrosSemLancar(HttpStatus status, StatusSincronizacao esperado) {
            StructuredWorkout workout = workoutSimples("menthoros-6");
            when(client.atualizarEvento(eq(API_KEY), eq(ATHLETE_ID), eq(10L), any()))
                    .thenThrow(new IntervalsIcuApiException(status, "erro " + status.value()));

            PushResult resultado = adapter.push(conexao, workout, 10L);

            assertThat(resultado.sucesso()).isFalse();
            assertThat(resultado.eventId()).isNull();
            assertThat(resultado.statusErro()).isEqualTo(esperado);
            assertThat(resultado.mensagem()).isNotBlank();
            assertThat(resultado.mensagem()).doesNotContain(API_KEY);
        }

        @Test
        @DisplayName("falha de transporte (status nulo) vira ERRO_TEMPORARIO sem lançar")
        void falhaDeTransporteViraErroTemporario() {
            StructuredWorkout workout = workoutSimples("menthoros-6b");
            when(client.atualizarEvento(eq(API_KEY), eq(ATHLETE_ID), eq(10L), any()))
                    .thenThrow(new IntervalsIcuApiException("timeout de conexão", new RuntimeException("io")));

            PushResult resultado = adapter.push(conexao, workout, 10L);

            assertThat(resultado.sucesso()).isFalse();
            assertThat(resultado.statusErro()).isEqualTo(StatusSincronizacao.ERRO_TEMPORARIO);
            assertThat(resultado.mensagem()).isNotBlank();
            assertThat(resultado.mensagem()).doesNotContain(API_KEY);
        }

        @Test
        @DisplayName("namePrefix presente é concatenado ao name; ausente mantém o nome intacto")
        void namePrefixConcatenado() {
            StructuredWorkout comPrefixo = new StructuredWorkout(
                    "menthoros-7", "CONTINUO 15/07", "[Calibração]", DATA, "desc",
                    List.of(WorkoutStep.simples("Corrida", 1800, null, IntensityTarget.SEM_OBJETIVO)));
            when(client.atualizarEvento(eq(API_KEY), eq(ATHLETE_ID), eq(10L), any()))
                    .thenReturn(new IcuEventDto(10L, "menthoros-7", "[Calibração] CONTINUO 15/07",
                            "2026-07-15T00:00:00"));

            adapter.push(conexao, comPrefixo, 10L);

            ArgumentCaptor<JsonNode> captor = ArgumentCaptor.forClass(JsonNode.class);
            verify(client).atualizarEvento(eq(API_KEY), eq(ATHLETE_ID), eq(10L), captor.capture());
            assertThat(captor.getValue().get("name").asText()).isEqualTo("[Calibração] CONTINUO 15/07");

            StructuredWorkout semPrefixo = workoutSimples("menthoros-8");
            when(client.atualizarEvento(eq(API_KEY), eq(ATHLETE_ID), eq(11L), any()))
                    .thenReturn(new IcuEventDto(11L, "menthoros-8", "CONTINUO 15/07", "2026-07-15T00:00:00"));

            adapter.push(conexao, semPrefixo, 11L);

            ArgumentCaptor<JsonNode> captor2 = ArgumentCaptor.forClass(JsonNode.class);
            verify(client).atualizarEvento(eq(API_KEY), eq(ATHLETE_ID), eq(11L), captor2.capture());
            assertThat(captor2.getValue().get("name").asText()).isEqualTo("CONTINUO 15/07");
        }
    }

    @Nested
    @DisplayName("tocarEvento")
    class TocarEvento {

        @Test
        @DisplayName("faz PUT com payload mínimo contendo APENAS external_id com o valor canônico")
        void putComPayloadMinimo() {
            when(client.atualizarEvento(eq(API_KEY), eq(ATHLETE_ID), eq(999L), any()))
                    .thenReturn(new IcuEventDto(999L, "menthoros-abc", "X", "2026-07-15T00:00:00"));

            adapter.tocarEvento(conexao, 999L, "menthoros-abc");

            ArgumentCaptor<JsonNode> captor = ArgumentCaptor.forClass(JsonNode.class);
            verify(client).atualizarEvento(eq(API_KEY), eq(ATHLETE_ID), eq(999L), captor.capture());
            JsonNode payload = captor.getValue();
            assertThat(payload.size()).isEqualTo(1);
            assertThat(payload.get("external_id").asText()).isEqualTo("menthoros-abc");
        }

        @Test
        @DisplayName("exceção da API (inclusive 404) é absorvida, nunca propaga")
        void excecaoDaApiNaoPropaga() {
            when(client.atualizarEvento(eq(API_KEY), eq(ATHLETE_ID), eq(999L), any()))
                    .thenThrow(new IntervalsIcuApiException(HttpStatus.NOT_FOUND, "evento não encontrado"));

            assertThatCode(() -> adapter.tocarEvento(conexao, 999L, "menthoros-abc"))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("exceção inesperada (não vinda da API) também é absorvida")
        void excecaoInesperadaNaoPropaga() {
            when(client.atualizarEvento(eq(API_KEY), eq(ATHLETE_ID), eq(999L), any()))
                    .thenThrow(new RuntimeException("boom"));

            assertThatCode(() -> adapter.tocarEvento(conexao, 999L, "menthoros-abc"))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("removerOrfaos")
    class RemoverOrfaos {

        @Test
        @DisplayName("deleta apenas eventos menthoros-* ausentes do set atual; ignora eventos do atleta")
        void deletaApenasOrfaosComPrefixo() {
            LocalDate inicio = LocalDate.of(2026, 7, 13);
            LocalDate fim = LocalDate.of(2026, 7, 19);
            List<IcuEventDto> eventos = List.of(
                    new IcuEventDto(1L, "menthoros-a", "A", "2026-07-14T00:00:00"),
                    new IcuEventDto(2L, "menthoros-b", "B", "2026-07-14T00:00:00"),
                    new IcuEventDto(3L, "corrida-manual", "C", "2026-07-14T00:00:00")
            );
            when(client.listarEventos(API_KEY, ATHLETE_ID, inicio, fim)).thenReturn(eventos);

            adapter.removerOrfaos(conexao, inicio, fim, Set.of("menthoros-a"));

            verify(client).deletarEvento(API_KEY, ATHLETE_ID, 2L);
            verify(client, never()).deletarEvento(API_KEY, ATHLETE_ID, 1L);
            verify(client, never()).deletarEvento(API_KEY, ATHLETE_ID, 3L);
        }

        @Test
        @DisplayName("404 no delete de órfão é ignorado")
        void ignora404NoDelete() {
            LocalDate inicio = LocalDate.of(2026, 7, 13);
            LocalDate fim = LocalDate.of(2026, 7, 19);
            List<IcuEventDto> eventos = List.of(
                    new IcuEventDto(2L, "menthoros-b", "B", "2026-07-14T00:00:00")
            );
            when(client.listarEventos(API_KEY, ATHLETE_ID, inicio, fim)).thenReturn(eventos);
            org.mockito.Mockito.doThrow(new IntervalsIcuApiException(HttpStatus.NOT_FOUND, "não encontrado"))
                    .when(client).deletarEvento(API_KEY, ATHLETE_ID, 2L);

            adapter.removerOrfaos(conexao, inicio, fim, Set.of());

            verify(client).deletarEvento(API_KEY, ATHLETE_ID, 2L);
        }
    }

    @Nested
    @DisplayName("percentualDoLimiar")
    class PercentualDoLimiar {

        @Test
        @DisplayName("truncado pelo provedor, resolve exatamente no bpm para todo LTHR fisiológico")
        void truncaExatamenteNoBpm() {
            for (int lthr = 100; lthr <= 200; lthr++) {
                for (int bpm = 60; bpm <= 210; bpm++) {
                    double pct = IntervalsIcuAdapter.percentualDoLimiar(bpm, lthr);
                    assertThat(resolvidoPeloProvedor(pct, lthr))
                            .as("bpm=%d lthr=%d pct=%s", bpm, lthr, pct)
                            .isEqualTo(bpm);
                }
            }
        }

        @Test
        @DisplayName("o caso que motivou: 107 bpm com LTHR 168 — 63,69% dava 106, o ponto médio dá 107")
        void casoReal() {
            assertThat(resolvidoPeloProvedor(63.69, 168)).isEqualTo(106);
            assertThat(resolvidoPeloProvedor(IntervalsIcuAdapter.percentualDoLimiar(107, 168), 168)).isEqualTo(107);
        }

        @Test
        @DisplayName("percentual tem no máximo duas casas decimais")
        void duasCasas() {
            double pct = IntervalsIcuAdapter.percentualDoLimiar(107, 168);
            assertThat(Math.round(pct * 100) / 100.0).isEqualTo(pct);
        }
    }

    // ===== Fixtures =====

    /** LTHR cadastrado no provedor — deliberadamente ≠ de qualquer valor do Menthoros. */
    private static final int LTHR_PROVEDOR = 185;

    /** Reproduz a resolução do intervals.icu observada em 2026-10-01: {@code floor(pct × lthr / 100)}. */
    static int resolvidoPeloProvedor(double pct, int lthr) {
        return (int) Math.floor(pct * lthr / 100.0);
    }

    private StructuredWorkout workoutComFc(String externalId, int startBpm, int endBpm) {
        return new StructuredWorkout(externalId, "CONTINUO 15/07", null, DATA, "desc",
                List.of(WorkoutStep.simples("Corrida", 1800, null, new HrTarget(startBpm, endBpm))));
    }

    private StructuredWorkout workoutSimples(String externalId) {
        return new StructuredWorkout(externalId, "CONTINUO 15/07", null, DATA, "desc",
                List.of(WorkoutStep.simples("Corrida", 1800, null, IntensityTarget.SEM_OBJETIVO)));
    }

    static Stream<Arguments> statusParaErro() {
        return Stream.of(
                Arguments.of(HttpStatus.UNAUTHORIZED, StatusSincronizacao.ERRO_AUTENTICACAO),
                Arguments.of(HttpStatus.FORBIDDEN, StatusSincronizacao.ERRO_AUTENTICACAO),
                Arguments.of(HttpStatus.UNPROCESSABLE_ENTITY, StatusSincronizacao.ERRO_VALIDACAO),
                Arguments.of(HttpStatus.TOO_MANY_REQUESTS, StatusSincronizacao.ERRO_LIMITE_RATE),
                Arguments.of(HttpStatus.INTERNAL_SERVER_ERROR, StatusSincronizacao.ERRO_TEMPORARIO)
        );
    }
}
