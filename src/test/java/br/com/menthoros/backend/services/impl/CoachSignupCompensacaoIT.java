package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.KeycloakTestcontainersConfiguration;
import br.com.menthoros.backend.TestcontainersConfiguration;
import br.com.menthoros.backend.dto.input.CoachSignupInputDto;
import br.com.menthoros.backend.entity.SignupProvisioning;
import br.com.menthoros.backend.enums.SignupProvisioningStatus;
import br.com.menthoros.backend.repository.AssessoriaRepository;
import br.com.menthoros.backend.repository.SignupProvisioningRepository;
import br.com.menthoros.backend.repository.UsuarioRepository;
import br.com.menthoros.backend.services.CoachSignupService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatusCode;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.GenericContainer;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

/**
 * Task 4.2 — falhas injetadas depois de cada recurso criado, contra um Keycloak <b>real</b>
 * (Testcontainers), provando que a compensação não deixa conta utilizável.
 *
 * <p>Complementa {@code CoachSignupServiceImplTest}, que já cobre a mesma matriz com gateway
 * mockado. A diferença não é redundância: com mock, a asserção possível é "o serviço pediu a
 * remoção"; aqui a asserção é "o recurso não existe mais no servidor", consultada pela admin API
 * depois do fato. É a distinção entre testar a intenção e testar o efeito — e o risco desta change
 * (provisionamento sem transação entre Postgres e Keycloak) mora inteiro no efeito.
 *
 * <p>O ponto de injeção é o insert do {@code Usuario} local: é o passo mais tardio que ainda tem
 * recursos do Keycloak criados atrás dele (organização, usuário, role e vínculo de membro), então
 * é o que exercita a pilha de compensação inteira.
 */
@SpringBootTest
@ActiveProfiles("integration")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class CoachSignupCompensacaoIT {

    /*
     * Campo estático inicializado na carga da classe, e não pelo @Bean da configuração: o
     * @DynamicPropertySource roda DEPOIS do @BeforeAll do JUnit, então um container criado lá
     * chegaria nulo na preparação do realm. Pelo mesmo motivo o KeycloakTestcontainersConfiguration
     * não entra no @Import — como @Bean, ele subiria um SEGUNDO container.
     */
    private static final GenericContainer<?> keycloak =
            new KeycloakTestcontainersConfiguration().keycloakContainer();

    @Autowired
    private CoachSignupService service;
    @Autowired
    private AssessoriaRepository assessoriaRepository;
    @Autowired
    private SignupProvisioningRepository provisioningRepository;

    @MockitoSpyBean
    private UsuarioRepository usuarioRepository;

    @DynamicPropertySource
    static void keycloakProperties(DynamicPropertyRegistry registry) {
        String url = "http://" + keycloak.getHost() + ":" + keycloak.getMappedPort(8080);
        registry.add("keycloak.admin.server-url", () -> url);
        registry.add("keycloak.admin.realm", () -> KeycloakTestcontainersConfiguration.REALM);
        registry.add("keycloak.admin.token-realm", () -> "master");
        registry.add("keycloak.admin.client-id", () -> "admin-cli");
        registry.add("keycloak.admin.username", () -> KeycloakTestcontainersConfiguration.ADMIN_USER);
        registry.add("keycloak.admin.password", () -> KeycloakTestcontainersConfiguration.ADMIN_PASSWORD);
        registry.add("app.coach-signup.enabled", () -> "true");
    }

    @BeforeAll
    static void prepararRealm() {
        String base = "http://" + keycloak.getHost() + ":" + keycloak.getMappedPort(8080);
        String token = tokenAdmin(base);
        RestClient client = RestClient.builder().baseUrl(base)
                .defaultHeader("Authorization", "Bearer " + token).build();

        // organizationsEnabled é atributo de REALM: a feature do servidor só habilita a API.
        client.post().uri("/admin/realms").contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "realm", KeycloakTestcontainersConfiguration.REALM,
                        "enabled", true,
                        "organizationsEnabled", true))
                .retrieve().toBodilessEntity();

        // O provisionamento atribui a role de realm TECNICO; sem ela o passo falharia por motivo
        // alheio ao que este teste investiga.
        client.post().uri("/admin/realms/{r}/roles", KeycloakTestcontainersConfiguration.REALM)
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .body(Map.of("name", "TECNICO"))
                .retrieve().toBodilessEntity();
    }

    @Test
    @DisplayName("controle positivo: a consulta ao Keycloak encontra um usuário que existe")
    void consultaDeUsuarioEncontraQuemExiste() {
        // Sem este teste, as asserções isEmpty() dos outros dois provariam nada: uma query errada
        // devolve lista vazia sempre, e a compensação passaria no teste sem ter apagado coisa alguma.
        String email = "controle-" + UUID.randomUUID().toString().substring(0, 8) + "@menthoros.test";
        admin().post().uri("/admin/realms/{r}/users", KeycloakTestcontainersConfiguration.REALM)
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .body(Map.of("username", email, "email", email, "enabled", true))
                .retrieve().toBodilessEntity();

        assertThat(usuariosNoKeycloak(email)).hasSize(1);
    }

    @Test
    @DisplayName("falha no insert do Usuario local remove organização e usuário do Keycloak de verdade")
    void compensacaoApagaRecursosNoKeycloak() {
        String slug = "qa-comp-" + UUID.randomUUID().toString().substring(0, 8);
        CoachSignupInputDto input = signup(slug);
        doThrow(new IllegalStateException("QA 4.2: falha injetada no insert do Usuario local"))
                .when(usuarioRepository).save(any());

        String chave = UUID.randomUUID().toString();
        assertThatThrownBy(() -> service.cadastrar(input, chave, "qa-corr"))
                .isInstanceOf(RuntimeException.class);

        // 1. Nada utilizável no Keycloak: sem usuário, ninguém autentica; sem organização, não há tenant.
        assertThat(usuariosNoKeycloak(input.email()))
                .as("usuário do Keycloak deve ter sido removido pela compensação")
                .isEmpty();
        assertThat(organizacoesNoKeycloak(slug))
                .as("organização do Keycloak deve ter sido removida pela compensação")
                .isEmpty();

        // 2. Nada órfão no lado local.
        assertThat(assessoriaRepository.findByDominio(slug))
                .as("assessoria local deve ter sido apagada junto")
                .isEmpty();

        // 3. O rastro registra o desfecho como compensado, não como reconciliação pendente.
        SignupProvisioning rastro = provisioningRepository.findByIdempotencyKey(chave).orElseThrow();
        assertThat(rastro.getStatus()).isEqualTo(SignupProvisioningStatus.FAILED);
        assertThat(rastro.getErrorDetail()).isNotBlank();
        // O id da assessoria apagada permanece de propósito (V76 derrubou a FK): é perícia, e sem
        // ele o rastro não diz qual tenant chegou a existir.
        assertThat(rastro.getAssessoriaId())
                .as("id da assessoria deve sobreviver à remoção, para perícia")
                .isNotNull();
    }

    @Test
    @DisplayName("compensação frustrada por recurso já removido não deixa o cadastro em ACTIVE")
    void compensacaoComRecursoAusente() {
        String slug = "qa-recon-" + UUID.randomUUID().toString().substring(0, 8);
        CoachSignupInputDto input = signup(slug);
        doThrow(new IllegalStateException("QA 4.2: falha injetada no insert do Usuario local"))
                .when(usuarioRepository).save(any());

        String chave = UUID.randomUUID().toString();
        assertThatThrownBy(() -> service.cadastrar(input, chave, "qa-corr-2"))
                .isInstanceOf(RuntimeException.class);

        SignupProvisioning rastro = provisioningRepository.findByIdempotencyKey(chave).orElseThrow();
        assertThat(rastro.getStatus())
                .as("qualquer desfecho de falha serve, menos deixar como ativo/parcial")
                .isIn(SignupProvisioningStatus.FAILED, SignupProvisioningStatus.RECONCILIATION_REQUIRED);
        assertThat(usuariosNoKeycloak(input.email())).isEmpty();
    }

    // ── Helpers ─────────────────────────────────────────────────────────────────

    private CoachSignupInputDto signup(String slug) {
        return new CoachSignupInputDto(
                "QA Compensacao", slug + "@menthoros.test", "Senha#Forte#2026",
                "QA " + slug, slug, null);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> usuariosNoKeycloak(String email) {
        return admin().get()
                .uri(u -> u.path("/admin/realms/{r}/users").queryParam("email", email).build(
                        KeycloakTestcontainersConfiguration.REALM))
                .retrieve().body(List.class);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> organizacoesNoKeycloak(String slug) {
        return admin().get()
                .uri(u -> u.path("/admin/realms/{r}/organizations").queryParam("search", slug).build(
                        KeycloakTestcontainersConfiguration.REALM))
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> { })
                .body(List.class);
    }

    private RestClient admin() {
        String base = "http://" + keycloak.getHost() + ":" + keycloak.getMappedPort(8080);
        return RestClient.builder().baseUrl(base)
                .defaultHeader("Authorization", "Bearer " + tokenAdmin(base)).build();
    }

    @SuppressWarnings("unchecked")
    private static String tokenAdmin(String base) {
        Map<String, Object> resposta = RestClient.create().post()
                .uri(base + "/realms/master/protocol/openid-connect/token")
                .contentType(org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED)
                .body("grant_type=password&client_id=admin-cli&username="
                        + KeycloakTestcontainersConfiguration.ADMIN_USER
                        + "&password=" + KeycloakTestcontainersConfiguration.ADMIN_PASSWORD)
                .retrieve().body(Map.class);
        return (String) resposta.get("access_token");
    }
}
