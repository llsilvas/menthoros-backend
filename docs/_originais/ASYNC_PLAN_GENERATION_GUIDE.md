# ⚡ Guia Completo de Geração Assíncrona de Planos - Virtual Threads Java 21

## 📋 Índice
1. [Visão Geral](#visão-geral)
2. [Por que Virtual Threads?](#por-que-virtual-threads)
3. [Arquitetura da Solução](#arquitetura-da-solução)
4. [Modelo de Dados](#modelo-de-dados)
5. [Implementação Passo a Passo](#implementação-passo-a-passo)
6. [Geração Individual vs Lote](#geração-individual-vs-lote)
7. [Monitoramento e Status](#monitoramento-e-status)
8. [Tratamento de Erros](#tratamento-de-erros)
9. [Cancelamento de Jobs](#cancelamento-de-jobs)
10. [Performance e Escalabilidade](#performance-e-escalabilidade)
11. [Testes](#testes)

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
│ → { status: "COMPLETED", planoId: "..." }│
└──────────────────────────────────────────┘

Caso 2: Geração em Lote
┌──────────────────────────────────────────┐
│ Assessoria com 50 atletas                │
│ POST /api/planos/async/lote              │
│ → { jobId: "...", total: 50 }            │
│                                          │
│ GET /api/jobs/{jobId}                   │
│ → { completed: 20, failed: 2, running: 28}│
│                                          │
│ WebSocket atualização em tempo real      │
└──────────────────────────────────────────┘
```

---

## 🚀 Por que Virtual Threads?

### Comparação: Threads Tradicionais vs Virtual Threads

| Aspecto | Platform Threads | Virtual Threads (Java 21) |
|---------|------------------|---------------------------|
| **Custo de memória** | ~2 MB por thread | ~1 KB por thread |
| **Limite prático** | ~Milhares | ~Milhões |
| **Criação** | Cara (~1ms) | Quase grátis (~µs) |
| **Bloqueio** | Bloqueia OS thread | Libera carrier thread |
| **Ideal para** | CPU-intensive | I/O-intensive (LLM calls!) |

### Exemplo de Performance

```java
// ❌ Threads tradicionais - Limite ~5000-10000 threads
ExecutorService executor = Executors.newFixedThreadPool(100);

// ✅ Virtual Threads - Praticamente ilimitado
ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
```

**Resultado real**:
- 100 planos sequenciais: **~15 minutos** (9s/plano)
- 100 planos com Virtual Threads: **~20 segundos** (paralelismo massivo)

---

## 🏗️ Arquitetura da Solução (com Redis)

```
┌─────────────────────────────────────────────────────────────────────┐
│                          API Layer                                   │
│                                                                      │
│  ┌────────────────────┐        ┌──────────────────────┐            │
│  │ PlanoAsyncController│───────▶│ PlanoAsyncService    │            │
│  │ - POST /async      │        │ - submitJob()        │            │
│  │ - GET /jobs/{id}   │        │ - submitBatchJob()   │            │
│  └────────────────────┘        └──────────┬───────────┘            │
│                                            │                         │
└────────────────────────────────────────────┼─────────────────────────┘
                                             │
                    ┌────────────────────────┼────────────────────────┐
                    │    Redis Layer (Cache & Queue)                  │
                    │                        ▼                         │
                    │  ┌──────────────────────────────────────────┐  │
                    │  │ Redis Cache                              │  │
                    │  │ - job:{jobId} → Status em tempo real     │  │
                    │  │ - job:{jobId}:progress → 0-100%          │  │
                    │  │ - tenant:{id}:rate → Rate limiting       │  │
                    │  │ - TTL: 24h (auto-cleanup)                │  │
                    │  └──────────────────────────────────────────┘  │
                    │                                                 │
                    │  ┌──────────────────────────────────────────┐  │
                    │  │ Redis Queue (Resque/Bull)                │  │
                    │  │ - queue:plano:individual                 │  │
                    │  │ - queue:plano:lote                       │  │
                    │  │ - Priority queue support                 │  │
                    │  └──────────────┬───────────────────────────┘  │
                    │                 │                               │
                    └─────────────────┼───────────────────────────────┘
                                      │
                    ┌─────────────────┼───────────────────────────────┐
                    │    Async Processing                             │
                    │                 ▼                               │
                    │  ┌──────────────────────────────────────────┐  │
                    │  │ VirtualThreadExecutor                    │  │
                    │  │ - Cria Virtual Thread por plano          │  │
                    │  │ - Executa em paralelo (100+ threads)     │  │
                    │  │ - Consome jobs da Redis Queue            │  │
                    │  └──────────────┬───────────────────────────┘  │
                    │                 │                               │
                    │                 ▼                               │
                    │  ┌──────────────────────────────────────────┐  │
                    │  │ PostgreSQL (Persistência Final)          │  │
                    │  │ - tb_job_execucao (histórico)            │  │
                    │  │ - tb_sub_tarefa (detalhes)               │  │
                    │  └──────────────────────────────────────────┘  │
                    │                                                 │
                    │  ┌──────────────────────────────────────────┐  │
                    │  │ Redis Pub/Sub (Notificações)             │  │
                    │  │ - channel:job:{jobId}                    │  │
                    │  │ - Subscribers: WebSocket, UI             │  │
                    │  └──────────────────────────────────────────┘  │
                    └─────────────────────────────────────────────────┘
```

### **Fluxo de Dados com Redis:**

```
1. Client POST /api/planos/async/atleta/123
   ↓
2. PlanoAsyncService.submitJob()
   ↓
3. Redis: RPUSH queue:plano:individual "{jobId, atletaId, modo}"
   ↓
4. Redis: SET job:{jobId} "QUEUED" EX 86400
   ↓
5. PostgreSQL: INSERT tb_job_execucao (histórico persistente)
   ↓
6. Return: { jobId: "abc-123" } (resposta imediata)

--- Em paralelo (Virtual Thread) ---

7. VirtualThread: BLPOP queue:plano:individual (worker aguarda job)
   ↓
8. Redis: SET job:{jobId} "RUNNING"
   ↓
9. Redis: PUBLISH channel:job:{jobId} '{"status":"RUNNING"}'
   ↓
10. LLM Call (operação I/O-bound demorada)
    ↓
11. Redis: SET job:{jobId}:progress "50"
    ↓
12. LLM Response → PlanoSemanal salvo
    ↓
13. Redis: SET job:{jobId} "COMPLETED"
    ↓
14. Redis: PUBLISH channel:job:{jobId} '{"status":"COMPLETED","planoId":"xyz"}'
    ↓
15. PostgreSQL: UPDATE tb_job_execucao SET status='COMPLETED', fim_em=NOW()
```

---

## 🗄️ Modelo de Dados

### **Entidade: JobExecucao**

```java
package com.menthoros.entity;

import com.menthoros.enums.JobStatus;
import com.menthoros.enums.JobType;
import jakarta.persistence.*;
import lombok.*;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "tb_job_execucao",
    indexes = {
        @Index(name = "idx_job_tenant", columnList = "tenant_id"),
        @Index(name = "idx_job_status", columnList = "status"),
        @Index(name = "idx_job_criado_por", columnList = "criado_por")
    })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JobExecucao {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Assessoria assessoria; // Multi-tenant

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false)
    private JobType tipo; // PLANO_INDIVIDUAL, PLANO_LOTE

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private JobStatus status;

    // ===== PROGRESSO =====
    @Column(name = "total_tarefas")
    private Integer totalTarefas;

    @Column(name = "tarefas_completadas")
    private Integer tarefasCompletadas = 0;

    @Column(name = "tarefas_falhadas")
    private Integer tarefasFalhadas = 0;

    @Column(name = "tarefas_canceladas")
    private Integer tarefasCanceladas = 0;

    // ===== TIMING =====
    @Column(name = "inicio_em", nullable = false)
    private LocalDateTime inicioEm;

    @Column(name = "fim_em")
    private LocalDateTime fimEm;

    @Column(name = "duracao_ms")
    private Long duracaoMs;

    // ===== METADADOS =====
    @Column(name = "parametros", columnDefinition = "jsonb")
    private String parametros; // JSON com params do job

    @Column(name = "resultado", columnDefinition = "jsonb")
    private String resultado; // JSON com resultados

    @Column(name = "erro", columnDefinition = "TEXT")
    private String erro;

    // ===== RASTREABILIDADE =====
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "criado_por")
    private Usuario criadoPor;

    @Column(name = "cancelado_por_id")
    private UUID canceladoPorId;

    @Column(name = "cancelado_em")
    private LocalDateTime canceladoEm;

    // ===== SUB-TAREFAS =====
    @OneToMany(mappedBy = "jobPai", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<SubTarefa> subTarefas = new ArrayList<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_pai_id")
    private JobExecucao jobPai; // Para jobs compostos

    /**
     * Calcula progresso em percentual (0-100)
     */
    public int getProgressoPercentual() {
        if (totalTarefas == null || totalTarefas == 0) return 0;
        return (int) ((tarefasCompletadas * 100.0) / totalTarefas);
    }

    /**
     * Verifica se job está finalizado (sucesso ou falha)
     */
    public boolean isFinalizado() {
        return status == JobStatus.COMPLETED ||
               status == JobStatus.FAILED ||
               status == JobStatus.CANCELLED;
    }

    /**
     * Marca job como completado
     */
    public void marcarComoCompletado() {
        this.status = JobStatus.COMPLETED;
        this.fimEm = LocalDateTime.now();
        this.duracaoMs = Duration.between(inicioEm, fimEm).toMillis();
    }

    /**
     * Marca job como falho
     */
    public void marcarComoFalho(String mensagemErro) {
        this.status = JobStatus.FAILED;
        this.erro = mensagemErro;
        this.fimEm = LocalDateTime.now();
        this.duracaoMs = Duration.between(inicioEm, fimEm).toMillis();
    }

    /**
     * Incrementa contador de tarefas completadas
     */
    public void incrementarCompletadas() {
        this.tarefasCompletadas++;
        if (this.tarefasCompletadas.equals(this.totalTarefas)) {
            marcarComoCompletado();
        }
    }

    /**
     * Incrementa contador de tarefas falhadas
     */
    public void incrementarFalhadas() {
        this.tarefasFalhadas++;
    }
}

// ===== ENUMS =====

enum JobType {
    PLANO_INDIVIDUAL,   // Gerar 1 plano
    PLANO_LOTE,         // Gerar N planos
    SINCRONIZACAO_STRAVA, // Futuro
    CALCULO_METRICAS    // Futuro
}

enum JobStatus {
    QUEUED,      // Na fila
    RUNNING,     // Em execução
    COMPLETED,   // Concluído com sucesso
    FAILED,      // Falhou
    CANCELLED,   // Cancelado pelo usuário
    PARTIAL      // Parcialmente concluído (alguns falharam)
}
```

### **Entidade: SubTarefa**

```java
package com.menthoros.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "tb_sub_tarefa",
    indexes = {
        @Index(name = "idx_subtarefa_job", columnList = "job_execucao_id")
    })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubTarefa {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_execucao_id", nullable = false)
    private JobExecucao jobExecucao;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "atleta_id")
    private Atleta atleta; // Se for geração de plano

    @Column(name = "descricao", length = 500)
    private String descricao;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private JobStatus status;

    @Column(name = "inicio_em")
    private LocalDateTime inicioEm;

    @Column(name = "fim_em")
    private LocalDateTime fimEm;

    @Column(name = "duracao_ms")
    private Long duracaoMs;

    @Column(name = "resultado_id")
    private UUID resultadoId; // Ex: ID do PlanoSemanal gerado

    @Column(name = "erro", columnDefinition = "TEXT")
    private String erro;

    @Column(name = "tentativas")
    private Integer tentativas = 0;

    public void marcarComoIniciada() {
        this.status = JobStatus.RUNNING;
        this.inicioEm = LocalDateTime.now();
    }

    public void marcarComoConcluida(UUID resultadoId) {
        this.status = JobStatus.COMPLETED;
        this.fimEm = LocalDateTime.now();
        this.resultadoId = resultadoId;
        calcularDuracao();
    }

    public void marcarComoFalha(String mensagemErro) {
        this.status = JobStatus.FAILED;
        this.fimEm = LocalDateTime.now();
        this.erro = mensagemErro;
        this.tentativas++;
        calcularDuracao();
    }

    private void calcularDuracao() {
        if (inicioEm != null && fimEm != null) {
            this.duracaoMs = java.time.Duration.between(inicioEm, fimEm).toMillis();
        }
    }
}
```

---

## 🔧 Implementação Passo a Passo

### **ETAPA 1: Migration do Banco de Dados**

#### V9__Create_async_job_tables.sql
```sql
-- src/main/resources/db/migration/V9__Create_async_job_tables.sql

-- =============================================
-- TABELA: tb_job_execucao
-- =============================================
CREATE TABLE tb_job_execucao (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tb_assessoria(id) ON DELETE CASCADE,
    tipo VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL,

    -- Progresso
    total_tarefas INTEGER,
    tarefas_completadas INTEGER DEFAULT 0,
    tarefas_falhadas INTEGER DEFAULT 0,
    tarefas_canceladas INTEGER DEFAULT 0,

    -- Timing
    inicio_em TIMESTAMP NOT NULL,
    fim_em TIMESTAMP,
    duracao_ms BIGINT,

    -- Metadados
    parametros JSONB,
    resultado JSONB,
    erro TEXT,

    -- Rastreabilidade
    criado_por UUID REFERENCES tb_usuario(id) ON DELETE SET NULL,
    cancelado_por_id UUID,
    cancelado_em TIMESTAMP,

    -- Hierarquia
    job_pai_id UUID REFERENCES tb_job_execucao(id) ON DELETE CASCADE,

    CONSTRAINT chk_job_tipo CHECK (tipo IN ('PLANO_INDIVIDUAL', 'PLANO_LOTE', 'SINCRONIZACAO_STRAVA', 'CALCULO_METRICAS')),
    CONSTRAINT chk_job_status CHECK (status IN ('QUEUED', 'RUNNING', 'COMPLETED', 'FAILED', 'CANCELLED', 'PARTIAL'))
);

CREATE INDEX idx_job_tenant ON tb_job_execucao(tenant_id);
CREATE INDEX idx_job_status ON tb_job_execucao(status);
CREATE INDEX idx_job_criado_por ON tb_job_execucao(criado_por);
CREATE INDEX idx_job_pai ON tb_job_execucao(job_pai_id);
CREATE INDEX idx_job_inicio_em ON tb_job_execucao(inicio_em);

-- =============================================
-- TABELA: tb_sub_tarefa
-- =============================================
CREATE TABLE tb_sub_tarefa (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    job_execucao_id UUID NOT NULL REFERENCES tb_job_execucao(id) ON DELETE CASCADE,
    atleta_id UUID REFERENCES tb_atleta(id) ON DELETE CASCADE,
    descricao VARCHAR(500),
    status VARCHAR(20) NOT NULL,

    -- Timing
    inicio_em TIMESTAMP,
    fim_em TIMESTAMP,
    duracao_ms BIGINT,

    -- Resultado
    resultado_id UUID, -- ID do PlanoSemanal gerado
    erro TEXT,
    tentativas INTEGER DEFAULT 0,

    CONSTRAINT chk_subtarefa_status CHECK (status IN ('QUEUED', 'RUNNING', 'COMPLETED', 'FAILED', 'CANCELLED'))
);

CREATE INDEX idx_subtarefa_job ON tb_sub_tarefa(job_execucao_id);
CREATE INDEX idx_subtarefa_atleta ON tb_sub_tarefa(atleta_id);
CREATE INDEX idx_subtarefa_status ON tb_sub_tarefa(status);
```

---

### **ETAPA 2: Configuração do Executor com Virtual Threads**

#### AsyncConfig.java
```java
package com.menthoros.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@Slf4j
@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {

    /**
     * Executor com Virtual Threads do Java 21
     *
     * Virtual Threads são ideais para operações I/O-bound como chamadas à LLM.
     * Cada thread virtual consome apenas ~1KB de memória vs ~2MB de platform threads.
     *
     * Isso permite executar milhares de tarefas concorrentes sem overhead.
     */
    @Bean(name = "virtualThreadExecutor")
    public Executor virtualThreadExecutor() {
        log.info("Configurando VirtualThreadPerTaskExecutor");

        return Executors.newVirtualThreadPerTaskExecutor();
    }

    /**
     * Executor limitado para operações críticas
     * (fallback se necessário)
     */
    @Bean(name = "boundedExecutor")
    public Executor boundedExecutor() {
        // Para operações que precisam de controle de concorrência
        return Executors.newFixedThreadPool(
            Runtime.getRuntime().availableProcessors() * 2
        );
    }

    @Override
    public Executor getAsyncExecutor() {
        return virtualThreadExecutor();
    }
}
```

---

### **ETAPA 3: Configuração do Redis**

#### RedisConfig.java
```java
package com.menthoros.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

@Slf4j
@Configuration
@EnableCaching
public class RedisConfig {

    @Value("${spring.data.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.data.redis.port:6379}")
    private int redisPort;

    @Value("${spring.data.redis.password:}")
    private String redisPassword;

    /**
     * Configuração da conexão Redis usando Lettuce (async driver)
     */
    @Bean
    public LettuceConnectionFactory redisConnectionFactory() {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration(redisHost, redisPort);

        if (redisPassword != null && !redisPassword.isEmpty()) {
            config.setPassword(redisPassword);
        }

        log.info("Configurando Redis em {}:{}", redisHost, redisPort);
        return new LettuceConnectionFactory(config);
    }

    /**
     * RedisTemplate para operações genéricas
     * Suporta serialização JSON com Jackson
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(
            RedisConnectionFactory connectionFactory,
            ObjectMapper objectMapper) {

        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        // Serializers
        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        GenericJackson2JsonRedisSerializer jsonSerializer =
            new GenericJackson2JsonRedisSerializer(objectMapper);

        // Key: String, Value: JSON
        template.setKeySerializer(stringSerializer);
        template.setValueSerializer(jsonSerializer);
        template.setHashKeySerializer(stringSerializer);
        template.setHashValueSerializer(jsonSerializer);

        template.afterPropertiesSet();
        return template;
    }

    /**
     * Cache Manager com TTL configurável por cache
     */
    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofHours(24)) // TTL padrão: 24h
            .serializeKeysWith(
                RedisSerializationContext.SerializationPair.fromSerializer(
                    new StringRedisSerializer()
                )
            )
            .serializeValuesWith(
                RedisSerializationContext.SerializationPair.fromSerializer(
                    new GenericJackson2JsonRedisSerializer()
                )
            )
            .disableCachingNullValues();

        // Configurações específicas por cache
        return RedisCacheManager.builder(connectionFactory)
            .cacheDefaults(defaultConfig)
            .withCacheConfiguration("jobs",
                defaultConfig.entryTtl(Duration.ofHours(24)))
            .withCacheConfiguration("job-progress",
                defaultConfig.entryTtl(Duration.ofHours(1)))
            .withCacheConfiguration("rate-limit",
                defaultConfig.entryTtl(Duration.ofMinutes(1)))
            .build();
    }

    /**
     * Redis Pub/Sub Message Listener Container
     * Para notificações em tempo real
     */
    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(
            RedisConnectionFactory connectionFactory) {

        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        return container;
    }

    /**
     * Topic para publicar eventos de job
     */
    @Bean
    public ChannelTopic jobEventsTopic() {
        return new ChannelTopic("job-events");
    }
}
```

#### application.yml (adicionar configuração Redis)
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
          max-wait: 2000ms
        shutdown-timeout: 200ms
```

#### pom.xml (adicionar dependência)
```xml
<!-- Redis -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>
<dependency>
    <groupId>io.lettuce</groupId>
    <artifactId>lettuce-core</artifactId>
</dependency>
```

---

### **ETAPA 4: Redis Job Cache Service**

#### RedisJobCacheService.java
```java
package com.menthoros.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.menthoros.dto.JobStatusDto;
import com.menthoros.enums.JobStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class RedisJobCacheService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    private static final String JOB_KEY_PREFIX = "job:";
    private static final String JOB_PROGRESS_PREFIX = "job:progress:";
    private static final String JOB_QUEUE_PREFIX = "queue:plano:";
    private static final Duration DEFAULT_TTL = Duration.ofHours(24);

    /**
     * Armazena status do job em cache
     */
    public void cacheJobStatus(UUID jobId, JobStatus status) {
        String key = JOB_KEY_PREFIX + jobId;
        redisTemplate.opsForValue().set(key, status.name(), DEFAULT_TTL);
        log.debug("Redis: Cached job {} status: {}", jobId, status);
    }

    /**
     * Recupera status do job do cache
     */
    public JobStatus getJobStatus(UUID jobId) {
        String key = JOB_KEY_PREFIX + jobId;
        Object value = redisTemplate.opsForValue().get(key);

        if (value != null) {
            return JobStatus.valueOf(value.toString());
        }
        return null;
    }

    /**
     * Atualiza progresso do job (0-100)
     */
    public void updateJobProgress(UUID jobId, int progress) {
        String key = JOB_PROGRESS_PREFIX + jobId;
        redisTemplate.opsForValue().set(key, progress, Duration.ofHours(1));
        log.debug("Redis: Updated job {} progress: {}%", jobId, progress);
    }

    /**
     * Recupera progresso do job
     */
    public Integer getJobProgress(UUID jobId) {
        String key = JOB_PROGRESS_PREFIX + jobId;
        Object value = redisTemplate.opsForValue().get(key);

        if (value != null) {
            return Integer.parseInt(value.toString());
        }
        return 0;
    }

    /**
     * Armazena DTO completo do job
     */
    public void cacheJobDto(UUID jobId, JobStatusDto dto) {
        try {
            String key = JOB_KEY_PREFIX + jobId + ":dto";
            String json = objectMapper.writeValueAsString(dto);
            redisTemplate.opsForValue().set(key, json, DEFAULT_TTL);
        } catch (JsonProcessingException e) {
            log.error("Erro ao serializar JobStatusDto", e);
        }
    }

    /**
     * Recupera DTO completo do job
     */
    public JobStatusDto getJobDto(UUID jobId) {
        try {
            String key = JOB_KEY_PREFIX + jobId + ":dto";
            Object value = redisTemplate.opsForValue().get(key);

            if (value != null) {
                return objectMapper.readValue(value.toString(), JobStatusDto.class);
            }
        } catch (JsonProcessingException e) {
            log.error("Erro ao desserializar JobStatusDto", e);
        }
        return null;
    }

    /**
     * Adiciona job na fila Redis
     */
    public void enqueueJob(String queueName, String jobPayload) {
        String key = JOB_QUEUE_PREFIX + queueName;
        redisTemplate.opsForList().rightPush(key, jobPayload);
        log.info("Redis: Enqueued job to {}", queueName);
    }

    /**
     * Remove job da fila (BLPOP - blocking)
     */
    public String dequeueJob(String queueName, long timeout) {
        String key = JOB_QUEUE_PREFIX + queueName;
        Object value = redisTemplate.opsForList().leftPop(key, timeout, TimeUnit.SECONDS);

        return value != null ? value.toString() : null;
    }

    /**
     * Obtém tamanho da fila
     */
    public Long getQueueSize(String queueName) {
        String key = JOB_QUEUE_PREFIX + queueName;
        return redisTemplate.opsForList().size(key);
    }

    /**
     * Rate limiting por tenant (sliding window)
     */
    public boolean checkRateLimit(UUID tenantId, int maxJobsPerMinute) {
        String key = "tenant:" + tenantId + ":rate";

        ValueOperations<String, Object> ops = redisTemplate.opsForValue();
        Long currentCount = ops.increment(key);

        if (currentCount == 1) {
            // Primeira requisição no período - define TTL
            redisTemplate.expire(key, Duration.ofMinutes(1));
        }

        boolean allowed = currentCount <= maxJobsPerMinute;

        if (!allowed) {
            log.warn("Rate limit excedido para tenant {}: {} jobs/min",
                tenantId, currentCount);
        }

        return allowed;
    }

    /**
     * Lock distribuído para prevenir execução duplicada
     */
    public boolean acquireLock(UUID jobId, Duration lockDuration) {
        String key = "lock:job:" + jobId;
        Boolean acquired = redisTemplate.opsForValue()
            .setIfAbsent(key, "locked", lockDuration);

        return Boolean.TRUE.equals(acquired);
    }

    /**
     * Libera lock
     */
    public void releaseLock(UUID jobId) {
        String key = "lock:job:" + jobId;
        redisTemplate.delete(key);
    }

    /**
     * Publica evento no canal Pub/Sub
     */
    public void publishJobEvent(UUID jobId, String eventType, String payload) {
        String channel = "channel:job:" + jobId;
        String message = String.format("{\"type\":\"%s\",\"payload\":%s}", eventType, payload);

        redisTemplate.convertAndSend(channel, message);
        log.debug("Redis: Published event to {}: {}", channel, eventType);
    }

    /**
     * Remove job do cache
     */
    public void evictJob(UUID jobId) {
        String jobKey = JOB_KEY_PREFIX + jobId;
        String progressKey = JOB_PROGRESS_PREFIX + jobId;
        String dtoKey = JOB_KEY_PREFIX + jobId + ":dto";

        redisTemplate.delete(jobKey);
        redisTemplate.delete(progressKey);
        redisTemplate.delete(dtoKey);

        log.debug("Redis: Evicted job {} from cache", jobId);
    }
}
```

---

### **ETAPA 5: Repository**

#### JobExecucaoRepository.java
```java
package com.menthoros.repository;

import com.menthoros.entity.JobExecucao;
import com.menthoros.enums.JobStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface JobExecucaoRepository extends JpaRepository<JobExecucao, UUID> {

    List<JobExecucao> findByAssessoriaIdOrderByInicioEmDesc(UUID assessoriaId);

    List<JobExecucao> findByStatusIn(List<JobStatus> statuses);

    @Query("SELECT j FROM JobExecucao j WHERE j.assessoria.id = :assessoriaId AND j.status = :status")
    List<JobExecucao> findByAssessoriaAndStatus(UUID assessoriaId, JobStatus status);

    @Query("SELECT j FROM JobExecucao j WHERE j.status IN ('QUEUED', 'RUNNING') AND j.inicioEm < :timeout")
    List<JobExecucao> findJobsAntigos(LocalDateTime timeout);

    long countByAssessoriaIdAndStatus(UUID assessoriaId, JobStatus status);
}
```

---

### **ETAPA 4: Service de Execução Assíncrona**

#### PlanoAsyncService.java
```java
package com.menthoros.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.menthoros.entity.*;
import com.menthoros.enums.*;
import com.menthoros.multitenancy.TenantContext;
import com.menthoros.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlanoAsyncService {

    private final PlanoService planoService;
    private final AtletaRepository atletaRepository;
    private final JobExecucaoRepository jobRepository;
    private final SubTarefaRepository subTarefaRepository;
    private final UsuarioRepository usuarioRepository;
    private final WebSocketNotificationService notificationService;
    private final ObjectMapper objectMapper;

    /**
     * Submete job para gerar plano individual de forma assíncrona
     */
    @Transactional
    public JobExecucao submitPlanoIndividual(UUID atletaId, ModoGeracaoPlano modo) {
        UUID tenantId = TenantContext.getTenantId();

        Atleta atleta = atletaRepository.findById(atletaId)
            .orElseThrow(() -> new RuntimeException("Atleta não encontrado"));

        // Criar job
        JobExecucao job = JobExecucao.builder()
            .assessoria(atleta.getAssessoria())
            .tipo(JobType.PLANO_INDIVIDUAL)
            .status(JobStatus.QUEUED)
            .totalTarefas(1)
            .inicioEm(LocalDateTime.now())
            .parametros(buildParametros(atletaId, modo))
            .build();

        job = jobRepository.save(job);

        // Criar sub-tarefa
        SubTarefa subTarefa = SubTarefa.builder()
            .jobExecucao(job)
            .atleta(atleta)
            .descricao("Gerar plano para " + atleta.getNome())
            .status(JobStatus.QUEUED)
            .build();

        subTarefaRepository.save(subTarefa);

        // Executar assincronamente
        executarPlanoIndividualAsync(job.getId(), subTarefa.getId(), atletaId, modo, tenantId);

        log.info("Job {} submetido para atleta {}", job.getId(), atletaId);
        return job;
    }

    /**
     * Submete job para gerar planos em lote
     */
    @Transactional
    public JobExecucao submitPlanoLote(List<UUID> atletaIds, ModoGeracaoPlano modo) {
        UUID tenantId = TenantContext.getTenantId();

        // Criar job pai
        JobExecucao job = JobExecucao.builder()
            .assessoria(atletaRepository.findById(atletaIds.get(0))
                .orElseThrow().getAssessoria())
            .tipo(JobType.PLANO_LOTE)
            .status(JobStatus.QUEUED)
            .totalTarefas(atletaIds.size())
            .inicioEm(LocalDateTime.now())
            .parametros(buildParametrosLote(atletaIds, modo))
            .build();

        job = jobRepository.save(job);

        // Criar sub-tarefas
        List<SubTarefa> subTarefas = new ArrayList<>();
        for (UUID atletaId : atletaIds) {
            Atleta atleta = atletaRepository.findById(atletaId).orElse(null);
            if (atleta != null) {
                SubTarefa subTarefa = SubTarefa.builder()
                    .jobExecucao(job)
                    .atleta(atleta)
                    .descricao("Gerar plano para " + atleta.getNome())
                    .status(JobStatus.QUEUED)
                    .build();
                subTarefas.add(subTarefa);
            }
        }

        subTarefaRepository.saveAll(subTarefas);

        // Executar todas as sub-tarefas em paralelo com Virtual Threads
        executarPlanoLoteAsync(job.getId(), subTarefas, modo, tenantId);

        log.info("Job lote {} submetido para {} atletas", job.getId(), atletaIds.size());
        return job;
    }

    /**
     * Executa geração de plano individual em Virtual Thread
     */
    @Async("virtualThreadExecutor")
    public void executarPlanoIndividualAsync(
            UUID jobId,
            UUID subTarefaId,
            UUID atletaId,
            ModoGeracaoPlano modo,
            UUID tenantId) {

        // Configurar tenant context na thread
        TenantContext.setTenantId(tenantId);

        try {
            // Atualizar status para RUNNING
            atualizarStatusJob(jobId, JobStatus.RUNNING);
            atualizarStatusSubTarefa(subTarefaId, JobStatus.RUNNING);

            log.info("[Job {}] Iniciando geração de plano para atleta {}", jobId, atletaId);

            // Gerar plano (operação custosa)
            PlanoSemanal plano = planoService.gerarPlanoTreino(atletaId, modo);

            // Atualizar status para COMPLETED
            finalizarSubTarefa(subTarefaId, plano.getId(), null);
            finalizarJob(jobId, plano.getId());

            // Notificar via WebSocket
            notificationService.notifyJobProgress(jobId, 100);
            notificationService.notifyJobCompleted(jobId, plano.getId());

            log.info("[Job {}] Plano gerado com sucesso: {}", jobId, plano.getId());

        } catch (Exception e) {
            log.error("[Job {}] Erro ao gerar plano", jobId, e);

            finalizarSubTarefa(subTarefaId, null, e.getMessage());
            falharJob(jobId, e.getMessage());

            notificationService.notifyJobFailed(jobId, e.getMessage());
        } finally {
            TenantContext.clear();
        }
    }

    /**
     * Executa geração de planos em lote usando Virtual Threads
     */
    @Async("virtualThreadExecutor")
    public void executarPlanoLoteAsync(
            UUID jobId,
            List<SubTarefa> subTarefas,
            ModoGeracaoPlano modo,
            UUID tenantId) {

        TenantContext.setTenantId(tenantId);

        try {
            atualizarStatusJob(jobId, JobStatus.RUNNING);

            log.info("[Job {}] Iniciando geração em lote de {} planos", jobId, subTarefas.size());

            // Criar CompletableFuture para cada sub-tarefa
            List<CompletableFuture<Void>> futures = subTarefas.stream()
                .map(subTarefa -> CompletableFuture.runAsync(() -> {
                    // Cada sub-tarefa roda em sua própria Virtual Thread
                    processarSubTarefa(jobId, subTarefa, modo, tenantId);
                }))
                .collect(Collectors.toList());

            // Aguardar todas as tarefas
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

            // Verificar resultado final
            JobExecucao job = jobRepository.findById(jobId).orElseThrow();

            if (job.getTarefasFalhadas() > 0) {
                job.setStatus(JobStatus.PARTIAL);
            } else {
                job.marcarComoCompletado();
            }

            jobRepository.save(job);

            notificationService.notifyJobCompleted(jobId, null);

            log.info("[Job {}] Lote finalizado: {} sucesso, {} falhas",
                jobId, job.getTarefasCompletadas(), job.getTarefasFalhadas());

        } catch (Exception e) {
            log.error("[Job {}] Erro crítico no lote", jobId, e);
            falharJob(jobId, e.getMessage());
        } finally {
            TenantContext.clear();
        }
    }

    /**
     * Processa uma sub-tarefa individual
     */
    private void processarSubTarefa(
            UUID jobId,
            SubTarefa subTarefa,
            ModoGeracaoPlano modo,
            UUID tenantId) {

        TenantContext.setTenantId(tenantId);

        try {
            subTarefa.marcarComoIniciada();
            subTarefaRepository.save(subTarefa);

            log.debug("[Job {}] Processando sub-tarefa {} para atleta {}",
                jobId, subTarefa.getId(), subTarefa.getAtleta().getNome());

            // Gerar plano
            PlanoSemanal plano = planoService.gerarPlanoTreino(
                subTarefa.getAtleta().getId(),
                modo
            );

            // Marcar como concluída
            subTarefa.marcarComoConcluida(plano.getId());
            subTarefaRepository.save(subTarefa);

            // Atualizar progresso do job pai
            incrementarProgresso(jobId);

            // Notificar progresso
            JobExecucao job = jobRepository.findById(jobId).orElseThrow();
            notificationService.notifyJobProgress(
                jobId,
                job.getProgressoPercentual()
            );

        } catch (Exception e) {
            log.error("[Job {}] Erro na sub-tarefa {}", jobId, subTarefa.getId(), e);

            subTarefa.marcarComoFalha(e.getMessage());
            subTarefaRepository.save(subTarefa);

            incrementarFalhas(jobId);
        } finally {
            TenantContext.clear();
        }
    }

    // ===== MÉTODOS AUXILIARES =====

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected void atualizarStatusJob(UUID jobId, JobStatus status) {
        JobExecucao job = jobRepository.findById(jobId).orElseThrow();
        job.setStatus(status);
        jobRepository.save(job);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected void atualizarStatusSubTarefa(UUID subTarefaId, JobStatus status) {
        SubTarefa subTarefa = subTarefaRepository.findById(subTarefaId).orElseThrow();
        subTarefa.setStatus(status);
        if (status == JobStatus.RUNNING) {
            subTarefa.marcarComoIniciada();
        }
        subTarefaRepository.save(subTarefa);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected void finalizarSubTarefa(UUID subTarefaId, UUID resultadoId, String erro) {
        SubTarefa subTarefa = subTarefaRepository.findById(subTarefaId).orElseThrow();
        if (erro == null) {
            subTarefa.marcarComoConcluida(resultadoId);
        } else {
            subTarefa.marcarComoFalha(erro);
        }
        subTarefaRepository.save(subTarefa);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected void finalizarJob(UUID jobId, UUID resultadoId) {
        JobExecucao job = jobRepository.findById(jobId).orElseThrow();
        job.incrementarCompletadas();
        job.setResultado(String.format("{\"planoId\":\"%s\"}", resultadoId));
        jobRepository.save(job);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected void falharJob(UUID jobId, String mensagemErro) {
        JobExecucao job = jobRepository.findById(jobId).orElseThrow();
        job.marcarComoFalho(mensagemErro);
        jobRepository.save(job);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected void incrementarProgresso(UUID jobId) {
        JobExecucao job = jobRepository.findById(jobId).orElseThrow();
        job.incrementarCompletadas();
        jobRepository.save(job);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected void incrementarFalhas(UUID jobId) {
        JobExecucao job = jobRepository.findById(jobId).orElseThrow();
        job.incrementarFalhadas();
        jobRepository.save(job);
    }

    private String buildParametros(UUID atletaId, ModoGeracaoPlano modo) {
        try {
            Map<String, Object> params = new HashMap<>();
            params.put("atletaId", atletaId.toString());
            params.put("modo", modo.name());
            return objectMapper.writeValueAsString(params);
        } catch (Exception e) {
            return "{}";
        }
    }

    private String buildParametrosLote(List<UUID> atletaIds, ModoGeracaoPlano modo) {
        try {
            Map<String, Object> params = new HashMap<>();
            params.put("atletaIds", atletaIds.stream().map(UUID::toString).collect(Collectors.toList()));
            params.put("modo", modo.name());
            params.put("total", atletaIds.size());
            return objectMapper.writeValueAsString(params);
        } catch (Exception e) {
            return "{}";
        }
    }
}
```

---

## 🎯 **PRÓXIMAS ETAPAS NO ROADMAP**

Esta é a parte 1 do guia. O documento continua com:
- Controller REST
- WebSocket para notificações em tempo real
- Cancelamento de jobs
- Retry automático
- Monitoramento e métricas
- Testes de carga

---

**Autor**: Claude Code
**Data**: 2025-10-10
**Versão**: 1.0.0