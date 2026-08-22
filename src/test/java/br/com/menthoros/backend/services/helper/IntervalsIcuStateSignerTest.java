package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.config.external.IntervalsIcuProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * O state é a única coisa que liga o callback público a um atleta. Todo caminho de rejeição aqui
 * é um caminho que, se passasse, permitiria vincular uma conta intervals.icu ao registro de outro
 * atleta (D2).
 */
class IntervalsIcuStateSignerTest {

    private static final String SECRET = "segredo-do-app-663-bem-comprido";
    private static final Instant AGORA = Instant.parse("2026-08-21T12:00:00Z");

    private IntervalsIcuStateSigner signerEm(Instant instante) {
        IntervalsIcuProperties props = new IntervalsIcuProperties();
        props.setClientSecret(SECRET);
        return new IntervalsIcuStateSigner(props, Clock.fixed(instante, ZoneOffset.UTC));
    }

    private final IntervalsIcuStateSigner signer = signerEm(AGORA);

    @Nested
    @DisplayName("assinar")
    class Assinar {

        @Test
        @DisplayName("produz três partes separadas por ponto")
        void formatoTresPartes() {
            String state = signer.assinar(UUID.randomUUID());

            assertThat(state.split("\\.")).hasSize(3);
        }

        @Test
        @DisplayName("states de atletas diferentes não colidem")
        void atletasDiferentesProduzemStatesDiferentes() {
            assertThat(signer.assinar(UUID.randomUUID()))
                    .isNotEqualTo(signer.assinar(UUID.randomUUID()));
        }

        @Test
        @DisplayName("a assinatura não é o segredo nem o contém")
        void assinaturaNaoVazaOSegredo() {
            String state = signer.assinar(UUID.randomUUID());

            assertThat(state).doesNotContain(SECRET);
        }

        @Test
        @DisplayName("é seguro para URL — sem +, / ou = do base64 padrão")
        void assinaturaEhUrlSafe() {
            // O state viaja como query param na URL de autorização; base64 padrão quebraria
            // no round-trip do provedor.
            String assinatura = signer.assinar(UUID.randomUUID()).split("\\.")[2];

            assertThat(assinatura).doesNotContain("+").doesNotContain("/").doesNotContain("=");
        }
    }

    @Nested
    @DisplayName("validar")
    class Validar {

        @Test
        @DisplayName("round-trip devolve o atletaId original")
        void roundTripFeliz() {
            UUID atletaId = UUID.randomUUID();

            assertThat(signer.validar(signer.assinar(atletaId))).contains(atletaId);
        }

        @Test
        @DisplayName("assinatura adulterada é rejeitada")
        void assinaturaAdulterada() {
            String state = signer.assinar(UUID.randomUUID());
            String[] partes = state.split("\\.");
            String adulterado = partes[0] + "." + partes[1] + "." + partes[2].substring(1) + "X";

            assertThat(signer.validar(adulterado)).isEmpty();
        }

        // O ataque que D2 existe para impedir: trocar o atletaId mantendo uma assinatura
        // legítima obtida do próprio fluxo.
        @Test
        @DisplayName("atletaId trocado com assinatura de outro state é rejeitado")
        void atletaIdTrocado() {
            String state = signer.assinar(UUID.randomUUID());
            String[] partes = state.split("\\.");
            String forjado = UUID.randomUUID() + "." + partes[1] + "." + partes[2];

            assertThat(signer.validar(forjado)).isEmpty();
        }

        @Test
        @DisplayName("timestamp trocado com assinatura legítima é rejeitado")
        void timestampTrocado() {
            String state = signer.assinar(UUID.randomUUID());
            String[] partes = state.split("\\.");
            String forjado = partes[0] + "." + (Long.parseLong(partes[1]) + 1) + "." + partes[2];

            assertThat(signer.validar(forjado)).isEmpty();
        }

        @Test
        @DisplayName("state assinado com outro segredo é rejeitado")
        void outroSegredo() {
            IntervalsIcuProperties outrasProps = new IntervalsIcuProperties();
            outrasProps.setClientSecret("segredo-diferente");
            IntervalsIcuStateSigner outroSigner =
                    new IntervalsIcuStateSigner(outrasProps, Clock.fixed(AGORA, ZoneOffset.UTC));

            assertThat(signer.validar(outroSigner.assinar(UUID.randomUUID()))).isEmpty();
        }

        @Test
        @DisplayName("state com 9 minutos ainda é aceito")
        void noveMinutosAceito() {
            String state = signerEm(AGORA).assinar(UUID.randomUUID());

            IntervalsIcuStateSigner noveMinDepois = signerEm(AGORA.plus(Duration.ofMinutes(9)));

            assertThat(noveMinDepois.validar(state)).isPresent();
        }

        @Test
        @DisplayName("state com 11 minutos é rejeitado (TTL de 10)")
        void onzeMinutosRejeitado() {
            String state = signerEm(AGORA).assinar(UUID.randomUUID());

            IntervalsIcuStateSigner onzeMinDepois = signerEm(AGORA.plus(Duration.ofMinutes(11)));

            assertThat(onzeMinDepois.validar(state)).isEmpty();
        }

        @Test
        @DisplayName("state do futuro além da tolerância de skew é rejeitado")
        void futuroAlemDoSkew() {
            String state = signerEm(AGORA.plus(Duration.ofMinutes(5))).assinar(UUID.randomUUID());

            assertThat(signerEm(AGORA).validar(state)).isEmpty();
        }

        @Test
        @DisplayName("skew pequeno de relógio entre instâncias é tolerado")
        void skewPequenoTolerado() {
            // Multi-instância: quem assina e quem valida podem ser JVMs diferentes.
            String state = signerEm(AGORA.plusSeconds(20)).assinar(UUID.randomUUID());

            assertThat(signerEm(AGORA).validar(state)).isPresent();
        }
    }

    @Nested
    @DisplayName("validarEntradaMalformada")
    class ValidarEntradaMalformada {

        // Nenhum destes pode lançar: o callback é público e a entrada é do atacante.
        // Uma exceção aqui viraria 500 e violaria CA13.
        @Test
        @DisplayName("null retorna vazio sem lançar")
        void nulo() {
            assertThatCode(() -> assertThat(signer.validar(null)).isEmpty()).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("string vazia retorna vazio sem lançar")
        void vazio() {
            assertThatCode(() -> assertThat(signer.validar("")).isEmpty()).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("sem separador retorna vazio sem lançar")
        void semSeparador() {
            assertThatCode(() -> assertThat(signer.validar("abcdef")).isEmpty()).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("partes demais retorna vazio sem lançar")
        void partesDemais() {
            assertThatCode(() -> assertThat(signer.validar("a.b.c.d")).isEmpty()).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("atletaId não-UUID retorna vazio sem lançar")
        void atletaIdNaoUuid() {
            String state = signer.assinar(UUID.randomUUID());
            String[] partes = state.split("\\.");

            assertThatCode(() -> assertThat(signer.validar("nao-e-uuid." + partes[1] + "." + partes[2])).isEmpty())
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("timestamp não-numérico retorna vazio sem lançar")
        void timestampNaoNumerico() {
            String state = signer.assinar(UUID.randomUUID());
            String[] partes = state.split("\\.");

            assertThatCode(() -> assertThat(signer.validar(partes[0] + ".abc." + partes[2])).isEmpty())
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("assinatura com caractere inválido de base64 retorna vazio sem lançar")
        void assinaturaBase64Invalida() {
            String state = signer.assinar(UUID.randomUUID());
            String[] partes = state.split("\\.");

            assertThatCode(() -> assertThat(signer.validar(partes[0] + "." + partes[1] + ".!!!@@@")).isEmpty())
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("assinatura vazia retorna vazio sem lançar")
        void assinaturaVazia() {
            String state = signer.assinar(UUID.randomUUID());
            String[] partes = state.split("\\.");

            Optional<UUID> resultado = signer.validar(partes[0] + "." + partes[1] + ".");
            assertThat(resultado).isEmpty();
        }
    }
}
