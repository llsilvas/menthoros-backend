# 🗺️ Roadmap de Implementação - Geração Assíncrona de Planos (Virtual Threads)

## 📅 Cronograma Sugerido

### **SPRINT 1 - Fundação do Sistema Assíncrono (Semana 1)**
**Objetivo**: Criar estrutura base e configuração de Virtual Threads

#### Tarefas:
- [ ] **1.1** Atualizar para Java 21 (se necessário)
  ```bash
  # Verificar versão
  java -version

  # Atualizar pom.xml
  <properties>
      <java.version>21</java.version>
  </properties>
  ```

- [ ] **1.2** Criar enums
  - Arquivo: `src/main/java/com/menthoros/enums/JobType.java`
  - Valores: `PLANO_INDIVIDUAL`, `PLANO_LOTE`, `SINCRONIZACAO_STRAVA`, `CALCULO_METRICAS`

  - Arquivo: `src/main/java/com/menthoros/enums/JobStatus.java`
  - Valores: `QUEUED`, `RUNNING`, `COMPLETED`, `FAILED`, `CANCELLED`, `PARTIAL`

- [ ] **1.3** Criar entidade `JobExecucao`
  - Arquivo: `src/main/java/com/menthoros/entity/JobExecucao.java`
  - Campos: id, tenant_id, tipo, status, progresso, timing, metadados

- [ ] **1.4** Criar entidade `SubTarefa`
  - Arquivo: `src/main/java/com/menthoros/entity/SubTarefa.java`
  - Relacionamento com JobExecucao e Atleta

- [ ] **1.5** Criar migration do banco
  - Arquivo: `src/main/resources/db/migration/V9__Create_async_job_tables.sql`
  - Tabelas: `tb_job_execucao`, `tb_sub_tarefa`
  - Índices otimizados

- [ ] **1.6** Executar migration
  ```bash
  mvn flyway:migrate
  ```

- [ ] **1.7** Criar repositories
  - `JobExecucaoRepository.java`
  - `SubTarefaRepository.java`

- [ ] **1.8** Configurar AsyncConfig
  - Arquivo: `src/main/java/com/menthoros/config/AsyncConfig.java`
  - Criar `virtualThreadExecutor()` com `Executors.newVirtualThreadPerTaskExecutor()`
  - Criar `boundedExecutor()` como fallback

- [ ] **1.9** Adicionar dependências Redis no pom.xml
  ```xml
  <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-data-redis</artifactId>
  </dependency>
  <dependency>
      <groupId>io.lettuce</groupId>
      <artifactId>lettuce-core</artifactId>
  </dependency>
  ```

- [ ] **1.10** Configurar Redis
  - Arquivo: `src/main/java/com/menthoros/config/RedisConfig.java`
  - Configurar `LettuceConnectionFactory` (driver assíncrono)
  - Configurar `RedisTemplate` com serialização JSON
  - Configurar `CacheManager` com TTL específicos
  - Configurar `RedisMessageListenerContainer` para Pub/Sub

- [ ] **1.11** Adicionar configurações Redis no application.yml
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
  ```

- [ ] **1.12** Criar `RedisJobCacheService`
  - Arquivo: `src/main/java/com/menthoros/services/RedisJobCacheService.java`
  - Métodos de cache: `cacheJobStatus()`, `getJobStatus()`, `updateJobProgress()`
  - Métodos de fila: `enqueueJob()`, `dequeueJob()`, `getQueueSize()`
  - Métodos de controle: `checkRateLimit()`, `acquireLock()`, `releaseLock()`
  - Métodos Pub/Sub: `publishJobEvent()`

- [ ] **1.13** Criar DTOs
  - `JobStatusDto.java`: DTO para serialização em cache

**Entregáveis**: Estrutura de dados, Virtual Threads e Redis configurados

---

### **SPRINT 2 - Service de Execução Assíncrona (Semana 1-2)**
**Objetivo**: Implementar lógica core de geração assíncrona

#### Tarefas:
- [ ] **2.1** Criar `PlanoAsyncService`
  - Arquivo: `src/main/java/com/menthoros/services/PlanoAsyncService.java`
  - Método: `submitPlanoIndividual(atletaId, modo)`
  - Método: `submitPlanoLote(atletaIds, modo)`
  - Método: `executarPlanoIndividualAsync()` com `@Async`
  - Método: `executarPlanoLoteAsync()` com `@Async`

- [ ] **2.2** Implementar processamento de sub-tarefas
  - Método: `processarSubTarefa()`
  - Uso de `CompletableFuture` para paralelização
  - Gerenciamento de TenantContext em cada thread

- [ ] **2.3** Implementar métodos auxiliares
  - `atualizarStatusJob()`
  - `finalizarJob()`
  - `falharJob()`
  - `incrementarProgresso()`
  - `incrementarFalhas()`

- [ ] **2.4** Criar tratamento de erros
  - Try-catch em cada sub-tarefa
  - Logging detalhado
  - Propagação de erros para job pai

- [ ] **2.5** Garantir isolamento de tenant
  - `TenantContext.setTenantId()` em cada thread
  - `TenantContext.clear()` no finally

- [ ] **2.6** Integrar Redis no PlanoAsyncService
  - Cachear status do job em Redis após cada atualização
  - Usar `RedisJobCacheService.cacheJobStatus()` e `updateJobProgress()`
  - Publicar eventos via Redis Pub/Sub: `publishJobEvent()`
  - Verificar rate limit antes de submeter job: `checkRateLimit()`
  - Adquirir lock distribuído para evitar execução duplicada: `acquireLock()`

- [ ] **2.7** Testes unitários básicos
  - Arquivo: `src/test/java/com/menthoros/services/PlanoAsyncServiceTest.java`
  - Testar submissão de job
  - Mock de PlanoService e RedisJobCacheService
  - Testar cache hit/miss

**Entregáveis**: Geração assíncrona funcionando com cache Redis

---

### **SPRINT 3 - Controllers REST (Semana 2)**
**Objetivo**: Criar endpoints para interação com jobs

#### Tarefas:
- [ ] **3.1** Criar DTOs
  - `JobSubmitRequest.java`: atletaId(s), modo
  - `JobSubmitResponse.java`: jobId, status, estimatedTime
  - `JobStatusResponse.java`: jobId, status, progress, sub-tarefas
  - `SubTarefaDto.java`: id, atleta, status, resultado

- [ ] **3.2** Criar `PlanoAsyncController`
  - Arquivo: `src/main/java/com/menthoros/controller/PlanoAsyncController.java`

  - **Endpoint 1**: Gerar plano individual assíncrono
    ```java
    POST /api/planos/async/individual
    Body: { "atletaId": "uuid", "modo": "PROXIMA_SEMANA" }
    Response: { "jobId": "uuid", "status": "QUEUED" }
    ```

  - **Endpoint 2**: Gerar planos em lote
    ```java
    POST /api/planos/async/lote
    Body: { "atletaIds": ["uuid1", "uuid2", ...], "modo": "PROXIMA_SEMANA" }
    Response: { "jobId": "uuid", "totalTarefas": 10 }
    ```

  - **Endpoint 3**: Consultar status do job
    ```java
    GET /api/jobs/{jobId}
    Response: {
      "jobId": "uuid",
      "status": "RUNNING",
      "progress": 65,
      "completadas": 13,
      "falhadas": 2,
      "subTarefas": [...]
    }
    ```

  - **Endpoint 4**: Listar jobs da assessoria
    ```java
    GET /api/jobs?status=RUNNING&limit=20
    Response: [ { job1 }, { job2 }, ... ]
    ```

  - **Endpoint 5**: Cancelar job
    ```java
    DELETE /api/jobs/{jobId}
    Response: 204 No Content
    ```

- [ ] **3.3** Implementar validações
  - Validar que atletas pertencem ao tenant
  - Validar limite de jobs simultâneos (ex: max 10)
  - Validar permissões (apenas ADMIN/TECNICO)

- [ ] **3.4** Adicionar anotações Swagger
  - `@Operation`, `@ApiResponse`, etc.
  - Documentar parâmetros e respostas

- [ ] **3.5** Testes de integração
  - Arquivo: `src/test/java/com/menthoros/controller/PlanoAsyncControllerIT.java`
  - Testar submissão e consulta de jobs

**Entregáveis**: API REST completa para jobs assíncronos

---

### **SPRINT 4 - WebSocket para Notificações Tempo Real (Semana 3)**
**Objetivo**: Notificar frontend sobre progresso dos jobs

#### Tarefas:
- [ ] **4.1** Adicionar dependência WebSocket
  ```xml
  <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-websocket</artifactId>
  </dependency>
  ```

- [ ] **4.2** Configurar WebSocket
  - Arquivo: `src/main/java/com/menthoros/config/WebSocketConfig.java`
  - Configurar STOMP over WebSocket
  - Endpoint: `/ws`
  - Topic: `/topic/jobs/{jobId}`

- [ ] **4.3** Criar `WebSocketNotificationService`
  - Arquivo: `src/main/java/com/menthoros/services/WebSocketNotificationService.java`
  - Método: `notifyJobProgress(jobId, progress)`
  - Método: `notifyJobCompleted(jobId, result)`
  - Método: `notifyJobFailed(jobId, error)`
  - Método: `notifySubTarefaCompleted(jobId, subTarefaId)`

- [ ] **4.4** Integrar com PlanoAsyncService
  - Chamar notificationService após cada atualização
  - Notificar a cada 10% de progresso (evitar spam)

- [ ] **4.5** Criar DTOs de notificação
  - `JobProgressNotification.java`
  - `JobCompletedNotification.java`
  - `JobFailedNotification.java`

- [ ] **4.6** Documentar protocolo WebSocket
  - Como conectar
  - Como subscrever a tópicos
  - Formato das mensagens

- [ ] **4.7** Testar com cliente WebSocket
  - Usar Postman ou biblioteca JavaScript
  - Verificar recebimento de notificações

**Entregáveis**: WebSocket funcionando com notificações tempo real

---

### **SPRINT 5 - Cancelamento e Retry (Semana 3-4)**
**Objetivo**: Permitir cancelamento e retry de jobs

#### Tarefas:
- [ ] **5.1** Implementar cancelamento
  - Método: `PlanoAsyncService.cancelarJob(jobId)`
  - Marcar job como CANCELLED
  - Interromper sub-tarefas em execução (best-effort)
  - Notificar via WebSocket

- [ ] **5.2** Implementar verificação de cancelamento
  - Em cada sub-tarefa, verificar se job foi cancelado
  - Se sim, interromper gracefully

- [ ] **5.3** Criar estratégia de retry
  - Retry automático em falhas (max 3 tentativas)
  - Exponential backoff: 5s, 15s, 45s
  - Não fazer retry em erros de validação

- [ ] **5.4** Implementar retry de sub-tarefas
  - Adicionar campo `tentativas` em SubTarefa
  - Método: `retrySubTarefa(subTarefaId)`

- [ ] **5.5** Criar endpoint de retry manual
  ```java
  POST /api/jobs/{jobId}/retry
  Response: { "jobId": "new-uuid", "status": "QUEUED" }
  ```

- [ ] **5.6** Implementar cleanup de jobs antigos
  - Job agendado (`@Scheduled`) para limpar jobs finalizados após 30 dias
  - Manter apenas últimas 100 execuções por assessoria

**Entregáveis**: Cancelamento e retry funcionando

---

### **SPRINT 6 - Monitoramento e Métricas (Semana 4)**
**Objetivo**: Observabilidade do sistema assíncrono

#### Tarefas:
- [ ] **6.1** Criar `JobMonitoringService`
  - Arquivo: `src/main/java/com/menthoros/services/JobMonitoringService.java`
  - Método: `getJobsEmExecucao()`
  - Método: `getEstatisticas(assessoriaId)`
  - Método: `getTempoMedioGeracao()`

- [ ] **6.2** Criar dashboard de métricas
  - Endpoint: `GET /api/jobs/metricas`
  - Resposta:
    ```json
    {
      "jobsAtivos": 5,
      "jobsFilaEspera": 12,
      "tempoMedioSegundos": 8.5,
      "taxaSucesso": 0.95,
      "planoGeradosHoje": 123
    }
    ```

- [ ] **6.3** Adicionar logs estruturados
  - Log de início/fim de cada job
  - Log de cada sub-tarefa
  - Incluir tenant_id, jobId, atletaId em todos os logs

- [ ] **6.4** Criar alertas
  - Alerta se job demora > 5 minutos
  - Alerta se taxa de falha > 20%
  - Alerta se muitos jobs na fila (> 50)

- [ ] **6.5** Integrar com Actuator
  - Expor métricas via `/actuator/metrics`
  - Custom metric: `planos.gerados.total`
  - Custom metric: `planos.tempo.geracao.segundos`

- [ ] **6.6** Criar endpoint de health check específico
  ```java
  GET /api/jobs/health
  Response: {
    "status": "UP",
    "virtualThreadsAtivos": 45,
    "jobsPendentes": 3
  }
  ```

**Entregáveis**: Monitoramento completo

---

### **SPRINT 7 - Priorização e Fila (Semana 5)**
**Objetivo**: Gerenciar prioridade de jobs

#### Tarefas:
- [ ] **7.1** Adicionar campo prioridade em JobExecucao
  - Migration: `ALTER TABLE tb_job_execucao ADD COLUMN prioridade INTEGER DEFAULT 5`
  - Enum: `JobPrioridade.java` (ALTA=1, MEDIA=5, BAIXA=10)

- [ ] **7.2** Implementar fila prioritária
  - Usar `PriorityBlockingQueue` para jobs na memória
  - Processar jobs de alta prioridade primeiro

- [ ] **7.3** Criar regras de prioridade
  - Planos individuais: prioridade ALTA
  - Lote pequeno (< 10): prioridade MEDIA
  - Lote grande (>= 10): prioridade BAIXA
  - Admin pode definir prioridade manualmente

- [ ] **7.4** Implementar rate limiting por assessoria
  - Max 5 jobs simultâneos por assessoria no plano BASIC
  - Max 20 jobs no PRO
  - Ilimitado no ENTERPRISE

- [ ] **7.5** Adicionar endpoint para alterar prioridade
  ```java
  PATCH /api/jobs/{jobId}/prioridade
  Body: { "prioridade": "ALTA" }
  ```

- [ ] **7.6** Implementar estimativa de tempo
  - Calcular tempo estimado baseado em:
    - Histórico de gerações anteriores
    - Número de atletas na fila
    - Prioridade do job

**Entregáveis**: Sistema de priorização funcionando

---

### **SPRINT 8 - Otimizações de Performance (Semana 5-6)**
**Objetivo**: Maximizar throughput

#### Tarefas:
- [ ] **8.1** Implementar cache de contexto
  - Cachear dados do atleta antes de gerar planos em lote
  - Evitar N+1 queries

- [ ] **8.2** Batch insert de sub-tarefas
  - Usar `saveAll()` para inserir sub-tarefas
  - Configurar batch size no Hibernate

- [ ] **8.3** Otimizar queries
  - Adicionar `@EntityGraph` para evitar lazy loading issues
  - Usar projection para carregar apenas campos necessários

- [ ] **8.4** Implementar circuit breaker para LLM
  - Usar Resilience4j
  - Se LLM falhar muito, pausar geração automática

- [ ] **8.5** Adicionar pooling de conexões
  - Aumentar pool size do HikariCP
  - Configurar timeout adequado

- [ ] **8.6** Implementar compressão de resultados
  - Comprimir JSON de parametros/resultado com GZIP
  - Economizar espaço no banco

**Entregáveis**: Performance otimizada

---

### **SPRINT 9 - Geração Agendada (Semana 6)**
**Objetivo**: Agendar geração de planos

#### Tarefas:
- [ ] **9.1** Criar entidade `JobAgendado`
  - Campos: assessoria, atletas, modo, cron, ativo
  - Exemplo: "Gerar planos toda segunda às 6h"

- [ ] **9.2** Implementar scheduler
  - Usar `@Scheduled` do Spring
  - Verificar jobs agendados a cada minuto

- [ ] **9.3** Criar CRUD de agendamentos
  - `POST /api/agendamentos` - Criar
  - `GET /api/agendamentos` - Listar
  - `PUT /api/agendamentos/{id}` - Atualizar
  - `DELETE /api/agendamentos/{id}` - Remover

- [ ] **9.4** Validar expressões cron
  - Usar biblioteca cron-utils
  - Não permitir intervalos < 1 hora

- [ ] **9.5** Adicionar histórico de execuções
  - Relacionar JobExecucao com JobAgendado
  - Mostrar últimas 10 execuções

**Entregáveis**: Agendamento de geração automática

---

### **SPRINT 10 - Testes de Carga (Semana 7)**
**Objetivo**: Validar escalabilidade

#### Tarefas:
- [ ] **10.1** Criar testes de carga com JMeter/Gatling
  - Simular 100 atletas gerando planos simultaneamente
  - Verificar throughput e latência

- [ ] **10.2** Teste de stress
  - Submeter 1000 jobs ao mesmo tempo
  - Verificar que sistema não quebra

- [ ] **10.3** Teste de longa duração
  - Executar por 12 horas contínuas
  - Verificar memory leaks
  - Verificar se threads são liberadas

- [ ] **10.4** Teste de falhas
  - Simular falhas na LLM
  - Simular falhas no banco de dados
  - Verificar recuperação automática

- [ ] **10.5** Benchmark de Virtual Threads vs Platform Threads
  - Comparar desempenho
  - Documentar resultados

- [ ] **10.6** Otimizar baseado em resultados
  - Ajustar configurações
  - Adicionar índices se necessário

**Entregáveis**: Sistema testado e validado

---

### **SPRINT 11 - Documentação e Guias (Semana 7)**
**Objetivo**: Documentar completamente

#### Tarefas:
- [ ] **11.1** Documentação técnica
  - Arquitetura de Virtual Threads
  - Fluxo de execução de jobs
  - Modelo de dados

- [ ] **11.2** Guia do desenvolvedor
  - Como adicionar novo tipo de job
  - Como debugar jobs assíncronos
  - Boas práticas

- [ ] **11.3** Guia do usuário
  - Como gerar planos em lote
  - Como monitorar progresso
  - Como interpretar erros

- [ ] **11.4** API documentation (Swagger)
  - Exemplos de requests/responses
  - Códigos de erro
  - Rate limits

- [ ] **11.5** Runbook operacional
  - Como escalar sistema
  - Como limpar jobs antigos
  - Como resolver problemas comuns

**Entregáveis**: Documentação completa

---

## 🎯 Marcos de Entrega

| Marco | Descrição | Prazo Sugerido |
|-------|-----------|----------------|
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

## 🚀 Quick Start - Começar Hoje

### Comandos para Iniciar (Sprint 1):

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

## 💡 Benefícios Esperados

### Performance
- **Antes**: 100 planos = ~15 minutos (sequencial)
- **Depois**: 100 planos = ~20 segundos (paralelo com Virtual Threads)
- **Melhoria**: 45x mais rápido

### UX
- **Antes**: Usuário espera resposta (timeout em 30s)
- **Depois**: Resposta imediata + notificação quando pronto

### Escalabilidade
- **Antes**: ~10-20 threads (limite físico)
- **Depois**: Praticamente ilimitado (milhões de Virtual Threads)

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

**Próximo Passo**: Começar pelo Sprint 1, tarefa 1.1! 🎯

**Tempo estimado total**: 7 semanas
**Equipe recomendada**: 2 desenvolvedores
**Complexidade**: Alta
**Prioridade**: Alta (melhora significativa de UX)