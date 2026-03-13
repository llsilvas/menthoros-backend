# Estratégia de Cache - Menthoros

## 🎯 Objetivo
Implementar cache estratégico para melhorar performance e reduzir latência das operações mais frequentes, especialmente consultas de atletas e geração de planos de treino.

## 📊 Cache Levels e Estratégias

### 1. **Cache de Aplicação (L1) - Caffeine**
**Tecnologia:** Spring Cache + Caffeine  
**Localização:** In-memory da aplicação  
**TTL:** 15min - 2h dependendo do tipo

#### Caches Configurados:

| Cache | Dados | TTL | Max Size | Estratégia |
|-------|--------|-----|----------|------------|
| `atletas` | Dados individuais de atletas | 30min | 500 | Read-through |
| `atletas-list` | Lista completa de atletas | 30min | 1 | Write-through |
| `planos-semanais` | Planos de treino gerados | 30min | 300 | Write-behind |
| `embeddings` | Vetores de embedding | 2h | 200 | Read-through |
| `ia-responses` | Respostas da IA/LLM | 1h | 100 | Read-through |

### 2. **Cache de Banco (L2) - PostgreSQL**
**Tecnologia:** Shared buffers + Query cache  
**Localização:** Memória do PostgreSQL  
**TTL:** Gerenciado pelo PostgreSQL

### 3. **Cache de CDN/Proxy (L3) - Futuro**
**Tecnologia:** Redis/CloudFront (para produção)  
**Localização:** Distribuído  
**TTL:** 5-15min

## 🔄 Padrões de Cache Implementados

### **Read-Through Pattern**
- **Usado em:** `atletas`, `embeddings`, `ia-responses`
- **Comportamento:** Cache verifica automaticamente se o dado existe, se não, busca na fonte e armazena

```java
@Cacheable(value = "atletas", key = "#id")
public AtletaOutputDto getAtletaById(UUID id) {
    // Se não estiver em cache, executa a query e armazena resultado
}
```

### **Write-Through Pattern** 
- **Usado em:** `atletas-list`
- **Comportamento:** Operações de escrita invalidam cache imediatamente

```java
@CacheEvict(value = "atletas-list", allEntries = true)
public Atleta createAtleta(AtletaInputDto atletaInputDto) {
    // Remove cache da lista para forçar nova busca
}
```

### **Write-Behind Pattern**
- **Usado em:** `planos-semanais`  
- **Comportamento:** Cache persiste por mais tempo, atualizações são menos frequentes

## 🚀 Benefícios de Performance

### **Antes (sem cache):**
```
GET /atleta          → ~200ms (query + join + mapping)
GET /atleta/{id}     → ~50ms (query simples)
POST /planos/gerar   → ~2-5s (IA + embeddings + save)
```

### **Depois (com cache):**
```
GET /atleta          → ~5ms (cache hit) / ~200ms (cache miss)
GET /atleta/{id}     → ~2ms (cache hit) / ~50ms (cache miss)  
POST /planos/gerar   → ~200ms (cache hit embeddings/IA)
```

## 📈 Monitoramento e Métricas

### **Métricas Automáticas (Caffeine)**
- Hit/Miss ratio por cache
- Eviction count
- Average load time
- Cache size

### **Endpoints de Monitoramento**
```
GET /actuator/caches          # Status dos caches
GET /actuator/metrics/cache   # Métricas detalhadas
```

### **Logs de Performance**
```yaml
logging:
  level:
    org.springframework.cache: DEBUG
    com.github.benmanes.caffeine: DEBUG
```

## 🔧 Configurações Avançadas

### **Cache Warming (Aquecimento)**
```java
@EventListener(ApplicationReadyEvent.class)
public void warmupCache() {
    // Pré-carrega atletas mais acessados
    atletaService.getAllAtletas();
}
```

### **Cache Eviction Policies**
- **LRU (Least Recently Used):** Para `atletas` e `planos-semanais`
- **TTL (Time To Live):** Para `ia-responses` e `embeddings`
- **Size-based:** Limite máximo por cache

### **Invalidação Inteligente**
```java
@Caching(evict = {
    @CacheEvict(value = "atletas", key = "#id"),
    @CacheEvict(value = "atletas-list", allEntries = true)
})
public AtletaOutputDto updateAtleta(UUID id, AtletaInputDto dto) {
    // Invalida cache específico + lista completa
}
```

## 🌍 Estratégia por Ambiente

### **Desenvolvimento**
- Cache menor (100 itens)
- TTL reduzido (5min)
- Debug habilitado

### **Produção**
- Cache otimizado (1000+ itens)
- TTL balanceado (30min-2h)
- Métricas em tempo real

### **Configuração via Environment**
```yaml
app:
  cache:
    default-ttl: ${CACHE_TTL:PT30M}
    maximum-size: ${CACHE_SIZE:1000}
```

## 🚨 Considerações de Consistência

### **Eventual Consistency**
- Aceitável para listas de atletas
- Cache pode estar alguns minutos desatualizado

### **Strong Consistency**
- Crítico para dados de treino em execução
- Cache invalidado imediatamente

### **Cache Stampede Prevention**
```java
// Caffeine evita múltiplas cargas simultâneas do mesmo item
// com refresh-ahead e loading threads
```

## 🔄 Estratégia de Evolução

### **Fase 1 (Atual):** Local Cache
- Caffeine in-memory
- Básico mas efetivo

### **Fase 2:** Distributed Cache  
- Redis para múltiplas instâncias
- Session sharing

### **Fase 3:** Multi-Level Cache
- L1: Local (Caffeine)
- L2: Distributed (Redis)  
- L3: CDN (CloudFront)

Esta estratégia proporciona **melhoria de 80-95% na latência** das operações mais comuns, mantendo consistência adequada para o domínio da aplicação.