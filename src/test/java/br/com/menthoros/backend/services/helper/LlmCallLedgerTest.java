package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.ai.ledger.GenerationOutcome;
import br.com.menthoros.backend.ai.ledger.LlmCallContext;
import br.com.menthoros.backend.ai.ledger.LlmCallRegistro;
import br.com.menthoros.backend.ai.ledger.LlmCallResult;
import br.com.menthoros.backend.ai.ledger.Violacao;
import br.com.menthoros.backend.entity.LlmCall;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LlmCallLedgerTest {

    @Mock
    private LlmCallLedgerWriter writer;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private LlmCallLedger ledger() {
        return new LlmCallLedger(writer, objectMapper);
    }

    private static final UUID REQ = UUID.randomUUID();
    private static final UUID ATLETA = UUID.randomUUID();
    private static final UUID TENANT = UUID.randomUUID();

    private static LlmCallContext contexto(String nome) {
        return new LlmCallContext(REQ, ATLETA, nome, 2, "plano-v1", "abc123", "schema-v1");
    }

    private static LlmCallRegistro registro(LlmCallResult result, UUID tenant, String responseText,
                                            Optional<LlmCallContext> ctx) {
        return new LlmCallRegistro("plano", "gpt-4o", 100L, 20L, 50L, 0L, new BigDecimal("0.0012345678"),
                1500, result, tenant, 1, responseText, ctx);
    }

    @Nested
    @DisplayName("registrarChamada")
    class RegistrarChamada {

        @Test
        @DisplayName("mapeia todos os campos genéricos e os de enriquecimento")
        void mapeiaTudo() {
            UUID id = UUID.randomUUID();
            when(writer.inserir(any())).thenReturn(id);
            String resposta = "{\"plano\":\"ok\"}";

            Optional<UUID> resultado = ledger().registrarChamada(
                    registro(LlmCallResult.PENDING, TENANT, resposta, Optional.of(contexto("Maria Souza"))));

            assertThat(resultado).contains(id);
            ArgumentCaptor<LlmCall> captor = ArgumentCaptor.forClass(LlmCall.class);
            verify(writer).inserir(captor.capture());
            LlmCall c = captor.getValue();
            assertThat(c.getTenantId()).isEqualTo(TENANT);
            assertThat(c.getRoute()).isEqualTo("plano");
            assertThat(c.getModel()).isEqualTo("gpt-4o");
            assertThat(c.getInputTokens()).isEqualTo(100L);
            assertThat(c.getOutputTokens()).isEqualTo(20L);
            assertThat(c.getCacheReadTokens()).isEqualTo(50L);
            assertThat(c.getCacheWriteTokens()).isEqualTo(0L);
            assertThat(c.getCostUsd()).isEqualByComparingTo("0.0012345678");
            assertThat(c.getLatencyMs()).isEqualTo(1500);
            assertThat(c.getResult()).isEqualTo(LlmCallResult.PENDING);
            assertThat(c.getTransportRetries()).isEqualTo(1);
            assertThat(c.getGenerationRequestId()).isEqualTo(REQ);
            assertThat(c.getAtletaId()).isEqualTo(ATLETA);
            assertThat(c.getAttempt()).isEqualTo(2);
            assertThat(c.getPromptVersion()).isEqualTo("plano-v1");
            assertThat(c.getPromptHash()).isEqualTo("abc123");
            assertThat(c.getSchemaVersion()).isEqualTo("schema-v1");
            assertThat(c.getResponseJson()).isEqualTo(resposta);
            assertThat(c.getRequestOutcome()).isNull();
        }

        @Test
        @DisplayName("sem contexto grava só o genérico — enriquecimento e resposta ficam nulos mesmo que o texto venha")
        void semContextoNaoEnriquece() {
            when(writer.inserir(any())).thenReturn(UUID.randomUUID());

            ledger().registrarChamada(registro(LlmCallResult.SUCCESS, TENANT, "{\"x\":1}", Optional.empty()));

            ArgumentCaptor<LlmCall> captor = ArgumentCaptor.forClass(LlmCall.class);
            verify(writer).inserir(captor.capture());
            LlmCall c = captor.getValue();
            assertThat(c.getGenerationRequestId()).isNull();
            assertThat(c.getAtletaId()).isNull();
            assertThat(c.getAttempt()).isNull();
            assertThat(c.getPromptVersion()).isNull();
            assertThat(c.getResponseJson()).isNull();
            assertThat(c.getResult()).isEqualTo(LlmCallResult.SUCCESS);
        }

        @Test
        @DisplayName("tenant nulo é gravado como nulo (rota sem tenant), sem lançar")
        void tenantNulo() {
            when(writer.inserir(any())).thenReturn(UUID.randomUUID());

            assertThatCode(() -> ledger().registrarChamada(registro(LlmCallResult.SUCCESS, null, null, Optional.empty())))
                    .doesNotThrowAnyException();

            ArgumentCaptor<LlmCall> captor = ArgumentCaptor.forClass(LlmCall.class);
            verify(writer).inserir(captor.capture());
            assertThat(captor.getValue().getTenantId()).isNull();
        }

        @Test
        @DisplayName("caminho de exceção do provider: tokens e custo nulos são aceitos")
        void tokensNulos() {
            when(writer.inserir(any())).thenReturn(UUID.randomUUID());
            var registro = new LlmCallRegistro("plano", "desconhecido", null, null, null, null, null,
                    120000, LlmCallResult.TIMEOUT, TENANT, 0, null, Optional.empty());

            ledger().registrarChamada(registro);

            ArgumentCaptor<LlmCall> captor = ArgumentCaptor.forClass(LlmCall.class);
            verify(writer).inserir(captor.capture());
            assertThat(captor.getValue().getInputTokens()).isNull();
            assertThat(captor.getValue().getCostUsd()).isNull();
            assertThat(captor.getValue().getLatencyMs()).isEqualTo(120000);
        }

        @Test
        @DisplayName("writer lançando vira Optional vazio e nunca propaga (CA8)")
        void writerFalhaNaoPropaga() {
            when(writer.inserir(any())).thenThrow(new RuntimeException("banco fora"));

            Optional<UUID> resultado = ledger().registrarChamada(
                    registro(LlmCallResult.PENDING, TENANT, "{}", Optional.of(contexto("Maria"))));

            assertThat(resultado).isEmpty();
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"   "})
        @DisplayName("resposta nula, vazia ou em branco não gera response_json")
        void respostaVazia(String texto) {
            when(writer.inserir(any())).thenReturn(UUID.randomUUID());

            ledger().registrarChamada(registro(LlmCallResult.PENDING, TENANT, texto, Optional.of(contexto("Maria"))));

            ArgumentCaptor<LlmCall> captor = ArgumentCaptor.forClass(LlmCall.class);
            verify(writer).inserir(captor.capture());
            assertThat(captor.getValue().getResponseJson()).isNull();
        }

        @Test
        @DisplayName("resposta que não é JSON válido (truncada) é embrulhada como string JSON, não perdida")
        void respostaNaoJson() {
            when(writer.inserir(any())).thenReturn(UUID.randomUUID());

            ledger().registrarChamada(registro(LlmCallResult.PENDING, TENANT, "{\"treinos\": [", Optional.of(contexto(null))));

            ArgumentCaptor<LlmCall> captor = ArgumentCaptor.forClass(LlmCall.class);
            verify(writer).inserir(captor.capture());
            assertThat(captor.getValue().getResponseJson()).isEqualTo("\"{\\\"treinos\\\": [\"");
        }

        @Test
        @DisplayName("nome do atleta é redigido na resposta antes de gravar (D7)")
        void redigeNome() {
            when(writer.inserir(any())).thenReturn(UUID.randomUUID());
            String resposta = "{\"justificativaIa\":\"Maria Souza, a Maria precisa de Z2; maria-souza@x.com\"}";

            ledger().registrarChamada(registro(LlmCallResult.PENDING, TENANT, resposta, Optional.of(contexto("Maria Souza"))));

            ArgumentCaptor<LlmCall> captor = ArgumentCaptor.forClass(LlmCall.class);
            verify(writer).inserir(captor.capture());
            assertThat(captor.getValue().getResponseJson())
                    .doesNotContainIgnoringCase("maria")
                    .doesNotContainIgnoringCase("souza")
                    .contains("[ATLETA]");
        }
    }

    @Nested
    @DisplayName("anonimizarRespostasDoAtleta")
    class AnonimizarRespostasDoAtleta {

        @Test
        @DisplayName("repassa o atletaId ao writer e loga quando anula alguma linha")
        void repassaAoWriter() {
            when(writer.anonimizarRespostasDoAtleta(ATLETA)).thenReturn(3);

            ledger().anonimizarRespostasDoAtleta(ATLETA);

            verify(writer).anonimizarRespostasDoAtleta(ATLETA);
        }

        @Test
        @DisplayName("zero linhas anuladas não lança nem exige nada além da chamada")
        void zeroLinhas() {
            when(writer.anonimizarRespostasDoAtleta(ATLETA)).thenReturn(0);

            assertThatCode(() -> ledger().anonimizarRespostasDoAtleta(ATLETA)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("atletaId nulo é no-op — não chama o writer")
        void atletaIdNulo() {
            ledger().anonimizarRespostasDoAtleta(null);

            verifyNoInteractions(writer);
        }

        @Test
        @DisplayName("writer lançando é engolido (CA8)")
        void writerLancandoNaoPropaga() {
            when(writer.anonimizarRespostasDoAtleta(ATLETA)).thenThrow(new RuntimeException("banco fora"));

            assertThatCode(() -> ledger().anonimizarRespostasDoAtleta(ATLETA)).doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("purgarRespostasAntigas")
    class PurgarRespostasAntigas {

        private final Instant corte = Instant.parse("2026-06-01T00:00:00Z");

        @Test
        @DisplayName("repassa o corte ao writer e devolve o total anulado")
        void repassaAoWriterEDevolveTotal() {
            when(writer.purgarRespostasAntesDe(corte)).thenReturn(7);

            int total = ledger().purgarRespostasAntigas(corte);

            assertThat(total).isEqualTo(7);
            verify(writer).purgarRespostasAntesDe(corte);
        }

        @Test
        @DisplayName("writer lançando é engolido e devolve 0 (CA8)")
        void writerLancandoDevolveZero() {
            when(writer.purgarRespostasAntesDe(corte)).thenThrow(new RuntimeException("banco fora"));

            int total = ledger().purgarRespostasAntigas(corte);

            assertThat(total).isZero();
        }
    }

    @Nested
    @DisplayName("redigirNome")
    class RedigirNome {

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"  "})
        @DisplayName("sem nome, o texto volta intacto")
        void semNome(String nome) {
            assertThat(LlmCallLedger.redigirNome("Plano da Maria", nome)).isEqualTo("Plano da Maria");
        }

        @ParameterizedTest
        @CsvSource({
                "'Maria Souza', 'Maria Souza treina', '[ATLETA] treina'",
                "'Maria Souza', 'MARIA e souza', '[ATLETA] e [ATLETA]'",
                "'Maria Souza', 'Mariana corre', 'Mariana corre'",
                "'Ana de Sá', 'Ana de Sá; ana; de; sá', '[ATLETA]; [ATLETA]; de; sá'",
                "'João', 'joão e Joao', '[ATLETA] e Joao'",
        })
        @DisplayName("nome completo e cada parte com 3+ letras, sem cortar palavras maiores nem partículas")
        void casos(String nome, String texto, String esperado) {
            assertThat(LlmCallLedger.redigirNome(texto, nome)).isEqualTo(esperado);
        }

        @Test
        @DisplayName("nome com caracteres especiais de regex não quebra")
        void nomeComRegex() {
            assertThat(LlmCallLedger.redigirNome("x (a+b) x", "(a+b)")).isEqualTo("x [ATLETA] x");
        }
    }

    @Nested
    @DisplayName("registrarResultado")
    class RegistrarResultado {

        @ParameterizedTest
        @EnumSource(LlmCallResult.class)
        @DisplayName("todo resultado é repassado ao writer com violações serializadas")
        void repassaTodoResultado(LlmCallResult result) {
            UUID id = UUID.randomUUID();
            when(writer.atualizarResultado(eq(id), eq(result), anyString())).thenReturn(true);

            ledger().registrarResultado(id, result, List.of(new Violacao("PACE_TETO", "5:00 acima do teto")));

            ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
            verify(writer).atualizarResultado(eq(id), eq(result), json.capture());
            assertThat(json.getValue()).isEqualTo("[{\"key\":\"PACE_TETO\",\"mensagem\":\"5:00 acima do teto\"}]");
        }

        @Test
        @DisplayName("lista vazia ou nula grava violations nulo")
        void semViolacoes() {
            UUID id = UUID.randomUUID();
            when(writer.atualizarResultado(eq(id), eq(LlmCallResult.SUCCESS), isNull())).thenReturn(true);

            ledger().registrarResultado(id, LlmCallResult.SUCCESS, List.of());
            ledger().registrarResultado(id, LlmCallResult.SUCCESS, null);

            verify(writer, org.mockito.Mockito.times(2)).atualizarResultado(eq(id), eq(LlmCallResult.SUCCESS), isNull());
        }

        @Test
        @DisplayName("id nulo (linha nunca gravada) não chama o writer")
        void idNulo() {
            ledger().registrarResultado(null, LlmCallResult.SUCCESS, List.of());
            verifyNoInteractions(writer);
        }

        @Test
        @DisplayName("linha não encontrada e writer lançando são engolidos")
        void naoEncontradaEFalha() {
            UUID id = UUID.randomUUID();
            when(writer.atualizarResultado(eq(id), any(), any())).thenReturn(false)
                    .thenThrow(new IllegalStateException("timeout"));

            assertThatCode(() -> {
                ledger().registrarResultado(id, LlmCallResult.SUCCESS, List.of());
                ledger().registrarResultado(id, LlmCallResult.SUCCESS, List.of());
            }).doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("registrarDesfecho")
    class RegistrarDesfecho {

        @ParameterizedTest
        @EnumSource(GenerationOutcome.class)
        @DisplayName("todo desfecho é repassado ao writer pela requisição")
        void repassaTodoDesfecho(GenerationOutcome outcome) {
            when(writer.atualizarDesfecho(REQ, outcome)).thenReturn(true);
            ledger().registrarDesfecho(REQ, outcome);
            verify(writer).atualizarDesfecho(REQ, outcome);
        }

        @Test
        @DisplayName("requisição nula é no-op")
        void requisicaoNula() {
            ledger().registrarDesfecho(null, GenerationOutcome.PERSISTED);
            verifyNoInteractions(writer);
        }

        @Test
        @DisplayName("requisição sem chamadas e writer lançando são engolidos")
        void semChamadasEFalha() {
            when(writer.atualizarDesfecho(eq(REQ), any())).thenReturn(false)
                    .thenThrow(new RuntimeException("banco fora"));

            assertThatCode(() -> {
                ledger().registrarDesfecho(REQ, GenerationOutcome.CONFLICT);
                ledger().registrarDesfecho(REQ, GenerationOutcome.CONFLICT);
            }).doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("LlmCallRegistro")
    class Registro {

        @Test
        @DisplayName("rejeita rota, modelo, resultado ou contexto nulos e latência negativa")
        void validacao() {
            assertThatThrownBy(() -> new LlmCallRegistro(" ", "m", null, null, null, null, null, 1, LlmCallResult.SUCCESS, null, null, null, Optional.empty()))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new LlmCallRegistro("r", "", null, null, null, null, null, 1, LlmCallResult.SUCCESS, null, null, null, Optional.empty()))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new LlmCallRegistro("r", "m", null, null, null, null, null, 1, null, null, null, null, Optional.empty()))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new LlmCallRegistro("r", "m", null, null, null, null, null, 1, LlmCallResult.SUCCESS, null, null, null, null))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new LlmCallRegistro("r", "m", null, null, null, null, null, -1, LlmCallResult.SUCCESS, null, null, null, Optional.empty()))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new Violacao(" ", "m")).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    @DisplayName("o ledger nunca chama o writer para gravar o prompt — não existe tal método")
    void naoHaEscritaDePrompt() {
        assertThat(LlmCallLedgerWriter.class.getDeclaredMethods())
                .extracting(java.lang.reflect.Method::getName)
                .noneMatch(n -> n.toLowerCase().contains("prompt"));
        verify(writer, never()).inserir(any());
    }
}
