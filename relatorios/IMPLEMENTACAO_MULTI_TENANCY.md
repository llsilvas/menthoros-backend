# Relatório de Implementação Multi-Tenancy - Sistema Menthoros

## 📋 Sumário Executivo

Este documento apresenta uma análise detalhada e um plano de implementação completo para transformar o sistema **Menthoros** em uma plataforma **multi-tenant**, permitindo que múltiplas assessorias de corrida utilizem o sistema com isolamento total de dados entre elas.

### Objetivo
Permitir que diferentes assessorias de corrida utilizem o sistema, cada uma com acesso exclusivo aos seus próprios atletas, planos de treino e dados relacionados.

---

## 🔍 Análise da Arquitetura Atual

### Estado Atual
O sistema Menthoros atualmente opera em **modo single-tenant**, onde:
- ❌ Não há conceito de "assessoria" ou "organização"
- ❌ Todos os dados são compartilhados em um único namespace
- ❌ Não existe autenticação/autorização implementada
- ❌ Controllers acessam dados sem filtro por tenant
- ❌ Repositories retornam todos os registros sem segregação

### Estrutura de Entidades Identificadas

```
Atleta (entidade raiz)
├── PlanoMetaDados
├── MetricasDiarias
├── TreinoRealizado
├── TreinoPlanejado
├── PlanoSemanal
│   └── TreinoPlanejado
└── Prova
```

### Pontos Críticos Identificados

1. **Sem Segregação de Dados**
   - Todos os atletas são visíveis para todos
   - Queries não filtram por organização

2. **Sem Autenticação/Autorização**
   - Nenhuma camada de segurança implementada
   - Não há conceito de usuário ou permissões

3. **Controllers sem Validação de Acesso**
   - Endpoints públicos sem verificação de propriedade
   - Possível acesso cruzado entre tenants

4. **Cache sem Isolamento**
   - Cache compartilhado entre todos os tenants

---

## 🎯 Estratégia Recomendada: Shared Database + Discriminator Column

### Por que esta abordagem?

| Critério | Shared DB + Discriminator | Separate DB per Tenant | Separate Schema per Tenant |
|----------|---------------------------|------------------------|---------------------------|
| **Custo de infraestrutura** | ✅ Baixo | ❌ Alto | ⚠️ Médio |
| **Facilidade de manutenção** | ✅ Simples | ❌ Complexo | ⚠️ Médio |
| **Escalabilidade** | ✅ Excelente | ⚠️ Limitada | ⚠️ Boa |
| **Isolamento de dados** | ⚠️ Lógico | ✅ Físico | ✅ Físico |
| **Performance** | ✅ Excelente | ⚠️ Pode variar | ✅ Boa |
| **Backup/Restore** | ✅ Simples | ❌ Complexo | ⚠️ Médio |
| **Adequação ao projeto** | ✅ Ideal | ❌ Overkill | ⚠️ Desnecessário |

**Decisão**: **Shared Database + Discriminator Column** (tenant_id)

---

## 📐 Modelo de Dados Multi-Tenant

### Nova Entidade: Assessoria

```java
@Entity
@Table(name = "tb_assessoria",
    indexes = {
        @Index(name = "idx_assessoria_ativo", columnList = "ativo"),
        @Index(name = "idx_assessoria_dominio", columnList = "dominio", unique = true)
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Assessoria {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 200)
    private String nome;

    @Column(length = 500)
    private String descricao;

    @Column(unique = true, nullable = false, length = 100)
    private String dominio; // ex: "corredoreselite", "runfast"

    @Column(name = "email_contato", length = 200)
    private String emailContato;

    @Column(name = "telefone", length = 20)
    private String telefone;

    @Column(name = "cnpj", length = 20)
    private String cnpj;

    @Column(name = "endereco", length = 500)
    private String endereco;

    @Column(name = "logo_url", length = 500)
    private String logoUrl;

    @Column(name = "data_criacao", nullable = false, updatable = false)
    private LocalDateTime dataCriacao;

    @Column(name = "data_atualizacao")
    private LocalDateTime dataAtualizacao;

    @Enumerated(EnumType.STRING)
    @Column(name = "plano_assinatura")
    private PlanoAssinatura planoAssinatura; // BASICO, PREMIUM, ENTERPRISE

    @Column(name = "limite_atletas")
    private Integer limiteAtletas; // null = ilimitado

    @Column(name = "quantidade_atletas_ativos")
    private Integer quantidadeAtletasAtivos = 0;

    @Column(nullable = false)
    private Boolean ativo = true;

    // Configurações específicas da assessoria
    @Column(name = "timezone", length = 50)
    private String timezone = "America/Sao_Paulo";

    @Column(name = "idioma", length = 10)
    private String idioma = "pt-BR";

    // Relacionamentos
    @OneToMany(mappedBy = "assessoria", fetch = FetchType.LAZY)
    private List<Usuario> usuarios;

    @OneToMany(mappedBy = "assessoria", fetch = FetchType.LAZY)
    private List<Atleta> atletas;

    @PrePersist
    protected void onCreate() {
        dataCriacao = LocalDateTime.now();
        dataAtualizacao = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        dataAtualizacao = LocalDateTime.now();
    }
}
```

### Nova Entidade: Usuário

```java
@Entity
@Table(name = "tb_usuario",
    indexes = {
        @Index(name = "idx_usuario_email", columnList = "email", unique = true),
        @Index(name = "idx_usuario_assessoria", columnList = "assessoria_id")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 200)
    private String nome;

    @Column(unique = true, nullable = false, length = 200)
    private String email;

    @Column(nullable = false)
    private String senha; // BCrypt hash

    @Column(name = "telefone", length = 20)
    private String telefone;

    @Column(name = "foto_perfil_url", length = 500)
    private String fotoPerfilUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoUsuario tipo; // ADMIN_SISTEMA, ADMIN_ASSESSORIA, TREINADOR, VISUALIZADOR

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assessoria_id", nullable = false)
    private Assessoria assessoria;

    @Column(nullable = false)
    private Boolean ativo = true;

    @Column(name = "data_criacao", nullable = false, updatable = false)
    private LocalDateTime dataCriacao;

    @Column(name = "data_ultimo_acesso")
    private LocalDateTime dataUltimoAcesso;

    @Column(name = "ultimo_ip_acesso", length = 50)
    private String ultimoIpAcesso;

    @PrePersist
    protected void onCreate() {
        dataCriacao = LocalDateTime.now();
    }
}
```

### Enums Necessários

```java
public enum PlanoAssinatura {
    BASICO,    // Até 50 atletas
    PREMIUM,   // Até 200 atletas
    ENTERPRISE // Ilimitado
}

public enum TipoUsuario {
    ADMIN_SISTEMA,      // Acesso total (Anthropic/Suporte)
    ADMIN_ASSESSORIA,   // Administrador da assessoria
    TREINADOR,          // Pode criar/editar planos
    VISUALIZADOR        // Apenas leitura
}
```

### Alteração nas Entidades Existentes

#### Atleta (modificado)

```java
@Entity
@Table(name = "tb_atleta",
indexes = {
        @Index(name = "idx_atleta_ativo", columnList = "ativo"),
        @Index(name = "idx_atleta_assessoria", columnList = "assessoria_id"), // NOVO
        @Index(name = "idx_atleta_nivel_experiencia", columnList = "nivel_experiencia")
})
public class Atleta {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // ===== MULTI-TENANCY =====
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assessoria_id", nullable = false)
    private Assessoria assessoria; // NOVO CAMPO

    // ... resto dos campos existentes ...

    @Column(name = "email", unique = true) // NOVO - para vínculo futuro com conta
    private String email;

    // ... outros campos ...
}
```

#### Outras Entidades

Adicionar campo `assessoria_id` em:
- ✅ PlanoSemanal
- ✅ TreinoRealizado
- ✅ TreinoPlanejado (opcional - pode herdar do Atleta)
- ✅ Prova
- ✅ PlanoMetaDados (opcional - pode herdar do Atleta)
- ✅ MetricasDiarias (opcional - pode herdar do Atleta)

---

## 🔐 Camada de Segurança

### 1. Spring Security Configuration

```java
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable()) // API REST
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/public/**", "/api-docs/**", "/swagger-ui/**").permitAll()
                .requestMatchers("/api/admin/**").hasRole("ADMIN_SISTEMA")
                .requestMatchers("/api/**").authenticated()
            )
            .addFilterBefore(jwtAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter() {
        return new JwtAuthenticationFilter();
    }
}
```

### 2. JWT Authentication Filter

```java
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    @Autowired
    private JwtTokenProvider tokenProvider;

    @Autowired
    private TenantContext tenantContext;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            String jwt = extractJwtFromRequest(request);

            if (StringUtils.hasText(jwt) && tokenProvider.validateToken(jwt)) {
                UUID userId = tokenProvider.getUserIdFromToken(jwt);
                UUID assessoriaId = tokenProvider.getAssessoriaIdFromToken(jwt);
                String role = tokenProvider.getRoleFromToken(jwt);

                // Configurar contexto de tenant
                tenantContext.setCurrentTenantId(assessoriaId);
                tenantContext.setCurrentUserId(userId);

                // Criar autenticação Spring Security
                UserDetails userDetails = new TenantUserDetails(userId, assessoriaId, role);
                UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());

                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        } catch (Exception ex) {
            logger.error("Não foi possível configurar autenticação", ex);
        }

        filterChain.doFilter(request, response);
    }

    private String extractJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
```

### 3. Tenant Context (Thread-Local)

```java
@Component
public class TenantContext {

    private static final ThreadLocal<UUID> CURRENT_TENANT = new ThreadLocal<>();
    private static final ThreadLocal<UUID> CURRENT_USER = new ThreadLocal<>();

    public void setCurrentTenantId(UUID tenantId) {
        CURRENT_TENANT.set(tenantId);
    }

    public UUID getCurrentTenantId() {
        UUID tenantId = CURRENT_TENANT.get();
        if (tenantId == null) {
            throw new TenantNotFoundException("Contexto de tenant não configurado");
        }
        return tenantId;
    }

    public void setCurrentUserId(UUID userId) {
        CURRENT_USER.set(userId);
    }

    public UUID getCurrentUserId() {
        return CURRENT_USER.get();
    }

    public void clear() {
        CURRENT_TENANT.remove();
        CURRENT_USER.remove();
    }
}
```

### 4. JWT Token Provider

```java
@Component
public class JwtTokenProvider {

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    @Value("${app.jwt.expiration}")
    private long jwtExpirationMs;

    public String generateToken(Usuario usuario) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + jwtExpirationMs);

        return Jwts.builder()
                .setSubject(usuario.getId().toString())
                .claim("assessoriaId", usuario.getAssessoria().getId().toString())
                .claim("email", usuario.getEmail())
                .claim("role", usuario.getTipo().name())
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .signWith(SignatureAlgorithm.HS512, jwtSecret)
                .compact();
    }

    public UUID getUserIdFromToken(String token) {
        Claims claims = Jwts.parser()
                .setSigningKey(jwtSecret)
                .parseClaimsJws(token)
                .getBody();

        return UUID.fromString(claims.getSubject());
    }

    public UUID getAssessoriaIdFromToken(String token) {
        Claims claims = Jwts.parser()
                .setSigningKey(jwtSecret)
                .parseClaimsJws(token)
                .getBody();

        return UUID.fromString(claims.get("assessoriaId", String.class));
    }

    public String getRoleFromToken(String token) {
        Claims claims = Jwts.parser()
                .setSigningKey(jwtSecret)
                .parseClaimsJws(token)
                .getBody();

        return claims.get("role", String.class);
    }

    public boolean validateToken(String authToken) {
        try {
            Jwts.parser().setSigningKey(jwtSecret).parseClaimsJws(authToken);
            return true;
        } catch (SignatureException | MalformedJwtException | ExpiredJwtException |
                 UnsupportedJwtException | IllegalArgumentException ex) {
            // Log error
            return false;
        }
    }
}
```

---

## 🛡️ Filtros Automáticos por Tenant

### 1. Hibernate Filter

```java
@FilterDef(name = "tenantFilter", parameters = @ParamDef(name = "tenantId", type = UUID.class))
@Filter(name = "tenantFilter", condition = "assessoria_id = :tenantId")
public class Atleta {
    // ... campos ...
}
```

### 2. EntityListener para Tenant

```java
@Component
public class TenantEntityListener {

    @Autowired
    private TenantContext tenantContext;

    @PrePersist
    @PreUpdate
    public void setTenant(Object entity) {
        if (entity instanceof TenantAware) {
            TenantAware tenantAware = (TenantAware) entity;
            if (tenantAware.getAssessoriaId() == null) {
                tenantAware.setAssessoriaId(tenantContext.getCurrentTenantId());
            }
        }
    }
}

public interface TenantAware {
    UUID getAssessoriaId();
    void setAssessoriaId(UUID assessoriaId);
}
```

### 3. Hibernate Interceptor

```java
@Component
public class TenantHibernateInterceptor extends EmptyInterceptor {

    @Autowired
    private TenantContext tenantContext;

    @Override
    public String onPrepareStatement(String sql) {
        // Adiciona filtro WHERE assessoria_id = ? automaticamente
        if (sql.toLowerCase().contains("from tb_atleta") &&
            !sql.toLowerCase().contains("assessoria_id")) {
            // Lógica de injeção de filtro
        }
        return sql;
    }
}
```

---

## 🔄 Alterações nos Repositories

### Repository Base

```java
@NoRepositoryBean
public interface TenantAwareRepository<T, ID> extends JpaRepository<T, ID> {

    default List<T> findAllByCurrentTenant() {
        // Implementação via @Query com :#{tenantContext.currentTenantId}
        return findAll();
    }
}
```

### AtletaRepository (modificado)

```java
public interface AtletaRepository extends TenantAwareRepository<Atleta, UUID> {

    @Query("""
        SELECT a FROM Atleta a
        WHERE a.assessoria.id = :#{@tenantContext.getCurrentTenantId()}
        AND a.ativo = 'ATIVO'
        ORDER BY a.nome ASC
    """)
    List<Atleta> findAllAtletas();

    @Query("""
        SELECT a FROM Atleta a
        WHERE a.id = :id
        AND a.assessoria.id = :#{@tenantContext.getCurrentTenantId()}
    """)
    Optional<Atleta> findByIdSecure(UUID id);

    @Query("""
        SELECT a FROM Atleta a
        WHERE a.email = :email
        AND a.assessoria.id = :#{@tenantContext.getCurrentTenantId()}
    """)
    Optional<Atleta> findByEmailAndCurrentTenant(String email);
}
```

### PlanoSemanalRepository (modificado)

```java
public interface PlanoSemanalRepository extends TenantAwareRepository<PlanoSemanal, UUID> {

    @Query("""
        SELECT p FROM PlanoSemanal p
        JOIN FETCH p.atleta a
        WHERE p.atleta.id = :atletaId
        AND a.assessoria.id = :#{@tenantContext.getCurrentTenantId()}
        ORDER BY p.semanaInicio DESC
    """)
    List<PlanoSemanal> findByAtletaIdSecure(UUID atletaId);

    @Query("""
        SELECT p FROM PlanoSemanal p
        WHERE p.id = :id
        AND p.atleta.assessoria.id = :#{@tenantContext.getCurrentTenantId()}
    """)
    Optional<PlanoSemanal> findByIdSecure(UUID id);
}
```

---

## 🎛️ Alterações nos Services

### AtletaService (modificado)

```java
@Service
@RequiredArgsConstructor
public class AtletaServiceImpl implements AtletaService {

    private final AtletaRepository atletaRepository;
    private final TenantContext tenantContext;
    private final AssessoriaRepository assessoriaRepository;

    @Override
    @Transactional
    public Atleta createAtleta(AtletaInputDto dto) {
        UUID assessoriaId = tenantContext.getCurrentTenantId();

        // Verificar limite de atletas
        Assessoria assessoria = assessoriaRepository.findById(assessoriaId)
            .orElseThrow(() -> new ResourceNotFoundException("Assessoria não encontrada"));

        if (assessoria.getLimiteAtletas() != null &&
            assessoria.getQuantidadeAtletasAtivos() >= assessoria.getLimiteAtletas()) {
            throw new BusinessException("Limite de atletas atingido para o plano atual");
        }

        Atleta atleta = atletaMapper.toEntity(dto);
        atleta.setAssessoria(assessoria);

        Atleta saved = atletaRepository.save(atleta);

        // Atualizar contador
        assessoria.setQuantidadeAtletasAtivos(assessoria.getQuantidadeAtletasAtivos() + 1);
        assessoriaRepository.save(assessoria);

        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public AtletaOutputDto getAtletaById(UUID id) {
        Atleta atleta = atletaRepository.findByIdSecure(id)
            .orElseThrow(() -> new ResourceNotFoundException("Atleta não encontrado ou sem permissão"));

        return atletaMapper.toOutputDto(atleta);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AtletaOutputDto> getAllAtletas() {
        // Já filtra por tenant automaticamente
        return atletaRepository.findAllAtletas().stream()
            .map(atletaMapper::toOutputDto)
            .toList();
    }

    @Override
    @Transactional
    public void deleteAtleta(UUID id) {
        Atleta atleta = atletaRepository.findByIdSecure(id)
            .orElseThrow(() -> new ResourceNotFoundException("Atleta não encontrado"));

        atleta.setAtivo(AtletaStatus.INATIVO);
        atletaRepository.save(atleta);

        // Atualizar contador
        Assessoria assessoria = atleta.getAssessoria();
        assessoria.setQuantidadeAtletasAtivos(assessoria.getQuantidadeAtletasAtivos() - 1);
        assessoriaRepository.save(assessoria);
    }
}
```

---

## 🌐 Novos Controllers

### AuthController

```java
@RestController
@RequestMapping("/api/auth")
@Tag(name = "Autenticação", description = "Endpoints de autenticação e autorização")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final JwtTokenProvider tokenProvider;

    @PostMapping("/login")
    @Operation(summary = "Login", description = "Autentica usuário e retorna token JWT")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        Usuario usuario = authService.authenticate(request.getEmail(), request.getSenha());
        String token = tokenProvider.generateToken(usuario);

        return ResponseEntity.ok(LoginResponse.builder()
            .token(token)
            .tipo("Bearer")
            .expiresIn(jwtExpirationMs)
            .usuario(UsuarioDto.from(usuario))
            .build());
    }

    @PostMapping("/refresh")
    @Operation(summary = "Refresh Token", description = "Renova token JWT")
    public ResponseEntity<LoginResponse> refresh(@RequestHeader("Authorization") String token) {
        // Implementação de refresh
        return ResponseEntity.ok(/* novo token */);
    }

    @PostMapping("/logout")
    @Operation(summary = "Logout", description = "Invalida token do usuário")
    public ResponseEntity<Void> logout() {
        // Implementação de logout (blacklist de token)
        return ResponseEntity.noContent().build();
    }
}
```

### AssessoriaController

```java
@RestController
@RequestMapping("/api/assessoria")
@Tag(name = "Assessoria", description = "Gerenciamento de assessorias")
@PreAuthorize("hasAnyRole('ADMIN_SISTEMA', 'ADMIN_ASSESSORIA')")
@RequiredArgsConstructor
public class AssessoriaController {

    private final AssessoriaService assessoriaService;
    private final TenantContext tenantContext;

    @GetMapping("/info")
    @Operation(summary = "Informações da assessoria", description = "Retorna dados da assessoria atual")
    public ResponseEntity<AssessoriaDto> getInfo() {
        UUID assessoriaId = tenantContext.getCurrentTenantId();
        return ResponseEntity.ok(assessoriaService.getById(assessoriaId));
    }

    @PutMapping("/info")
    @Operation(summary = "Atualizar assessoria", description = "Atualiza dados da assessoria")
    public ResponseEntity<AssessoriaDto> update(@Valid @RequestBody AssessoriaUpdateDto dto) {
        UUID assessoriaId = tenantContext.getCurrentTenantId();
        return ResponseEntity.ok(assessoriaService.update(assessoriaId, dto));
    }

    @GetMapping("/estatisticas")
    @Operation(summary = "Estatísticas da assessoria")
    public ResponseEntity<EstatisticasDto> getEstatisticas() {
        UUID assessoriaId = tenantContext.getCurrentTenantId();
        return ResponseEntity.ok(assessoriaService.getEstatisticas(assessoriaId));
    }

    @GetMapping("/limite-atletas")
    @Operation(summary = "Verificar limite de atletas")
    public ResponseEntity<LimiteAtletasDto> getLimiteAtletas() {
        UUID assessoriaId = tenantContext.getCurrentTenantId();
        return ResponseEntity.ok(assessoriaService.getLimiteAtletas(assessoriaId));
    }
}
```

### UsuarioController

```java
@RestController
@RequestMapping("/api/usuarios")
@Tag(name = "Usuários", description = "Gerenciamento de usuários da assessoria")
@PreAuthorize("hasRole('ADMIN_ASSESSORIA')")
@RequiredArgsConstructor
public class UsuarioController {

    private final UsuarioService usuarioService;
    private final TenantContext tenantContext;

    @PostMapping
    @Operation(summary = "Criar usuário", description = "Cria novo usuário na assessoria")
    public ResponseEntity<UsuarioDto> create(@Valid @RequestBody UsuarioCreateDto dto) {
        UUID assessoriaId = tenantContext.getCurrentTenantId();
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(usuarioService.create(assessoriaId, dto));
    }

    @GetMapping
    @Operation(summary = "Listar usuários", description = "Lista todos os usuários da assessoria")
    public ResponseEntity<List<UsuarioDto>> listAll() {
        UUID assessoriaId = tenantContext.getCurrentTenantId();
        return ResponseEntity.ok(usuarioService.findByAssessoria(assessoriaId));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Atualizar usuário")
    public ResponseEntity<UsuarioDto> update(
            @PathVariable UUID id,
            @Valid @RequestBody UsuarioUpdateDto dto) {
        UUID assessoriaId = tenantContext.getCurrentTenantId();
        return ResponseEntity.ok(usuarioService.update(assessoriaId, id, dto));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Desativar usuário")
    public ResponseEntity<Void> deactivate(@PathVariable UUID id) {
        UUID assessoriaId = tenantContext.getCurrentTenantId();
        usuarioService.deactivate(assessoriaId, id);
        return ResponseEntity.noContent().build();
    }
}
```

---

## 🗄️ Cache Multi-Tenant

### Cache Configuration

```java
@Configuration
@EnableCaching
public class MultiTenantCacheConfig {

    @Bean
    public CacheManager cacheManager(TenantContext tenantContext) {
        return new CaffeineCacheManager() {
            @Override
            protected Cache createCaffeineCache(String name) {
                return new TenantAwareCache(
                    name,
                    Caffeine.newBuilder()
                        .expireAfterWrite(30, TimeUnit.MINUTES)
                        .maximumSize(1000)
                        .build(),
                    tenantContext
                );
            }
        };
    }
}
```

### Tenant-Aware Cache

```java
public class TenantAwareCache extends CaffeineCache {

    private final TenantContext tenantContext;

    public TenantAwareCache(String name,
                            com.github.benmanes.caffeine.cache.Cache<Object, Object> cache,
                            TenantContext tenantContext) {
        super(name, cache);
        this.tenantContext = tenantContext;
    }

    @Override
    protected Object lookup(Object key) {
        String tenantKey = getTenantKey(key);
        return super.lookup(tenantKey);
    }

    @Override
    public void put(Object key, Object value) {
        String tenantKey = getTenantKey(key);
        super.put(tenantKey, value);
    }

    private String getTenantKey(Object key) {
        UUID tenantId = tenantContext.getCurrentTenantId();
        return tenantId + ":" + key;
    }
}
```

---

## 📊 Migrations (Flyway)

### V1__create_multi_tenancy_tables.sql

```sql
-- =====================================================
-- TABELA: tb_assessoria
-- =====================================================
CREATE TABLE tb_assessoria (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    nome VARCHAR(200) NOT NULL,
    descricao VARCHAR(500),
    dominio VARCHAR(100) UNIQUE NOT NULL,
    email_contato VARCHAR(200),
    telefone VARCHAR(20),
    cnpj VARCHAR(20),
    endereco VARCHAR(500),
    logo_url VARCHAR(500),
    plano_assinatura VARCHAR(50) NOT NULL DEFAULT 'BASICO',
    limite_atletas INTEGER,
    quantidade_atletas_ativos INTEGER DEFAULT 0,
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    timezone VARCHAR(50) DEFAULT 'America/Sao_Paulo',
    idioma VARCHAR(10) DEFAULT 'pt-BR',
    data_criacao TIMESTAMP NOT NULL DEFAULT NOW(),
    data_atualizacao TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_plano_assinatura CHECK (plano_assinatura IN ('BASICO', 'PREMIUM', 'ENTERPRISE'))
);

CREATE INDEX idx_assessoria_ativo ON tb_assessoria(ativo);
CREATE INDEX idx_assessoria_dominio ON tb_assessoria(dominio);

-- =====================================================
-- TABELA: tb_usuario
-- =====================================================
CREATE TABLE tb_usuario (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    nome VARCHAR(200) NOT NULL,
    email VARCHAR(200) UNIQUE NOT NULL,
    senha VARCHAR(255) NOT NULL,
    telefone VARCHAR(20),
    foto_perfil_url VARCHAR(500),
    tipo VARCHAR(50) NOT NULL,
    assessoria_id UUID NOT NULL REFERENCES tb_assessoria(id) ON DELETE CASCADE,
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    data_criacao TIMESTAMP NOT NULL DEFAULT NOW(),
    data_ultimo_acesso TIMESTAMP,
    ultimo_ip_acesso VARCHAR(50),
    CONSTRAINT chk_tipo_usuario CHECK (tipo IN ('ADMIN_SISTEMA', 'ADMIN_ASSESSORIA', 'TREINADOR', 'VISUALIZADOR'))
);

CREATE INDEX idx_usuario_email ON tb_usuario(email);
CREATE INDEX idx_usuario_assessoria ON tb_usuario(assessoria_id);

-- =====================================================
-- ADICIONAR COLUNA assessoria_id NAS TABELAS EXISTENTES
-- =====================================================

-- Atleta
ALTER TABLE tb_atleta ADD COLUMN assessoria_id UUID;
ALTER TABLE tb_atleta ADD COLUMN email VARCHAR(200);
CREATE INDEX idx_atleta_assessoria ON tb_atleta(assessoria_id);
CREATE INDEX idx_atleta_email ON tb_atleta(email);

-- PlanoSemanal
ALTER TABLE tb_plano_semanal ADD COLUMN assessoria_id UUID;
CREATE INDEX idx_plano_semanal_assessoria ON tb_plano_semanal(assessoria_id);

-- TreinoRealizado
ALTER TABLE tb_treino_realizado ADD COLUMN assessoria_id UUID;
CREATE INDEX idx_treino_realizado_assessoria ON tb_treino_realizado(assessoria_id);

-- Prova
ALTER TABLE tb_prova ADD COLUMN assessoria_id UUID;
CREATE INDEX idx_prova_assessoria ON tb_prova(assessoria_id);

-- =====================================================
-- POPULAR assessoria_id PARA DADOS EXISTENTES
-- =====================================================

-- Criar assessoria padrão para migração
INSERT INTO tb_assessoria (id, nome, dominio, email_contato, plano_assinatura, ativo)
VALUES (
    '00000000-0000-0000-0000-000000000001',
    'Assessoria Legado',
    'legado',
    'contato@legado.com',
    'ENTERPRISE',
    TRUE
);

-- Atualizar todos os registros existentes
UPDATE tb_atleta SET assessoria_id = '00000000-0000-0000-0000-000000000001' WHERE assessoria_id IS NULL;
UPDATE tb_plano_semanal SET assessoria_id = '00000000-0000-0000-0000-000000000001' WHERE assessoria_id IS NULL;
UPDATE tb_treino_realizado SET assessoria_id = '00000000-0000-0000-0000-000000000001' WHERE assessoria_id IS NULL;
UPDATE tb_prova SET assessoria_id = '00000000-0000-0000-0000-000000000001' WHERE assessoria_id IS NULL;

-- Tornar assessoria_id obrigatório
ALTER TABLE tb_atleta ALTER COLUMN assessoria_id SET NOT NULL;
ALTER TABLE tb_plano_semanal ALTER COLUMN assessoria_id SET NOT NULL;
ALTER TABLE tb_treino_realizado ALTER COLUMN assessoria_id SET NOT NULL;
ALTER TABLE tb_prova ALTER COLUMN assessoria_id SET NOT NULL;

-- Adicionar FKs
ALTER TABLE tb_atleta ADD CONSTRAINT fk_atleta_assessoria
    FOREIGN KEY (assessoria_id) REFERENCES tb_assessoria(id) ON DELETE RESTRICT;

ALTER TABLE tb_plano_semanal ADD CONSTRAINT fk_plano_semanal_assessoria
    FOREIGN KEY (assessoria_id) REFERENCES tb_assessoria(id) ON DELETE RESTRICT;

ALTER TABLE tb_treino_realizado ADD CONSTRAINT fk_treino_realizado_assessoria
    FOREIGN KEY (assessoria_id) REFERENCES tb_assessoria(id) ON DELETE RESTRICT;

ALTER TABLE tb_prova ADD CONSTRAINT fk_prova_assessoria
    FOREIGN KEY (assessoria_id) REFERENCES tb_assessoria(id) ON DELETE RESTRICT;
```

---

## 🔧 Configuração do application.yml

```yaml
app:
  # ----------------------------------------
  # JWT CONFIGURATION
  # ----------------------------------------
  jwt:
    secret: ${JWT_SECRET:your-256-bit-secret-key-change-in-production}
    expiration: 86400000 # 24 horas em millisegundos

  # ----------------------------------------
  # MULTI-TENANCY CONFIGURATION
  # ----------------------------------------
  multi-tenancy:
    enabled: true
    default-tenant: "00000000-0000-0000-0000-000000000001"

  # ----------------------------------------
  # PLANOS DE ASSINATURA
  # ----------------------------------------
  subscription:
    basico:
      limite-atletas: 50
      preco-mensal: 199.00
    premium:
      limite-atletas: 200
      preco-mensal: 499.00
    enterprise:
      limite-atletas: null # ilimitado
      preco-mensal: 999.00
```

---

## 📝 DTOs Necessários

### LoginRequest / LoginResponse

```java
@Data
@Builder
public class LoginRequest {
    @NotBlank
    @Email
    private String email;

    @NotBlank
    @Size(min = 6)
    private String senha;
}

@Data
@Builder
public class LoginResponse {
    private String token;
    private String tipo;
    private Long expiresIn;
    private UsuarioDto usuario;
}
```

### UsuarioDto

```java
@Data
@Builder
public class UsuarioDto {
    private UUID id;
    private String nome;
    private String email;
    private String telefone;
    private TipoUsuario tipo;
    private UUID assessoriaId;
    private String assessoriaNome;
    private Boolean ativo;
    private LocalDateTime dataCriacao;

    public static UsuarioDto from(Usuario usuario) {
        return UsuarioDto.builder()
            .id(usuario.getId())
            .nome(usuario.getNome())
            .email(usuario.getEmail())
            .telefone(usuario.getTelefone())
            .tipo(usuario.getTipo())
            .assessoriaId(usuario.getAssessoria().getId())
            .assessoriaNome(usuario.getAssessoria().getNome())
            .ativo(usuario.getAtivo())
            .dataCriacao(usuario.getDataCriacao())
            .build();
    }
}
```

### AssessoriaDto

```java
@Data
@Builder
public class AssessoriaDto {
    private UUID id;
    private String nome;
    private String descricao;
    private String dominio;
    private String emailContato;
    private String telefone;
    private String logoUrl;
    private PlanoAssinatura planoAssinatura;
    private Integer limiteAtletas;
    private Integer quantidadeAtletasAtivos;
    private Boolean ativo;
    private LocalDateTime dataCriacao;
}
```

---

## 🎯 Plano de Implementação Faseado

### Fase 1: Estrutura Base (2-3 semanas)
1. ✅ Criar entidades `Assessoria` e `Usuario`
2. ✅ Adicionar campo `assessoria_id` nas entidades principais
3. ✅ Criar migrations Flyway
4. ✅ Implementar `TenantContext` e `TenantEntityListener`
5. ✅ Configurar Hibernate Filters

### Fase 2: Segurança (2 semanas)
1. ✅ Implementar Spring Security
2. ✅ Criar JWT Authentication Filter
3. ✅ Implementar JwtTokenProvider
4. ✅ Criar AuthController e AuthService
5. ✅ Adicionar tratamento de exceções de segurança

### Fase 3: Repositories e Services (2 semanas)
1. ✅ Modificar todos os repositories para filtrar por tenant
2. ✅ Atualizar services para usar contexto de tenant
3. ✅ Adicionar validações de limite de atletas
4. ✅ Implementar audit logs

### Fase 4: Controllers e APIs (1 semana)
1. ✅ Criar AssessoriaController
2. ✅ Criar UsuarioController
3. ✅ Atualizar controllers existentes com anotações de segurança
4. ✅ Atualizar documentação Swagger

### Fase 5: Cache e Performance (1 semana)
1. ✅ Implementar cache multi-tenant
2. ✅ Otimizar queries com índices
3. ✅ Testes de performance

### Fase 6: Testes (2 semanas)
1. ✅ Testes unitários de isolamento de tenant
2. ✅ Testes de integração multi-tenant
3. ✅ Testes de segurança (tentativas de cross-tenant access)
4. ✅ Testes de carga

### Fase 7: Deploy e Monitoramento (1 semana)
1. ✅ Deploy em staging
2. ✅ Configurar logs e métricas
3. ✅ Validação final
4. ✅ Deploy em produção

**Total: 11-12 semanas**

---

## ⚠️ Pontos de Atenção

### 1. Segurança Crítica

❌ **Nunca confiar apenas em filtros JPA/Hibernate**
- Sempre validar tenant_id explicitamente em operações críticas
- Usar queries nomeadas com verificação de tenant

❌ **Cross-Tenant Data Leakage**
```java
// ERRADO
@Query("SELECT a FROM Atleta a WHERE a.id = :id")
Optional<Atleta> findById(UUID id);

// CORRETO
@Query("SELECT a FROM Atleta a WHERE a.id = :id AND a.assessoria.id = :#{@tenantContext.getCurrentTenantId()}")
Optional<Atleta> findByIdSecure(UUID id);
```

### 2. Performance

⚠️ **Índices são essenciais**
```sql
CREATE INDEX idx_atleta_assessoria ON tb_atleta(assessoria_id);
CREATE INDEX idx_atleta_assessoria_ativo ON tb_atleta(assessoria_id, ativo);
```

⚠️ **Cuidado com N+1 queries**
```java
// Use JOIN FETCH para evitar N+1
@Query("""
    SELECT DISTINCT a FROM Atleta a
    LEFT JOIN FETCH a.planoMetaDados
    WHERE a.assessoria.id = :tenantId
""")
List<Atleta> findAllWithMetadados(UUID tenantId);
```

### 3. Migrations

✅ **Sempre testar migrations em staging**
✅ **Criar backups antes de alterar esquema**
✅ **Migrations devem ser reversíveis**

### 4. Testes de Isolamento

```java
@Test
void deveImpedirAcessoCrossTenant() {
    UUID tenant1 = criarTenant("Assessoria A");
    UUID tenant2 = criarTenant("Assessoria B");

    Atleta atletaTenant1 = criarAtleta(tenant1);

    // Simular acesso do tenant2 ao atleta do tenant1
    tenantContext.setCurrentTenantId(tenant2);

    // Deve lançar exceção ou retornar vazio
    assertThrows(ResourceNotFoundException.class, () ->
        atletaService.getAtletaById(atletaTenant1.getId())
    );
}
```

---

## 📚 Documentação Adicional

### Endpoints de Autenticação

```
POST   /api/auth/login           - Login
POST   /api/auth/refresh         - Refresh token
POST   /api/auth/logout          - Logout
POST   /api/auth/forgot-password - Recuperar senha
POST   /api/auth/reset-password  - Resetar senha
```

### Endpoints de Assessoria

```
GET    /api/assessoria/info             - Informações da assessoria
PUT    /api/assessoria/info             - Atualizar assessoria
GET    /api/assessoria/estatisticas     - Estatísticas
GET    /api/assessoria/limite-atletas   - Verificar limite
```

### Endpoints de Usuários

```
POST   /api/usuarios              - Criar usuário
GET    /api/usuarios              - Listar usuários
GET    /api/usuarios/{id}         - Buscar usuário
PUT    /api/usuarios/{id}         - Atualizar usuário
DELETE /api/usuarios/{id}         - Desativar usuário
```

---

## 🔍 Monitoramento e Logs

### Logs Estruturados

```java
@Slf4j
@Component
public class TenantAuditLogger {

    @Autowired
    private TenantContext tenantContext;

    public void logAcesso(String recurso, String acao) {
        log.info("Tenant={} User={} Acao={} Recurso={}",
            tenantContext.getCurrentTenantId(),
            tenantContext.getCurrentUserId(),
            acao,
            recurso
        );
    }

    public void logAcessoNegado(String recurso) {
        log.warn("ACESSO_NEGADO Tenant={} User={} Recurso={}",
            tenantContext.getCurrentTenantId(),
            tenantContext.getCurrentUserId(),
            recurso
        );
    }
}
```

### Métricas Prometheus

```java
@Component
public class TenantMetrics {

    private final Counter acessosCounter;
    private final Counter acessosNegadosCounter;

    public TenantMetrics(MeterRegistry registry) {
        this.acessosCounter = Counter.builder("tenant.acessos")
            .description("Total de acessos por tenant")
            .tag("tipo", "total")
            .register(registry);

        this.acessosNegadosCounter = Counter.builder("tenant.acessos.negados")
            .description("Total de acessos negados")
            .tag("tipo", "negado")
            .register(registry);
    }
}
```

---

## ✅ Checklist de Implementação

### Banco de Dados
- [ ] Criar tabela `tb_assessoria`
- [ ] Criar tabela `tb_usuario`
- [ ] Adicionar coluna `assessoria_id` em todas as entidades
- [ ] Criar índices de performance
- [ ] Testar migrations em staging

### Entidades e Mapeamento
- [ ] Criar entidade `Assessoria`
- [ ] Criar entidade `Usuario`
- [ ] Adicionar campo `assessoria` nas entidades existentes
- [ ] Implementar interface `TenantAware`
- [ ] Configurar Hibernate Filters

### Segurança
- [ ] Implementar Spring Security
- [ ] Criar JWT Authentication Filter
- [ ] Implementar JwtTokenProvider
- [ ] Criar TenantContext
- [ ] Adicionar validações de acesso

### Repositories
- [ ] Atualizar AtletaRepository
- [ ] Atualizar PlanoSemanalRepository
- [ ] Atualizar TreinoRealizadoRepository
- [ ] Criar AssessoriaRepository
- [ ] Criar UsuarioRepository

### Services
- [ ] Atualizar AtletaService
- [ ] Atualizar PlanoService
- [ ] Atualizar TreinoService
- [ ] Criar AssessoriaService
- [ ] Criar UsuarioService
- [ ] Criar AuthService

### Controllers
- [ ] Criar AuthController
- [ ] Criar AssessoriaController
- [ ] Criar UsuarioController
- [ ] Atualizar controllers existentes
- [ ] Adicionar anotações de segurança

### Cache
- [ ] Implementar TenantAwareCache
- [ ] Configurar CacheManager multi-tenant
- [ ] Testar isolamento de cache

### Testes
- [ ] Testes de isolamento de tenant
- [ ] Testes de segurança (cross-tenant)
- [ ] Testes de autenticação
- [ ] Testes de autorização
- [ ] Testes de performance

### Documentação
- [ ] Atualizar Swagger/OpenAPI
- [ ] Documentar novos endpoints
- [ ] Criar guia de onboarding para assessorias
- [ ] Documentar estrutura de permissões

---

## 🚀 Próximos Passos

1. **Validação do Relatório**
   - Revisar com equipe de desenvolvimento
   - Validar requisitos de negócio
   - Aprovar estratégia de multi-tenancy

2. **Setup do Projeto**
   - Criar branch feature/multi-tenancy
   - Configurar ambiente de desenvolvimento
   - Preparar banco de dados de teste

3. **Início da Fase 1**
   - Criar entidades base
   - Implementar migrations
   - Testes iniciais

4. **Iterações Semanais**
   - Reuniões de acompanhamento
   - Code reviews
   - Ajustes e refinamentos

---

**Documento criado em**: 2025-10-08
**Versão**: 1.0
**Status**: ✅ Pronto para revisão
**Estimativa de implementação**: 11-12 semanas
**Complexidade**: Alta
**Prioridade**: Crítica para escalabilidade do produto