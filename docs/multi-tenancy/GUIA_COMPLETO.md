# Multi-Tenancy - Guia Completo de Implementação

> **Nota**: Este documento foi consolidado a partir de:
> - `MULTI_TENANCY_INTEGRATION_GUIDE.md`
> - `MULTI_TENANCY_IMPLEMENTATION_ROADMAP.md`
>
> Combina tanto a arquitetura e implementação técnica quanto o roadmap detalhado de desenvolvimento em 12 sprints.

---

## Índice

1. [Visão Geral](#visão-geral)
2. [Por que Keycloak](#por-que-keycloak)
3. [Arquitetura Multi-Tenancy com Keycloak](#arquitetura-multi-tenancy-com-keycloak)
4. [Modelo de Dados](#modelo-de-dados)
5. [Configuração do Keycloak](#configuração-do-keycloak)
6. [Implementação Técnica](#implementação-técnica)
7. [Spring Security e OAuth2](#spring-security-e-oauth2)
8. [Tenant Context e Sincronização](#tenant-context-e-sincronização)
9. [Testes e Segurança](#testes-e-segurança)
10. [Roadmap de Implementação](#roadmap-de-implementação)
11. [Checklist Final](#checklist-final)

---

## Visão Geral

### O que é Multi-Tenancy com Keycloak?

Multi-tenancy permite que **múltiplas assessorias esportivas** (tenants) usem a mesma instância do Menthoros, mantendo seus dados **completamente isolados** e **seguros**, com autenticação e autorização gerenciadas centralmente pelo **Keycloak**.

### Por que Keycloak?

- **Autenticação Centralizada**: SSO, MFA, Social Login
- **Gestão de Usuários**: Interface administrativa completa
- **OAuth2/OIDC**: Padrão da indústria
- **Escalável**: Suporta milhares de usuários/tenants
- **Auditoria**: Logs completos de autenticação
- **Customização**: Temas, fluxos de login personalizados

### Benefícios

- **Segurança**: Keycloak gerencia senhas, tokens, MFA
- **Manutenibilidade**: Sem código de autenticação próprio
- **Conformidade**: LGPD, GDPR out-of-the-box
- **UX**: Login único entre aplicações
- **Tempo de mercado**: Implementação mais rápida

---

## Arquitetura Multi-Tenancy com Keycloak

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

## Modelo de Dados

### Entidade: Assessoria (Tenant)

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

### Entidade: Usuario (Cache do Keycloak)

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

## Configuração do Keycloak

### Iniciando Keycloak via Docker

#### docker-compose.yml

```yaml
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

Iniciar com:
```bash
docker-compose up -d
```

### Passos de Configuração Manual

#### 1. Acessar Admin Console
- URL: http://localhost:8080
- Login: admin / admin123

#### 2. Criar Realm
1. Hover "master" → Create realm
2. Name: `menthoros-app`
3. Enable

#### 3. Criar Client (Backend)
1. Clients → Create client
2. **Client ID**: `menthoros-backend`
3. **Client authentication**: ON
4. **Authorization**: ON
5. **Valid redirect URIs**: `http://localhost:8098/*`
6. **Web origins**: `*`
7. Save

#### 4. Criar Client Roles
1. menthoros-backend → Roles → Create role
2. Criar: `ADMIN`, `TECNICO`, `VISUALIZADOR`

#### 5. Configurar Token Mappers
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

#### 6. Criar Groups (Assessorias)
1. Groups → Create group
2. **Name**: `assessoria-corridasserra`
3. Attributes → Add:
   - Key: `tenant_id`
   - Value: (UUID da assessoria no banco)

Repetir para cada assessoria.

---

## Implementação Técnica

### Adicionar Dependências

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

### Configurar application.yml

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

### Migration do Banco de Dados

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

## Spring Security e OAuth2

### SecurityConfig.java

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

## Tenant Context e Sincronização

### JwtTenantFilter.java

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

### TenantContext.java

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

### UsuarioSyncService.java

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

## Testes e Segurança

### Checklist de Segurança

- [ ] **Keycloak configurado**: Realm, clients, roles, mappers
- [ ] **JWT validation**: Spring Security valida tokens
- [ ] **Tenant isolation**: TenantContext configurado em todas requests
- [ ] **User sync**: tb_usuario sincroniza automaticamente
- [ ] **Groups attributes**: tenant_id mapeado corretamente
- [ ] **Logs auditáveis**: tenant_id em todos os logs
- [ ] **Testes de vazamento**: Dados não cruzam entre tenants

---

## Roadmap de Implementação

### Cronograma Sugerido

O roadmap abaixo está estruturado em 12 sprints de uma semana cada, totalizando aproximadamente 3 meses para implementação completa.

---

### SPRINT 1 - Configurar Keycloak e Infraestrutura (Semana 1-2)

**Objetivo**: Keycloak rodando e configurado para multi-tenancy

#### Tarefas:
- [ ] **1.1** Configurar docker-compose com Keycloak + PostgreSQL
  - Keycloak 23.0.0
  - PostgreSQL 15 para Keycloak
  - PostgreSQL 15 para Menthoros (pode ser o mesmo)

- [ ] **1.2** Acessar Admin Console e criar Realm
  - Realm: `menthoros-app`
  - Configurar email settings (SMTP)
  - Customizar tema (opcional)

- [ ] **1.3** Criar Client para Backend
  - Client ID: `menthoros-backend`
  - Client authentication: ON
  - Client secret configurado
  - Valid redirect URIs configuradas

- [ ] **1.4** Criar Client para Frontend (SPA)
  - Client ID: `menthoros-frontend`
  - Client authentication: OFF (public)
  - Standard flow enabled
  - Direct access grants enabled

- [ ] **1.5** Criar Client Roles
  - ADMIN, TECNICO, VISUALIZADOR
  - Atribuir composite roles se necessário

- [ ] **1.6** Configurar Token Mappers
  - User Attribute Mapper: tenant_id
  - Group Membership Mapper: groups
  - Audience Mapper: para validar token
  - Roles Mapper: mapear client roles

- [ ] **1.7** Testar obtenção de tokens
  ```bash
  curl -X POST http://localhost:8080/realms/menthoros-app/protocol/openid-connect/token \
    -d "client_id=menthoros-backend" \
    -d "client_secret=..." \
    -d "grant_type=password" \
    -d "username=admin@test.com" \
    -d "password=123456"
  ```

**Entregáveis**: Keycloak funcionando e emitindo tokens JWT

---

### SPRINT 2 - Fundação do Multi-Tenancy (Semana 2-3)

**Objetivo**: Criar estrutura de dados e migrations

#### Tarefas:
- [ ] **2.1** Adicionar dependências ao `pom.xml`
  - `spring-boot-starter-oauth2-resource-server`
  - `spring-boot-starter-security`
  - `keycloak-admin-client` (23.0.0)
  - `spring-boot-starter-validation`

- [ ] **2.2** Criar/Atualizar entidade `Assessoria`
  - Arquivo: `src/main/java/com/menthoros/entity/Assessoria.java`
  - Adicionar campos: `keycloakGroupId`, `keycloakRealm`
  - Manter campos existentes: plano, features, endereço, etc.

- [ ] **2.3** Criar enum `PlanoAssessoria` (se não existir)
  - Arquivo: `src/main/java/com/menthoros/enums/PlanoAssessoria.java`
  - Valores: BASIC, PRO, ENTERPRISE

- [ ] **2.4** Criar/Atualizar entidade `Usuario`
  - Arquivo: `src/main/java/com/menthoros/entity/Usuario.java`
  - **IMPORTANTE**: Esta é uma entidade de CACHE do Keycloak
  - Campos: id (UUID do Keycloak), keycloakId, email, nome, role, tenant_id
  - Campos de sync: ultimaSinc, emailVerificado

- [ ] **2.5** Criar enum `UserRole`
  - Arquivo: `src/main/java/com/menthoros/enums/UserRole.java`
  - Valores: ADMIN, TECNICO, VISUALIZADOR

- [ ] **2.6** Criar migration V8
  - Arquivo: `src/main/resources/db/migration/V8__Create_keycloak_multi_tenancy.sql`
  - ALTER TABLE tb_assessoria: adicionar keycloak_group_id, keycloak_realm
  - CREATE TABLE tb_usuario (com keycloak_id)
  - Adicionar `tenant_id` em TODAS as tabelas existentes (se ainda não foi feito)

- [ ] **2.7** Executar migration
  ```bash
  mvn flyway:migrate
  ```

- [ ] **2.8** Criar repositories
  - `AssessoriaRepository.java`
  - `UsuarioRepository.java`: findByKeycloakId(), findByEmail()

**Entregáveis**: Modelo de dados preparado para Keycloak

---

### SPRINT 3 - Configurar Spring Security OAuth2 (Semana 3-4)

**Objetivo**: Autenticação via JWT do Keycloak

#### Tarefas:
- [ ] **3.1** Configurar application.yml
  ```yaml
  spring:
    security:
      oauth2:
        resourceserver:
          jwt:
            issuer-uri: http://localhost:8080/realms/menthoros-app
            jwk-set-uri: http://localhost:8080/realms/menthoros-app/protocol/openid-connect/certs

  keycloak:
    realm: menthoros-app
    auth-server-url: http://localhost:8080
    admin:
      client-id: menthoros-backend
      client-secret: ${KEYCLOAK_CLIENT_SECRET}
  ```

- [ ] **3.2** Criar `SecurityConfig`
  - Arquivo: `src/main/java/com/menthoros/config/SecurityConfig.java`
  - Configurar OAuth2 Resource Server
  - Desabilitar CSRF (API REST)
  - Configurar endpoints públicos vs protegidos
  - JwtAuthenticationConverter para extrair roles

- [ ] **3.3** Criar `JwtTenantFilter`
  - Arquivo: `src/main/java/com/menthoros/security/JwtTenantFilter.java`
  - Extrair tenant_id do JWT
  - Configurar `TenantContext`
  - Chamar `UsuarioSyncService` para sincronizar usuário
  - Limpar contexto após requisição

- [ ] **3.4** Criar `TenantContext`
  - Arquivo: `src/main/java/com/menthoros/multitenancy/TenantContext.java`
  - ThreadLocal para armazenar tenant atual
  - Métodos: setTenantId(), getTenantId(), clear()

- [ ] **3.5** Criar `UsuarioSyncService`
  - Arquivo: `src/main/java/com/menthoros/services/UsuarioSyncService.java`
  - syncUserFromJwt(): criar/atualizar usuário no banco
  - Extrair dados do JWT: sub, email, name, roles
  - Criar Usuario se não existir, atualizar se existir

- [ ] **3.6** Testar autenticação
  - Obter token do Keycloak
  - Fazer request para endpoint protegido com header `Authorization: Bearer {token}`
  - Verificar que TenantContext foi configurado
  - Verificar que usuário foi sincronizado no banco

**Entregáveis**: Autenticação OAuth2 funcionando

---

### SPRINT 4 - Isolamento de Dados (Semana 4-5)

**Objetivo**: Garantir isolamento por tenant_id

#### Tarefas:
- [ ] **4.1** Criar interface `TenantAware`
  - Arquivo: `src/main/java/com/menthoros/multitenancy/TenantAware.java`
  - Interface marker para entidades multi-tenant

- [ ] **4.2** Criar annotation `@TenantFilter`
  - Arquivo: `src/main/java/com/menthoros/multitenancy/TenantFilter.java`
  - Hibernate Filter: `@FilterDef` com parameter tenantId
  - `@Filter` com condition: `tenant_id = :tenantId`

- [ ] **4.3** Atualizar TODAS as entidades
  - Adicionar campo `assessoria` (ManyToOne)
  - Implementar interface `TenantAware`
  - Aplicar annotation `@TenantFilter`
  - Entidades: Atleta, TreinoRealizado, TreinoPlanejado, PlanoSemanal, PlanoMetaDados, Prova, MetricasDiarias

- [ ] **4.4** Criar `TenantEntityListener`
  - Arquivo: `src/main/java/com/menthoros/multitenancy/TenantEntityListener.java`
  - `@PrePersist` para setar tenant automaticamente

- [ ] **4.5** Configurar Hibernate Filters
  - Arquivo: `src/main/java/com/menthoros/config/HibernateConfig.java`
  - Ativar filtro `tenantFilter` globalmente
  - Setar parâmetro tenantId do TenantContext

- [ ] **4.6** Criar testes de isolamento
  - Arquivo: `src/test/java/com/menthoros/multitenancy/TenantIsolationTest.java`
  - Criar 2 assessorias
  - Criar atletas em cada uma
  - Verificar que queries não retornam dados cruzados

**Entregáveis**: Isolamento de dados 100% funcional

---

### SPRINT 5 - Gestão de Assessorias no Keycloak (Semana 5-6)

**Objetivo**: CRUD de assessorias com sincronização Keycloak

#### Tarefas:
- [ ] **5.1** Criar `KeycloakAdminService`
  - Arquivo: `src/main/java/com/menthoros/services/KeycloakAdminService.java`
  - Métodos para interagir com Keycloak Admin API:
    - createGroup(nome, tenantId)
    - addUserToGroup(userId, groupId)
    - assignRoleToUser(userId, role)
    - deleteGroup(groupId)

- [ ] **5.2** Criar DTOs
  - `AssessoriaInputDto.java`: dados de criação/atualização
  - `AssessoriaOutputDto.java`: dados de resposta
  - `AssessoriaConfigDto.java`: configurações visuais

- [ ] **5.3** Criar `AssessoriaService`
  - Arquivo: `src/main/java/com/menthoros/services/AssessoriaService.java`
  - Métodos: criar, atualizar, buscar, listar, desativar
  - **IMPORTANTE**: Ao criar assessoria, criar Group no Keycloak
  - Sincronizar keycloakGroupId no banco

- [ ] **5.4** Criar `AssessoriaController`
  - Arquivo: `src/main/java/com/menthoros/controller/AssessoriaController.java`
  - `POST /api/assessorias` - Criar assessoria
  - `GET /api/assessorias/{id}` - Buscar por ID
  - `PUT /api/assessorias/{id}` - Atualizar
  - `DELETE /api/assessorias/{id}` - Desativar
  - `GET /api/assessorias/{id}/config` - Configurações

- [ ] **5.5** Implementar validações de negócio
  - Validar domínio único
  - Validar limites de atletas/técnicos
  - Validar plano vs features habilitadas

- [ ] **5.6** Testes de integração com Keycloak
  - Criar assessoria e verificar Group criado no Keycloak
  - Verificar atributo tenant_id no Group

**Entregáveis**: Gestão completa de assessorias + Keycloak sync

---

### SPRINT 6 - Gestão de Usuários (Keycloak como Fonte) (Semana 6-7)

**Objetivo**: Gestão de usuários via Keycloak

#### Tarefas:
- [ ] **6.1** Criar `UsuarioKeycloakService`
  - Arquivo: `src/main/java/com/menthoros/services/UsuarioKeycloakService.java`
  - **IMPORTANTE**: Usuários são criados NO KEYCLOAK, não no banco
  - Métodos:
    - createUser(email, nome, password, tenantId, role)
    - updateUser(userId, dados)
    - deleteUser(userId)
    - assignToAssessoria(userId, tenantId)
    - changeRole(userId, role)

- [ ] **6.2** Criar DTOs
  - `UsuarioInputDto.java`
  - `UsuarioOutputDto.java`
  - `AlterarSenhaDto.java`
  - `ConviteUsuarioDto.java`

- [ ] **6.3** Criar `UsuarioController`
  - Arquivo: `src/main/java/com/menthoros/controller/UsuarioController.java`
  - **OBS**: Endpoints fazem operações no Keycloak
  - `POST /api/usuarios` - Criar usuário no Keycloak
  - `GET /api/usuarios` - Listar usuários (ler de tb_usuario - cache)
  - `GET /api/usuarios/{id}` - Buscar por ID
  - `PUT /api/usuarios/{id}` - Atualizar no Keycloak
  - `PUT /api/usuarios/{id}/senha` - Alterar senha (Keycloak)
  - `DELETE /api/usuarios/{id}` - Desativar (Keycloak)

- [ ] **6.4** Implementar sistema de convites
  - Arquivo: `src/main/java/com/menthoros/services/ConviteService.java`
  - Enviar email com link de cadastro
  - Link redireciona para Keycloak com pre-fill de dados
  - Após registro, adicionar ao Group correto

- [ ] **6.5** Implementar validações
  - Apenas ADMIN pode criar outros usuários
  - Validar limite de técnicos por plano
  - Email único no realm

- [ ] **6.6** Testes de autorização
  - Testar que TECNICO não cria outros usuários
  - Testar que usuário não acessa dados de outra assessoria

**Entregáveis**: Gestão completa de usuários via Keycloak

---

### SPRINT 7 - Atualizar Endpoints Existentes (Semana 7-8)

**Objetivo**: Aplicar multi-tenancy em todos os endpoints

#### Tarefas:
- [ ] **7.1** Atualizar `AtletaController`
  - Remover verificação manual de tenant_id
  - Confiar no filtro automático do Hibernate
  - Adicionar `@PreAuthorize` onde necessário

- [ ] **7.2** Atualizar `PlanoTreinoController`
  - Garantir isolamento por tenant
  - Validar que plano pertence ao tenant

- [ ] **7.3** Atualizar `TreinoRealizadoController`
  - Filtrar por tenant automaticamente

- [ ] **7.4** Atualizar todos os Services
  - Remover lógica manual de filtragem
  - Confiar no `@TenantFilter`
  - Adicionar validações se necessário

- [ ] **7.5** Criar `TenantValidator`
  - Arquivo: `src/main/java/com/menthoros/multitenancy/TenantValidator.java`
  - Método para validar se entidade pertence ao tenant atual
  - Usar em casos específicos

- [ ] **7.6** Atualizar Swagger/OpenAPI
  - Adicionar esquema de autenticação OAuth2
  - Configurar security schemes
  - Documentar header `Authorization: Bearer {token}`
  - Endpoints públicos vs protegidos

**Entregáveis**: Todos os endpoints com multi-tenancy

---

### SPRINT 8 - Dashboard e Métricas por Tenant (Semana 8)

**Objetivo**: Dashboard para admins da assessoria

#### Tarefas:
- [ ] **8.1** Criar `DashboardService`
  - Arquivo: `src/main/java/com/menthoros/services/DashboardService.java`
  - Métricas filtradas por tenant:
    - Total atletas ativos
    - Treinos realizados no mês
    - TSS médio
    - Aderência ao plano

- [ ] **8.2** Criar `DashboardController`
  - Endpoint: `GET /api/dashboard/metricas`
  - Endpoint: `GET /api/dashboard/atletas-ativos`
  - Endpoint: `GET /api/dashboard/treinos-mes`

- [ ] **8.3** Implementar cache por tenant
  - Cache key incluindo tenant_id
  - TTL configurável por assessoria
  - Invalidar cache ao criar/atualizar dados

- [ ] **8.4** Criar relatórios
  - Relatório de aderência ao plano
  - Relatório de progressão dos atletas
  - Export CSV/PDF

**Entregáveis**: Dashboard funcional

---

### SPRINT 9 - Onboarding e Trial (Semana 9-10)

**Objetivo**: Processo de cadastro de novas assessorias

#### Tarefas:
- [ ] **9.1** Criar endpoint público de registro
  - Endpoint: `POST /api/public/assessorias/register`
  - Fluxo:
    1. Criar assessoria no banco
    2. Criar Group no Keycloak com tenant_id
    3. Criar usuário admin no Keycloak
    4. Adicionar usuário ao Group
    5. Atribuir role ADMIN
    6. Ativar trial automático (14 dias)

- [ ] **9.2** Criar `OnboardingService`
  - Arquivo: `src/main/java/com/menthoros/services/OnboardingService.java`
  - Configurações iniciais
  - Email de boas-vindas
  - Tour guiado (frontend)

- [ ] **9.3** Implementar job de expiração de trial
  - Verificar diariamente assessorias com trial expirado
  - Desativar automaticamente
  - Enviar email de notificação
  - Desabilitar usuários no Keycloak (opcional)

- [ ] **9.4** Criar wizard de configuração inicial
  - Tela 1: Dados da assessoria + primeiro usuário
  - Tela 2: Personalização (cores, logo)
  - Tela 3: Convites para técnicos
  - Tela 4: Importar atletas (CSV)

**Entregáveis**: Onboarding self-service completo

---

### SPRINT 10 - Features por Plano (Semana 10)

**Objetivo**: Diferenciação de planos

#### Tarefas:
- [ ] **10.1** Criar `FeatureGuard`
  - Arquivo: `src/main/java/com/menthoros/multitenancy/FeatureGuard.java`
  - Verificar se assessoria tem feature habilitada
  - Lançar exceção se não tiver

- [ ] **10.2** Criar annotation `@RequiresFeature`
  - Exemplo: `@RequiresFeature("IA_AVANCADA")`
  - Aspect para interceptar e validar feature

- [ ] **10.3** Implementar limitadores por plano
  - BASIC: até 20 atletas, sem IA avançada
  - PRO: até 100 atletas, IA avançada
  - ENTERPRISE: ilimitado, todas features

- [ ] **10.4** Criar serviço de upgrade de plano
  - Endpoint: `POST /api/assessorias/{id}/upgrade`
  - Atualizar limites
  - Habilitar features
  - Enviar confirmação

- [ ] **10.5** Validar limites em tempo de criação
  - Bloquear criação de atleta se atingiu limite
  - Bloquear features desabilitadas

**Entregáveis**: Planos diferenciados funcionando

---

### SPRINT 11 - Testes e Segurança (Semana 11)

**Objetivo**: Garantir qualidade e segurança

#### Tarefas:
- [ ] **11.1** Testes de isolamento
  - Criar 2 assessorias de teste
  - Criar atletas em cada uma
  - Verificar que queries não retornam dados cruzados
  - Testar com múltiplos usuários simultâneos

- [ ] **11.2** Testes de autorização
  - ADMIN pode tudo
  - TECNICO pode gerenciar atletas
  - VISUALIZADOR apenas lê
  - Testar `@PreAuthorize`

- [ ] **11.3** Testes de sincronização Keycloak
  - Criar usuário no Keycloak
  - Fazer login
  - Verificar que usuário foi sincronizado no banco
  - Atualizar usuário no Keycloak
  - Fazer novo login
  - Verificar que dados foram atualizados

- [ ] **11.4** Testes de performance
  - Benchmark com múltiplos tenants
  - Verificar impacto dos índices
  - Otimizar queries lentas
  - Verificar N+1 queries

- [ ] **11.5** Testes de carga
  - Simular 100 tenants simultâneos
  - Verificar vazamento de memória
  - Testar ThreadLocal cleanup
  - Monitorar Keycloak

- [ ] **11.6** Auditoria de segurança
  - Validação de JWT (expiração, assinatura)
  - Testes de vazamento de dados
  - SQL Injection (impossível com JPA, mas validar)
  - XSS (frontend)
  - Rate limiting

- [ ] **11.7** Implementar rate limiting por tenant
  - Limitar requests por assessoria
  - Usar Redis para contador
  - Configurar limites por plano

- [ ] **11.8** Logs estruturados
  - Incluir tenant_id em TODOS os logs
  - Incluir user_id (keycloak_id)
  - Facilitar troubleshooting

**Entregáveis**: Sistema auditado e seguro

---

### SPRINT 12 - Documentação e Deploy (Semana 12)

**Objetivo**: Preparar para produção

#### Tarefas:
- [ ] **12.1** Documentação técnica
  - Arquitetura multi-tenant com Keycloak
  - Fluxo de autenticação OAuth2
  - Modelo de dados
  - Sincronização Keycloak ↔ Menthoros

- [ ] **12.2** Documentação de API
  - Swagger completo
  - Exemplos de requests
  - Códigos de erro
  - Fluxo OAuth2

- [ ] **12.3** Guia de desenvolvimento
  - Como adicionar nova entidade multi-tenant
  - Como criar novo endpoint protegido
  - Como adicionar nova feature flag
  - Boas práticas

- [ ] **12.4** Guia de administração Keycloak
  - Como criar uma nova assessoria manualmente
  - Como adicionar usuário a uma assessoria
  - Como resetar senha
  - Como configurar MFA

- [ ] **12.5** Scripts de deploy
  - Dockerfile (Menthoros backend)
  - docker-compose.yml (completo)
  - Kubernetes manifests (opcional)
  - Scripts de backup

- [ ] **12.6** Configurar ambientes
  - DEV, STAGING, PROD
  - Variáveis de ambiente
  - Secrets (Keycloak client secret, etc)
  - Configurar Keycloak em produção (banco externo, HA)

- [ ] **12.7** Monitoramento
  - Logs centralizados (ELK/Loki)
  - Métricas (Prometheus + Grafana)
  - Alertas (falhas de autenticação, tenants inativos, etc)
  - Dashboard de Keycloak

- [ ] **12.8** Backup strategy
  - Backup automático diário (PostgreSQL)
  - Backup de configurações do Keycloak
  - Possibilidade de restaurar um tenant específico
  - Testes de restore

**Entregáveis**: Sistema production-ready

---

## Marcos de Entrega

| Marco | Descrição | Prazo Sugerido |
|-------|-----------|----------------|
| **M1** | Keycloak configurado e emitindo tokens | Fim da Semana 2 |
| **M2** | Modelo de dados multi-tenant | Fim da Semana 3 |
| **M3** | Autenticação OAuth2 funcionando | Fim da Semana 4 |
| **M4** | Isolamento de dados 100% | Fim da Semana 5 |
| **M5** | CRUD de assessorias + Keycloak sync | Fim da Semana 6 |
| **M6** | Gestão de usuários via Keycloak | Fim da Semana 7 |
| **M7** | Endpoints migrados | Fim da Semana 8 |
| **M8** | Onboarding self-service | Fim da Semana 10 |
| **M9** | Features por plano | Fim da Semana 10 |
| **M10** | Testes e segurança | Fim da Semana 11 |
| **M11** | Deploy em produção | Fim da Semana 12 |

---

## Quick Start - Começar Hoje

### Comandos para Iniciar (Sprint 1):

```bash
# 1. Criar docker-compose.yml
cat > docker-compose.yml << 'EOF'
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
      POSTGRES_USER: postgres
      POSTGRES_PASSWORD: postgres
    ports:
      - "5432:5432"
    volumes:
      - postgres-data:/var/lib/postgresql/data
      - ./init-databases.sql:/docker-entrypoint-initdb.d/init-databases.sql
    networks:
      - menthoros-network

volumes:
  postgres-data:

networks:
  menthoros-network:
EOF

# 2. Criar script de inicialização de bancos
cat > init-databases.sql << 'EOF'
-- Banco para Keycloak
CREATE DATABASE keycloak OWNER postgres;

-- Banco para Menthoros
CREATE DATABASE "menthoros-multi" OWNER postgres;
\c "menthoros-multi"
CREATE USER menthoros WITH PASSWORD 'menthoros123';
GRANT ALL PRIVILEGES ON DATABASE "menthoros-multi" TO menthoros;
GRANT ALL ON SCHEMA public TO menthoros;
EOF

# 3. Subir containers
docker-compose up -d

# 4. Aguardar Keycloak iniciar
echo "Aguardando Keycloak iniciar..."
sleep 30

# 5. Abrir Admin Console
echo "Acesse: http://localhost:8080"
echo "Login: admin / admin123"
```

---

## Checklist Final

Antes de considerar multi-tenancy completo:

### Funcionalidade
- [ ] Keycloak configurado (realm, clients, roles, mappers)
- [ ] Múltiplas assessorias cadastradas (Groups no Keycloak)
- [ ] Isolamento de dados 100% funcional
- [ ] Autenticação OAuth2 com JWT
- [ ] Sincronização automática Keycloak → tb_usuario
- [ ] Usuários com roles diferentes
- [ ] Dashboard por assessoria
- [ ] Onboarding self-service

### Qualidade
- [ ] Cobertura de testes > 80%
- [ ] Testes de isolamento passando
- [ ] Testes de sincronização Keycloak
- [ ] Performance adequada com 100+ tenants
- [ ] Documentação completa

### Segurança
- [ ] JWT validation funcionando
- [ ] Token expiration configurado
- [ ] Senhas gerenciadas pelo Keycloak
- [ ] Rate limiting por tenant
- [ ] Logs auditáveis com tenant_id
- [ ] Testes de vazamento de dados
- [ ] MFA habilitado (opcional)

### Keycloak
- [ ] Backup automático de configurações
- [ ] Monitoring ativo
- [ ] Alta disponibilidade (produção)
- [ ] Temas customizados (opcional)
- [ ] Email SMTP configurado

### Produção
- [ ] Backups automáticos (Postgres + Keycloak)
- [ ] Monitoramento ativo
- [ ] Alertas configurados
- [ ] Documentação de runbook
- [ ] Disaster recovery testado

---

## Riscos e Mitigações

| Risco | Impacto | Mitigação |
|-------|---------|-----------|
| **Keycloak indisponível** | CRÍTICO | HA cluster, cache local de usuários |
| **Vazamento de dados entre tenants** | CRÍTICO | Testes automatizados de isolamento |
| **Sincronização falha** | ALTO | Retry logic, logs detalhados |
| **Performance JWT validation** | MÉDIO | Cache de JWK, validação local |
| **Migração de dados existentes** | ALTO | Script testado, rollback plan |
| **Complexidade operacional** | MÉDIO | Documentação, monitoramento |

---

## Padrões de Design Utilizados

- **OAuth2 Resource Server Pattern**: Validação de tokens
- **Tenant Context Pattern**: ThreadLocal para contexto
- **Cache Aside Pattern**: tb_usuario como cache do Keycloak
- **Repository Pattern**: Isolamento na camada de dados
- **Strategy Pattern**: Diferentes planos

---

## Referências Adicionais

- [Keycloak Admin REST API](https://www.keycloak.org/docs-api/latest/rest-api/index.html)
- [Spring Security OAuth2](https://docs.spring.io/spring-security/reference/servlet/oauth2/index.html)
- [Keycloak Multi-Tenancy](https://www.keycloak.org/docs/latest/server_admin/#_per_realm_admin_permissions)
- [Keycloak Documentation](https://www.keycloak.org/documentation)
- [Spring Security OAuth2 Resource Server](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/index.html)

---

## Comparativo: Com Keycloak vs Sem Keycloak

| Aspecto | Com Keycloak | Sem Keycloak |
|---------|--------------|--------------|
| **Gestão de senhas** | Keycloak | Backend (BCrypt) |
| **MFA** | Nativo | Implementar manualmente |
| **Social Login** | Nativo | Implementar manualmente |
| **SSO** | Nativo | Implementar manualmente |
| **Auditoria** | Completa | Implementar manualmente |
| **Reset de senha** | Fluxo pronto | Implementar manualmente |
| **Email verification** | Fluxo pronto | Implementar manualmente |
| **Complexidade inicial** | Maior | Menor |
| **Manutenção** | Menor | Maior |
| **Escalabilidade** | Alta | Média |

---

## Próximos Passos

Comece pelo **Sprint 1, tarefa 1.1**!

- Tempo estimado total: 12 semanas (3 meses)
- Equipe recomendada: 2-3 desenvolvedores
- Versão: 2.0.0 (Keycloak)

---

**Autor**: Claude Code
**Data**: 2026-03-19
**Versão**: 2.0.0 (Keycloak)
