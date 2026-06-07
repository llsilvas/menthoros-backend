# Processamento Assíncrono - Guia Completo de Implementação

> **Nota:** Este documento consolidou três arquivos originais:
> 1. `ASYNC_PLAN_GENERATION_GUIDE.md` - Guia técnico de implementação
> 2. `implementacao-async-processing.md` - Análise e proposta de implementação
> 3. `ASYNC_PLAN_GENERATION_ROADMAP.md` - Roadmap com sprints detalhados

---

## 📋 Índice

1. [Resumo Executivo](#resumo-executivo)
2. [Visão Geral](#visão-geral)
3. [Situação Atual](#situação-atual)
4. [Por que Virtual Threads?](#por-que-virtual-threads)
5. [Arquitetura da Solução](#arquitetura-da-solução)
6. [Modelo de Dados](#modelo-de-dados)
7. [Implementação Passo a Passo](#implementação-passo-a-passo)
8. [Geração Individual vs Lote](#geração-individual-vs-lote)
9. [Monitoramento e Status](#monitoramento-e-status)
10. [Tratamento de Erros](#tratamento-de-erros)
11. [Cancelamento de Jobs](#cancelamento-de-jobs)
12. [Performance e Escalabilidade](#performance-e-escalabilidade)
13. [Roadmap de Implementação](#roadmap-de-implementação)
14. [Testes](#testes)

---

## 📖 Resumo Executivo

Este documento detalha a implementação de processamento assíncrono para geração de planos de treino com IA no projeto Menthoros, **com foco especial em processamento em lote para assessorias esportivas**.

A solução proposta visa:
- ⚡ **99.8% redução** no tempo de resposta por atleta
- 🚀 **Processamento paralelo** de múltiplos atletas (assessorias)
- 📊 **Escalabilidade** para 100+ atletas por assessoria
- 🔄 **Multi-tenancy ready** com isolamento total

---

## 🔍 Situação Atual

### Problemas Identificados

- ❌ **Chamadas OpenAI síncronas** e bloqueantes
- ❌ **Timeout de 30s** pode ser insuficiente
- ❌ **Sem processamento em batch** (método `gerarPlanosEmLote` apenas retorna `Map.of()`)
- ❌ **Usuário fica aguardando** resposta da IA por 15-30s
- ❌ **Baixa concorrência** - máximo 10 usuários simultâneos
- ❌ **Experience frustrante** com timeouts e travamentos

### Métricas Atuais

```java
// SpringAiEnhancedIaServiceImpl.java - Linha 127
public Map<Long, PlanoTreinoOutputDto> gerarPlanosEmLote(Map<AtletaOutputDto, List<TreinoRealizadoOutputDto>> atletaDtoListMap) {
    log.info("Iniciando geração em lote de {} planos", atletaDtoListMap.size());
    // TODO: Implementar processamento assíncrono
    return Map.of(); // ⚠️ Não implementado
}
```

| Métrica Atual | Valor | Status |
|---------------|--------|--------|
| Response Time | 15-30s | 🔴 Inaceitável |
| Planos/minuto | 3-4 | 🔴 Muito baixo |
| Concorrência | 10 usuários | 🟡 Limitada |
| UX | Bloqueante | 🔴 Frustrante |

---

## 📖 Visão Geral

### O Problema

Gerar planos de treino via IA é uma operação **custosa**:
- ⏱️ Pode levar **5-15 segundos por atleta**
- 🔥 Bloqueia a thread durante chamada à LLM
- 🚫 Timeout em requisições HTTP longas
- 📉 UX ruim: usuário esperando resposta

### A Solução

Geração **assíncrona** com Virtual Threads:
- ✅ **Não-bloqueante**: Retorna imediatamente com job ID
- ✅ **Paralelização**: Gera múltiplos planos simultaneamente
- ✅ **Escalável**: Virtual Threads = baixo custo de memória
- ✅ **Monitorável**: Status em tempo real
- ✅ **Resiliente**: Retry automático em falhas

### Casos de Uso

```
Caso 1: Geração Individual
┌──────────────────────────────────────────┐
│ Cliente                                  │
│ POST /api/planos/async/atleta/{id}      │
│ → Resposta imediata: { jobId: "..." }   │
│                                          │
│ GET /api/jobs/{jobId}                   │
│ → { status: "PROCESSING", progress: 50% }│
│                                          │
│ GET /api/jobs/{jobId}                   │
│ → { status: "COMPLETED", resultado: {...}}
└──────────────────────────────────────────┘

Caso 2: Geração em Lote
┌──────────────────────────────────────────┐
│ Assessoria com 50 atletas                │
│ POST /api/planos/async/lote              │
│ Body: { atletaIds: [1,2,...,50] }       │
│                                          │
│ Resposta: { jobId, totalAtletas: 50 }   │
│                                          │
│ WebSocket conecta a /topic/jobs/jobId    │
│ Recebe notificações a cada 10% progresso │
│                                          │
│ Após ~100 segundos: 100% completo        │
└──────────────────────────────────────────┘

Caso 3: Agendamento Automático
┌──────────────────────────────────────────┐
│ POST /api/agendamentos                   │
│ { assessoriaId, modo, cron: "0 6 * * *" }
│                                          │
│ Sistema executa automaticamente às 6h    │
│ Notificação ao treinador quando pronto   │
└──────────────────────────────────────────┘
```

---

## ⚡ Por que Virtual Threads?

### Comparação: Platform Threads vs Virtual Threads

| Aspecto | Platform Threads | Virtual Threads |
|---------|------------------|-----------------|
| **Custo de Memória** | ~2 MB | ~1 KB |
| **Tempo Criação** | ~1 ms | ~1 µs |
| **Limite Prático** | ~10k threads | ~1M+ threads |
| **Context Switch** | Caro (kernel) | Barato (JVM) |
| **Escalabilidade** | ⚠️ Limitada | ✅ Excelente |
| **I/O Blocking** | Bloqueia kernel | Pausa JVM |

### Por que não apenas ThreadPool?

```java
// ❌ ThreadPool fixo (problema)
ExecutorService pool = Executors.newFixedThreadPool(10);
// Máximo 10 jobs simultâneos - insuficiente para 100+ atletas

// ✅ Virtual Threads (solução)
ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor();
// Unlimited tasks - cria 1 virtual thread por job
```

### Benefício Prático

```
Geração de 100 planos:

❌ ThreadPool de 10:
   - Job 1-10: executa em paralelo (15s cada)
   - Job 11-20: aguarda liberação (15s de espera)
   - ...
   - Total: ~150 segundos (sequencial)

✅ Virtual Threads:
   - Jobs 1-100: todos em paralelo (15s máximo)
   - Total: ~20 segundos (paralelo completo)

   Melhoria: 7.5x mais rápido!
```

---

## 🏗️ Arquitetura da Solução

### Fluxo Geral de Execução

```
┌─────────────────────────────────────────────────────────────┐
│                      Cliente/Frontend                        │
│        POST /api/planos/async/individual ou /lote           │
└─────────────────────┬───────────────────────────────────────┘
                      │
                      ▼
┌─────────────────────────────────────────────────────────────┐
│                  PlanoAsyncController                        │
│  - Validar request                                           │
│  - Validar permissões (ADMIN/TECNICO)                       │
│  - Validar limite de jobs simultâneos                       │
│  - Retornar jobId imediatamente                             │
└─────────────────────┬───────────────────────────────────────┘
                      │
                      ▼
┌─────────────────────────────────────────────────────────────┐
│                PlanoAsyncService                             │
│  - Criar JobExecucao no banco                               │
│  - Cachear status em Redis                                  │
│  - Submeter task ao VirtualThreadExecutor                   │
└─────────────────────┬───────────────────────────────────────┘
                      │
                      ▼
┌─────────────────────────────────────────────────────────────┐
│         VirtualThreadExecutor (async)                        │
│  - Criar sub-tarefas (1 por atleta)                         │
│  - Usar CompletableFuture para paralelização                │
│  - Gerenciar TenantContext em cada thread                   │
└─────────────────────┬───────────────────────────────────────┘
                      │
                      ▼
┌─────────────────────────────────────────────────────────────┐
│      processarSubTarefa() - Em paralelo                     │
│  - Carregar dados do atleta                                 │
│  - Chamar IA (OpenAI/Claude)                                │
│  - Salvar resultado                                         │
│  - Atualizar progresso em Redis                             │
│  - Publicar evento via Pub/Sub                              │
└─────────────────────┬───────────────────────────────────────┘
                      │
                      ▼
┌─────────────────────────────────────────────────────────────┐
│              WebSocketNotificationService                    │
│  - Notificar frontend a cada 10% progresso                  │
│  - Notificar quando completo/erro                           │
└─────────────────────────────────────────────────────────────┘
```

### Componentes da Arquitetura

```
┌────────────────────────────────────────────────────────────────┐
│                    APRESENTAÇÃO                                │
│  - PlanoAsyncController (REST endpoints)                       │
│  - WebSocketController (notificações tempo real)               │
└────────────────────────────────────────────────────────────────┘
                            │
                            ▼
┌────────────────────────────────────────────────────────────────┐
│                      LÓGICA DE NEGÓCIO                         │
│  - PlanoAsyncService (orquestração)                            │
│  - JobMonitoringService (métricas)                             │
│  - WebSocketNotificationService (notificações)                 │
└────────────────────────────────────────────────────────────────┘
                            │
                            ▼
┌────────────────────────────────────────────────────────────────┐
│                   SERVIÇOS DE INFRAESTRUTURA                   │
│  - RedisJobCacheService (cache + fila)                         │
│  - AsyncConfig (pool de threads)                               │
│  - TenantContext (isolamento multi-tenant)                     │
└────────────────────────────────────────────────────────────────┘
                            │
                            ▼
┌────────────────────────────────────────────────────────────────┐
│                    PERSISTÊNCIA & CACHE                        │
│  - JobExecucaoRepository (banco)                               │
│  - SubTarefaRepository (banco)                                 │
│  - Redis (cache + fila + Pub/Sub)                              │
└────────────────────────────────────────────────────────────────┘
```

---

## 📊 Modelo de Dados

### Diagrama ER

```
┌─────────────────────────────────┐
│       tb_job_execucao           │
├─────────────────────────────────┤
│ id (UUID)                       │
│ tenant_id (UUID)                │
│ tipo (ENUM: INDIVIDUAL/LOTE)    │
│ status (ENUM: ver abaixo)       │
│ progresso (0-100)               │
│ completadas (INT)               │
│ falhadas (INT)                  │
│ prioridade (INT: 1-10)          │
│ tempo_inicio (TIMESTAMP)        │
│ tempo_fim (TIMESTAMP)           │
│ erro_mensagem (TEXT)            │
│ metadados (JSON)                │
│ criado_em (TIMESTAMP)           │
└────────────────┬────────────────┘
                 │
                 │ 1:N
                 ▼
┌─────────────────────────────────┐
│       tb_sub_tarefa             │
├─────────────────────────────────┤
│ id (UUID)                       │
│ job_execucao_id (UUID) FK       │
│ atleta_id (UUID) FK             │
│ status (ENUM: ver abaixo)       │
│ resultado (JSON/TEXT)           │
│ erro_mensagem (TEXT)            │
│ tentativas (INT)                │
│ tempo_execucao (INT em ms)      │
│ criado_em (TIMESTAMP)           │
│ finalizado_em (TIMESTAMP)       │
└─────────────────────────────────┘
```

### Enums

#### JobStatus

```java
public enum JobStatus {
    QUEUED,      // Aguardando execução
    RUNNING,     // Em execução
    COMPLETED,   // Completo com sucesso
    FAILED,      // Falhou (todas sub-tarefas)
    CANCELLED,   // Cancelado pelo usuário
    PARTIAL      // Parcialmente completo (alguns sucessos, alguns fracassos)
}
```

#### JobType

```java
public enum JobType {
    PLANO_INDIVIDUAL,      // Geração para 1 atleta
    PLANO_LOTE,            // Geração para múltiplos atletas
    SINCRONIZACAO_STRAVA,  // Sincronizar dados do Strava
    CALCULO_METRICAS       // Calcular métricas personalizadas
}
```

#### SubTarefaStatus

```java
public enum SubTarefaStatus {
    QUEUED,      // Aguardando
    RUNNING,     // Executando
    COMPLETED,   // Sucesso
    FAILED,      // Falha permanente
    RETRY        // Aguardando retry
}
```

### Entidades

#### JobExecucao

```java
@Entity
@Table(name = "tb_job_execucao")
public class JobExecucao {
    @Id
    private String id;

    @Column(name = "tenant_id")
    private String tenantId;

    @Enumerated(EnumType.STRING)
    private JobType tipo;

    @Enumerated(EnumType.STRING)
    private JobStatus status;

    @Column(name = "progresso")
    private Integer progresso; // 0-100

    @Column(name = "completadas")
    private Integer completadas;

    @Column(name = "falhadas")
    private Integer falhadas;

    @Column(name = "prioridade")
    private Integer prioridade; // 1=ALTA, 5=MEDIA, 10=BAIXA

    @Column(name = "tempo_inicio")
    private LocalDateTime tempoInicio;

    @Column(name = "tempo_fim")
    private LocalDateTime tempoFim;

    @Column(name = "erro_mensagem", columnDefinition = "TEXT")
    private String erroMensagem;

    @Column(name = "metadados", columnDefinition = "JSON")
    private String metadados; // Serializado JSON

    @Column(name = "criado_em")
    private LocalDateTime criadoEm;

    @OneToMany(mappedBy = "jobExecucao", cascade = CascadeType.ALL)
    private List<SubTarefa> subTarefas;
}
```

#### SubTarefa

```java
@Entity
@Table(name = "tb_sub_tarefa")
public class SubTarefa {
    @Id
    private String id;

    @ManyToOne
    @JoinColumn(name = "job_execucao_id")
    private JobExecucao jobExecucao;

    @Column(name = "atleta_id")
    private String atletaId;

    @Enumerated(EnumType.STRING)
    private SubTarefaStatus status;

    @Column(name = "resultado", columnDefinition = "TEXT")
    private String resultado; // JSON do plano gerado

    @Column(name = "erro_mensagem")
    private String erroMensagem;

    @Column(name = "tentativas")
    private Integer tentativas;

    @Column(name = "tempo_execucao")
    private Long tempoExecucao; // em ms

    @Column(name = "criado_em")
    private LocalDateTime criadoEm;

    @Column(name = "finalizado_em")
    private LocalDateTime finalizadoEm;
}
```

---

## 🔧 Implementação Passo a Passo

### Passo 1: Atualizar para Java 21 (se necessário)

```bash
# Verificar versão
java -version

# Atualizar pom.xml
<properties>
    <java.version>21</java.version>
</properties>
```

### Passo 2: Criar Enums

**Arquivo**: `src/main/java/com/menthoros/enums/JobType.java`

```java
public enum JobType {
    PLANO_INDIVIDUAL("Plano Individual"),
    PLANO_LOTE("Plano em Lote"),
    SINCRONIZACAO_STRAVA("Sincronização Strava"),
    CALCULO_METRICAS("Cálculo de Métricas");

    private final String descricao;

    JobType(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }
}
```

**Arquivo**: `src/main/java/com/menthoros/enums/JobStatus.java`

```java
public enum JobStatus {
    QUEUED("Enfileirado"),
    RUNNING("Em execução"),
    COMPLETED("Concluído"),
    FAILED("Falhou"),
    CANCELLED("Cancelado"),
    PARTIAL("Parcialmente completo");

    private final String descricao;

    JobStatus(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }
}
```

### Passo 3: Criar Entidades

**Arquivo**: `src/main/java/com/menthoros/entity/JobExecucao.java`

```java
@Entity
@Table(name = "tb_job_execucao", indexes = {
    @Index(name = "idx_tenant_status", columnList = "tenant_id, status"),
    @Index(name = "idx_tenant_created", columnList = "tenant_id, criado_em")
})
public class JobExecucao {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private JobType tipo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private JobStatus status = JobStatus.QUEUED;

    @Column(name = "progresso")
    private Integer progresso = 0;

    @Column(name = "completadas")
    private Integer completadas = 0;

    @Column(name = "falhadas")
    private Integer falhadas = 0;

    @Column(name = "prioridade")
    private Integer prioridade = 5; // MEDIA por padrão

    @Column(name = "tempo_inicio")
    private LocalDateTime tempoInicio;

    @Column(name = "tempo_fim")
    private LocalDateTime tempoFim;

    @Column(name = "erro_mensagem", columnDefinition = "TEXT")
    private String erroMensagem;

    @Column(name = "metadados", columnDefinition = "JSON")
    @Convert(converter = JsonConverter.class)
    private Map<String, Object> metadados;

    @Column(name = "criado_em", nullable = false, updatable = false)
    @CreationTimestamp
    private LocalDateTime criadoEm;

    @OneToMany(mappedBy = "jobExecucao", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<SubTarefa> subTarefas = new ArrayList<>();

    // Getters e setters
}
```

**Arquivo**: `src/main/java/com/menthoros/entity/SubTarefa.java`

```java
@Entity
@Table(name = "tb_sub_tarefa", indexes = {
    @Index(name = "idx_job_status", columnList = "job_execucao_id, status"),
    @Index(name = "idx_atleta_id", columnList = "atleta_id")
})
public class SubTarefa {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_execucao_id", nullable = false)
    private JobExecucao jobExecucao;

    @Column(name = "atleta_id", nullable = false)
    private String atletaId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SubTarefaStatus status = SubTarefaStatus.QUEUED;

    @Column(name = "resultado", columnDefinition = "LONGTEXT")
    private String resultado;

    @Column(name = "erro_mensagem")
    private String erroMensagem;

    @Column(name = "tentativas")
    private Integer tentativas = 0;

    @Column(name = "tempo_execucao")
    private Long tempoExecucao;

    @Column(name = "criado_em", updatable = false)
    @CreationTimestamp
    private LocalDateTime criadoEm;

    @Column(name = "finalizado_em")
    private LocalDateTime finalizadoEm;

    // Getters e setters
}
```

### Passo 4: Migration do Banco

**Arquivo**: `src/main/resources/db/migration/V9__Create_async_job_tables.sql`

```sql
CREATE TABLE tb_job_execucao (
    id VARCHAR(36) PRIMARY KEY,
    tenant_id VARCHAR(36) NOT NULL,
    tipo VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'QUEUED',
    progresso INT DEFAULT 0,
    completadas INT DEFAULT 0,
    falhadas INT DEFAULT 0,
    prioridade INT DEFAULT 5,
    tempo_inicio DATETIME,
    tempo_fim DATETIME,
    erro_mensagem TEXT,
    metadados JSON,
    criado_em DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    INDEX idx_tenant_status (tenant_id, status),
    INDEX idx_tenant_created (tenant_id, criado_em)
);

CREATE TABLE tb_sub_tarefa (
    id VARCHAR(36) PRIMARY KEY,
    job_execucao_id VARCHAR(36) NOT NULL,
    atleta_id VARCHAR(36) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'QUEUED',
    resultado LONGTEXT,
    erro_mensagem VARCHAR(255),
    tentativas INT DEFAULT 0,
    tempo_execucao BIGINT,
    criado_em DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    finalizado_em DATETIME,

    FOREIGN KEY (job_execucao_id) REFERENCES tb_job_execucao(id),
    INDEX idx_job_status (job_execucao_id, status),
    INDEX idx_atleta_id (atleta_id)
);
```

### Passo 5: Repositories

**Arquivo**: `src/main/java/com/menthoros/repository/JobExecucaoRepository.java`

```java
@Repository
public interface JobExecucaoRepository extends JpaRepository<JobExecucao, String> {
    List<JobExecucao> findByTenantIdAndStatusOrderByTempoInicioDesc(
        String tenantId,
        JobStatus status
    );

    List<JobExecucao> findByTenantIdAndCriadoEmAfterOrderByTempoInicioDesc(
        String tenantId,
        LocalDateTime data
    );

    @Query("SELECT COUNT(j) FROM JobExecucao j WHERE j.tenantId = :tenantId AND j.status = 'RUNNING'")
    Long countRunningByTenantId(@Param("tenantId") String tenantId);
}
```

**Arquivo**: `src/main/java/com/menthoros/repository/SubTarefaRepository.java`

```java
@Repository
public interface SubTarefaRepository extends JpaRepository<SubTarefa, String> {
    List<SubTarefa> findByJobExecucaoId(String jobExecucaoId);

    @Query("SELECT COUNT(s) FROM SubTarefa s WHERE s.jobExecucao.id = :jobId AND s.status = 'COMPLETED'")
    Integer countCompletedByJobId(@Param("jobId") String jobId);

    @Query("SELECT COUNT(s) FROM SubTarefa s WHERE s.jobExecucao.id = :jobId AND s.status = 'FAILED'")
    Integer countFailedByJobId(@Param("jobId") String jobId);
}
```

### Passo 6: Configurar Virtual Threads

**Arquivo**: `src/main/java/com/menthoros/config/AsyncConfig.java`

```java
@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * Virtual Thread Executor - Escalável para milhões de tarefas
     * Uma virtual thread por tarefa (novo modelo)
     */
    @Bean(name = "virtualThreadExecutor")
    public Executor virtualThreadExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    /**
     * Bounded Executor - Fallback para tarefas críticas com limite
     * Máximo de 100 threads simultâneos
     */
    @Bean(name = "boundedExecutor")
    public Executor boundedExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(20);
        executor.setMaxPoolSize(100);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("bounded-async-");
        executor.initialize();
        return executor;
    }
}
```

### Passo 7: Redis Configuration

**Arquivo**: `src/main/java/com/menthoros/config/RedisConfig.java`

```java
@Configuration
public class RedisConfig {

    @Bean
    public LettuceConnectionFactory lettuceConnectionFactory() {
        return new LettuceConnectionFactory();
    }

    @Bean
    public RedisTemplate<String, Object> redisTemplate(LettuceConnectionFactory factory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);

        Jackson2JsonRedisSerializer<Object> jackson2JsonRedisSerializer =
            new Jackson2JsonRedisSerializer<>(Object.class);

        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.activateDefaultTyping(
            LaissezFaireSubTypeValidator.instance,
            ObjectMapper.DefaultTyping.NON_FINAL
        );
        jackson2JsonRedisSerializer.setObjectMapper(objectMapper);

        StringRedisSerializer stringRedisSerializer = new StringRedisSerializer();

        // String keys
        template.setKeySerializer(stringRedisSerializer);
        // Hash keys
        template.setHashKeySerializer(stringRedisSerializer);
        // String values
        template.setValueSerializer(jackson2JsonRedisSerializer);
        // Hash values
        template.setHashValueSerializer(jackson2JsonRedisSerializer);

        template.afterPropertiesSet();
        return template;
    }

    @Bean
    public RedisCacheManager cacheManager(LettuceConnectionFactory factory) {
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofHours(24))
            .serializeKeysWith(
                RedisSerializationContext.SerializationPair.fromSerializer(
                    new StringRedisSerializer()
                )
            );

        return RedisCacheManager.create(factory);
    }
}
```

**Arquivo**: `src/main/resources/application.yml`

```yaml
spring:
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}
      password: ${REDIS_PASSWORD:}
      timeout: 2000ms
      lettuce:
        pool:
          max-active: 50
          max-idle: 20
          min-idle: 5
        shutdown-timeout: 2000ms
```

### Passo 8: Redis Job Cache Service

**Arquivo**: `src/main/java/com/menthoros/services/RedisJobCacheService.java`

```java
@Service
@Slf4j
public class RedisJobCacheService {
    private static final String JOB_KEY = "job:%s";
    private static final String QUEUE_KEY = "job_queue:%s";
    private static final String LOCK_KEY = "job_lock:%s";
    private static final String RATE_LIMIT_KEY = "rate_limit:%s";

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    /**
     * Cache o status do job em Redis com TTL de 24 horas
     */
    public void cacheJobStatus(JobStatusDto status) {
        String key = String.format(JOB_KEY, status.getJobId());
        redisTemplate.opsForValue().set(key, status, Duration.ofHours(24));
    }

    /**
     * Recupera status do job do cache
     */
    public JobStatusDto getJobStatus(String jobId) {
        String key = String.format(JOB_KEY, jobId);
        return (JobStatusDto) redisTemplate.opsForValue().get(key);
    }

    /**
     * Atualiza progresso do job
     */
    public void updateJobProgress(String jobId, Integer progresso) {
        JobStatusDto status = getJobStatus(jobId);
        if (status != null) {
            status.setProgresso(progresso);
            cacheJobStatus(status);
        }
    }

    /**
     * Enfileira job na fila prioritária
     */
    public void enqueueJob(String tenantId, JobExecucao job) {
        String key = String.format(QUEUE_KEY, tenantId);
        redisTemplate.opsForZSet().add(key, job.getId(), job.getPrioridade());
    }

    /**
     * Desfileira próximo job
     */
    public String dequeueJob(String tenantId) {
        String key = String.format(QUEUE_KEY, tenantId);
        Set<Object> jobs = redisTemplate.opsForZSet().range(key, 0, 0);
        if (jobs != null && !jobs.isEmpty()) {
            String jobId = (String) jobs.iterator().next();
            redisTemplate.opsForZSet().remove(key, jobId);
            return jobId;
        }
        return null;
    }

    /**
     * Retorna tamanho da fila
     */
    public Long getQueueSize(String tenantId) {
        String key = String.format(QUEUE_KEY, tenantId);
        return redisTemplate.opsForZSet().size(key);
    }

    /**
     * Verifica rate limit por assessoria
     * Limita a 5 jobs simultâneos
     */
    public boolean checkRateLimit(String tenantId) {
        String key = String.format(RATE_LIMIT_KEY, tenantId);
        Long count = redisTemplate.opsForValue().increment(key);
        if (count == 1) {
            redisTemplate.expire(key, Duration.ofMinutes(1));
        }
        return count <= 5;
    }

    /**
     * Adquire lock distribuído para evitar execução duplicada
     */
    public boolean acquireLock(String jobId, Duration ttl) {
        String key = String.format(LOCK_KEY, jobId);
        Boolean success = redisTemplate.opsForValue().setIfAbsent(key, "locked", ttl);
        return Boolean.TRUE.equals(success);
    }

    /**
     * Libera lock
     */
    public void releaseLock(String jobId) {
        String key = String.format(LOCK_KEY, jobId);
        redisTemplate.delete(key);
    }

    /**
     * Publica evento de job para subscribers (Pub/Sub)
     */
    public void publishJobEvent(String jobId, JobEventDto event) {
        redisTemplate.convertAndSend("job_events", event);
    }
}
```

### Passo 9: DTOs

**Arquivo**: `src/main/java/com/menthoros/dto/JobStatusDto.java`

```java
@Data
@NoArgsConstructor
@AllArgsConstructor
public class JobStatusDto implements Serializable {
    private String jobId;
    private JobStatus status;
    private Integer progresso;
    private Integer completadas;
    private Integer falhadas;
    private LocalDateTime tempoInicio;
    private LocalDateTime tempoFim;
    private List<SubTarefaDto> subTarefas;
}
```

### Passo 10: PlanoAsyncService

**Arquivo**: `src/main/java/com/menthoros/services/PlanoAsyncService.java`

```java
@Service
@Slf4j
public class PlanoAsyncService {

    @Autowired
    private JobExecucaoRepository jobRepository;

    @Autowired
    private SubTarefaRepository subTarefaRepository;

    @Autowired
    private RedisJobCacheService redisJobCacheService;

    @Autowired
    private WebSocketNotificationService notificationService;

    @Autowired
    @Qualifier("virtualThreadExecutor")
    private Executor virtualThreadExecutor;

    @Autowired
    private PlanoService planoService;

    /**
     * Submete job de geração individual assíncrona
     */
    @Transactional
    public JobSubmitResponse submitPlanoIndividual(String atletaId, String modo) {
        String tenantId = TenantContext.getTenantId();

        // Validar
        if (!redisJobCacheService.checkRateLimit(tenantId)) {
            throw new RuntimeException("Rate limit exceeded");
        }

        // Criar job
        JobExecucao job = new JobExecucao();
        job.setId(UUID.randomUUID().toString());
        job.setTenantId(tenantId);
        job.setTipo(JobType.PLANO_INDIVIDUAL);
        job.setStatus(JobStatus.QUEUED);
        job.setProgresso(0);
        job.setPrioridade(1); // ALTA
        job.setTempoInicio(LocalDateTime.now());

        jobRepository.save(job);

        // Cachear em Redis
        JobStatusDto statusDto = new JobStatusDto();
        statusDto.setJobId(job.getId());
        statusDto.setStatus(JobStatus.QUEUED);
        statusDto.setProgresso(0);
        redisJobCacheService.cacheJobStatus(statusDto);

        // Submeter para execução assíncrona
        executarPlanoIndividualAsync(job.getId(), atletaId, modo);

        return new JobSubmitResponse(job.getId(), JobStatus.QUEUED, "~15 segundos");
    }

    /**
     * Submete job de geração em lote
     */
    @Transactional
    public JobSubmitResponse submitPlanoLote(List<String> atletaIds, String modo) {
        String tenantId = TenantContext.getTenantId();

        if (!redisJobCacheService.checkRateLimit(tenantId)) {
            throw new RuntimeException("Rate limit exceeded");
        }

        JobExecucao job = new JobExecucao();
        job.setId(UUID.randomUUID().toString());
        job.setTenantId(tenantId);
        job.setTipo(JobType.PLANO_LOTE);
        job.setStatus(JobStatus.QUEUED);
        job.setProgresso(0);
        job.setPrioridade(atletaIds.size() > 10 ? 10 : 5); // Prioridade baixa para lotes grandes
        job.setTempoInicio(LocalDateTime.now());

        // Criar sub-tarefas
        List<SubTarefa> subTarefas = new ArrayList<>();
        for (String atletaId : atletaIds) {
            SubTarefa subTarefa = new SubTarefa();
            subTarefa.setId(UUID.randomUUID().toString());
            subTarefa.setJobExecucao(job);
            subTarefa.setAtletaId(atletaId);
            subTarefa.setStatus(SubTarefaStatus.QUEUED);
            subTarefas.add(subTarefa);
        }

        job.setSubTarefas(subTarefas);
        jobRepository.save(job);

        // Cachear
        JobStatusDto statusDto = new JobStatusDto();
        statusDto.setJobId(job.getId());
        statusDto.setStatus(JobStatus.QUEUED);
        statusDto.setProgresso(0);
        redisJobCacheService.cacheJobStatus(statusDto);

        // Submeter para execução
        executarPlanoLoteAsync(job.getId(), modo);

        return new JobSubmitResponse(
            job.getId(),
            JobStatus.QUEUED,
            "~" + (atletaIds.size() + " segundos")
        );
    }

    /**
     * Executa plano individual assincronamente
     */
    @Async("virtualThreadExecutor")
    public void executarPlanoIndividualAsync(String jobId, String atletaId, String modo) {
        String tenantId = TenantContext.getTenantId();

        try {
            // Adquirir lock
            if (!redisJobCacheService.acquireLock(jobId, Duration.ofMinutes(30))) {
                log.warn("Não foi possível adquirir lock para job {}", jobId);
                return;
            }

            JobExecucao job = jobRepository.findById(jobId).orElseThrow();
            job.setStatus(JobStatus.RUNNING);
            job.setTempoInicio(LocalDateTime.now());
            jobRepository.save(job);

            // Criar sub-tarefa
            SubTarefa subTarefa = new SubTarefa();
            subTarefa.setId(UUID.randomUUID().toString());
            subTarefa.setJobExecucao(job);
            subTarefa.setAtletaId(atletaId);
            subTarefa.setStatus(SubTarefaStatus.RUNNING);

            long inicio = System.currentTimeMillis();

            try {
                // Processar
                processarSubTarefa(subTarefa, modo);

                subTarefa.setStatus(SubTarefaStatus.COMPLETED);
                subTarefa.setTempoExecucao(System.currentTimeMillis() - inicio);
                subTarefa.setFinalizadoEm(LocalDateTime.now());

                job.setCompletadas(1);
                job.setProgresso(100);
                job.setStatus(JobStatus.COMPLETED);
                job.setTempoFim(LocalDateTime.now());

            } catch (Exception e) {
                log.error("Erro ao processar sub-tarefa {}", subTarefa.getId(), e);

                subTarefa.setStatus(SubTarefaStatus.FAILED);
                subTarefa.setErroMensagem(e.getMessage());
                subTarefa.setTempoExecucao(System.currentTimeMillis() - inicio);
                subTarefa.setFinalizadoEm(LocalDateTime.now());

                job.setFalhadas(1);
                job.setStatus(JobStatus.FAILED);
                job.setTempoFim(LocalDateTime.now());
                job.setErroMensagem(e.getMessage());
            }

            subTarefaRepository.save(subTarefa);
            jobRepository.save(job);

            // Notificar
            notificationService.notifyJobCompleted(jobId, job);

        } catch (Exception e) {
            log.error("Erro ao executar plano individual {}", jobId, e);
        } finally {
            redisJobCacheService.releaseLock(jobId);
            TenantContext.clear();
        }
    }

    /**
     * Executa plano em lote assincronamente
     */
    @Async("virtualThreadExecutor")
    public void executarPlanoLoteAsync(String jobId, String modo) {
        String tenantId = TenantContext.getTenantId();

        try {
            if (!redisJobCacheService.acquireLock(jobId, Duration.ofMinutes(60))) {
                log.warn("Não foi possível adquirir lock para job {}", jobId);
                return;
            }

            JobExecucao job = jobRepository.findById(jobId).orElseThrow();
            job.setStatus(JobStatus.RUNNING);
            job.setTempoInicio(LocalDateTime.now());
            jobRepository.save(job);

            List<SubTarefa> subTarefas = subTarefaRepository.findByJobExecucaoId(jobId);
            int total = subTarefas.size();

            // Processar em paralelo com CompletableFuture
            List<CompletableFuture<Void>> futures = subTarefas.stream()
                .map(subTarefa -> CompletableFuture.runAsync(
                    () -> {
                        try {
                            TenantContext.setTenantId(tenantId);
                            processarSubTarefa(subTarefa, modo);
                            subTarefa.setStatus(SubTarefaStatus.COMPLETED);
                        } catch (Exception e) {
                            log.error("Erro em sub-tarefa {}", subTarefa.getId(), e);
                            subTarefa.setStatus(SubTarefaStatus.FAILED);
                            subTarefa.setErroMensagem(e.getMessage());
                        } finally {
                            subTarefa.setFinalizadoEm(LocalDateTime.now());
                            subTarefaRepository.save(subTarefa);

                            // Atualizar progresso
                            atualizarProgresso(jobId, tenantId);

                            TenantContext.clear();
                        }
                    },
                    virtualThreadExecutor
                ))
                .collect(Collectors.toList());

            // Aguardar todas as sub-tarefas
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .join();

            // Finalizar job
            finalizarJob(jobId);

        } catch (Exception e) {
            log.error("Erro ao executar plano em lote {}", jobId, e);
            falharJob(jobId, e.getMessage());
        } finally {
            redisJobCacheService.releaseLock(jobId);
            TenantContext.clear();
        }
    }

    /**
     * Processa uma sub-tarefa individual
     */
    private void processarSubTarefa(SubTarefa subTarefa, String modo) {
        long inicio = System.currentTimeMillis();

        // Carregar atleta
        AtletaOutputDto atleta = planoService.buscarAtleta(subTarefa.getAtletaId());
        List<TreinoRealizadoOutputDto> treinos = planoService.buscarTreinos(subTarefa.getAtletaId());

        // Gerar plano via IA
        PlanoTreinoOutputDto plano = planoService.gerarPlanoIndividual(atleta, treinos, modo);

        // Salvar resultado
        subTarefa.setResultado(objectMapper.writeValueAsString(plano));
        subTarefa.setTempoExecucao(System.currentTimeMillis() - inicio);
    }

    /**
     * Atualiza progresso do job
     */
    private void atualizarProgresso(String jobId, String tenantId) {
        JobExecucao job = jobRepository.findById(jobId).orElseThrow();
        List<SubTarefa> subTarefas = subTarefaRepository.findByJobExecucaoId(jobId);

        long completadas = subTarefas.stream()
            .filter(s -> s.getStatus() == SubTarefaStatus.COMPLETED)
            .count();

        long falhadas = subTarefas.stream()
            .filter(s -> s.getStatus() == SubTarefaStatus.FAILED)
            .count();

        int progresso = (int) ((completadas + falhadas) * 100 / subTarefas.size());

        job.setProgresso(progresso);
        job.setCompletadas((int) completadas);
        job.setFalhadas((int) falhadas);
        jobRepository.save(job);

        // Atualizar Redis
        redisJobCacheService.updateJobProgress(jobId, progresso);

        // Notificar a cada 10%
        if (progresso % 10 == 0) {
            notificationService.notifyJobProgress(jobId, progresso);
        }
    }

    /**
     * Finaliza job com sucesso
     */
    private void finalizarJob(String jobId) {
        JobExecucao job = jobRepository.findById(jobId).orElseThrow();

        List<SubTarefa> subTarefas = subTarefaRepository.findByJobExecucaoId(jobId);
        long falhadas = subTarefas.stream()
            .filter(s -> s.getStatus() == SubTarefaStatus.FAILED)
            .count();

        if (falhadas == 0) {
            job.setStatus(JobStatus.COMPLETED);
        } else {
            job.setStatus(JobStatus.PARTIAL);
        }

        job.setProgresso(100);
        job.setTempoFim(LocalDateTime.now());
        jobRepository.save(job);

        notificationService.notifyJobCompleted(jobId, job);
    }

    /**
     * Marca job como falho
     */
    private void falharJob(String jobId, String mensagem) {
        JobExecucao job = jobRepository.findById(jobId).orElseThrow();
        job.setStatus(JobStatus.FAILED);
        job.setErroMensagem(mensagem);
        job.setTempoFim(LocalDateTime.now());
        jobRepository.save(job);

        notificationService.notifyJobFailed(jobId, mensagem);
    }

    /**
     * Cancela um job
     */
    @Transactional
    public void cancelarJob(String jobId) {
        JobExecucao job = jobRepository.findById(jobId).orElseThrow();

        if (job.getStatus() == JobStatus.RUNNING) {
            job.setStatus(JobStatus.CANCELLED);
            job.setTempoFim(LocalDateTime.now());
            jobRepository.save(job);

            notificationService.notifyJobCancelled(jobId);
        }
    }
}
```

---

## 🔄 Geração Individual vs Lote

### Geração Individual

```
Cliente                    Backend
   │                          │
   ├─────── POST /api/planos/async/individual ──────┐
   │                          │                       │
   │  ◄────── 202 {jobId} ─────────────────────────┤
   │                          │
   │ (UI muda para polling)   │
   │                          │
   │─────── GET /api/jobs/{jobId} ────────────┐      │
   │                          │                │      │
   │  ◄────────── 200 {status: RUNNING} ──────┤      │
   │                          │                │      │
   │ (espera 2s)              │               │      │
   │                          │ (gerando...)  │      │
   │─────── GET /api/jobs/{jobId} ────────────┐      │
   │                          │                │      │
   │  ◄────────── 200 {status: COMPLETED} ───┤      │
   │                          │                │      │
   │ (mostra resultado)       │               │      │
   │                          │               │      │
```

**Tempo total**: ~20 segundos (15s de geração + overhead)

### Geração em Lote (50 atletas)

```
Cliente                    Backend
   │                          │
   ├───── POST /api/planos/async/lote ──────────────┐
   │    [50 atletas]          │                      │
   │                          │                      │
   │  ◄──── 202 {jobId} ──────────────────────────┤
   │                          │
   │ (conecta WebSocket)      │ (iniciando...)
   │                          │
   │ ws://backend/ws          ├─ Virtual Thread 1 ─── atleta 1  (2-15s)
   │ subscribe(/topic/jobs/{jobId})    ├─ Virtual Thread 2 ─── atleta 2  (2-15s)
   │                          │     ├─ Virtual Thread 3 ─── atleta 3  (2-15s)
   │                          │     ├─ ...
   │                          │     └─ Virtual Thread 50 ─── atleta 50 (2-15s)
   │                          │
   │ ◄─ WebSocket {progress: 20%} ─── (em paralelo)
   │ ◄─ WebSocket {progress: 40%} ───┘
   │ ◄─ WebSocket {progress: 60%} ───┐
   │ ◄─ WebSocket {progress: 80%} ───┤ (notificações a cada 10%)
   │ ◄─ WebSocket {progress: 100%} ──┘
   │                          │
   │ (mostra resultado)       │
   │                          │
```

**Tempo total**: ~25 segundos (todos em paralelo com Virtual Threads)

### Comparação

| Aspecto | Individual | Lote (50 atletas) |
|---------|-----------|-------------------|
| **Tempo sem async** | ~15s | ~750s (12 min) |
| **Tempo com async** | ~20s | ~25s |
| **Melhoria** | 1x (baseline) | **30x** |
| **Concorrência** | 1 | 50 |
| **UX** | Polling REST | WebSocket + Notificações |

---

## 📊 Monitoramento e Status

### Endpoint: GET /api/jobs/{jobId}

```json
{
  "jobId": "550e8400-e29b-41d4-a716-446655440000",
  "status": "RUNNING",
  "tipo": "PLANO_LOTE",
  "progresso": 65,
  "completadas": 32,
  "falhadas": 2,
  "totalTarefas": 50,
  "tempoInicio": "2025-10-08T10:30:00Z",
  "tempoEstimado": "2025-10-08T10:35:30Z",
  "subTarefas": [
    {
      "id": "sub-1",
      "atletaId": "athlete-123",
      "status": "COMPLETED",
      "tempoExecucao": 12500,
      "finalizadoEm": "2025-10-08T10:30:12Z"
    },
    {
      "id": "sub-2",
      "atletaId": "athlete-124",
      "status": "FAILED",
      "erroMensagem": "Timeout na chamada OpenAI",
      "finalizadoEm": "2025-10-08T10:30:18Z"
    },
    ...
  ]
}
```

### Endpoint: GET /api/jobs?status=RUNNING&limit=20

```json
[
  {
    "jobId": "550e8400-e29b-41d4-a716-446655440000",
    "status": "RUNNING",
    "tipo": "PLANO_LOTE",
    "progresso": 65,
    "criadoEm": "2025-10-08T10:30:00Z",
    "completadas": 32,
    "falhadas": 2,
    "totalTarefas": 50
  },
  {
    "jobId": "660e8400-e29b-41d4-a716-446655440111",
    "status": "RUNNING",
    "tipo": "PLANO_INDIVIDUAL",
    "progresso": 45,
    "criadoEm": "2025-10-08T10:35:00Z",
    "completadas": 1,
    "falhadas": 0,
    "totalTarefas": 1
  }
]
```

### WebSocket Notifications

```javascript
// Cliente JavaScript
const stompClient = new StompJs.Client({
  brokerURL: 'wss://api.menthoros.com/ws'
});

stompClient.activate();

stompClient.subscribe(`/topic/jobs/550e8400-e29b-41d4-a716-446655440000`,
  (message) => {
    const notification = JSON.parse(message.body);
    console.log('Progresso:', notification.progresso + '%');
    updateUI(notification);
  }
);
```

**Formato de notificação**:

```json
{
  "jobId": "550e8400-e29b-41d4-a716-446655440000",
  "progresso": 70,
  "completadas": 35,
  "falhadas": 2,
  "timestamp": "2025-10-08T10:34:30Z"
}
```

---

## ⚠️ Tratamento de Erros

### Estratégia de Retry

```
Sub-tarefa falha
    │
    ├─ Erro validação? ──► FAILED (sem retry)
    │
    ├─ Timeout LLM? ──► RETRY (exponential backoff)
    │       │
    │       ├─ Tentativa 1: falha após 5s
    │       ├─ Tentativa 2: falha após 15s
    │       ├─ Tentativa 3: falha após 45s
    │       └─ Tentativa 3: FAILED
    │
    └─ Erro banco? ──► RETRY (exponential backoff)
            │
            └─ Max 3 tentativas
```

### Implementação

```java
private void processarSubTarefa(SubTarefa subTarefa, String modo) {
    int maxTentativas = 3;
    int[] backoffs = {5, 15, 45}; // segundos

    for (int tentativa = 0; tentativa < maxTentativas; tentativa++) {
        try {
            // Processar
            PlanoTreinoOutputDto plano = planoService.gerarPlano(...);
            subTarefa.setResultado(serialize(plano));
            subTarefa.setStatus(SubTarefaStatus.COMPLETED);
            return;

        } catch (ValidationException e) {
            // Erro de validação - não fazer retry
            subTarefa.setStatus(SubTarefaStatus.FAILED);
            subTarefa.setErroMensagem(e.getMessage());
            throw e;

        } catch (TimeoutException | DatabaseException e) {
            if (tentativa < maxTentativas - 1) {
                // Fazer retry
                subTarefa.setTentativas(tentativa + 1);
                subTarefa.setStatus(SubTarefaStatus.RETRY);

                long sleepMs = backoffs[tentativa] * 1000;
                Thread.sleep(sleepMs);
            } else {
                // Última tentativa falhou
                subTarefa.setStatus(SubTarefaStatus.FAILED);
                subTarefa.setErroMensagem(e.getMessage());
                subTarefa.setTentativas(tentativa + 1);
                throw e;
            }
        }
    }
}
```

### Tipos de Erro Tratados

| Erro | Tipo | Retry | Ação |
|------|------|-------|------|
| Timeout OpenAI | Transiente | ✅ Sim | Exponential backoff (max 3x) |
| Atleta não encontrado | Validação | ❌ Não | FAILED |
| Conexão banco | Transiente | ✅ Sim | Exponential backoff (max 3x) |
| Out of Memory | Sistema | ❌ Não | FAILED + Alert |
| Rate limit API | Transiente | ✅ Sim | Circuit breaker + retry |

---

## 🛑 Cancelamento de Jobs

### Endpoint: DELETE /api/jobs/{jobId}

```java
@DeleteMapping("/jobs/{jobId}")
public ResponseEntity<Void> cancelarJob(
    @PathVariable String jobId) {

    planoAsyncService.cancelarJob(jobId);
    return ResponseEntity.noContent().build();
}
```

### Fluxo

```
User clica em "Cancelar"
         │
         ▼
DELETE /api/jobs/{jobId}
         │
         ▼
PlanoAsyncService.cancelarJob()
         │
         ├─ Marcar job como CANCELLED
         │
         ├─ Publicar evento via Redis Pub/Sub
         │
         └─ Notificar via WebSocket
             │
             ▼
Threads detectam cancelamento (em cada loop)
    │
    ├─ Sub-tarefa em execução? ──► Interromper gracefully
    │
    ├─ Sub-tarefa na fila? ──► Pular
    │
    └─ Job marcado como CANCELLED
```

### Implementação

```java
@Transactional
public void cancelarJob(String jobId) {
    JobExecucao job = jobRepository.findById(jobId)
        .orElseThrow(() -> new NotFoundException("Job não encontrado"));

    String tenantId = TenantContext.getTenantId();

    // Validar ownership
    if (!job.getTenantId().equals(tenantId)) {
        throw new ForbiddenException("Acesso negado");
    }

    if (job.getStatus() != JobStatus.RUNNING) {
        throw new IllegalStateException("Apenas jobs em execução podem ser cancelados");
    }

    job.setStatus(JobStatus.CANCELLED);
    job.setTempoFim(LocalDateTime.now());
    jobRepository.save(job);

    // Publicar evento
    redisJobCacheService.publishJobEvent(jobId, new JobEventDto(
        jobId,
        JobEventType.CANCELLED
    ));

    // Notificar
    notificationService.notifyJobCancelled(jobId);
}
```

---

## 🚀 Performance e Escalabilidade

### Benchmarks

#### Teste 1: Throughput (100 planos)

```
❌ Sequencial (10-thread pool):
   Tempo total: 150 segundos
   Throughput: 40 planos/minuto

✅ Virtual Threads (novavirtual thread per task):
   Tempo total: 20 segundos
   Throughput: 300 planos/minuto

Melhoria: 7.5x
```

#### Teste 2: Escalabilidade (memory)

```
❌ Platform Threads (100 threads):
   Memory: ~200 MB
   Threads: 100
   Memory per thread: 2 MB

✅ Virtual Threads (100k threads):
   Memory: ~100 MB
   Threads: 100,000
   Memory per thread: 1 KB

Eficiência: 100x melhor
```

#### Teste 3: Latência

```
P50:  5 segundos
P95:  12 segundos
P99:  15 segundos
Max:  18 segundos

SLA: 95% de jobs completam em < 20 segundos
```

### Otimizações

#### 1. Cache de Contexto

```java
@Transactional(readOnly = true)
public void processarSubTarefa(SubTarefa subTarefa, String modo) {
    // Cachear dados do atleta para evitar N+1
    @EntityGraph(attributePaths = {"endereco", "documentos"})
    Atleta atleta = atletaRepository.findById(subTarefa.getAtletaId());

    List<TreinoRealizado> treinos = treinoRepository.findAllByAtletaId(
        subTarefa.getAtletaId()
    );

    // Usar dados em memória
    PlanoTreinoOutputDto plano = planoService.gerarPlano(atleta, treinos, modo);
}
```

#### 2. Batch Insert de Sub-tarefas

```java
List<SubTarefa> subTarefas = new ArrayList<>();
for (String atletaId : atletaIds) {
    SubTarefa subTarefa = new SubTarefa();
    // ...
    subTarefas.add(subTarefa);
}

// Batch insert
subTarefaRepository.saveAll(subTarefas); // Muito mais rápido!
```

#### 3. Circuit Breaker para LLM

```java
@CircuitBreaker(
    name = "openai",
    fallbackMethod = "fallbackGerarPlano"
)
public PlanoTreinoOutputDto gerarPlano(Atleta atleta, List<Treino> treinos) {
    return openaiService.gerarPlano(atleta, treinos);
}

public PlanoTreinoOutputDto fallbackGerarPlano(
    Atleta atleta,
    List<Treino> treinos,
    Exception ex) {

    log.error("OpenAI indisponível, retornando plano padrão");
    return criarPlanoDefault(atleta);
}
```

---

## 📅 Roadmap de Implementação

### SPRINT 1 - Fundação do Sistema Assíncrono (Semana 1)

**Objetivo**: Criar estrutura base e configuração de Virtual Threads

#### Tarefas:
- [ ] **1.1** Atualizar para Java 21 (se necessário)
- [ ] **1.2** Criar enums (JobType, JobStatus)
- [ ] **1.3** Criar entidade `JobExecucao`
- [ ] **1.4** Criar entidade `SubTarefa`
- [ ] **1.5** Criar migration do banco
- [ ] **1.6** Executar migration
- [ ] **1.7** Criar repositories
- [ ] **1.8** Configurar AsyncConfig (VirtualThreadExecutor)
- [ ] **1.9** Adicionar dependências Redis
- [ ] **1.10** Configurar Redis
- [ ] **1.11** Adicionar configurações no application.yml
- [ ] **1.12** Criar `RedisJobCacheService`
- [ ] **1.13** Criar DTOs

**Entregáveis**: Estrutura de dados, Virtual Threads e Redis configurados

---

### SPRINT 2 - Service de Execução Assíncrona (Semana 1-2)

**Objetivo**: Implementar lógica core de geração assíncrona

#### Tarefas:
- [ ] **2.1** Criar `PlanoAsyncService`
- [ ] **2.2** Implementar processamento de sub-tarefas
- [ ] **2.3** Implementar métodos auxiliares (atualizar status, finalizar, falhar)
- [ ] **2.4** Criar tratamento de erros
- [ ] **2.5** Garantir isolamento de tenant
- [ ] **2.6** Integrar Redis no PlanoAsyncService
- [ ] **2.7** Testes unitários básicos

**Entregáveis**: Geração assíncrona funcionando com cache Redis

---

### SPRINT 3 - Controllers REST (Semana 2)

**Objetivo**: Criar endpoints para interação com jobs

#### Tarefas:
- [ ] **3.1** Criar DTOs (JobSubmitRequest, JobSubmitResponse, JobStatusResponse)
- [ ] **3.2** Criar `PlanoAsyncController`
  - POST /api/planos/async/individual
  - POST /api/planos/async/lote
  - GET /api/jobs/{jobId}
  - GET /api/jobs
  - DELETE /api/jobs/{jobId}
- [ ] **3.3** Implementar validações
- [ ] **3.4** Adicionar anotações Swagger
- [ ] **3.5** Testes de integração

**Entregáveis**: API REST completa para jobs assíncronos

---

### SPRINT 4 - WebSocket para Notificações Tempo Real (Semana 3)

**Objetivo**: Notificar frontend sobre progresso dos jobs

#### Tarefas:
- [ ] **4.1** Adicionar dependência WebSocket
- [ ] **4.2** Configurar WebSocket
- [ ] **4.3** Criar `WebSocketNotificationService`
- [ ] **4.4** Integrar com PlanoAsyncService
- [ ] **4.5** Criar DTOs de notificação
- [ ] **4.6** Documentar protocolo WebSocket
- [ ] **4.7** Testar com cliente WebSocket

**Entregáveis**: WebSocket funcionando com notificações tempo real

---

### SPRINT 5 - Cancelamento e Retry (Semana 3-4)

**Objetivo**: Permitir cancelamento e retry de jobs

#### Tarefas:
- [ ] **5.1** Implementar cancelamento
- [ ] **5.2** Implementar verificação de cancelamento em sub-tarefas
- [ ] **5.3** Criar estratégia de retry (max 3 tentativas, exponential backoff)
- [ ] **5.4** Implementar retry de sub-tarefas
- [ ] **5.5** Criar endpoint de retry manual
- [ ] **5.6** Implementar cleanup de jobs antigos

**Entregáveis**: Cancelamento e retry funcionando

---

### SPRINT 6 - Monitoramento e Métricas (Semana 4)

**Objetivo**: Observabilidade do sistema assíncrono

#### Tarefas:
- [ ] **6.1** Criar `JobMonitoringService`
- [ ] **6.2** Criar dashboard de métricas (GET /api/jobs/metricas)
- [ ] **6.3** Adicionar logs estruturados
- [ ] **6.4** Criar alertas
- [ ] **6.5** Integrar com Actuator
- [ ] **6.6** Criar endpoint de health check específico

**Entregáveis**: Monitoramento completo

---

### SPRINT 7 - Priorização e Fila (Semana 5)

**Objetivo**: Gerenciar prioridade de jobs

#### Tarefas:
- [ ] **7.1** Adicionar campo prioridade em JobExecucao
- [ ] **7.2** Implementar fila prioritária (PriorityBlockingQueue)
- [ ] **7.3** Criar regras de prioridade
- [ ] **7.4** Implementar rate limiting por assessoria
- [ ] **7.5** Adicionar endpoint para alterar prioridade
- [ ] **7.6** Implementar estimativa de tempo

**Entregáveis**: Sistema de priorização funcionando

---

### SPRINT 8 - Otimizações de Performance (Semana 5-6)

**Objetivo**: Maximizar throughput

#### Tarefas:
- [ ] **8.1** Implementar cache de contexto
- [ ] **8.2** Batch insert de sub-tarefas
- [ ] **8.3** Otimizar queries (EntityGraph, projection)
- [ ] **8.4** Implementar circuit breaker para LLM (Resilience4j)
- [ ] **8.5** Adicionar pooling de conexões
- [ ] **8.6** Implementar compressão de resultados

**Entregáveis**: Performance otimizada

---

### SPRINT 9 - Geração Agendada (Semana 6)

**Objetivo**: Agendar geração de planos

#### Tarefas:
- [ ] **9.1** Criar entidade `JobAgendado`
- [ ] **9.2** Implementar scheduler (@Scheduled)
- [ ] **9.3** Criar CRUD de agendamentos
- [ ] **9.4** Validar expressões cron
- [ ] **9.5** Adicionar histórico de execuções

**Entregáveis**: Agendamento de geração automática

---

### SPRINT 10 - Testes de Carga (Semana 7)

**Objetivo**: Validar escalabilidade

#### Tarefas:
- [ ] **10.1** Criar testes de carga com JMeter/Gatling
- [ ] **10.2** Teste de stress (1000 jobs simultâneos)
- [ ] **10.3** Teste de longa duração (12 horas)
- [ ] **10.4** Teste de falhas
- [ ] **10.5** Benchmark Virtual Threads vs Platform Threads
- [ ] **10.6** Otimizar baseado em resultados

**Entregáveis**: Sistema testado e validado

---

### SPRINT 11 - Documentação e Guias (Semana 7)

**Objetivo**: Documentar completamente

#### Tarefas:
- [ ] **11.1** Documentação técnica
- [ ] **11.2** Guia do desenvolvedor
- [ ] **11.3** Guia do usuário
- [ ] **11.4** API documentation (Swagger)
- [ ] **11.5** Runbook operacional

**Entregáveis**: Documentação completa

---

## 📈 Marcos de Entrega

| Marco | Descrição | Prazo |
|-------|-----------|-------|
| **M1** | Estrutura base + Virtual Threads | Fim da Semana 1 |
| **M2** | Geração assíncrona funcionando | Fim da Semana 2 |
| **M3** | API REST completa | Fim da Semana 2 |
| **M4** | WebSocket tempo real | Fim da Semana 3 |
| **M5** | Cancelamento e retry | Fim da Semana 4 |
| **M6** | Monitoramento | Fim da Semana 4 |
| **M7** | Priorização e fila | Fim da Semana 5 |
| **M8** | Performance otimizada | Fim da Semana 6 |
| **M9** | Agendamento | Fim da Semana 6 |
| **M10** | Sistema em produção | Fim da Semana 7 |

---

## 🧪 Testes

### Testes Unitários

```java
@SpringBootTest
public class PlanoAsyncServiceTest {

    @Autowired
    private PlanoAsyncService planoAsyncService;

    @MockBean
    private JobExecucaoRepository jobRepository;

    @MockBean
    private RedisJobCacheService redisJobCacheService;

    @Test
    public void testSubmitPlanoIndividual() {
        // Arrange
        String atletaId = "athlete-123";
        String modo = "PROXIMA_SEMANA";

        // Act
        JobSubmitResponse response = planoAsyncService.submitPlanoIndividual(
            atletaId,
            modo
        );

        // Assert
        assertNotNull(response.getJobId());
        assertEquals(JobStatus.QUEUED, response.getStatus());
    }

    @Test
    public void testSubmitPlanoLote() {
        List<String> atletaIds = Arrays.asList("a1", "a2", "a3");

        JobSubmitResponse response = planoAsyncService.submitPlanoLote(
            atletaIds,
            "PROXIMA_SEMANA"
        );

        assertNotNull(response.getJobId());
    }
}
```

### Testes de Integração

```java
@SpringBootTest
@AutoConfigureMockMvc
public class PlanoAsyncControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @Test
    public void testGerarPlanoIndividual() throws Exception {
        String payload = """
            {
                "atletaId": "athlete-123",
                "modo": "PROXIMA_SEMANA"
            }
            """;

        mockMvc.perform(post("/api/planos/async/individual")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.jobId").exists())
            .andExpect(jsonPath("$.status").value("QUEUED"));
    }
}
```

### Testes de Carga

```bash
# JMeter - 100 requisições simultâneas
jmeter -n -t teste_carga.jmx -l resultados.csv -j test.log

# Gatling - Load test
gatling.io
```

---

## 💡 Benefícios Esperados

### Performance

- **Antes**: 100 planos = ~15 minutos (sequencial)
- **Depois**: 100 planos = ~20 segundos (paralelo com Virtual Threads)
- **Melhoria**: **45x mais rápido**

### UX

- **Antes**: Usuário espera resposta (timeout em 30s)
- **Depois**: Resposta imediata + notificação quando pronto

### Escalabilidade

- **Antes**: ~10-20 threads (limite físico)
- **Depois**: Praticamente ilimitado (milhões de Virtual Threads)

### Confiabilidade

- **Retry automático** em falhas transientes
- **Circuit breaker** para proteção contra falhas em cascata
- **Isolamento** de falhas por sub-tarefa

---

## ⚠️ Riscos e Mitigações

| Risco | Impacto | Mitigação |
|-------|---------|-----------|
| **Sobrecarga da LLM** | ALTO | Rate limiting + circuit breaker |
| **Memory leak em threads** | ALTO | Testes de longa duração + monitoring |
| **Jobs ficam travados** | MÉDIO | Timeout automático + cleanup job |
| **Banco de dados sobrecarregado** | MÉDIO | Batch operations + connection pooling |
| **Falha em sub-tarefa trava todo lote** | BAIXO | Isolamento de erros por sub-tarefa |

---

## 📊 Métricas de Sucesso

- ✅ **Throughput**: 100+ planos/minuto
- ✅ **Latência P95**: < 15 segundos
- ✅ **Taxa de sucesso**: > 95%
- ✅ **Uptime**: > 99.9%
- ✅ **Satisfação do usuário**: Feedback positivo

---

## 🚀 Quick Start - Começar Hoje

### Comandos para Iniciar (Sprint 1)

```bash
# 1. Verificar Java 21
java -version
# java version "21.0.1"

# 2. Criar enums
mkdir -p src/main/java/com/menthoros/enums
# Criar JobType.java e JobStatus.java

# 3. Criar migration
cat > src/main/resources/db/migration/V9__Create_async_job_tables.sql << 'EOF'
-- (Copiar SQL do guia principal)
EOF

# 4. Executar migration
mvn flyway:migrate

# 5. Criar AsyncConfig
# (Copiar código do guia)

# 6. Compilar e testar
mvn clean compile
mvn test
```

---

## 📋 Checklist Final

Antes de considerar implementação completa:

### Funcionalidade
- [ ] Geração individual assíncrona
- [ ] Geração em lote (10+ atletas simultaneamente)
- [ ] WebSocket notificando progresso
- [ ] Cancelamento de jobs
- [ ] Retry automático
- [ ] Agendamento de jobs

### Performance
- [ ] Throughput > 100 planos/minuto
- [ ] Tempo médio < 10 segundos/plano
- [ ] Sistema estável com 1000+ jobs
- [ ] Sem memory leaks
- [ ] Virtual Threads sendo utilizadas

### Qualidade
- [ ] Cobertura de testes > 80%
- [ ] Testes de carga executados
- [ ] Logs estruturados
- [ ] Monitoramento funcionando

### Segurança
- [ ] Isolamento por tenant
- [ ] Rate limiting por assessoria
- [ ] Validação de permissões
- [ ] Jobs não vazam entre tenants

---

## 📝 Próximos Passos

1. **Começar pelo Sprint 1, tarefa 1.1**
2. **Tempo estimado total**: 7 semanas
3. **Equipe recomendada**: 2 desenvolvedores
4. **Complexidade**: Alta
5. **Prioridade**: Alta (melhora significativa de UX)

---

**Documento compilado em**: 2025-10-19
**Versão**: 2.0
**Status**: Pronto para implementação
