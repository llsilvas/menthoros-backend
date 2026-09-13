package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.ai.ledger.LlmCallContext;
import br.com.menthoros.backend.ai.ledger.LlmCallResult;
import br.com.menthoros.backend.ai.ledger.LlmCallScope;
import br.com.menthoros.backend.ai.ledger.PromptHashCalculator;
import br.com.menthoros.backend.ai.ledger.Violacao;
import br.com.menthoros.backend.domain.compliance.PromptVersion;
import br.com.menthoros.backend.domain.compliance.SchemaVersion;
import br.com.menthoros.backend.exception.LLMException;
import br.com.menthoros.backend.exception.PlanoNaoConformeException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlanoLlmLedgerHookTest {

    @Mock
    private LlmCallLedger ledger;
    @Mock
    private PromptHashCalculator promptHash;

    private PlanoLlmLedgerHook hook;
    private final UUID req = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        hook = new PlanoLlmLedgerHook(ledger, promptHash);
        LlmCallScope.openRequest(req, UUID.randomUUID(), "Maria");
    }

    @AfterEach
    void tearDown() {
        LlmCallScope.closeRequest();
    }

    /** Simula o advisor: durante a chamada, grava a linha e registra o id. */
    private static UUID advisorGravou() {
        UUID id = UUID.randomUUID();
        LlmCallScope.registerCallId(id);
        return id;
    }

    @Nested
    @DisplayName("chamar")
    class Chamar {

        @Test
        @DisplayName("abre a tentativa com número e versões, devolve o resultado e guarda o id do advisor")
        void abreTentativaEGuardaId() {
            when(promptHash.valor()).thenReturn("hash-1");
            AtomicReference<Optional<LlmCallContext>> visto = new AtomicReference<>();
            AtomicReference<UUID> id = new AtomicReference<>();
            var sessao = hook.novaSessao();

            String r = sessao.chamar(2, () -> {
                visto.set(LlmCallScope.current());
                id.set(advisorGravou());
                return "dto";
            });

            assertThat(r).isEqualTo("dto");
            LlmCallContext ctx = visto.get().orElseThrow();
            assertThat(ctx.attempt()).isEqualTo(2);
            assertThat(ctx.promptVersion()).isEqualTo(PromptVersion.CURRENT);
            assertThat(ctx.promptHash()).isEqualTo("hash-1");
            assertThat(ctx.schemaVersion()).isEqualTo(SchemaVersion.CURRENT);
            assertThat(ctx.generationRequestId()).isEqualTo(req);
            assertThat(sessao.ultimaChamada()).contains(id.get());
            assertThat(LlmCallScope.current()).as("tentativa fechada ao sair").isEmpty();
            assertThat(LlmCallScope.currentGenerationRequestId()).as("requisição continua").contains(req);
            verifyNoInteractions(ledger);
        }

        @Test
        @DisplayName("exceção depois de o advisor gravar = falha de conversão → PARSE_ERROR com a mensagem")
        void excecaoAposGravarEhParseError() {
            when(promptHash.valor()).thenReturn("h");
            AtomicReference<UUID> id = new AtomicReference<>();
            var sessao = hook.novaSessao();

            assertThatThrownBy(() -> sessao.chamar(1, () -> {
                id.set(advisorGravou());
                throw new IllegalStateException("Cannot deserialize");
            })).isInstanceOf(IllegalStateException.class);

            ArgumentCaptor<List<Violacao>> captor = ArgumentCaptor.forClass(List.class);
            verify(ledger).registrarResultado(eq(id.get()), eq(LlmCallResult.PARSE_ERROR), captor.capture());
            assertThat(captor.getValue()).containsExactly(new Violacao("PARSE_ERROR", "Cannot deserialize"));
            assertThat(sessao.ultimaChamada()).contains(id.get());
            assertThat(LlmCallScope.current()).isEmpty();
        }

        @Test
        @DisplayName("exceção sem id gravado = provider falhou; o advisor já registrou, o hook não toca no ledger")
        void excecaoSemIdNaoRegistra() {
            when(promptHash.valor()).thenReturn("h");
            var sessao = hook.novaSessao();

            assertThatThrownBy(() -> sessao.chamar(1, () -> {
                throw new RuntimeException("503");
            })).isInstanceOf(RuntimeException.class);

            verifyNoInteractions(ledger);
            assertThat(sessao.ultimaChamada()).isEmpty();
            assertThat(LlmCallScope.current()).isEmpty();
        }

        @Test
        @DisplayName("mensagem nula da exceção vira 'sem mensagem'; longa é truncada em 300")
        void mensagemNulaOuLonga() {
            when(promptHash.valor()).thenReturn("h");
            var sessao = hook.novaSessao();
            String longa = "x".repeat(400);

            assertThatThrownBy(() -> sessao.chamar(1, () -> { advisorGravou(); throw new RuntimeException((String) null); }))
                    .isInstanceOf(RuntimeException.class);
            assertThatThrownBy(() -> sessao.chamar(2, () -> { advisorGravou(); throw new RuntimeException(longa); }))
                    .isInstanceOf(RuntimeException.class);

            ArgumentCaptor<List<Violacao>> captor = ArgumentCaptor.forClass(List.class);
            verify(ledger, org.mockito.Mockito.times(2)).registrarResultado(any(), eq(LlmCallResult.PARSE_ERROR), captor.capture());
            assertThat(captor.getAllValues().get(0).get(0).mensagem()).isEqualTo("sem mensagem");
            assertThat(captor.getAllValues().get(1).get(0).mensagem()).hasSize(301).endsWith("…");
        }

        @Test
        @DisplayName("segunda tentativa substitui o id da primeira")
        void segundaTentativaSubstituiId() {
            when(promptHash.valor()).thenReturn("h");
            var sessao = hook.novaSessao();
            AtomicReference<UUID> id2 = new AtomicReference<>();

            sessao.chamar(1, () -> { advisorGravou(); return "a"; });
            sessao.chamar(2, () -> { id2.set(advisorGravou()); return "b"; });

            assertThat(sessao.ultimaChamada()).contains(id2.get());
        }

        @Test
        @DisplayName("tentativa inválida falha antes de chamar")
        void tentativaInvalida() {
            var sessao = hook.novaSessao();
            assertThatThrownBy(() -> sessao.chamar(0, () -> "x")).isInstanceOf(IllegalArgumentException.class);
            verifyNoInteractions(ledger);
        }
    }

    @Nested
    @DisplayName("validar")
    class Validar {

        private PlanoLlmLedgerHook.Sessao sessaoComChamada(UUID id) {
            when(promptHash.valor()).thenReturn("h");
            var sessao = hook.novaSessao();
            sessao.chamar(1, () -> { LlmCallScope.registerCallId(id); return "dto"; });
            return sessao;
        }

        @Test
        @DisplayName("validação ok → SUCCESS sem violações e devolve o validado")
        void sucesso() {
            UUID id = UUID.randomUUID();
            var sessao = sessaoComChamada(id);

            String r = sessao.validar(() -> "validado");

            assertThat(r).isEqualTo("validado");
            verify(ledger).registrarResultado(id, LlmCallResult.SUCCESS, List.of());
        }

        @Test
        @DisplayName("PlanoNaoConformeException → VALIDATION_REJECTED com as keys reais, e relança")
        void naoConformeComKeysReais() {
            UUID id = UUID.randomUUID();
            var sessao = sessaoComChamada(id);
            var violacoes = List.of(new Violacao("TSS_FORA_DA_FAIXA", "180 vs 120"), new Violacao("DIA_ERRADO", "SEG"));

            assertThatThrownBy(() -> sessao.validar(() -> { throw new PlanoNaoConformeException("diverge", violacoes); }))
                    .isInstanceOf(PlanoNaoConformeException.class)
                    .isInstanceOf(LLMException.class);

            verify(ledger).registrarResultado(id, LlmCallResult.VALIDATION_REJECTED, violacoes);
        }

        @Test
        @DisplayName("LLMException genérica → VALIDATION_REJECTED com key estrutural e mensagem, e relança")
        void rejeicaoEstruturalGenerica() {
            UUID id = UUID.randomUUID();
            var sessao = sessaoComChamada(id);

            assertThatThrownBy(() -> sessao.validar(() -> { throw new LLMException("REGENERATIVO com 2 etapas"); }))
                    .isInstanceOf(LLMException.class);

            verify(ledger).registrarResultado(id, LlmCallResult.VALIDATION_REJECTED,
                    List.of(new Violacao("VALIDACAO_ESTRUTURAL", "REGENERATIVO com 2 etapas")));
        }

        @Test
        @DisplayName("exceção que não é LLMException propaga sem tocar no ledger")
        void outraExcecaoPropaga() {
            UUID id = UUID.randomUUID();
            var sessao = sessaoComChamada(id);

            assertThatThrownBy(() -> sessao.validar(() -> { throw new IllegalStateException("bug"); }))
                    .isInstanceOf(IllegalStateException.class);

            verify(ledger, never()).registrarResultado(any(), any(), any());
        }

        @Test
        @DisplayName("validar sem chamada anterior repassa id nulo (o ledger loga e ignora)")
        void semChamadaAnterior() {
            var sessao = hook.novaSessao();

            sessao.validar(() -> "ok");

            verify(ledger).registrarResultado(isNull(), eq(LlmCallResult.SUCCESS), eq(List.of()));
        }
    }

    @Test
    @DisplayName("truncar: nulo, curto, exatamente 300 e 301")
    void truncar() {
        assertThat(PlanoLlmLedgerHook.truncar(null)).isEqualTo("sem mensagem");
        assertThat(PlanoLlmLedgerHook.truncar("abc")).isEqualTo("abc");
        assertThat(PlanoLlmLedgerHook.truncar("x".repeat(300))).hasSize(300);
        assertThat(PlanoLlmLedgerHook.truncar("x".repeat(301))).hasSize(301).endsWith("…");
    }

    @Test
    @DisplayName("PlanoNaoConformeException copia a lista e aceita nula")
    void excecaoCopiaLista() {
        var lista = new java.util.ArrayList<>(List.of(new Violacao("K", "m")));
        var e = new PlanoNaoConformeException("msg", lista);
        lista.clear();
        assertThat(e.violacoes()).hasSize(1);
        assertThat(new PlanoNaoConformeException("msg", null).violacoes()).isEmpty();
    }
}
