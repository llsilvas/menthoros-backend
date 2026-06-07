# Segurança Multi-Tenancy - Checklist e Testes

> **Nota**: Este documento consolida o `SECURITY_CHECKLIST.md` e o `SECURITY_TESTS.md` originais.

**Projeto**: Menthoros
**Data**: 2025-10-13 (Atualizado)
**Versão**: 2.0.0

---

## 📋 Status Geral

| Categoria | Status | Completo |
|-----------|--------|----------|
| 1. Configuração Keycloak | 🟢 Completo | 100% ✅ |
| 2. Validação JWT | 🟢 Completo | 100% ✅ |
| 3. Isolamento de Tenant | 🟡 Parcial | 60% |
| 4. Sincronização de Usuários | 🟢 Completo | 100% ✅ |
| 5. Auditoria e Logs | 🟡 Parcial | 60% |
| 6. Testes de Segurança | 🔴 Crítico | 0% |

**Legenda:**
- 🟢 Completo: Implementado e testado
- 🟡 Parcial: Implementado mas necessita melhorias
- 🔴 Crítico: Não implementado, risco de segurança

**✅ MARCO ALCANÇADO**: Sistema Multi-Tenancy com Keycloak funcionando! Autenticação e sincronização de usuários operacionais.

---

## 1. Configuração do Keycloak

### 1.1 Realm Configuration
- [x] **Realm criado**: `menthoros-app`
  - Status: 🟢 Configurado e funcionando
  - Data: 2025-10-13
  - URL: http://localhost:8443/admin

### 1.2 Client Configuration
- [x] **Client criado**: `menthoros-backend`
  - Status: 🟢 Configurado e funcionando
  - Configurações aplicadas:
    - ✓ Client Protocol: `openid-connect`
    - ✓ Access Type: `confidential`
    - ✓ Direct Access Grants: **habilitado**
    - ✓ Client Secret: configurado
    - ✓ Valid Redirect URIs: configurado
  - Data: 2025-10-13

### 1.3 Roles Configuration
- [x] **Client Roles criadas**:
  - [x] ✓ `ADMIN` - Administrador da assessoria
  - [x] ✓ `TECNICO` - Técnico com acesso a atletas
  - [x] ✓ `VISUALIZADOR` - Acesso somente leitura
  - Status: 🟢 Completo
  - Data: 2025-10-13

### 1.4 Token Mappers
- [x] **Mapper: tenant_id**
  - Type: User Attribute
  - User Attribute: `tenant_id` (do Group Attribute)
  - Token Claim Name: `tenant_id`
  - Claim JSON Type: String
  - Add to ID token: ✓
  - Add to access token: ✓
  - Add to userinfo: ✓
  - Status: 🟢 **Configurado e testado**
  - Data: 2025-10-13
  - **VALIDADO**: JWT contém claim `tenant_id` corretamente

- [x] **Mapper: roles**
  - Type: User Client Role
  - Client ID: `menthoros-backend`
  - Token Claim Name: `roles`
  - Add to access token: ✓
  - Multivalued: ✓
  - Status: 🟢 **Configurado e testado**
  - Data: 2025-10-13
  - **VALIDADO**: JWT contém claim `roles` corretamente

### 1.5 Groups Configuration
- [x] **Estrutura de Groups**:
  - [x] ✓ Group criado: "Menthoros Default" (ou similar)
  - [x] ✓ Attribute `tenant_id` configurado: `6d95d34c-800c-4565-a4b4-386dd0a494ac`
  - Status: 🟢 Completo
  - Data: 2025-10-13
  - **VALIDADO**: tenant_id aparece no JWT

### 1.6 Test Users
- [x] **Usuário de teste criado**:
  - [x] ✓ Admin: username=`admin`, email=`lsilva.info@gmail.com`
  - [x] ✓ Adicionado ao Group com tenant_id
  - [x] ✓ Role ADMIN atribuída
  - Status: 🟢 Completo
  - Data: 2025-10-13
  - **Próximo**: Criar mais usuários para testes de isolamento

---

## 2. Validação JWT (Spring Security)

### 2.1 OAuth2 Resource Server
- [x] **application.yml configurado**
  - ✓ `spring.security.oauth2.resourceserver.jwt.issuer-uri`
  - ✓ `spring.security.oauth2.resourceserver.jwt.jwk-set-uri`
  - Status: 🟢 Completo
  - Localização: `src/main/resources/application.yml:124-125`

### 2.2 SecurityConfig
- [x] **SecurityConfig criado**
  - ✓ OAuth2 Resource Server habilitado
  - ✓ Stateless session management
  - ✓ JwtAuthenticationConverter configurado
  - ✓ JWT Filter configurado
  - Status: 🟢 **Completo e testado**
  - Localização: `src/main/java/com/menthoros/config/SecurityConfig.java`
  - Data: 2025-10-13
  - **VALIDADO**: Tokens JWT sendo validados corretamente

### 2.3 JWT Claims Validation
- [x] **Validação de claims implementada**:
  - [x] ✓ Valida presença de `tenant_id` (no JwtTenantFilter)
  - [x] ✓ Valida presença de `roles`
  - [x] ✓ Valida formato do `tenant_id` (UUID)
  - [x] ✓ Rejeita tokens sem tenant_id (HTTP 403)
  - Status: 🟢 **Implementado e testado**
  - Localização: `src/main/java/com/menthoros/security/JwtTenantFilter.java`
  - Data: 2025-10-13

### 2.4 Authorities Converter
- [x] **JwtAuthenticationConverter configurado**
  - ✓ Converte claim `roles` em authorities
  - ✓ Adiciona prefix `ROLE_`
  - Status: 🟢 Completo
  - Localização: `SecurityConfig.java:38-46`

---

## 3. Isolamento de Tenant

### 3.1 TenantContext
- [x] **TenantContext implementado**
  - ✓ ThreadLocal para armazenar tenant_id
  - ✓ Método clear() para limpeza
  - ✓ Método hasTenant() para verificação
  - Status: 🟡 Parcial
  - Localização: `src/main/java/com/menthoros/multitenancy/TenantContext.java`
  - **Problemas encontrados**:
    1. ❌ Métodos `setTenantId()` e `getTenantId()` são **private** (devem ser public)
    2. ❌ Typo: `CURRENT_TENTANT` deveria ser `CURRENT_TENANT`
  - Prioridade: Crítica

### 3.2 JwtTenantFilter
- [x] **Filter implementado**
  - ✓ Extrai tenant_id do JWT
  - ✓ Configura TenantContext
  - ✓ Limpa contexto no finally
  - Status: 🟡 Parcial
  - Localização: `src/main/java/com/menthoros/security/JwtTenantFilter.java`
  - **Problemas encontrados**:
    1. ❌ Não pode chamar `TenantContext.setTenantId()` (método é private)
    2. ⚠️ Não sincroniza usuário automaticamente
    3. ⚠️ Não valida se tenant existe no banco
    4. ⚠️ Log de warning mas continua processamento (deveria rejeitar?)
  - Prioridade: Crítica

### 3.3 Repository Filters
- [ ] **Filtros automáticos por tenant**:
  - [ ] `@Where` ou `@Filter` em entidades
  - [ ] Base repository com tenant filtering
  - [ ] Queries JPQL com tenant_id
  - Status: 🔴 Não implementado
  - Prioridade: Crítica
  - **RISCO**: Dados podem vazar entre tenants

### 3.4 Service Layer Validation
- [ ] **Validação em services**:
  - [ ] Verificar tenant_id em operações CRUD
  - [ ] Impedir acesso a recursos de outros tenants
  - [ ] Logs de tentativas de acesso indevido
  - Status: 🔴 Não implementado
  - Prioridade: Alta

### 3.5 Entity Validation
- [ ] **Entidades com tenant_id**:
  - [x] ✓ Usuario (via Assessoria FK)
  - [ ] Atleta
  - [ ] PlanoTreino
  - [ ] Treino
  - [ ] Outras entidades relacionadas
  - Status: 🟡 Parcial
  - Prioridade: Crítica

---

## 4. Sincronização de Usuários

### 4.1 UsuarioSyncService
- [x] **Service criado**:
  - [x] ✓ Sincroniza usuário do Keycloak no primeiro acesso
  - [x] ✓ Atualiza dados se mudaram no Keycloak
  - [x] ✓ Atualiza campo `ultima_sinc`
  - [x] ✓ Atualiza campo `ultimo_acesso`
  - Status: 🟢 **Implementado e testado**
  - Localização: `src/main/java/com/menthoros/services/UsuarioSyncService.java`
  - Data: 2025-10-13
  - **VALIDADO**: Usuário sincronizado corretamente no banco

### 4.2 Integração com JwtTenantFilter
- [x] **Filter chama sync service**:
  - [x] ✓ Após extrair tenant_id
  - [x] ✓ Antes de processar request
  - [x] ✓ Tratamento de erros (usuário sem tenant, etc)
  - Status: 🟢 **Implementado e testado**
  - Localização: `src/main/java/com/menthoros/security/JwtTenantFilter.java`
  - Data: 2025-10-13
  - **VALIDADO**: Sincronização automática funcionando

### 4.3 Background Sync Job
- [ ] **Job de sincronização**:
  - [ ] Scheduled task (ex: a cada hora)
  - [ ] Sincroniza usuários com `ultima_sinc` > 1 hora
  - [ ] Query: `UsuarioRepository.findUsuariosPendenteSincronizacao()` (query já criada)
  - Status: 🟡 Parcialmente implementado
  - Prioridade: Média
  - **Nota**: Query existe, falta criar o @Scheduled task

### 4.4 KeycloakAdminService
- [ ] **Service para Keycloak Admin API**:
  - [ ] Buscar dados do usuário por keycloak_id
  - [ ] Verificar groups do usuário
  - [ ] Verificar roles do usuário
  - [ ] Criar/atualizar usuários (admin)
  - Status: 🔴 Não implementado
  - Prioridade: Média
  - **Nota**: Não é crítico, pois sync via JWT está funcionando

---

## 5. Auditoria e Logs

### 5.1 Logs com Tenant ID
- [x] **TenantContext tem logs**:
  - ✓ Log ao configurar tenant
  - ✓ Log ao limpar tenant
  - ✓ Warning se tenant não configurado
  - Status: 🟢 Completo
  - Localização: `TenantContext.java:13,20,26`

- [x] **JwtTenantFilter tem logs**:
  - ✓ Log debug com tenant e URI
  - ✓ Warning se JWT sem tenant_id
  - Status: 🟢 Completo
  - Localização: `JwtTenantFilter.java:38,40`

### 5.2 MDC (Mapped Diagnostic Context)
- [ ] **Adicionar tenant_id ao MDC**:
  - [ ] Configurar no JwtTenantFilter
  - [ ] Incluir em todos os logs automaticamente
  - [ ] Formato: `[tenant: uuid]`
  - Status: 🔴 Não implementado
  - Prioridade: Média
  - Benefício: Rastreabilidade total

### 5.3 Audit Trail
- [ ] **Tabela de auditoria**:
  - [ ] tb_audit_log
  - [ ] Campos: tenant_id, usuario_id, acao, entidade, timestamp
  - [ ] AuditService para registrar ações
  - Status: 🔴 Não implementado
  - Prioridade: Baixa (futuro)

### 5.4 Logs Estruturados
- [ ] **JSON Logging**:
  - [ ] Logback com JSON encoder
  - [ ] Campos estruturados: tenant_id, user_id, action, resource
  - Status: 🔴 Não implementado
  - Prioridade: Baixa

---

## 6. Testes de Segurança

### 6.1 Testes de Isolamento de Tenant

#### 6.1.1 Test: Usuário não acessa dados de outro tenant

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

#### 6.1.2 Test: Queries sempre filtram por tenant_id

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

#### 6.1.3 Test: Update e Delete só afetam dados do tenant

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

**Status**: 🔴 Não implementado | **Prioridade**: Crítica

### 6.2 Testes de Validação JWT

#### 6.2.1 Test: JWT válido é aceito

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

#### 6.2.2 Test: JWT expirado é rejeitado

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

#### 6.2.3 Test: JWT sem tenant_id é rejeitado

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

#### 6.2.4 Test: JWT com tenant_id inválido é rejeitado

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

#### 6.2.5 Test: JWT com signature inválida é rejeitado

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

**Status**: 🔴 Não implementado | **Prioridade**: Alta

### 6.3 Testes de Autorização

#### 6.3.1 Test: ADMIN pode gerenciar usuários

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

#### 6.3.2 Test: TECNICO pode gerenciar atletas

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

#### 6.3.3 Test: VISUALIZADOR só lê dados

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

**Status**: 🔴 Não implementado | **Prioridade**: Alta

### 6.4 Testes de Sincronização

#### 6.4.1 Test: Primeiro login cria usuário

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

#### 6.4.2 Test: Login subsequente atualiza ultima_sinc

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

#### 6.4.3 Test: Mudança no Keycloak é refletida

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

**Status**: 🔴 Não implementado | **Prioridade**: Média

### 6.5 Testes de Performance

#### 6.5.1 Test: Carga com múltiplos tenants

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

#### 6.5.2 Test: ThreadLocal não vaza entre requests

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

**Status**: 🔴 Não implementado | **Prioridade**: Baixa

---

## 7. Como Executar os Testes

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

## 8. Mock Annotations

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

## 9. Documentação Adicional

### 9.1 Guias
- [x] **MULTI_TENANCY_INTEGRATION_GUIDE.md** - Arquitetura, configuração Keycloak, exemplos de código
- [x] **MULTI_TENANCY_IMPLEMENTATION_ROADMAP.md** - Sprints e tarefas organizadas
- [x] **README.md** - Seção Keycloak, instruções de setup, URLs importantes

### 9.2 Scripts de Automação
- [ ] **Scripts para Keycloak** (Não implementado, Prioridade: Média):
  - Script de criação de realm
  - Script de criação de client
  - Script de criação de roles
  - Script de criação de mappers

---

## 🚨 Ações Críticas Imediatas

### ✅ Prioridade 1 - BLOQUEADORES (COMPLETO!)

1. ✅ **Corrigir TenantContext** - **FEITO!**
   - ✓ Métodos `setTenantId()` e `getTenantId()` tornados públicos
   - ✓ Typo corrigido: `CURRENT_TENTANT` → `CURRENT_TENANT`
   - ✓ Adicionado método `getRequiredTenantId()`
   - Data: 2025-10-13

2. ✅ **Corrigir SecurityConfig** - **FEITO!**
   - ✓ OAuth2 Resource Server configurado
   - ✓ JwtAuthenticationConverter configurado
   - ✓ JwtTenantFilter integrado
   - Data: 2025-10-13

3. ✅ **Criar UsuarioSyncService** - **FEITO!**
   - ✓ Sincronização básica implementada
   - ✓ Integrado com JwtTenantFilter
   - ✓ Método `syncUsuarioFromJwt()` implementado e testado
   - Data: 2025-10-13

4. ✅ **Validação de tenant_id no JWT** - **FEITO!**
   - ✓ Validação implementada no JwtTenantFilter
   - ✓ Tokens sem tenant_id são rejeitados (HTTP 403)
   - ✓ Validação de formato UUID
   - Data: 2025-10-13

### Prioridade 2 - CRÍTICOS (Fazer esta semana) ⚠️

1. **Implementar filtros de tenant em repositories** (2h) 🔴 **URGENTE**
   - Criar BaseRepository com tenant filtering
   - Atualizar todos os repositories
   - Adicionar `@Where(clause = "tenant_id = :tenantId")` nas entidades
   - **RISCO CRÍTICO**: Sem isso, dados podem vazar entre tenants!

2. **Criar testes de isolamento de tenant** (3h)
   - Testes de vazamento de dados
   - Testes de queries cross-tenant
   - Testes de autorização

3. ✅ **Configurar Keycloak** - **FEITO!**
   - ✓ Realm, client, roles, mappers criados
   - ✓ Group de teste criado
   - ✓ Usuário de teste configurado
   - Data: 2025-10-13

### Prioridade 3 - IMPORTANTES (Fazer próximas 2 semanas)

1. **Implementar KeycloakAdminService** (4h)
2. **Adicionar MDC logging** (1h)
3. **Criar scripts de automação Keycloak** (2h)
4. **Documentar testes de segurança** (2h)

---

## 📊 Métricas de Segurança

| Métrica | Atual | Meta | Status |
|---------|-------|------|--------|
| Cobertura de testes de segurança | 0% | 80% | 🔴 |
| Isolamento de tenant verificado | Não | Sim | 🔴 |
| Logs auditáveis | Parcial | Total | 🟡 |
| Validação JWT completa | 50% | 100% | 🟡 |
| Sincronização automática | Não | Sim | 🔴 |

---

## 📋 Métricas de Sucesso dos Testes

| Teste | Meta | Status |
|-------|------|--------|
| Isolamento de tenant | 100% aprovado | ⏳ Pendente |
| Validação JWT | 100% aprovado | ⏳ Pendente |
| Autorização por role | 100% aprovado | ⏳ Pendente |
| Sincronização usuário | 100% aprovado | ⏳ Pendente |
| Performance (1000 req/s) | < 500ms p95 | ⏳ Pendente |

---

## 📝 Notas de Segurança

### Riscos Atuais

1. **CRÍTICO - Vazamento de dados entre tenants**
   - Repositories não filtram por tenant_id
   - Qualquer usuário pode acessar dados de outros tenants
   - **AÇÃO**: Implementar filtros de tenant urgentemente

2. **CRÍTICO - JWT sem tenant_id aceito**
   - Sistema não valida presença de tenant_id
   - Pode causar NullPointerException
   - **AÇÃO**: Adicionar validação customizada de JWT

3. **CRÍTICO - TenantContext inacessível**
   - Métodos private impedem uso correto
   - JwtTenantFilter não funciona corretamente
   - **AÇÃO**: Corrigir visibilidade dos métodos

4. **ALTO - Usuários não sincronizados**
   - tb_usuario pode estar vazia
   - Relacionamentos FK podem falhar
   - **AÇÃO**: Implementar UsuarioSyncService

5. **MÉDIO - Falta auditoria completa**
   - Difícil rastrear acessos e mudanças
   - **AÇÃO**: Adicionar MDC e audit trail

### Próximos Passos

1. Corrigir bloqueadores críticos (Prioridade 1)
2. Implementar testes de segurança
3. Configurar Keycloak completamente
4. Realizar testes de penetração básicos
5. Code review focado em segurança

---

**Última atualização**: 2025-10-13
**Responsável**: Equipe Menthoros
**Próxima revisão**: Após implementação das ações críticas
