package br.com.menthoros.backend;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;

/**
 * Keycloak descartável para os testes de provisionamento do auto-cadastro.
 *
 * <p>Por que um Keycloak de verdade e não um mock: a compensação do signup chama a admin API para
 * desfazer organização e usuário. Com gateway mockado, o teste prova que o serviço <em>pediu</em> a
 * remoção; só contra um servidor real dá para afirmar que o recurso <em>deixou de existir</em> —
 * que é o enunciado da task 4.2 ("não deixa conta utilizável").
 *
 * <p>{@code --features=organization} é obrigatório: a Organizations API
 * ({@code /admin/realms/{realm}/organizations}) não existe sem ela, e o gateway do produto é
 * construído inteiramente sobre ela. Sem a flag o container sobe e os endpoints respondem 404, o
 * que se parece com bug do produto.
 *
 * <p>Em modo dev o Keycloak usa banco em memória: o container morre com o estado, e cada classe de
 * teste recebe um realm limpo — importante aqui, porque estes testes criam e apagam usuários e
 * organizações de propósito.
 */
@TestConfiguration(proxyBeanMethods = false)
public class KeycloakTestcontainersConfiguration {

    public static final String ADMIN_USER = "admin";
    public static final String ADMIN_PASSWORD = "admin";
    /** Realm criado pelo teste; o {@code master} não tem organizações habilitadas. */
    public static final String REALM = "menthoros-test";

    private static final int PORTA_HTTP = 8080;

    @Bean(destroyMethod = "stop")
    public GenericContainer<?> keycloakContainer() {
        GenericContainer<?> container = new GenericContainer<>(
                DockerImageName.parse("quay.io/keycloak/keycloak:26.6"))
                .withExposedPorts(PORTA_HTTP)
                .withEnv("KC_BOOTSTRAP_ADMIN_USERNAME", ADMIN_USER)
                .withEnv("KC_BOOTSTRAP_ADMIN_PASSWORD", ADMIN_PASSWORD)
                .withEnv("KC_HEALTH_ENABLED", "true")
                .withCommand("start-dev", "--features=organization")
                // O health check vive em porta de management própria (9000) desde a 25; usar a
                // console do admin na 8080 evita expor uma segunda porta só para esperar o boot.
                .waitingFor(Wait.forHttp("/realms/master").forStatusCode(200))
                .withStartupTimeout(Duration.ofMinutes(3));
        container.start();
        return container;
    }
}
