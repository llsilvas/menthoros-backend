# Padrões de Melhoria - Config & Repository

## 📦 CONFIG PACKAGE - Recomendações

### 1. **Problema: SecurityConfig com URLs hardcoded**

**Atual:**
```java
.requestMatchers(
    "/api/public/**",
    "/swagger-ui/**",
    "/api-docs/**",
    "/actuator/health",
    "/api/strava/webhook",      // ❌ Hardcoded
    "/api/strava/callback"      // ❌ Hardcoded
).permitAll()
```

**Solução: Externalizar em Properties**
```java
// SecurityProperties.java
@ConfigurationProperties(prefix = "app.security")
public class SecurityProperties {
    private List<String> publicPaths = List.of(
        "/api/public/**",
        "/swagger-ui/**",
        "/api-docs/**",
        "/actuator/health"
    );
    private List<String> stravaPaths = List.of(
        "/api/v1/strava/webhook",
        "/api/v1/strava/callback"
    );
}

// application.yml
app:
  security:
    public-paths:
      - "/api/public/**"
      - "/swagger-ui/**"
      - "/api-docs/**"
      - "/actuator/health"
    strava-paths:
      - "/api/v1/strava/webhook"
      - "/api/v1/strava/callback"
```

---

### 2. **Problema: Configs dispersas sem hierarquia**

**Atual:**
- CacheConfig, SecurityConfig, CorsConfig, OpenApiConfig, LLMConfig, StravaProperties...
- Sem organização clara
- Duplicação de responsabilidades

**Solução: Estrutura Hierárquica**

```
config/
├── core/
│   ├── CoreProperties.java           // app.*, server.*, logging
│   ├── CoreSecurityConfig.java       // Security, CORS, JWT
│   └── CoreCacheConfig.java          // Cache, Redis
├── external/
│   ├── StravaProperties.java         // Strava API
│   ├── StravaWebClientConfig.java
│   ├── LLMProperties.java            // OpenAI/LLM
│   └── LLMConfig.java
├── persistence/
│   ├── DatabaseConfig.java           // JPA, Hibernate
│   └── FlywayConfig.java             // Migrations
├── documentation/
│   ├── OpenApiConfig.java
│   └── OpenApiProperties.java
└── async/
    └── AsyncConfig.java              // Thread pools, async
```

---

### 3. **Problema: OpenApiConfig mistura versioning com configuração**

**Atual:**
```java
.version("1.0.0")  // ❌ Hardcoded
```

**Solução: Integrar com POM/Gradle**
```java
@Configuration
public class OpenApiConfig {
    
    @Value("${project.version}")  // ✅ Do pom.xml via Maven
    private String apiVersion;
    
    @Value("${app.environment:dev}")
    private String environment;
    
    @Bean
    public OpenAPI menthorosOpenAPI() {
        // Usar apiVersion
    }
}

// pom.xml
<properties>
    <project.version>1.0.0</project.version>
</properties>

// application.yml
app:
  environment: ${ENV:dev}
```

---

### 4. **Problema: CacheConfig não valida configurações**

**Atual:**
```java
@Value("${app.cache.default-ttl:PT30M}")
private Duration defaultTtl;  // ❌ Sem validação
```

**Solução: Properties com Validação**
```java
@ConfigurationProperties(prefix = "app.cache")
@Validated
public class CacheProperties {
    
    @NotNull
    @Positive(message = "TTL deve ser positivo")
    private Duration defaultTtl = Duration.ofMinutes(30);
    
    @NotNull
    @Min(value = 10, message = "Tamanho mínimo é 10")
    private long maximumSize = 1000;
    
    @Valid
    private Map<String, CacheProfile> profiles = Map.of(
        "atletas", new CacheProfile(Duration.ofMinutes(30), 1000),
        "embeddings", new CacheProfile(Duration.ofHours(2), 500)
    );
    
    @Getter
    @Setter
    @Valid
    public static class CacheProfile {
        @NotNull
        @Positive
        private Duration ttl;
        
        @Min(1)
        private long maxSize;
    }
}

// CacheConfig.java
@Configuration
@EnableCaching
public class CacheConfig {
    
    @Bean
    public CacheManager cacheManager(CacheProperties props) {
        CaffeineCacheManager manager = new CaffeineCacheManager();
        manager.setCaffeine(Caffeine.newBuilder()
            .maximumSize(props.getMaximumSize())
            .expireAfterWrite(props.getDefaultTtl())
            .recordStats()
        );
        return manager;
    }
}
```

---

### 5. **Problema: Falta de Actuator/Health Checks**

**Solução Adicionar:**
```java
// HealthConfig.java
@Configuration
public class HealthConfig {
    
    @Bean
    public HealthIndicator stravaHealth(StravaProperties props) {
        return () -> {
            try {
                // Verificar conectividade com Strava
                return Health.up()
                    .withDetail("service", "strava")
                    .withDetail("baseUrl", props.getApiBaseUrl())
                    .build();
            } catch (Exception e) {
                return Health.down()
                    .withDetail("reason", e.getMessage())
                    .build();
            }
        };
    }
    
    @Bean
    public HealthIndicator cacheHealth(CacheManager cacheManager) {
        return () -> {
            Cache cache = cacheManager.getCache("atletas");
            return cache != null ? 
                Health.up().build() : 
                Health.down().build();
        };
    }
}
```

---

## 📚 REPOSITORY PACKAGE - Recomendações

### 1. **Problema: Repository Interface tem métodos duplicados**

**Atual:**
```java
// AtletaRepository.java
List<Atleta> findAllAtletasWithDias(@Param("tenantId") UUID tenantId);
List<Atleta> findAllAtletasWithProvas(@Param("tenantId") UUID tenantId);
List<Atleta> findAllAtletasWithBasicInfo(@Param("tenantId") UUID tenantId);
List<Atleta> findAllAtletas(@Param("tenantId") UUID tenantId);
// ❌ Redundância e sem clara distinção
```

**Solução: Padrão Specification ou Custom Methods com Entitygraph**
```java
// AtletaRepository.java
public interface AtletaRepository extends 
    PagingAndSortingRepository<Atleta, UUID>,
    JpaSpecificationExecutor<Atleta> {
    
    // Query padrão com EntityGraph para lazy-loading
    @EntityGraph(attributePaths = {"diasDisponiveis"})
    List<Atleta> findByAssessoriaIdAndAtivoOrderByNomeAsc(
        UUID tenantId, 
        String ativo
    );
    
    // Utilizar Specification para queries complexas
}

// AtletaSpecification.java
public class AtletaSpecification {
    
    public static Specification<Atleta> byTenant(UUID tenantId) {
        return (root, query, cb) -> 
            cb.equal(root.get("assessoria").get("id"), tenantId);
    }
    
    public static Specification<Atleta> active() {
        return (root, query, cb) -> 
            cb.equal(root.get("ativo"), "ATIVO");
    }
    
    public static Specification<Atleta> withStravaConnected() {
        return (root, query, cb) -> {
            Join<Atleta, IntegracaoExterna> join = 
                root.join("integracoes", JoinType.INNER);
            return cb.and(
                cb.equal(join.get("plataforma"), "STRAVA"),
                cb.isTrue(join.get("ativo")),
                cb.isNotNull(join.get("accessToken"))
            );
        };
    }
}

// Uso
List<Atleta> atletas = repository.findAll(
    Specification.where(AtletaSpecification.byTenant(tenantId))
        .and(AtletaSpecification.active())
        .and(AtletaSpecification.withStravaConnected())
);
```

---

### 2. **Problema: Falta de Padrão para Custom Repositories**

**Solução: Custom Repository Pattern**
```java
// AtletaRepositoryCustom.java
public interface AtletaRepositoryCustom {
    Page<Atleta> findAtletasWithFetchGraph(
        UUID tenantId, 
        String fetchType,  // "dias", "provas", "basic"
        Pageable pageable
    );
    
    List<Atleta> findAtletasWithMetrics(UUID tenantId);
}

// AtletaRepositoryImpl.java
public class AtletaRepositoryImpl implements AtletaRepositoryCustom {
    
    @PersistenceContext
    private EntityManager em;
    
    @Override
    public Page<Atleta> findAtletasWithFetchGraph(
        UUID tenantId, 
        String fetchType, 
        Pageable pageable) {
        
        EntityGraph<Atleta> graph = 
            em.createEntityGraph(Atleta.class);
        
        switch(fetchType) {
            case "dias" -> graph.addAttributeNodes("diasDisponiveis");
            case "provas" -> graph.addAttributeNodes("provas");
        }
        
        TypedQuery<Atleta> query = em.createQuery(
            "SELECT a FROM Atleta a WHERE a.assessoria.id = :tenantId",
            Atleta.class
        );
        query.setHint("javax.persistence.fetchgraph", graph);
        query.setParameter("tenantId", tenantId);
        
        return new PageImpl<>(
            query.getResultList(),
            pageable,
            getTotalCount(tenantId)
        );
    }
}

// AtletaRepository.java
public interface AtletaRepository extends 
    PagingAndSortingRepository<Atleta, UUID>,
    AtletaRepositoryCustom {  // ✅ Combina ambas interfaces
}
```

---

### 3. **Problema: Falta de DTOs para Queries otimizadas**

**Solução: Projection DTOs**
```java
// AtletaProjection.java
public interface AtletaProjection {
    UUID getId();
    String getNome();
    String getEmail();
}

// AtletaRepository.java
List<AtletaProjection> findProjectedAtletas(UUID tenantId);

// Uso eficiente em lista (não carrega entidade inteira)
List<AtletaProjection> atletas = 
    repository.findProjectedAtletas(tenantId);
```

---

### 4. **Problema: Sem controle de transação em Repository**

**Solução: Explicit Transactional Boundaries**
```java
// AtletaRepository.java
public interface AtletaRepository extends PagingAndSortingRepository<Atleta, UUID> {
    
    @Transactional(readOnly = true)
    @Query("SELECT a FROM Atleta a WHERE a.id = :id AND a.assessoria.id = :tenantId")
    Optional<Atleta> findByIdAndTenantId(
        @Param("id") UUID id, 
        @Param("tenantId") UUID tenantId
    );
    
    @Transactional
    @Modifying
    @Query("UPDATE Atleta a SET a.ativo = 'INATIVO' WHERE a.id = :id")
    int deactivateAthlete(@Param("id") UUID id);
}
```

---

### 5. **Problema: Sem auditoria de mudanças**

**Solução: Usar Spring Data Audit**
```java
// AuditableEntity.java
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class AuditableEntity {
    
    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;
    
    @CreatedBy
    @Column(nullable = false, updatable = false)
    private String createdBy;
    
    @LastModifiedBy
    @Column(nullable = false)
    private String updatedBy;
}

// Atleta.java
@Entity
public class Atleta extends AuditableEntity {
    // ...
}

// AuditConfig.java
@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorProvider")
public class AuditConfig {
    
    @Bean
    public AuditorAware<String> auditorProvider() {
        return () -> SecurityContextHolder.getContext()
            .getAuthentication()
            .map(auth -> auth.getName())
            .stream()
            .findFirst();
    }
}
```

---

## 🎯 Resumo de Melhorias Prioritárias

| Prioridade | Config | Repository |
|-----------|--------|-----------|
| **🔴 Alta** | Externalizar SecurityConfig URLs | Remover métodos duplicados com Specification |
| **🔴 Alta** | Validar Properties (Bean Validation) | Implementar Custom Repository Pattern |
| **🟠 Média** | Estruturar pacote config hierarquicamente | Adicionar Projection DTOs |
| **🟠 Média** | Integrar version do pom.xml | Adicionar Auditoria com @CreatedBy/@LastModifiedBy |
| **🟡 Baixa** | Adicionar Health Checks | Explicitar @Transactional |

---

## 📋 Próximos Passos Recomendados

1. **Config**: Refatorar SecurityConfig com SecurityProperties
2. **Config**: Adicionar validações nas Properties classes
3. **Repository**: Implementar AtletaSpecification
4. **Repository**: Criar AtletaProjection para queries leves
5. **Config + Repository**: Adicionar Health Indicators e Auditoria
