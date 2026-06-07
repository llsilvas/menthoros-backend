# Testes de Segurança - Multi-Tenancy

**Projeto**: Menthoros
**Data**: 2025-10-13
**Versão**: 1.0.0

---

## 📋 Sumário

1. [Testes de Isolamento de Tenant](#1-testes-de-isolamento-de-tenant)
2. [Testes de Validação JWT](#2-testes-de-validação-jwt)
3. [Testes de Autorização](#3-testes-de-autorização)
4. [Testes de Sincronização](#4-testes-de-sincronização)
5. [Testes de Performance](#5-testes-de-performance)
6. [Como Executar](#como-executar)

---

## 1. Testes de Isolamento de Tenant

### 1.1 Test: Usuário não acessa dados de outro tenant

**Objetivo**: Verificar que um usuário do Tenant A não consegue acessar dados do Tenant B

```java
@SpringBootTest
@AutoConfigureMockMvc
class TenantIsolationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @WithMockKeycloakUser(tenantId = "tenant-a-uuid", roles = "ADMIN")
    void deveBloquearAcessoADadosDeOutroTenant() throws Exception {
        // Tenta buscar atleta do Tenant B
        UUID atletaTenantB = UUID.fromString("atleta-do-tenant-b-uuid");

        mockMvc.perform(get("/api/atletas/{id}", atletaTenantB))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Acesso negado"));
    }

    @Test
    @WithMockKeycloakUser(tenantId = "tenant-a-uuid", roles = "ADMIN")
    void deveRetornarApenasAtletasDoProprioTenant() throws Exception {
        mockMvc.perform(get("/api/atletas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[*].tenantId").value(everyItem(equalTo("tenant-a-uuid"))));
    }
}
```

### 1.2 Test: Queries sempre filtram por tenant_id

```java
@DataJpaTest
class TenantFilterRepositoryTest {

    @Autowired
    private AtletaRepository atletaRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void deveRetornarApenasAtletasDoTenantCorreto() {
        // Arrange
        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();

        Atleta atletaTenantA = criarAtleta("João", tenantA);
        Atleta atletaTenantB = criarAtleta("Maria", tenantB);

        entityManager.persist(atletaTenantA);
        entityManager.persist(atletaTenantB);
        entityManager.flush();

        // Configura contexto para Tenant A
        TenantContext.setTenantId(tenantA);

        // Act
        List<Atleta> atletas = atletaRepository.findAll();

        // Assert
        assertThat(atletas).hasSize(1);
        assertThat(atletas.get(0).getNome()).isEqualTo("João");
        assertThat(atletas.get(0).getTenantId()).isEqualTo(tenantA);

        TenantContext.clear();
    }

    @Test
    void deveLancarExcecaoSeNaoHouverTenantConfigurado() {
        // Arrange
        TenantContext.clear();

        // Act & Assert
        assertThatThrownBy(() -> atletaRepository.findAll())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Tenant não configurado");
    }
}
```

### 1.3 Test: Update e Delete só afetam dados do tenant

```java
@Test
@WithMockKeycloakUser(tenantId = "tenant-a-uuid", roles = "ADMIN")
void deveBloquearUpdateEmAtletaDeOutroTenant() throws Exception {
    UUID atletaTenantB = UUID.fromString("atleta-do-tenant-b-uuid");

    String atletaAtualizado = """
        {
            "nome": "Nome Alterado",
            "email": "alterado@example.com"
        }
        """;

    mockMvc.perform(put("/api/atletas/{id}", atletaTenantB)
            .contentType(MediaType.APPLICATION_JSON)
            .content(atletaAtualizado))
            .andExpect(status().isForbidden());
}

@Test
@WithMockKeycloakUser(tenantId = "tenant-a-uuid", roles = "ADMIN")
void deveBloquearDeleteEmAtletaDeOutroTenant() throws Exception {
    UUID atletaTenantB = UUID.fromString("atleta-do-tenant-b-uuid");

    mockMvc.perform(delete("/api/atletas/{id}", atletaTenantB))
            .andExpect(status().isForbidden());
}
```

---

## 2. Testes de Validação JWT

### 2.1 Test: JWT válido é aceito

```java
@SpringBootTest
@AutoConfigureMockMvc
class JwtValidationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void deveAceitarJWTValido() throws Exception {
        String validToken = createValidJWT(
            "user-uuid",
            "tenant-uuid",
            List.of("ADMIN"),
            Instant.now().plus(1, ChronoUnit.HOURS)
        );

        mockMvc.perform(get("/api/atletas")
                .header("Authorization", "Bearer " + validToken))
                .andExpect(status().isOk());
    }
}
```

### 2.2 Test: JWT expirado é rejeitado

```java
@Test
void deveRejeitarJWTExpirado() throws Exception {
    String expiredToken = createValidJWT(
        "user-uuid",
        "tenant-uuid",
        List.of("ADMIN"),
        Instant.now().minus(1, ChronoUnit.HOURS) // Expirado
    );

    mockMvc.perform(get("/api/atletas")
            .header("Authorization", "Bearer " + expiredToken))
            .andExpect(status().isUnauthorized());
}
```

### 2.3 Test: JWT sem tenant_id é rejeitado

```java
@Test
void deveRejeitarJWTSemTenantId() throws Exception {
    String tokenSemTenant = createJWTWithoutTenantId(
        "user-uuid",
        List.of("ADMIN"),
        Instant.now().plus(1, ChronoUnit.HOURS)
    );

    mockMvc.perform(get("/api/atletas")
            .header("Authorization", "Bearer " + tokenSemTenant))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error").value(containsString("tenant_id")));
}
```

### 2.4 Test: JWT com tenant_id inválido é rejeitado

```java
@Test
void deveRejeitarJWTComTenantIdInvalido() throws Exception {
    String tokenComTenantInvalido = createValidJWT(
        "user-uuid",
        "tenant-invalido-nao-uuid", // Não é um UUID válido
        List.of("ADMIN"),
        Instant.now().plus(1, ChronoUnit.HOURS)
    );

    mockMvc.perform(get("/api/atletas")
            .header("Authorization", "Bearer " + tokenComTenantInvalido))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error").value(containsString("inválido")));
}
```

### 2.5 Test: JWT com signature inválida é rejeitado

```java
@Test
void deveRejeitarJWTComSignatureInvalida() throws Exception {
    String tokenComSignatureInvalida = createJWTWithInvalidSignature(
        "user-uuid",
        "tenant-uuid",
        List.of("ADMIN")
    );

    mockMvc.perform(get("/api/atletas")
            .header("Authorization", "Bearer " + tokenComSignatureInvalida))
            .andExpect(status().isUnauthorized());
}
```

---

## 3. Testes de Autorização

### 3.1 Test: ADMIN pode gerenciar usuários

```java
@SpringBootTest
@AutoConfigureMockMvc
class AuthorizationTest {

    @Test
    @WithMockKeycloakUser(tenantId = "tenant-uuid", roles = "ADMIN")
    void adminPodeCriarUsuario() throws Exception {
        String novoUsuario = """
            {
                "nome": "Novo Técnico",
                "email": "tecnico@example.com",
                "role": "TECNICO"
            }
            """;

        mockMvc.perform(post("/api/usuarios")
                .contentType(MediaType.APPLICATION_JSON)
                .content(novoUsuario))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockKeycloakUser(tenantId = "tenant-uuid", roles = "ADMIN")
    void adminPodeDesativarUsuario() throws Exception {
        UUID usuarioId = UUID.randomUUID();

        mockMvc.perform(patch("/api/usuarios/{id}/desativar", usuarioId))
                .andExpect(status().isOk());
    }
}
```

### 3.2 Test: TECNICO pode gerenciar atletas

```java
@Test
@WithMockKeycloakUser(tenantId = "tenant-uuid", roles = "TECNICO")
void tecnicoPodeCriarAtleta() throws Exception {
    String novoAtleta = """
        {
            "nome": "João Silva",
            "email": "joao@example.com",
            "dataNascimento": "1990-05-15"
        }
        """;

    mockMvc.perform(post("/api/atletas")
            .contentType(MediaType.APPLICATION_JSON)
            .content(novoAtleta))
            .andExpect(status().isCreated());
}

@Test
@WithMockKeycloakUser(tenantId = "tenant-uuid", roles = "TECNICO")
void tecnicoPodeGerarPlano() throws Exception {
    UUID atletaId = UUID.randomUUID();

    mockMvc.perform(post("/api/planos/gerar/{atletaId}", atletaId))
            .andExpect(status().isCreated());
}

@Test
@WithMockKeycloakUser(tenantId = "tenant-uuid", roles = "TECNICO")
void tecnicoNaoPodeCriarUsuario() throws Exception {
    String novoUsuario = """
        {
            "nome": "Outro Técnico",
            "email": "outro@example.com"
        }
        """;

    mockMvc.perform(post("/api/usuarios")
            .contentType(MediaType.APPLICATION_JSON)
            .content(novoUsuario))
            .andExpect(status().isForbidden());
}
```

### 3.3 Test: VISUALIZADOR só lê dados

```java
@Test
@WithMockKeycloakUser(tenantId = "tenant-uuid", roles = "VISUALIZADOR")
void visualizadorPodeLerAtletas() throws Exception {
    mockMvc.perform(get("/api/atletas"))
            .andExpect(status().isOk());
}

@Test
@WithMockKeycloakUser(tenantId = "tenant-uuid", roles = "VISUALIZADOR")
void visualizadorNaoPodeCriarAtleta() throws Exception {
    String novoAtleta = """
        {
            "nome": "João Silva",
            "email": "joao@example.com"
        }
        """;

    mockMvc.perform(post("/api/atletas")
            .contentType(MediaType.APPLICATION_JSON)
            .content(novoAtleta))
            .andExpect(status().isForbidden());
}

@Test
@WithMockKeycloakUser(tenantId = "tenant-uuid", roles = "VISUALIZADOR")
void visualizadorNaoPodeEditarAtleta() throws Exception {
    UUID atletaId = UUID.randomUUID();

    String atletaAtualizado = """
        {
            "nome": "Nome Alterado"
        }
        """;

    mockMvc.perform(put("/api/atletas/{id}", atletaId)
            .contentType(MediaType.APPLICATION_JSON)
            .content(atletaAtualizado))
            .andExpect(status().isForbidden());
}
```

---

## 4. Testes de Sincronização

### 4.1 Test: Primeiro login cria usuário

```java
@SpringBootTest
class UsuarioSyncTest {

    @Autowired
    private UsuarioSyncService usuarioSyncService;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Test
    void deveCriarUsuarioNoPrimeiroLogin() {
        // Arrange
        Jwt jwt = createMockJWT("new-user-uuid", "tenant-uuid", "João", "Silva", "joao@example.com");
        UUID tenantId = UUID.fromString("tenant-uuid");

        // Act
        Usuario usuario = usuarioSyncService.syncUsuarioFromJwt(jwt, tenantId);

        // Assert
        assertThat(usuario).isNotNull();
        assertThat(usuario.getKeycloakId()).isEqualTo("new-user-uuid");
        assertThat(usuario.getEmail()).isEqualTo("joao@example.com");
        assertThat(usuario.getNome()).isEqualTo("João");
        assertThat(usuario.getSobrenome()).isEqualTo("Silva");
        assertThat(usuario.getAssessoria().getId()).isEqualTo(tenantId);

        // Verifica se foi persistido
        Optional<Usuario> salvo = usuarioRepository.findByKeycloakId("new-user-uuid");
        assertThat(salvo).isPresent();
    }
}
```

### 4.2 Test: Login subsequente atualiza ultima_sinc

```java
@Test
void deveAtualizarUltimaSincEmLoginSubsequente() throws InterruptedException {
    // Arrange - Primeiro login
    Jwt jwt = createMockJWT("user-uuid", "tenant-uuid", "João", "Silva", "joao@example.com");
    UUID tenantId = UUID.fromString("tenant-uuid");

    Usuario primeiroLogin = usuarioSyncService.syncUsuarioFromJwt(jwt, tenantId);
    LocalDateTime primeiraSinc = primeiroLogin.getUltimaSinc();

    Thread.sleep(100); // Garante diferença de tempo

    // Act - Segundo login
    Usuario segundoLogin = usuarioSyncService.syncUsuarioFromJwt(jwt, tenantId);

    // Assert
    assertThat(segundoLogin.getUltimaSinc()).isAfter(primeiraSinc);
}
```

### 4.3 Test: Mudança no Keycloak é refletida

```java
@Test
void deveAtualizarDadosQuandoMudaremNoKeycloak() {
    // Arrange - Dados originais
    Jwt jwtOriginal = createMockJWT("user-uuid", "tenant-uuid", "João", "Silva", "joao@example.com");
    UUID tenantId = UUID.fromString("tenant-uuid");

    usuarioSyncService.syncUsuarioFromJwt(jwtOriginal, tenantId);

    // Act - Usuário mudou nome no Keycloak
    Jwt jwtAtualizado = createMockJWT("user-uuid", "tenant-uuid", "João Carlos", "Silva Santos", "joao@example.com");
    Usuario usuarioAtualizado = usuarioSyncService.syncUsuarioFromJwt(jwtAtualizado, tenantId);

    // Assert
    assertThat(usuarioAtualizado.getNome()).isEqualTo("João Carlos");
    assertThat(usuarioAtualizado.getSobrenome()).isEqualTo("Silva Santos");
}
```

---

## 5. Testes de Performance

### 5.1 Test: Carga com múltiplos tenants

```java
@SpringBootTest
class PerformanceTest {

    @Test
    void deveLidarComMultiplosTenantsConcorrentes() throws InterruptedException {
        int numTenants = 10;
        int requestsPorTenant = 100;

        ExecutorService executor = Executors.newFixedThreadPool(20);
        CountDownLatch latch = new CountDownLatch(numTenants * requestsPorTenant);

        for (int tenantId = 0; tenantId < numTenants; tenantId++) {
            final UUID tenant = UUID.randomUUID();

            for (int i = 0; i < requestsPorTenant; i++) {
                executor.submit(() -> {
                    try {
                        // Simula request com JWT
                        mockMvc.perform(get("/api/atletas")
                                .header("Authorization", "Bearer " + createJWT(tenant)))
                                .andExpect(status().isOk());
                    } catch (Exception e) {
                        fail("Request falhou: " + e.getMessage());
                    } finally {
                        latch.countDown();
                    }
                });
            }
        }

        boolean completed = latch.await(30, TimeUnit.SECONDS);
        assertThat(completed).isTrue();

        executor.shutdown();
    }
}
```

### 5.2 Test: ThreadLocal não vaza entre requests

```java
@Test
void tenantContextNaoDeveVazarEntreRequests() throws Exception {
    UUID tenant1 = UUID.randomUUID();
    UUID tenant2 = UUID.randomUUID();

    // Request 1
    mockMvc.perform(get("/api/atletas")
            .header("Authorization", "Bearer " + createJWT(tenant1)))
            .andExpect(status().isOk());

    // Verifica que contexto foi limpo
    assertThat(TenantContext.hasTenant()).isFalse();

    // Request 2 (outro tenant)
    mockMvc.perform(get("/api/atletas")
            .header("Authorization", "Bearer " + createJWT(tenant2)))
            .andExpect(status().isOk());

    // Verifica que contexto foi limpo novamente
    assertThat(TenantContext.hasTenant()).isFalse();
}
```

---

## Como Executar

### Executar todos os testes de segurança

```bash
# Todos os testes de segurança (tag @SecurityTest)
mvn test -Dgroups=SecurityTest

# Apenas testes de isolamento de tenant
mvn test -Dtest=TenantIsolationTest

# Apenas testes de JWT
mvn test -Dtest=JwtValidationTest

# Todos os testes
mvn test
```

### Executar testes de integração com Keycloak real

```bash
# Sobe Keycloak com docker-compose
docker-compose up -d keycloak

# Aguarda Keycloak estar pronto
docker-compose logs -f keycloak | grep "Keycloak.*started"

# Executa testes de integração
mvn verify -Pintegration-tests

# Para o Keycloak
docker-compose down
```

### Coverage Report

```bash
# Gera relatório de cobertura
mvn clean test jacoco:report

# Abre relatório
open target/site/jacoco/index.html
```

---

## Mock Annotations

### @WithMockKeycloakUser

Anotação customizada para simular usuário autenticado via Keycloak em testes:

```java
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@WithSecurityContext(factory = WithMockKeycloakUserSecurityContextFactory.class)
public @interface WithMockKeycloakUser {
    String keycloakId() default "test-user-uuid";
    String tenantId();
    String email() default "test@example.com";
    String[] roles() default {"ADMIN"};
}
```

### Factory Implementation

```java
public class WithMockKeycloakUserSecurityContextFactory
        implements WithSecurityContextFactory<WithMockKeycloakUser> {

    @Override
    public SecurityContext createSecurityContext(WithMockKeycloakUser annotation) {
        SecurityContext context = SecurityContextHolder.createEmptyContext();

        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", annotation.keycloakId());
        claims.put("tenant_id", annotation.tenantId());
        claims.put("email", annotation.email());
        claims.put("roles", Arrays.asList(annotation.roles()));

        Jwt jwt = Jwt.withTokenValue("mock-token")
                .header("alg", "RS256")
                .claims(c -> c.putAll(claims))
                .build();

        JwtAuthenticationToken auth = new JwtAuthenticationToken(jwt);
        context.setAuthentication(auth);

        return context;
    }
}
```

---

## Métricas de Sucesso

| Teste | Meta | Status |
|-------|------|--------|
| Isolamento de tenant | 100% aprovado | ⏳ Pendente |
| Validação JWT | 100% aprovado | ⏳ Pendente |
| Autorização por role | 100% aprovado | ⏳ Pendente |
| Sincronização usuário | 100% aprovado | ⏳ Pendente |
| Performance (1000 req/s) | < 500ms p95 | ⏳ Pendente |

---

**Última atualização**: 2025-10-13
**Responsável**: Equipe Menthoros