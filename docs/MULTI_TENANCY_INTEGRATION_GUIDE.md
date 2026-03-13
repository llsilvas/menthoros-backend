# 🏢 Guia Completo de Implementação Multi-Tenancy com Keycloak - Menthoros

## 📋 Índice
1. [Visão Geral](#visão-geral)
2. [Arquitetura Multi-Tenancy com Keycloak](#arquitetura-multi-tenancy-com-keycloak)
3. [Modelo de Dados](#modelo-de-dados)
4. [Estratégias de Isolamento](#estratégias-de-isolamento)
5. [Configuração do Keycloak](#configuração-do-keycloak)
6. [Implementação Passo a Passo](#implementação-passo-a-passo)
7. [Autenticação OAuth2/OIDC](#autenticação-oauth2oidc)
8. [Tenant Context](#tenant-context)
9. [Sincronização com Keycloak](#sincronização-com-keycloak)
10. [Filtros e Interceptors](#filtros-e-interceptors)
11. [Testes Multi-Tenant](#testes-multi-tenant)
12. [Performance e Escalabilidade](#performance-e-escalabilidade)
13. [Segurança](#segurança)

---

## 📖 Visão Geral

### O que é Multi-Tenancy com Keycloak?

Multi-tenancy permite que **múltiplas assessorias esportivas** (tenants) usem a mesma instância do Menthoros, mantendo seus dados **completamente isolados** e **seguros**, com autenticação e autorização gerenciadas centralmente pelo **Keycloak**.

### Por que Keycloak?

- ✅ **Autenticação Centralizada**: SSO, MFA, Social Login
- ✅ **Gestão de Usuários**: Interface administrativa completa
- ✅ **OAuth2/OIDC**: Padrão da indústria
- ✅ **Escalável**: Suporta milhares de usuários/tenants
- ✅ **Auditoria**: Logs completos de autenticação
- ✅ **Customização**: Temas, fluxos de login personalizados

### Benefícios

- ✅ **Segurança**: Keycloak gerencia senhas, tokens, MFA
- ✅ **Manutenibilidade**: Sem código de autenticação próprio
- ✅ **Conformidade**: LGPD, GDPR out-of-the-box
- ✅ **UX**: Login único entre aplicações
- ✅ **Tempo de mercado**: Implementação mais rápida

---

## 🏗️ Arquitetura Multi-Tenancy com Keycloak

### Visão Geral da Arquitetura

```
┌─────────────────────────────────────────────────────────────────┐
│                         KEYCLOAK                                │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │  Realm: menthoros-app                                    │  │
│  │                                                           │  │
│  │  ├─ Group: assessoria-corridasserra                      │  │
│  │  │  ├─ Attributes: tenant_id=uuid1                       │  │
│  │  │  ├─ Users:                                            │  │
│  │  │  │  ├─ joao@corridasserra.com (Role: ADMIN)          │  │
│  │  │  │  └─ maria@corridasserra.com (Role: TECNICO)       │  │
│  │  │                                                        │  │
│  │  ├─ Group: assessoria-teamx                              │  │
│  │  │  ├─ Attributes: tenant_id=uuid2                       │  │
│  │  │  └─ Users:                                            │  │
│  │  │     └─ carlos@teamx.com (Role: ADMIN)                │  │
│  │                                                           │  │
│  │  ├─ Client: menthoros-backend                            │  │
│  │  │  ├─ Roles: ADMIN, TECNICO, VISUALIZADOR              │  │
│  │  │  └─ Mappers: tenant_id, groups, roles                │  │
│  │                                                           │  │
│  │  └─ Client: menthoros-frontend                           │  │
│  │     └─ Public client (SPA)                               │  │
│  └──────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
                            ↓ JWT Token
                            ↓ { sub, email, tenant_id, groups, roles }
┌─────────────────────────────────────────────────────────────────┐
│                    MENTHOROS BACKEND                            │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │  Spring Security OAuth2 Resource Server                  │  │
│  │  ├─ JWT Validation (JWK)                                 │  │
│  │  ├─ Extract tenant_id from token                         │  │
│  │  ├─ Set TenantContext                                    │  │
│  │  └─ Sync user to tb_usuario (if needed)                 │  │
│  └──────────────────────────────────────────────────────────┘  │
│                            ↓                                    │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │  PostgreSQL                                              │  │
│  │  ├─ tb_assessoria (tenant master)                        │  │
│  │  ├─ tb_usuario (cache from Keycloak)                     │  │
│  │  └─ tb_atleta, tb_treino_* (filtered by tenant_id)      │  │
│  └──────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
```

### Estratégia: Realm Único + Groups (Recomendado)

| Aspecto | Detalhes |
|---------|----------|
| **Realm** | Um realm `menthoros-app` para toda plataforma |
| **Groups** | Cada assessoria = um group com attribute `tenant_id` |
| **Users** | Usuários pertencem a um ou mais groups |
| **Roles** | Client roles: ADMIN, TECNICO, VISUALIZADOR |
| **Sync** | tb_usuario sincroniza dados do Keycloak (cache) |

---

## 🗄️ Modelo de Dados

### **Entidade: Assessoria (Tenant)**

```java
package com.menthoros.entity;

import com.menthoros.enums.PlanoAssessoria;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "tb_assessoria",
    indexes = {
        @Index(name = "idx_assessoria_dominio", columnList = "dominio", unique = true),
        @Index(name = "idx_assessoria_keycloak_group", columnList = "keycloak_group_id", unique = true),
        @Index(name = "idx_assessoria_ativo", columnList = "ativo")
    })
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Assessoria {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "nome", nullable = false, length = 200)
    private String nome;

    @Column(name = "dominio", unique = true, length = 100)
    private String dominio; // Ex: "corridasserra", "teamx"

    // ===== INTEGRAÇÃO KEYCLOAK =====
    @Column(name = "keycloak_group_id", unique = true, length = 100)
    private String keycloakGroupId; // ID do Group no Keycloak

    @Column(name = "keycloak_realm", length = 100)
    private String keycloakRealm = "menthoros-app";

    // ===== PLANO E COBRANÇA =====
    @Enumerated(EnumType.STRING)
    @Column(name = "plano", nullable = false)
    private PlanoAssessoria plano;

    // ===== CONFIGURAÇÕES, ENDEREÇO, FEATURES, etc =====
    // (Mesmos campos da versão anterior)

    @Column(name = "ativo", nullable = false)
    private Boolean ativo = true;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "assessoria", fetch = FetchType.LAZY)
    private List<Usuario> usuarios;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
```

### **Entidade: Usuario (Cache do Keycloak)**

```java
package com.menthoros.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "tb_usuario",
    indexes = {
        @Index(name = "idx_usuario_keycloak_id", columnList = "keycloak_id", unique = true),
        @Index(name = "idx_usuario_email", columnList = "email"),
        @Index(name = "idx_usuario_tenant", columnList = "tenant_id"),
        @Index(name = "idx_usuario_tenant_ativo", columnList = "tenant_id, ativo")
    })
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Usuario {

    @Id
    @Column(name = "id")
    private UUID id; // Mesmo ID do Keycloak (sub claim)

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Assessoria assessoria;

    // ===== DADOS SINCRONIZADOS DO KEYCLOAK =====
    @Column(name = "keycloak_id", unique = true, nullable = false, length = 100)
    private String keycloakId; // Sub do JWT

    @Column(name = "email", nullable = false, length = 100)
    private String email;

    @Column(name = "nome", nullable = false, length = 200)
    private String nome;

    @Column(name = "sobrenome", length = 200)
    private String sobrenome;

    @Column(name = "email_verificado")
    private Boolean emailVerificado = false;

    @Column(name = "avatar_url", length = 500)
    private String avatarUrl;

    // ===== METADADOS LOCAIS =====
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    private UserRole role;

    @Column(name = "ativo", nullable = false)
    private Boolean ativo = true;

    @Column(name = "ultimo_acesso")
    private LocalDateTime ultimoAcesso;

    @Column(name = "ultima_sinc")
    private LocalDateTime ultimaSinc; // Última sincronização com Keycloak

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}

enum UserRole {
    ADMIN,      // Admin da assessoria
    TECNICO,    // Técnico
    VISUALIZADOR // Apenas visualiza
}
```

---

## 🔧 Implementação Passo a Passo

### **ETAPA 1: Configurar Keycloak**

#### 1.1 Iniciar Keycloak via Docker

```yaml
# docker-compose.yml
version: '3.8'

services:
  keycloak:
    image: quay.io/keycloak/keycloak:23.0.0
    container_name: menthoros-keycloak
    environment:
      KEYCLOAK_ADMIN: admin
      KEYCLOAK_ADMIN_PASSWORD: admin123
      KC_DB: postgres
      KC_DB_URL: jdbc:postgresql://postgres:5432/keycloak
      KC_DB_USERNAME: keycloak
      KC_DB_PASSWORD: keycloak123
    command: start-dev
    ports:
      - "8080:8080"
    depends_on:
      - postgres
    networks:
      - menthoros-network

  postgres:
    image: postgres:15-alpine
    container_name: menthoros-postgres
    environment:
      POSTGRES_DB: menthoros-multi
      POSTGRES_USER: menthoros
      POSTGRES_PASSWORD: menthoros123
    ports:
      - "5432:5432"
    volumes:
      - postgres-data:/var/lib/postgresql/data
    networks:
      - menthoros-network

volumes:
  postgres-data:

networks:
  menthoros-network:
```

```bash
docker-compose up -d
```

#### 1.2 Acessar Admin Console

- URL: http://localhost:8080
- Login: admin / admin123

#### 1.3 Criar Realm

1. Hover "master" → Create realm
2. Name: `menthoros-app`
3. Enable

#### 1.4 Criar Client (Backend)

1. Clients → Create client
2. **Client ID**: `menthoros-backend`
3. **Client authentication**: ON
4. **Authorization**: ON
5. **Valid redirect URIs**: `http://localhost:8098/*`
6. **Web origins**: `*`
7. Save

#### 1.5 Criar Client Roles

1. menthoros-backend → Roles → Create role
2. Criar: `ADMIN`, `TECNICO`, `VISUALIZADOR`

#### 1.6 Configurar Token Mappers

1. menthoros-backend → Client scopes → menthoros-backend-dedicated
2. Add mapper → By configuration → User Attribute
   - **Name**: tenant_id
   - **User Attribute**: tenant_id
   - **Token Claim Name**: tenant_id
   - **Claim JSON Type**: String
   - **Add to ID token**: ON
   - **Add to access token**: ON

3. Add mapper → By configuration → Group Membership
   - **Name**: groups
   - **Token Claim Name**: groups
   - **Full group path**: OFF

#### 1.7 Criar Groups (Assessorias)

1. Groups → Create group
2. **Name**: `assessoria-corridasserra`
3. Attributes → Add:
   - Key: `tenant_id`
   - Value: (UUID da assessoria no banco)

Repetir para cada assessoria.

---

### **ETAPA 2: Adicionar Dependências**

#### pom.xml

```xml
<dependencies>
    <!-- Spring Security OAuth2 Resource Server -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-oauth2-resource-server</artifactId>
    </dependency>

    <!-- Spring Security -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-security</artifactId>
    </dependency>

    <!-- Keycloak Admin Client -->
    <dependency>
        <groupId>org.keycloak</groupId>
        <artifactId>keycloak-admin-client</artifactId>
        <version>23.0.0</version>
    </dependency>

    <!-- Validação -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-validation</artifactId>
    </dependency>
</dependencies>
```

---

### **ETAPA 3: Configurar application.yml**

```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: ${KEYCLOAK_ISSUER_URI:http://localhost:8080/realms/menthoros-app}
          jwk-set-uri: ${KEYCLOAK_JWK_URI:http://localhost:8080/realms/menthoros-app/protocol/openid-connect/certs}

keycloak:
  realm: ${KEYCLOAK_REALM:menthoros-app}
  auth-server-url: ${KEYCLOAK_URL:http://localhost:8080}
  admin:
    username: ${KEYCLOAK_ADMIN_USER:admin}
    password: ${KEYCLOAK_ADMIN_PASSWORD:admin123}
    client-id: ${KEYCLOAK_CLIENT_ID:menthoros-backend}
    client-secret: ${KEYCLOAK_CLIENT_SECRET}
```

---

### **ETAPA 4: Migration do Banco de Dados**

#### V8__Create_keycloak_multi_tenancy.sql

```sql
-- src/main/resources/db/migration/V8__Create_keycloak_multi_tenancy.sql

-- =============================================
-- ADICIONAR keycloak_group_id NA tb_assessoria
-- =============================================
ALTER TABLE tb_assessoria
    ADD COLUMN IF NOT EXISTS keycloak_group_id VARCHAR(100) UNIQUE,
    ADD COLUMN IF NOT EXISTS keycloak_realm VARCHAR(100) DEFAULT 'menthoros-app';

CREATE INDEX IF NOT EXISTS idx_assessoria_keycloak_group
    ON tb_assessoria (keycloak_group_id);

COMMENT ON COLUMN tb_assessoria.keycloak_group_id IS 'ID do grupo no Keycloak';
COMMENT ON COLUMN tb_assessoria.keycloak_realm IS 'Realm do Keycloak';

-- =============================================
-- TABELA: tb_usuario (Cache do Keycloak)
-- =============================================
CREATE TABLE IF NOT EXISTS tb_usuario
(
    id                UUID PRIMARY KEY,
    tenant_id         UUID        NOT NULL REFERENCES tb_assessoria (id) ON DELETE CASCADE,
    keycloak_id       VARCHAR(100) UNIQUE NOT NULL,
    email             VARCHAR(100) NOT NULL,
    nome              VARCHAR(200) NOT NULL,
    sobrenome         VARCHAR(200),
    email_verificado  BOOLEAN              DEFAULT FALSE,
    avatar_url        VARCHAR(500),
    role              VARCHAR(20) NOT NULL DEFAULT 'TECNICO',
    ativo             BOOLEAN     NOT NULL DEFAULT TRUE,
    ultimo_acesso     TIMESTAMP,
    ultima_sinc       TIMESTAMP,
    created_at        TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP,

    CONSTRAINT chk_role CHECK (role IN ('ADMIN', 'TECNICO', 'VISUALIZADOR'))
);

CREATE INDEX idx_usuario_keycloak_id ON tb_usuario (keycloak_id);
CREATE INDEX idx_usuario_email ON tb_usuario (email);
CREATE INDEX idx_usuario_tenant ON tb_usuario (tenant_id);
CREATE INDEX idx_usuario_tenant_ativo ON tb_usuario (tenant_id, ativo);
CREATE INDEX idx_usuario_tenant_role ON tb_usuario (tenant_id, role);

-- Trigger para updated_at
CREATE OR REPLACE FUNCTION update_usuario_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trigger_usuario_updated_at
    BEFORE UPDATE ON tb_usuario
    FOR EACH ROW
    EXECUTE FUNCTION update_usuario_updated_at();
```

---

### **ETAPA 5: Spring Security Configuration**

#### SecurityConfig.java

```java
package com.menthoros.config;

import com.menthoros.security.JwtTenantFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtTenantFilter jwtTenantFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/public/**", "/swagger-ui/**", "/api-docs/**").permitAll()
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
            )
            .addFilterAfter(jwtTenantFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter grantedAuthoritiesConverter = new JwtGrantedAuthoritiesConverter();
        grantedAuthoritiesConverter.setAuthoritiesClaimName("roles");
        grantedAuthoritiesConverter.setAuthorityPrefix("ROLE_");

        JwtAuthenticationConverter jwtAuthenticationConverter = new JwtAuthenticationConverter();
        jwtAuthenticationConverter.setJwtGrantedAuthoritiesConverter(grantedAuthoritiesConverter);
        return jwtAuthenticationConverter;
    }
}
```

---

### **ETAPA 6: JWT Tenant Filter**

#### JwtTenantFilter.java

```java
package com.menthoros.security;

import com.menthoros.multitenancy.TenantContext;
import com.menthoros.services.UsuarioSyncService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtTenantFilter extends OncePerRequestFilter {

    private final UsuarioSyncService usuarioSyncService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

            if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
                // Extrair tenant_id do JWT
                String tenantIdStr = jwt.getClaimAsString("tenant_id");

                if (tenantIdStr != null) {
                    UUID tenantId = UUID.fromString(tenantIdStr);
                    TenantContext.setTenantId(tenantId);

                    // Sincronizar usuário (se necessário)
                    String keycloakId = jwt.getSubject();
                    usuarioSyncService.syncUserFromJwt(jwt, tenantId);

                    log.debug("Tenant {} configurado para requisição {}", tenantId, request.getRequestURI());
                } else {
                    log.warn("JWT sem tenant_id: {}", jwt.getSubject());
                }
            }

            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }
}
```

---

### **ETAPA 7: Serviço de Sincronização**

#### UsuarioSyncService.java

```java
package com.menthoros.services;

import com.menthoros.entity.Assessoria;
import com.menthoros.entity.Usuario;
import com.menthoros.enums.UserRole;
import com.menthoros.repository.AssessoriaRepository;
import com.menthoros.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UsuarioSyncService {

    private final UsuarioRepository usuarioRepository;
    private final AssessoriaRepository assessoriaRepository;

    @Transactional
    public Usuario syncUserFromJwt(Jwt jwt, UUID tenantId) {
        String keycloakId = jwt.getSubject();
        String email = jwt.getClaimAsString("email");
        String nome = jwt.getClaimAsString("given_name");
        String sobrenome = jwt.getClaimAsString("family_name");
        Boolean emailVerificado = jwt.getClaimAsBoolean("email_verified");

        // Extrair role do JWT
        List<String> roles = jwt.getClaimAsStringList("roles");
        UserRole role = extractRole(roles);

        return usuarioRepository.findByKeycloakId(keycloakId)
            .map(usuario -> updateUsuario(usuario, email, nome, sobrenome, emailVerificado, role))
            .orElseGet(() -> createUsuario(keycloakId, email, nome, sobrenome, emailVerificado, role, tenantId));
    }

    private Usuario updateUsuario(Usuario usuario, String email, String nome,
                                   String sobrenome, Boolean emailVerificado, UserRole role) {
        usuario.setEmail(email);
        usuario.setNome(nome);
        usuario.setSobrenome(sobrenome);
        usuario.setEmailVerificado(emailVerificado);
        usuario.setRole(role);
        usuario.setUltimoAcesso(LocalDateTime.now());
        usuario.setUltimaSinc(LocalDateTime.now());

        log.debug("Usuário {} atualizado do Keycloak", email);
        return usuarioRepository.save(usuario);
    }

    private Usuario createUsuario(String keycloakId, String email, String nome,
                                   String sobrenome, Boolean emailVerificado,
                                   UserRole role, UUID tenantId) {
        Assessoria assessoria = assessoriaRepository.findById(tenantId)
            .orElseThrow(() -> new RuntimeException("Assessoria não encontrada: " + tenantId));

        Usuario usuario = Usuario.builder()
            .id(UUID.fromString(keycloakId))
            .keycloakId(keycloakId)
            .email(email)
            .nome(nome)
            .sobrenome(sobrenome)
            .emailVerificado(emailVerificado)
            .role(role)
            .ativo(true)
            .assessoria(assessoria)
            .ultimoAcesso(LocalDateTime.now())
            .ultimaSinc(LocalDateTime.now())
            .build();

        log.info("Novo usuário {} sincronizado do Keycloak", email);
        return usuarioRepository.save(usuario);
    }

    private UserRole extractRole(List<String> roles) {
        if (roles == null || roles.isEmpty()) {
            return UserRole.VISUALIZADOR;
        }

        if (roles.contains("ADMIN")) return UserRole.ADMIN;
        if (roles.contains("TECNICO")) return UserRole.TECNICO;
        return UserRole.VISUALIZADOR;
    }
}
```

---

### **ETAPA 8: Tenant Context**

#### TenantContext.java

```java
package com.menthoros.multitenancy;

import lombok.extern.slf4j.Slf4j;
import java.util.UUID;

@Slf4j
public class TenantContext {

    private static final ThreadLocal<UUID> CURRENT_TENANT = new InheritableThreadLocal<>();

    public static void setTenantId(UUID tenantId) {
        log.debug("Setting tenant context: {}", tenantId);
        CURRENT_TENANT.set(tenantId);
    }

    public static UUID getTenantId() {
        UUID tenantId = CURRENT_TENANT.get();
        if (tenantId == null) {
            log.warn("Nenhum tenant configurado no contexto!");
        }
        return tenantId;
    }

    public static void clear() {
        log.debug("Clearing tenant context");
        CURRENT_TENANT.remove();
    }

    public static boolean hasTenant() {
        return CURRENT_TENANT.get() != null;
    }
}
```

---

## ✅ Checklist de Segurança

- [ ] **Keycloak configurado**: Realm, clients, roles, mappers
- [ ] **JWT validation**: Spring Security valida tokens
- [ ] **Tenant isolation**: TenantContext configurado em todas requests
- [ ] **User sync**: tb_usuario sincroniza automaticamente
- [ ] **Groups attributes**: tenant_id mapeado corretamente
- [ ] **Logs auditáveis**: tenant_id em todos os logs
- [ ] **Testes de vazamento**: Dados não cruzam entre tenants

---

## 📚 Referências

- [Keycloak Documentation](https://www.keycloak.org/documentation)
- [Spring Security OAuth2 Resource Server](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/index.html)
- [Multi-Tenancy with Keycloak](https://www.keycloak.org/docs/latest/server_admin/#_per_realm_admin_permissions)

---

**Autor**: Claude Code
**Data**: 2025-10-13
**Versão**: 2.0.0 (Keycloak)