package br.com.menthoros.backend.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("InviteToken: geração, hash e sigilo")
class InviteTokenTest {

    @Nested
    @DisplayName("generate")
    class Generate {

        @Test
        @DisplayName("32 bytes em base64url sem padding dão 43 caracteres seguros para URL")
        void formatoDoToken() {
            InviteToken token = InviteToken.generate();

            assertThat(token.value()).hasSize(43).matches("^[A-Za-z0-9_-]+$");
        }

        @Test
        @DisplayName("dois tokens nunca coincidem")
        void tokensDistintos() {
            Set<String> vistos = new HashSet<>();
            for (int i = 0; i < 1_000; i++) {
                assertThat(vistos.add(InviteToken.generate().value())).isTrue();
            }
        }
    }

    @Nested
    @DisplayName("hash")
    class Hash {

        @Test
        @DisplayName("é SHA-256 em hex minúsculo, estável para o mesmo valor")
        void hashEstavel() {
            InviteToken token = InviteToken.generate();

            assertThat(token.hash())
                    .hasSize(64)
                    .matches("^[0-9a-f]+$")
                    .isEqualTo(InviteToken.hashOf(token.value()));
        }

        @Test
        @DisplayName("valores diferentes têm hashes diferentes")
        void hashesDistintos() {
            assertThat(InviteToken.hashOf("a")).isNotEqualTo(InviteToken.hashOf("b"));
        }

        @Test
        @DisplayName("valor em branco é rejeitado — hash de vazio abriria um lookup acidental")
        void rejeitaBranco() {
            assertThatThrownBy(() -> InviteToken.hashOf(" "))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    @DisplayName("toString nunca expõe o valor")
    void toStringMascarado() {
        InviteToken token = InviteToken.generate();

        assertThat(token.toString()).doesNotContain(token.value()).contains("***");
    }
}
