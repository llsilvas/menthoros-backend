# Menthoros Services - Guia de Arquitetura e Padrões

## 📋 Visão Geral

**Menthoros Services** é uma aplicação Spring Boot 3.5.4 em Java 21 projetada para gerenciar atletas e seus planos de treinamento com suporte a **multi-tenancy**. A arquitetura segue padrões de microsserviços modernos com integração de IA, cache distribuído e autenticação OAuth2 via Keycloak.

**Branch Principal:** `develop`
**Branch de Sprint:** `sprint-01`
**Versão:** 0.0.1-SNAPSHOT

---

## 🏗️ Arquitetura

### Padrão Arquitetural: Arquitetura em Camadas

```
┌─────────────────────────────────────────────────┐
│        PRESENTATION LAYER (Controllers)          │
├─────────────────────────────────────────────────┤
│        SERVICE LAYER (Business Logic)            │
│  ├── AtletaService                              │
│  ├── PlanoService                               │
│  ├── TreinoService                              │
│  ├── IaService                                  │
│  └── Serviços auxiliares (TSB, Métricas, etc)  │
├─────────────────────────────────────────────────┤
│   REPOSITORY LAYER (Data Access - Spring JPA)   │
├─────────────────────────────────────────────────┤
│  DATABASE LAYER (PostgreSQL + pgvector)         │
└─────────────────────────────────────────────────┘
```

### Padrões de Design Implementados

1. **Dependency Injection (DI):** Injeta dependências via Spring Framework
2. **Repository Pattern:** Acesso aos dados via Spring Data JPA
3. **DTO Pattern:** Separação entre camada de entrada/saída (Input/Output DTOs)
4. **Mapper Pattern:** MapStruct para conversão entre DTOs e Entities
5. **Service Layer Pattern:** Lógica de negócios isolada em serviços
6. **Global Exception Handling:** Handler centralizado para exceções
7. **Multi-Tenancy:** Isolamento de dados por tenant via TenantContext e Keycloak

---

## 🛠️ Stack Tecnológico

### Core Framework
- **Java 21** - Linguagem com features modernas (Records, Virtual Threads, etc)
- **Spring Boot 3.5.4** - Framework web robusto
- **Maven** - Gerenciador de dependências e build
- **Spring Data JPA** - ORM com Hibernate

### Banco de Dados
- **PostgreSQL 15+** - Banco relacional principal
- **pgvector 0.1.6** - Extensão para armazenar embeddings (vetores)
- **Flyway 11.7.2** - Controle de versão de schema de banco de dados
- **H2** - Banco em memória para testes

### IA e ML
- **Spring AI 1.0.0-M6** - Integração com modelos de IA
- **OpenAI API** - Modelos:
  - `gpt-4o` - Chat/análise (temperature: 0.2, max-tokens: 12000)
  - `text-embedding-3-small` - Embeddings (dimensões: 1536)

### Segurança e Autenticação
- **Spring Security** - Framework de segurança
- **OAuth2 Resource Server** - Validação de tokens JWT
- **Keycloak 25.0.3** - Identity Provider (IdP) para multi-tenancy
- **JWT** - Tokens com claims de tenant_id

### Performance e Cache
- **Spring Cache** - Abstração de caching
- **Caffeine** - Cache local in-memory (TTL: 30min, max-size: 1000)
- **Micrometer Prometheus** - Métricas e observabilidade

### Desenvolvimento e Documentação
- **Lombok 1.18.38** - Reduz boilerplate (getters, setters, constructores)
- **MapStruct 1.6.3** - Mapeamento automático entre entidades e DTOs
- **SpringDoc OpenAPI 2.8.5** - Documentação automática Swagger/OpenAPI
- **JUnit 5** - Framework de testes
- **Testcontainers** - Testes com containers Docker (PostgreSQL, etc)
- **Spring Boot Test** - Testes de integração
- **Byte Buddy Agent** - Mock inline para Mockito em ambientes restritos

### DevOps e Build
- **Docker** - Containerização
- **Docker Compose** - Orquestração local
- **JKube 1.18.1** - Deploy em Kubernetes/OpenShift
- **JaCoCo 0.8.11** - Cobertura de testes
- **Git Commit ID Maven Plugin** - Injeta info do commit no app

---

## 📁 Estrutura de Diretórios

```
src/main/java/com/menthoros/
├── config/              # Configurações Spring (Security, Cache, LLM, CORS, Jackson, Clock)
├── controller/          # REST Controllers
│   ├── AtletaController
│   ├── PlanoTreinoController
│   ├── TreinoRealizadoController
│   └── ErrorHandlerController
├── service/             # Lógica de negócio
│   ├── impl/            # Implementações dos serviços
│   ├── helper/          # Serviços auxiliares (TsbService, ZonaTreino, etc)
│   ├── prompt/          # Templates de prompts para LLM
│   ├── AtletaService
│   ├── PlanoService
│   ├── TreinoService
│   ├── IaService
│   ├── EmbeddingService
│   ├── MetricasAgregadasService
│   ├── PlanoMetadadosService
│   └── UsuarioSyncService
├── repository/          # Spring Data JPA Repositories
├── entity/              # Entidades JPA (mapeiam tabelas do BD)
│   ├── Atleta
│   ├── PlanoTreino
│   ├── PlanoSemanal
│   ├── TreinoPlanejado
│   ├── TreinoRealizado
│   ├── EtapaRealizada
│   ├── Prova
│   └── MetricasDiarias
├── dto/                 # Data Transfer Objects
│   ├── input/           # DTOs de entrada (requisições)
│   ├── output/          # DTOs de saída (respostas)
│   └── llm/             # DTOs específicos para LLM
├── mapper/              # MapStruct Mappers
│   ├── AtletaMapper
│   ├── PlanoMapper
│   ├── EtapaMapper
│   ├── PlanoSemanalMapper
│   └── TreinoMapper
├── exception/           # Exceções customizadas
│   ├── handler/         # GlobalExceptionHandler
│   ├── ResourceNotFoundException
│   ├── DuplicateResourceException
│   ├── DomainRuleViolationException
│   ├── LLMException
│   └── DomainNotFoundException
├── enums/               # Enumerações de domínio
│   ├── AtletaStatus, AtletaGenero
│   ├── TipoEtapa, TipoProva, DistanciaProva
│   ├── PlanoStatus, TreinoPlanejamentoStatus
│   ├── TipoTreino, FasePeriodizacao
│   ├── DiaSemana, NivelExperiencia
│   ├── FonteDados, StatusSincronizacao
│   └── MetricasThresholds, FaixaTsb
├── multitenancy/        # Multi-tenancy
│   └── TenantContext    # Context para isolamento de tenant
├── security/            # Segurança
│   └── JwtTenantFilter  # Filtro para extrair tenant_id do JWT
├── converter/           # Conversores de tipo
│   └── FloatListToVectorConverter  # Para pgvector
├── util/                # Utilidades gerais
└── MenthorosServicesApplication.java

src/main/resources/
├── application.yml      # Configuração principal
├── application-test.yml # Configuração de testes
└── db/
    ├── migration/       # Scripts Flyway (Vxx__*.sql)
    └── init/            # Scripts de inicialização
```

---

## 🔑 Conceitos Principais

### Multi-Tenancy
- **Implementação:** Baseada em Keycloak com JWT claims
- **TenantContext:** ThreadLocal para armazenar tenant_id durante requisição
- **JwtTenantFilter:** Extrai tenant_id do token JWT e popula TenantContext
- **Isolamento:** Queries filtram automaticamente por tenant_id
- **Realms Keycloak:** Um realm por assessoria/organização

### Tratamento de Exceções
```java
GlobalExceptionHandler mapeia:
- MethodArgumentNotValidException → 400 Bad Request
- ResourceNotFoundException → 404 Not Found
- OptimisticLockException → 409 Conflict
- DuplicateResourceException → 409 Conflict
- DataIntegrityViolationException → 409 Conflict
- LLMException → 503 Service Unavailable
- RuntimeException (OpenAI) → 502 Bad Gateway
- Exception (genérica) → 500 Internal Server Error
```

### Mapeamento de Entidades (MapStruct)
- **Config:** `@Mapper(componentModel = "spring", unmappedTargetPolicy = IGNORE)`
- **Injection:** Constructor-based (seguro para immutabilidade)
- **DTOs separados:** InputDto (requisições) vs OutputDto (respostas)
- **LLM DTOs:** Estrutura específica para prompts de IA

### Cache (Caffeine)
- **TTL:** 30 minutos (configurável: `app.cache.default-ttl`)
- **Tamanho máximo:** 1000 entradas
- **Uso:** Principalmente em listagens e dados frequentemente acessados
- **Anotações:** `@Cacheable`, `@CachePut`, `@CacheEvict`

### Segurança (Spring Security + OAuth2)
```yaml
# Configuração Keycloak
issuer-uri: http://localhost:8443/realms/menthoros-app
jwk-set-uri: http://localhost:8443/realms/menthoros-app/protocol/openid-connect/certs

# Claims no JWT:
- sub (user ID)
- tenant_id (isolamento multi-tenancy)
- roles (ADMIN, TECNICO, VISUALIZADOR)
```

### Endpoints Padrão

#### Atleta
- `POST /atleta` - Criar atleta
- `GET /atleta` - Listar atletas
- `GET /atleta/{id}` - Buscar por ID
- `PUT /atleta/{id}` - Atualizar
- `DELETE /atleta/{id}` - Deletar (soft delete)
- `GET /atleta/{id}/recalcular-metricas` - Recalcular métricas

#### Plano de Treino
- `POST /api/planos/gerar/{atletaId}` - Gerar plano (com IA)
- Outros endpoints em PlanoTreinoController

#### Treino
- `POST /api/treinos` - Registrar treino realizado
- Endpoints em TreinoRealizadoController

#### Documentação
- `GET /swagger-ui.html` - Swagger UI
- `GET /api-docs` - OpenAPI JSON

---

## 🔒 Padrões de Segurança

### Input Validation
```java
@Valid @RequestBody - Valida DTOs
@NotNull, @NotBlank, @Email, @Positive - Jakarta Validation
```

### Database Connection
```yaml
datasource:
  url: jdbc:postgresql://${DB_HOST}:${DB_PORT}/${DB_NAME}
  username: ${DB_USER}
  password: ${DB_PASSWORD}  # Via .env (nunca hardcode)
```

### CORS Configuration
```yaml
app.cors:
  allowed-origins: http://localhost:5173,http://localhost:3000
  allowed-methods: GET,POST,PUT,DELETE,OPTIONS
  allow-credentials: true
```

### Logging
```yaml
logging:
  level:
    root: info  # Produção: info, Desenvolvimento: debug
```

---

## 🧪 Testes

### Estrutura de Testes
```
src/test/java/com/menthoros/
├── unit/              # Testes unitários (*Test.java)
└── integration/       # Testes de integração (*IT.java)
```

### Configuração Maven
- **Surefire:** Executa testes unitários
- **Failsafe:** Executa testes de integração
- **Testcontainers:** Spins up PostgreSQL em testes
- **JaCoCo:** Gera relatório de cobertura

### Executando Testes
```bash
./mvnw test                    # Apenas unitários
./mvnw failsafe:integration-test  # Apenas integração
./mvnw verify                  # Unitários + integração + coverage
```

---

## 📊 Configuração de LLM (OpenAI)

### Chat
```yaml
model: gpt-4o
temperature: 0.2      # Baixa criatividade, respostas determinísticas
max-tokens: 12000     # Limite de tokens na resposta
top-p: 0.9           # Nucleus sampling
frequency-penalty: 0.1
presence-penalty: 0.0
```

### Embeddings
```yaml
model: text-embedding-3-small
dimensions: 1536      # Tamanho do vetor para pgvector
```

### Serviços de IA
- **EmbeddingService:** Gera embeddings de textos
- **IaService:** Orquestra interações com LLM
- **Prompts:** Armazenados em `services/prompt/`

---

## 🚀 Build e Deploy

### Build Local
```bash
./mvnw clean package           # Build JAR
./mvnw clean package -DskipTests  # Pula testes (rápido)
./mvnw spring-boot:run         # Executa localmente
```

### Docker
```bash
# Desenvolvimento
docker-compose up -d           # Inicia PostgreSQL, Keycloak, Redis
docker-compose down            # Para serviços

# Multi-tenancy
docker-compose -f docker-compose.multi-tenancy.yml up -d

# Build imagem
mvn clean package -Pdocker     # Ativa profile docker
```

### Migrations (Flyway)
- Arquivos: `src/main/resources/db/migration/Vxx__*.sql`
- Convenção: `V{numero}__{descricao}.sql`
- Executadas automaticamente no startup
- `flyway:baseline` - Se já existe schema sem versão
- **Importante:** Nunca modificar migrations já executadas

---

## 💡 Patterns e Boas Práticas

### 1. Service Layer
```java
// Interface
public interface PlanoService {
    PlanoTreino criarPlano(DadosPlanoDto dados);
}

// Implementação
@Service
@RequiredArgsConstructor
public class PlanoServiceImpl implements PlanoService {
    // Injeção por constructor via Lombok
}
```

### 2. DTO Conversão
```java
// No controller
Atleta atleta = atletaService.createAtleta(inputDto);
AtletaOutputDto response = atletaMapper.toOutputDto(atleta);
return ResponseEntity.status(HttpStatus.CREATED).body(response);
```

### 3. Exception Handling
```java
// Serviço lança exceção
if (atleta == null) {
    throw new ResourceNotFoundException("Atleta não encontrado");
}

// GlobalExceptionHandler mapeia para HTTP 404
@ExceptionHandler(ResourceNotFoundException.class)
public ResponseEntity<Map<String, Object>> handleNotFound(ResourceNotFoundException ex) {
    // Resposta padronizada
}
```

### 4. Cache com TTL
```java
@Cacheable(value = "atletas", key = "#id", unless = "#result == null")
public AtletaOutputDto getAtletaById(UUID id) {
    // Consulta DB apenas na primeira vez
}
```

### 5. Validação
```java
public class AtletaInputDto {
    @NotBlank(message = "Nome é obrigatório")
    private String nome;

    @Email(message = "Email inválido")
    private String email;
}
```

---

## 📝 Configuração do Projeto

### Variáveis de Ambiente (.env)
```env
# Server
SERVER_PORT=8080

# Database
DB_HOST=localhost
DB_PORT=5433
DB_NAME=menthoros-multi
DB_USER=menthoros
DB_PASSWORD=menthoros123

# Keycloak
KEYCLOAK_ISSUER_URI=http://localhost:8443/realms/menthoros-app
KEYCLOAK_JWK_URI=http://localhost:8443/realms/menthoros-app/protocol/openid-connect/certs
KC_ADMIN_USER=admin
KC_ADMIN_PASSWORD=admin123

# OpenAI
OPENAI_API_KEY=seu_api_key

# CORS
CORS_ALLOWED_ORIGINS=http://localhost:5173,http://localhost:3000
```

---

## 🔄 Git Workflow

### Branch Strategy
- `main` - Produção (protegido)
- `develop` - Desenvolvimento (base para features)
- `feature/*` - Novas features
- `sprint-*` - Sprints de desenvolvimento
- `bugfix/*` - Correções de bugs

### Commits
```bash
[feat](modulo): descrição breve
[fix](modulo): descrição breve
[refactor](modulo): descrição breve
[test](modulo): descrição breve
[docs]: descrição breve
```

---

## 📚 Documentação Relevante

- **DOCKER_QUICKSTART.md** - Setup rápido com Docker
- **docs/MULTI_TENANCY_INTEGRATION_GUIDE.md** - Setup Keycloak
- **docs/issues/** - Documentação de issues conhecidas
- **Swagger/OpenAPI** - http://localhost:8080/swagger-ui.html

---

## ⚠️ Pontos de Atenção

### Performance
- Usar `@LazyCollection` ou DTOs parciais para evitar N+1 queries
- Cache habilitado para listagens frequentes
- Índices em banco para campos de busca

### Segurança
- Nunca commitar `.env` ou dados sensíveis
- Validar sempre entrada do usuário
- JWT tokens devem conter tenant_id para isolamento multi-tenancy

### Database
- Nunca modificar migrations já executadas (create novas)
- Usar Flyway para versionamento de schema
- Sempre usar transações em operações críticas

### LLM
- Controlar custo de chamadas OpenAI (temperature baixo = determinístico)
- Implementar retry logic com exponential backoff
- Cachear embeddings quando possível

---

## 🔧 Troubleshooting Comum

### PostgreSQL não conecta
```bash
# Verificar se container está rodando
docker ps | grep postgres

# Reiniciar services
docker-compose down && docker-compose up -d
```

### Keycloak não inicia
```bash
# Keycloak demora ~2min na primeira vez
# Verificar logs: docker logs menthoros-keycloak

# Resetar Keycloak: docker-compose down -v
```

### Testes falhando
```bash
# Limpar cache Maven
./mvnw clean

# Recompilar com processor de MapStruct
./mvnw clean compile
```

---

## 📌 Anotações Úteis do Projeto

- `@RestController` - Mapeia classes como controllers REST
- `@Service` - Marca classe como serviço de negócio
- `@Repository` - Marca interface como repositório JPA
- `@Transactional` - Gerencia transações de banco
- `@Cacheable` - Cacheia resultado de método
- `@RequiredArgsConstructor` (Lombok) - Cria constructor com campos final
- `@Mapper` (MapStruct) - Define interface de mapeamento
- `@RestControllerAdvice` - Handler global de exceções
- `@Valid` - Valida DTOs antes de usar
- `@ConfigurationProperties` - Carrega props customizadas do YAML

---

## 🎯 Próximas Etapas Recomendadas

1. ✅ Setup local com Docker Compose
2. ✅ Configurar Keycloak com realm e clients
3. ✅ Executar testes para validar setup
4. ✅ Explorar endpoints via Swagger UI
5. ✅ Implementar feature nova seguindo padrões existentes

---

**Última Atualização:** 2025-03-02
**Versão da Documentação:** 1.0
