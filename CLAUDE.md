# Menthoros Services - Guia de Arquitetura e Padrões

## Visão Geral

**Menthoros Services** é uma aplicação Spring Boot 3.5.4 em Java 21 para gerenciar atletas e planos de treinamento de corrida com suporte a **multi-tenancy**. A arquitetura segue padrões de camadas com integração de IA (OpenAI), cache local (Caffeine) e autenticação OAuth2 via Keycloak.

**Branch Principal:** `develop`
**Versão:** 0.0.1-SNAPSHOT

---

## Arquitetura

### Padrão: Arquitetura em Camadas

```
┌──────────────────────────────────────────┐
│   PRESENTATION LAYER (Controllers)       │
│   AtletaController, PlanoTreinoController│
│   TreinoRealizadoController              │
├──────────────────────────────────────────┤
│   SERVICE LAYER (Business Logic)         │
│   ├── impl/          (implementações)    │
│   ├── helper/        (serviços auxiliares│
│   └── prompt/        (builders de prompt)│
├──────────────────────────────────────────┤
│   REPOSITORY LAYER (Spring Data JPA)     │
├──────────────────────────────────────────┤
│   DATABASE LAYER (PostgreSQL + pgvector) │
└──────────────────────────────────────────┘
```

### Padrões Implementados

1. **Dependency Injection** — Spring Framework, constructor-based via `@RequiredArgsConstructor`
2. **Repository Pattern** — Spring Data JPA com métodos tenant-aware
3. **DTO Pattern** — DTOs de entrada (`input/`), saída (`output/`) e LLM (`llm/`)
4. **Mapper Pattern** — MapStruct com `componentModel = "spring"`, `unmappedTargetPolicy = IGNORE`
5. **Service Layer** — Lógica de negócio isolada, interfaces separadas de implementações
6. **Global Exception Handling** — `GlobalExceptionHandler` com `@RestControllerAdvice`
7. **Multi-Tenancy** — Shared schema com `tenant_id`; isolamento via `TenantContext` (ThreadLocal) + `JwtTenantFilter`

---

## Stack Tecnológico

### Core
- **Java 21** — Records, Virtual Threads, Pattern Matching
- **Spring Boot 3.5.4**
- **Maven**
- **Spring Data JPA / Hibernate**

### Banco de Dados
- **PostgreSQL 15+** — banco principal
- **pgvector 0.1.6** — embeddings (vector(1536))
- **Flyway 11.7.2** — migrações (V1–V11)
- **H2** — banco em memória para testes

### IA e ML
- **Spring AI 1.0.0-M6**
- **OpenAI API**
  - Chat: `gpt-4o` (temperature: 0.2, max-tokens: 4000, timeout: 30s)
  - Embeddings: `text-embedding-3-small` (1536 dimensões)

### Segurança e Autenticação
- **Spring Security + OAuth2 Resource Server**
- **Keycloak 25.0.3** — Identity Provider
- **JWT** com claims `sub`, `tenant_id`, `roles`
- **Autenticação obrigatória** em todas as rotas de negócio
- Rotas públicas: `/api/public/**`, `/swagger-ui/**`, `/api-docs/**`, `/actuator/health`

### Performance e Cache
- **Spring Cache + Caffeine** — TTL: 30min, max: 1000 entradas
- Chaves de cache sempre incluem `tenantId` como prefixo (ex: `tenantId:entityId`)
- **Micrometer Prometheus** — métricas e observabilidade

### Desenvolvimento
- **Lombok 1.18.38**
- **MapStruct 1.6.3**
- **SpringDoc OpenAPI 2.8.5** — Swagger UI em `/swagger-ui.html`
- **JUnit 5 + Mockito + Testcontainers**
- **JaCoCo 0.8.11**

---

## Estrutura de Diretórios

```
src/main/java/com/menthoros/
├── config/
│   ├── CacheConfig          # Caffeine TTL/size
│   ├── ChatClientConfig     # OpenAI ChatClient
│   ├── ClockConfig
│   ├── CorsConfig           # Origins: localhost:5173, :5174, :3000
│   ├── JacksonConfig
│   ├── LLMConfig            # ThreadPool: core=2, max=5, queue=100
│   ├── OpenApiConfig
│   └── SecurityConfig       # JWT + Keycloak; .authenticated() ativo
│
├── controller/
│   ├── AtletaController            # /atleta
│   ├── PlanoTreinoController       # /planos
│   ├── TreinoRealizadoController   # /treinos
│   └── ErrorHandlerController      # URLs erradas → 400
│
├── services/
│   ├── AtletaService, EmbeddingService, IaService
│   ├── MetricasAgregadasService, PlanoMetadadosService
│   ├── PlanoService, ProvaService, TreinoService
│   ├── TsbService, UsuarioSyncService
│   ├── impl/
│   │   ├── AtletaServiceImpl, EmbeddingServiceImpl, IaServiceImpl
│   │   ├── MetricasAgregadasServiceImpl, MetricasAlertaService
│   │   ├── PlanoMetadadosServiceImpl, PlanoServiceImpl
│   │   ├── ProvaServiceImpl, TreinoServiceImpl, TsbServiceImpl
│   ├── helper/
│   │   ├── IntervaladoElegibilidadeService, PaceValidator
│   │   ├── PaceZoneCalculator, RecomendacaoIntervalado
│   │   ├── RedistribuicaoTreinoHelper, RegraGeracaoTreino
│   │   ├── TreinoHistoricoProvider, TssCalculatorService, ZonaTreinoService
│   └── prompt/
│       ├── PlanoTreinoPromptBuilder, PromptTemplateLoader
│       ├── AlertasPromptFormatter, DisponibilidadePromptFormatter
│       ├── MetricasPromptFormatter, PaceHistoricoFormatter
│       ├── PeriodizacaoPromptFormatter, RecuperacaoPromptFormatter
│       └── VariabilidadePromptFormatter
│
├── repository/
│   ├── BaseRepository<T, ID>
│   ├── AssessoriaRepository
│   ├── AtletaRepository             # findByIdAndTenantId, findByIdForUpdate
│   ├── MetricasDiariasRepository
│   ├── PlanoMetadadosRepository     # findByIdAndTenantId
│   ├── PlanoSemanalRepository       # findByIdAndTenantId
│   ├── ProvaRepository              # findByIdAndTenantId
│   ├── TreinoPlanejadoRepository    # findByIdAndTenantId
│   ├── TreinoRealizadoRepository    # findByIdAndTenantId, findByFonteDadosAndExternalIdAndTenantId
│   └── UsuarioRepository
│
├── entity/
│   ├── BaseEntity, Assessoria (tenant), Usuario
│   ├── Atleta                  # vector(1536) para embeddings
│   ├── TreinoBase, TreinoPlanejado, TreinoRealizado
│   ├── EtapaTreino, EtapaRealizada
│   ├── PlanoSemanal, PlanoTreino, PlanoMetaDados  # ManyToOne Assessoria
│   ├── MetricasDiarias
│   └── Prova
│
├── dto/
│   ├── input/   # AtletaInputDto, TreinoRealizadoInputDto, ProvaInputDto, ...
│   ├── output/  # AtletaOutputDto, PlanoSemanalOutputDto, TreinoRealizadoOutputDto, ...
│   └── llm/     # PlanoSemanalLlmDto, TreinoPlanejadoLlmDto, EtapaTreinoLlmDto
│
├── mapper/
│   ├── AtletaMapper, EtapaMapper, PlanoMapper
│   ├── PlanoSemanalMapper, ProvaMapper, TreinoMapper
│
├── exception/
│   ├── DomainNotFoundException, DomainRuleViolationException
│   ├── DuplicateResourceException, LLMException, ResourceNotFoundException
│   └── handler/GlobalExceptionHandler
│
├── enums/
│   ├── AtletaStatus, AtletaGenero, DiaSemana, NivelExperiencia
│   ├── TipoTreino (10 tipos com fatorImpacto e zonaFcAlvo)
│   ├── TipoEtapa, TipoProva, DistanciaProva, ProvaStatus
│   ├── PlanoStatus, TreinoPlanejamentoStatus, TreinoExecucaoStatus
│   ├── FasePeriodizacao, ModoGeracaoPlano
│   ├── FonteDados, StatusSincronizacao
│   ├── FaixaTsb, MetricasThresholds, NivelAlerta
│   ├── PlanoAssessoria, UserRole, CategoriaIntervalado
│
├── multitenancy/
│   └── TenantContext            # InheritableThreadLocal<UUID>
│
├── security/
│   └── JwtTenantFilter          # Extrai tenant_id do JWT → TenantContext
│
├── converter/
│   └── FloatListToVectorConverter  # List<Float> → pgvector
│
└── util/Utils

src/main/resources/
├── application.yml          # Config principal
├── application-dev.yml      # Dev com Keycloak
├── application-local.yml    # Dev SEM Keycloak (⚠️ nunca em produção)
├── application-cloud.yml    # Cloud/Railway
├── db/migration/            # V1–V11 (ver seção Migrations)
└── prompts/                 # Templates de prompts para LLM

src/test/java/com/menthoros/
├── enums/                   # FaixaTsbInterpretacaoTest, EnumJsonTest
├── services/impl/           # MetricasAlertaService*, PlanoServiceImpl*, ProvaServiceImpl*, TsbServiceImpl*
├── services/helper/         # Intervalado*, PaceValidator*, PaceZone*, Redistribuicao*, Regra*, TssCalculator*
├── services/prompt/         # Alertas*, PaceHistorico*, Periodizacao*, Variabilidade*
└── MenthorosServicesApplicationTests (integração com Testcontainers)
```

---

## Multi-Tenancy

**Modelo:** Shared schema com coluna `tenant_id` em todas as tabelas de domínio.

**Fluxo:**
```
Request → JwtTenantFilter → TenantContext.setTenantId(UUID)
             ↓
        Service chama TenantContext.getRequiredTenantId()
             ↓
        Repository.findByIdAndTenantId(id, tenantId)  ← filtro no select
             ↓ finally
        TenantContext.clear()
```

**Regras:**
- `TenantContext.getRequiredTenantId()` — usado em **todos** os services de negócio; lança `IllegalStateException` se não houver contexto
- **Sem fallback** para tenant default — removido de `AtletaServiceImpl` e `ProvaServiceImpl`
- `findById` global **proibido** em fluxos HTTP — usar `findByIdAndTenantId`
- Chaves de cache sempre com prefixo `tenantId` (ex: `T(TenantContext).getRequiredTenantId() + ':' + #id`)
- `PlanoMetaDados` tem campo `@ManyToOne Assessoria assessoria` mapeando `tenant_id`

**Profile local sem Keycloak:** usar `-Dspring.profiles.active=local` com `application-local.yml`. Nunca em produção ou CI.

---

## Tratamento de Exceções

```
GlobalExceptionHandler mapeia:
- MethodArgumentNotValidException → 400 Bad Request
- IllegalArgumentException        → 400 Bad Request
- ResourceNotFoundException       → 404 Not Found
- OptimisticLockException         → 409 Conflict
- DuplicateResourceException      → 409 Conflict
- DataIntegrityViolationException → 409 Conflict
- IllegalStateException           → 403 Forbidden  (tenant ausente)
- LLMException                    → 503 Service Unavailable
- RuntimeException (OpenAI)       → 502 Bad Gateway
- Exception (genérica)            → 500 Internal Server Error
```

---

## Cache

**Tecnologia:** Caffeine (in-memory, TTL 30min, max 1000 entradas)

**Chaves obrigatoriamente segmentadas por tenant:**
```java
// Entidade individual
@Cacheable(value = "atletas", key = "T(TenantContext).getRequiredTenantId() + ':' + #id")

// Lista
@Cacheable(value = "atletas-list", key = "T(TenantContext).getRequiredTenantId()")

// Evict — nunca usar allEntries = true; usar chave tenant
@CacheEvict(value = "atletas-list", key = "T(TenantContext).getRequiredTenantId()")
```

---

## Migrations Flyway

Arquivos em `src/main/resources/db/migration/`. **Nunca modificar migrations já executadas.**

| Versão | Descrição |
|--------|-----------|
| V1 | Schema inicial: extensões uuid-ossp e vector; tabelas base sem multi-tenancy |
| V2 | Multi-tenancy: cria `tb_assessoria` e `tb_usuario`; adiciona `tenant_id` nas tabelas existentes |
| V3 | `tb_plano_metadados`, `tb_faixa_tsb` com constantes por nível de experiência |
| V4 | Índices compostos para performance e filtros comuns |
| V5 | Fases de periodização e tipos de treino com enums |
| V6 | Campos fisiológicos do atleta: FCMax, limiar anaeróbico, zonas |
| V7 | Campos de `tb_treino_realizado` (elevação, FC, cadência) e `tb_etapa_realizada` |
| V8 | Suporte a sincronização externa: `external_id`, `fonte_dados`, `status_sincronizacao`; índice global `uk_treino_realizado_external_id` (substituído em V11) |
| V9 | Campos de alerta em `tb_plano_metadados`; views materializadas |
| V10 | Fix: coluna `observacao` em `tb_treino_realizado` |
| V11 | Multi-tenancy constraints: remove índice global de `external_id`; cria índice único `(tenant_id, fonte_dados, external_id)` em `tb_treino_realizado` |

---

## Endpoints

### Atleta (`/atleta`)
- `POST /atleta` — criar atleta
- `GET /atleta` — listar atletas do tenant
- `GET /atleta/{id}` — buscar por ID
- `PUT /atleta/{id}` — atualizar
- `DELETE /atleta/{id}` — soft delete (INATIVO)
- `GET /atleta/{id}/recalcular-metricas` — recalcular TSB/CTL/ATL

### Plano de Treino (`/planos`)
- `POST /planos/atletas/{atletaId}/gerar` — gerar plano semanal via IA
- `POST /planos/atletas/{atletaId}/gerar-enhanced` — gerar plano enhanced
- `DELETE /planos/{planoSemanalId}` — deletar plano
- `GET /planos/{id}` — buscar plano

### Treino (`/treinos`)
- `POST /treinos/{treinoPlanejadoId}/marcar-realizado` — registrar treino realizado
- `POST /treinos/{atletaId}/lancar-treino` — lançar treino manual

### Documentação
- `GET /swagger-ui.html` — Swagger UI
- `GET /api-docs` — OpenAPI JSON

---

## Segurança

- JWT obrigatório em todas as rotas de negócio (`.anyRequest().authenticated()`)
- `tenant_id` extraído do claim JWT pelo `JwtTenantFilter`
- Roles extraídas de `roles` (flat) e `resource_access.menthoros-api.roles` (Keycloak client roles)
- `app.security.roles-client-id` configurável (default: `menthoros-api`)

```yaml
# Configuração Keycloak
spring.security.oauth2.resourceserver.jwt.issuer-uri: http://localhost:8443/realms/menthoros-app

# Claims no JWT:
- sub (user ID)
- tenant_id (UUID da assessoria)
- roles (ADMIN, TECNICO, VISUALIZADOR)
- resource_access.menthoros-api.roles
```

---

## Configuração de LLM

```yaml
spring.ai.openai.chat.options:
  model: gpt-4o
  temperature: 0.2
  max-tokens: 4000

spring.ai.openai.embedding.options:
  model: text-embedding-3-small
  dimensions: 1536

app.llm:
  thread-pool:
    core-size: 2
    max-size: 5
    queue-capacity: 100
  timeout: 30s
```

---

## TipoTreino

| Tipo | Fator Impacto | Zona FC |
|------|--------------|---------|
| REGENERATIVO | 0.85 | Z1 |
| FACIL | 1.0 | Z2 |
| CONTINUO | 1.1 | Z2-3 |
| FARTLEK | 1.2 | Z2-4 |
| TEMPO_RUN | 1.25 | Z4 |
| LONGO | 1.15 | Z2 |
| PROVA | 1.3 | Z3-4 |
| INTERVALADO | 1.4 | Z5 |
| TIRO | 1.5 | Z5+ |
| SUBIDA | 1.6 | Z4-5 |

---

## Testes

### Estrutura
```
src/test/java/com/menthoros/
├── enums/              # Testes unitários de enums
├── services/impl/      # Testes de services (*Test.java)
├── services/helper/    # Testes de helpers
├── services/prompt/    # Testes de formatadores de prompt
└── *ApplicationTests   # Teste de integração com Testcontainers
```

### Executando
```bash
./mvnw test                        # Unitários
./mvnw failsafe:integration-test   # Integração
./mvnw verify                      # Tudo + cobertura JaCoCo
./mvnw clean package -DskipTests   # Build sem testes
```

### Configuração Maven
- **Surefire:** padrão `**/unit/**/*.java` (unitários)
- **Failsafe:** padrão `**/integration/*IT.java` (integração)
- **Byte Buddy Agent** — mock inline do Mockito
- **Testcontainers** — PostgreSQL em testes de integração

---

## Build e Deploy

```bash
./mvnw spring-boot:run                          # Dev local
./mvnw spring-boot:run -Dspring.profiles.active=local  # Sem Keycloak

docker-compose up -d                            # PostgreSQL + Keycloak
docker-compose -f docker-compose.multi-tenancy.yml up -d
```

---

## Variáveis de Ambiente (.env)

```env
SERVER_PORT=8080

DB_HOST=localhost
DB_PORT=5433
DB_NAME=menthoros-multi
DB_USER=menthoros
DB_PASSWORD=menthoros123

KEYCLOAK_ISSUER_URI=http://localhost:8443/realms/menthoros-app
KEYCLOAK_JWK_URI=http://localhost:8443/realms/menthoros-app/protocol/openid-connect/certs
KC_ADMIN_USER=admin
KC_ADMIN_PASSWORD=admin123

OPENAI_API_KEY=sua_chave

CORS_ALLOWED_ORIGINS=http://localhost:5173,http://localhost:3000
```

---

## Git Workflow

```
main       → produção (protegido)
develop    → base de desenvolvimento
feature/*  → novas features
sprint-*   → sprints
bugfix/*   → correções
```

**Commits:**
```
feat(módulo): descrição
fix(módulo): descrição
refactor(módulo): descrição
test(módulo): descrição
docs: descrição
```

---

## Pontos de Atenção

### Multi-Tenancy
- **Sempre** usar `TenantContext.getRequiredTenantId()` nos services — nunca `getTenantId()` (permite null)
- **Nunca** usar `findById` global em fluxos HTTP — usar `findByIdAndTenantId`
- Chaves de cache **devem** incluir `tenantId` como prefixo
- `IllegalStateException` sem tenant context → HTTP 403 (mapeado no `GlobalExceptionHandler`)

### Database
- Nunca modificar migrations já executadas (criar nova)
- Usar Flyway para toda alteração de schema
- Índice único `(tenant_id, fonte_dados, external_id)` em `tb_treino_realizado` (V11)

### Performance
- Evitar N+1 — usar DTOs parciais ou `@LazyCollection`
- Cache habilitado para listagens; invalidar por tenant, não globalmente

### LLM
- Temperature baixa (0.2) = respostas determinísticas
- Implementar retry com exponential backoff para falhas de rede OpenAI
- Cachear embeddings quando possível

---

## Troubleshooting

### PostgreSQL não conecta
```bash
docker ps | grep postgres
docker-compose down && docker-compose up -d
```

### Keycloak não inicia
```bash
# Demora ~2min na primeira vez
docker logs menthoros-keycloak
# Reset completo:
docker-compose down -v
```

### Testes falhando
```bash
./mvnw clean               # Limpar cache
./mvnw clean compile       # Reprocessar MapStruct
```

### 403 em endpoints de negócio
Verificar se o JWT contém o claim `tenant_id`. Configurar o mapper de grupo no Keycloak.

---

**Última Atualização:** 2026-04-13
**Versão da Documentação:** 2.0
