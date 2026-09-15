package br.com.menthoros.backend.services.helper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

@DisplayName("SchemaVersionResolver")
class SchemaVersionResolverTest {

    @Nested
    @DisplayName("usaV2")
    class UsaV2 {

        @Test
        @DisplayName("tenant no CSV da allowlist — usa v2")
        void tenantNoCsvUsaV2() {
            UUID tenant = UUID.randomUUID();
            var resolver = new SchemaVersionResolver(tenant.toString());

            assertThat(resolver.usaV2(tenant)).isTrue();
        }

        @Test
        @DisplayName("tenant fora do CSV — usa v1 (default)")
        void tenantForaDoCsvUsaV1() {
            var resolver = new SchemaVersionResolver(UUID.randomUUID().toString());

            assertThat(resolver.usaV2(UUID.randomUUID())).isFalse();
        }

        @Test
        @DisplayName("CSV vazio (default) — todos usam v1")
        void csvVazioTodosUsamV1() {
            var resolver = new SchemaVersionResolver("");

            assertThat(resolver.usaV2(UUID.randomUUID())).isFalse();
        }

        @Test
        @DisplayName("múltiplos tenants no CSV, com espaços — todos resolvem")
        void multiplosTenantsComEspacosResolvemTodos() {
            UUID t1 = UUID.randomUUID();
            UUID t2 = UUID.randomUUID();
            var resolver = new SchemaVersionResolver(" " + t1 + " , " + t2 + " ");

            assertThat(resolver.usaV2(t1)).isTrue();
            assertThat(resolver.usaV2(t2)).isTrue();
            assertThat(resolver.usaV2(UUID.randomUUID())).isFalse();
        }

        @Test
        @DisplayName("tenantId nulo — nunca usa v2, sem lançar")
        void tenantIdNuloNuncaUsaV2() {
            var resolver = new SchemaVersionResolver(UUID.randomUUID().toString());

            assertThat(resolver.usaV2(null)).isFalse();
        }

        @Test
        @DisplayName("CSV com token inválido não derruba o startup — ignora o token, mantém os válidos")
        void csvComTokenInvalidoNaoDerrubaStartup() {
            UUID valido = UUID.randomUUID();

            assertThatCode(() -> new SchemaVersionResolver("nao-e-um-uuid," + valido))
                    .doesNotThrowAnyException();

            var resolver = new SchemaVersionResolver("nao-e-um-uuid," + valido);
            assertThat(resolver.usaV2(valido)).isTrue();
        }
    }
}
