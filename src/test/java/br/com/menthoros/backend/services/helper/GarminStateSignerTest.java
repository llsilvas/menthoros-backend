package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.config.external.GarminProperties;
import br.com.menthoros.backend.config.external.IntervalsIcuProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/** D2 de garmin-connection: o state é o que liga o callback público a um atleta. */
class GarminStateSignerTest {

    private static final String SECRET = "segredo-garmin-bem-comprido";
    private static final Instant AGORA = Instant.parse("2026-10-26T12:00:00Z");

    private final GarminStateSigner signer = signerEm(AGORA, SECRET);

    private static GarminStateSigner signerEm(Instant instante, String secret) {
        GarminProperties props = new GarminProperties();
        props.setClientSecret(secret);
        return new GarminStateSigner(props, Clock.fixed(instante, ZoneOffset.UTC));
    }

    @Nested
    @DisplayName("assinar")
    class Assinar {

        @Test
        @DisplayName("dois states do mesmo atleta no mesmo segundo diferem (nonce)")
        void mesmoSegundoDiferem() {
            UUID atletaId = UUID.randomUUID();

            assertThat(signer.assinar(atletaId)).isNotEqualTo(signer.assinar(atletaId));
        }

        @Test
        @DisplayName("é seguro para URL e não contém o segredo")
        void urlSafeSemSegredo() {
            String state = signer.assinar(UUID.randomUUID());

            assertThat(state).doesNotContain(SECRET, "+", "/", "=", "|", " ");
        }
    }

    @Nested
    @DisplayName("validar")
    class Validar {

        @Test
        @DisplayName("round-trip devolve o atletaId")
        void roundTrip() {
            UUID atletaId = UUID.randomUUID();

            assertThat(signer.validar(signer.assinar(atletaId))).contains(atletaId);
        }

        @Test
        @DisplayName("rejeita state do IntervalsIcuStateSigner, mesmo com o mesmo segredo")
        void rejeitaStateDoIntervalsIcu() {
            IntervalsIcuProperties icu = new IntervalsIcuProperties();
            icu.setClientSecret(SECRET);
            String stateIcu = new IntervalsIcuStateSigner(icu, Clock.fixed(AGORA, ZoneOffset.UTC))
                    .assinar(UUID.randomUUID());

            assertThat(signer.validar(stateIcu)).isEmpty();
        }

        @Test
        @DisplayName("rejeita state sem o prefixo garmin")
        void rejeitaSemPrefixo() {
            String state = signer.assinar(UUID.randomUUID());
            String semPrefixo = state.substring(state.indexOf('.') + 1);

            assertThat(signer.validar(semPrefixo)).isEmpty();
            assertThat(signer.validar("outro" + state.substring(state.indexOf('.')))).isEmpty();
        }

        @Test
        @DisplayName("rejeita MAC alterado")
        void rejeitaMacAlterado() {
            String state = signer.assinar(UUID.randomUUID());
            String adulterado = state.substring(0, state.length() - 1)
                    + (state.endsWith("A") ? "B" : "A");

            assertThat(signer.validar(adulterado)).isEmpty();
        }

        @Test
        @DisplayName("rejeita atletaId trocado mantendo o MAC")
        void rejeitaAtletaTrocado() {
            String[] p = signer.assinar(UUID.randomUUID()).split("\\.");
            String forjado = String.join(".", p[0], UUID.randomUUID().toString(), p[2], p[3], p[4]);

            assertThat(signer.validar(forjado)).isEmpty();
        }

        @Test
        @DisplayName("rejeita state assinado com outro segredo")
        void rejeitaOutroSegredo() {
            String outro = signerEm(AGORA, "outro-segredo").assinar(UUID.randomUUID());

            assertThat(signer.validar(outro)).isEmpty();
        }

        @Test
        @DisplayName("TTL: 9 min aceita, 11 min rejeita")
        void ttl() {
            String state = signer.assinar(UUID.randomUUID());

            assertThat(signerEm(AGORA.plus(Duration.ofMinutes(9)), SECRET).validar(state)).isPresent();
            assertThat(signerEm(AGORA.plus(Duration.ofMinutes(11)), SECRET).validar(state)).isEmpty();
        }

        @Test
        @DisplayName("skew: 20 s no futuro aceita, 5 min no futuro rejeita")
        void skew() {
            String perto = signerEm(AGORA.plusSeconds(20), SECRET).assinar(UUID.randomUUID());
            String longe = signerEm(AGORA.plus(Duration.ofMinutes(5)), SECRET).assinar(UUID.randomUUID());

            assertThat(signer.validar(perto)).isPresent();
            assertThat(signer.validar(longe)).isEmpty();
        }

        @Test
        @DisplayName("entrada hostil nunca lança")
        void entradaHostil() {
            for (String entrada : new String[]{null, "", " ", "garmin", "garmin.a.b.c.d", "a.b.c.d.e.f",
                    "garmin.nao-uuid.123.nonce.mac", "garmin." + UUID.randomUUID() + ".abc.nonce.!!!"}) {
                assertThatCode(() -> assertThat(signer.validar(entrada)).isEmpty()).doesNotThrowAnyException();
            }
        }
    }
}
