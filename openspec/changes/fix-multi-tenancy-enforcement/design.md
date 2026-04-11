## Context

O backend Menthoros usa um modelo de **shared schema com `tenant_id`** onde cada assessoria (tenant) é isolada pela coluna `tenant_id` nas tabelas de domínio. A infraestrutura de multi-tenancy já existe:

- `TenantContext` (InheritableThreadLocal) propaga o UUID do tenant na thread da request
- `JwtTenantFilter` extrai `tenant_id` do JWT e popula o contexto
- `AtletaRepository` já tem `findByIdAndTenantId` como método tenant-aware modelo

O problema: a camada de serviço ainda contém 3 lacunas críticas que precisam ser removidas antes de ir para produção:
1. Segurança HTTP com `.anyRequest().permitAll()` — requests sem JWT funcionam
2. Fallback `resolveTenantId()` em `AtletaServiceImpl` e `ProvaServiceImpl` — requests sem tenant context operam em dados de outro tenant
3. `findById` global ainda usado em `TreinoServiceImpl`, `PlanoServiceImpl` e `PlanoMetadadosServiceImpl`

Complementarmente: cache sem segmentação por tenant e `PlanoMetaDados` sem campo `tenant_id` mapeado.

## Goals / Non-Goals

**Goals:**
- Toda rota de negócio exige JWT válido com `tenant_id`
- Toda leitura e escrita de entidade tenant-scoped filtra por tenant no mesmo select
- Cache não pode ter hit entre tenants diferentes
- Entidade `PlanoMetaDados` alinhada ao schema (campo `tenant_id` mapeado)
- Constraints compostas de integridade no banco para evitar vínculos entre tenants

**Non-Goals:**
- Propagação de tenant em execução assíncrona (jobs, schedulers) — P1, fora deste escopo
- Row Level Security no PostgreSQL — backlog
- Sincronização admin de usuários via Keycloak — P2, fora deste escopo
- Testes automatizados de isolamento — tratados como tarefa separada nesta entrega
- Modificar `TenantContext` (InheritableThreadLocal já funciona para requests síncronas HTTP)

## Decisions

### D1: Enforçar autenticação em `SecurityConfig`

**Decisão:** trocar `.anyRequest().permitAll()` por `.anyRequest().authenticated()`.

Manter públicos apenas: `/api/public/**`, `/swagger-ui/**`, `/api-docs/**`, `/actuator/health`.

**Profile local sem Keycloak:** criar `application-local.yml` com override de `SecurityConfig` via profile Spring `local` que mantém `permitAll` e `resolveTenantId()` com tenant fixo de desenvolvimento. Este profile nunca pode ser ativado em produção ou CI — documentar explicitamente no README de desenvolvimento. A task 1.4 cobre a criação deste profile.

**Alternativas consideradas:**
- Manter `permitAll` como default — rejeitado; o fallback de tenant continua presente e o risco de ir para produção sem perceber é alto
- Variável de ambiente para desabilitar auth — rejeitado; mais difícil de auditar que um profile explícito

---

### D2: Remover `resolveTenantId()` e usar `TenantContext.getRequiredTenantId()` diretamente

**Decisão:** deletar o método `resolveTenantId()` de `AtletaServiceImpl` e `ProvaServiceImpl`. Toda resolução de tenant passa a ser `TenantContext.getRequiredTenantId()`, que lança `IllegalStateException` se não houver contexto.

**Mapeamento HTTP de `IllegalStateException`:** o `GlobalExceptionHandler` atual não tem handler específico para `IllegalStateException` — ela cai no `RuntimeException` handler que delega para `handleGeneric` e retorna **500 Internal Server Error**, expondo a mensagem interna ao cliente. Adicionar `@ExceptionHandler(IllegalStateException.class)` mapeando para **403 Forbidden** com mensagem genérica. A task 0.1 cobre essa correção e deve ser executada antes das demais.

**Alternativas consideradas:**
- Manter fallback controlado por profile — rejeitado; introduz risco de configuração incorreta em produção e dificulta testes de isolamento
- Mapear para 401 Unauthorized — rejeitado; 401 implica "tente autenticar novamente"; 403 é mais correto porque o JWT pode ser válido mas sem `tenant_id`

---

### D3: Repositories tenant-aware com métodos explícitos

**Decisão:** adicionar métodos `findByIdAndTenantId(UUID id, UUID tenantId)` nos repositories que ainda não os têm (`PlanoSemanalRepository`, `TreinoPlanejadoRepository`, `TreinoRealizadoRepository`, `PlanoMetadadosRepository`, `ProvaRepository`). O padrão já existe em `AtletaRepository` e serve de modelo.

Services trocam `findById(id)` por `findByIdAndTenantId(id, TenantContext.getRequiredTenantId())`.

**Escopo de serviços cobertos:** além dos serviços listados nas tasks originais, `IaServiceImpl` linha 299 também faz `atletaRepository.findById(atletaId)` num fluxo HTTP (`PlanoTreinoController → PlanoServiceImpl → IaServiceImpl`). A task 4.8 cobre essa correção.

**Serviços fora de escopo nesta entrega:** `TsbServiceImpl` (linhas 80 e 363) e `MetricasAgregadasServiceImpl` (linha 241) usam `findById` global, mas são chamados internamente por serviços que já validaram o atleta via lookup tenant-aware — o caminho de acesso inicial é seguro. Marcado como P1 para próxima iteração.

**Alternativas consideradas:**
- Filtro Hibernate global (`@Filter`) — considerado mas rejeitado nesta fase: aumenta risco de regressão em queries existentes e requer teste mais extenso; métodos explícitos são mais rastreáveis
- `findByIdBasic` e `findByIdForUpdate` existentes no `AtletaRepository` — mantidos apenas para uso interno de services que já validam ownership por outro meio (ex: fetch de atleta já validado para calcular TSB)

---

### D4: Chaves de cache segmentadas por `tenantId`

**Decisão:** trocar chaves de cache para incluir `tenantId` como prefixo do ID:

```java
// Antes
@Cacheable(value = "atletas", key = "#id")
@Cacheable(value = "atletas-list")
@Cacheable(value = "metadados-atleta", key = "#atleta.id")

// Depois
@Cacheable(value = "atletas", key = "T(com.menthoros.multitenancy.TenantContext).getRequiredTenantId() + ':' + #id")
@Cacheable(value = "atletas-list", key = "T(com.menthoros.multitenancy.TenantContext).getRequiredTenantId()")
@Cacheable(value = "metadados-atleta", key = "T(com.menthoros.multitenancy.TenantContext).getRequiredTenantId() + ':' + #atleta.id")
```

`CacheEvict` segue o mesmo padrão, substituindo `allEntries = true` em listas por chave tenant.

**Alternativas consideradas:**
- Prefixo global por tenant no `CacheManager` — mais elegante mas requer customização do `CaffeineCacheManager`; as anotações SpEL têm menos risco de efeito colateral

---

### D5: Mapear `tenant_id` em `PlanoMetaDados` como `@ManyToOne Assessoria`

**Decisão:** seguir o padrão de `Atleta`, `PlanoSemanal` e `Prova` — adicionar campo:

```java
@ManyToOne(fetch = FetchType.LAZY, optional = false)
@JoinColumn(name = "tenant_id", nullable = false)
private Assessoria assessoria;
```

Atualizar `PlanoMetadadosServiceImpl` para popular `assessoria` ao criar metadados via `assessoriaRepository.getReferenceById(TenantContext.getRequiredTenantId())`.

**Nota de schema:** a coluna `tenant_id` já existe em `tb_plano_metadados` — foi adicionada pela migration V2 (`V2__Add_multi_tenancy_support.sql`, linhas 140–142). Não é necessário DDL adicional. A mudança é exclusivamente de mapeamento JPA na entidade e de persistência no serviço.

**Alternativas consideradas:**
- UUID simples `tenant_id` sem relação JPA (como `MetricasDiarias`) — rejeitado; metadados têm consultas diretas por atleta que se beneficiam de join via assessoria, e o padrão dominante no domínio é `@ManyToOne`

---

### D6: Migration com constraints compostas e índice de deduplicação

**Decisão:** criar migration `V11__Add_multi_tenancy_constraints.sql` com:
- `DROP INDEX IF EXISTS uk_treino_realizado_external_id` — índice global criado em V8, sem escopo de tenant; o `IF EXISTS` garante idempotência caso o índice não exista no ambiente (ex: banco restaurado parcialmente)
- CREATE de índice único composto `(tenant_id, fonte_dados, external_id)` em `tb_treino_realizado`, com filtro parcial `WHERE fonte_dados IS NOT NULL AND external_id IS NOT NULL`
- Constraints compostas em tabelas críticas para impedir vínculos entre tenants diferentes

A migration é DDL aditivo, exceto pelo DROP explícito do índice global de V8, que é necessário para evitar conflito semântico (dois tenants com o mesmo `external_id` da Strava seriam bloqueados pelo índice antigo). Não há alteração de colunas.

## Risks / Trade-offs

**[Risco] `IllegalStateException` → HTTP 500** *(HIGH — mitigado)*
`TenantContext.getRequiredTenantId()` lança `IllegalStateException`, que cai no handler genérico de `RuntimeException` retornando 500 e expondo mensagem interna. Mitigação: adicionar `@ExceptionHandler(IllegalStateException.class)` no `GlobalExceptionHandler` mapeando para 403 Forbidden com mensagem genérica. Coberto pela task 0.1, que deve ser executada primeiro.

**[Risco] `IaServiceImpl.findById` sem escopo de tenant** *(HIGH — mitigado)*
`IaServiceImpl` linha 299 faz `atletaRepository.findById(atletaId)` num fluxo HTTP real. Não coberto pelas tasks originais. Mitigação: task 4.8 adicionada.

**[Risco] DROP INDEX não idempotente na migration V11** *(MEDIUM — mitigado)*
`DROP INDEX` sem `IF EXISTS` falha se o índice não existir. Mitigação: usar `DROP INDEX IF EXISTS uk_treino_realizado_external_id` na V11.

**[Risco] Testes de integração quebram com `.authenticated()`** *(MEDIUM — mitigado)*
Os testes de integração existentes não enviam JWT e passam hoje porque `.permitAll()` está ativo. Após a task 1.1, todos retornarão 401. Mitigação: task 1.5 — configurar mock JWT (`@WithMockUser` / `SecurityMockMvcRequestPostProcessors.jwt()`) nos testes de integração que cobrem endpoints de negócio.

**[Risco] Quebra de integração com frontend sem JWT** *(MEDIUM — mitigado)*
A mudança no `SecurityConfig` deve ser coordenada com o time de frontend. Mitigação: profile `local` (D1) permite desenvolvimento sem Keycloak; frontend usa o mesmo JWT que já emite em dev.

**[Risco] `TsbServiceImpl` e `MetricasAgregadasServiceImpl` com `findById` global** *(LOW — aceito, P1)*
Chamados internamente por serviços que já validaram o atleta por tenant. O caminho de acesso inicial é seguro nesta entrega. Escopo P1 para próxima iteração.

**[Risco] Cold cache no primeiro deploy** *(LOW — aceito)*
Entradas antigas com chave `#id` nunca fazem match com a nova chave `tenantId:id`. Sem risco de stale data — apenas cache miss inevitável na primeira requisição de cada recurso após o deploy. TTL de 30 min resolve naturalmente.

**[Trade-off] Chave SpEL no `@Cacheable` é verbosa** → Aceito; alternativa de `CacheManager` customizado tem mais surface area de efeito colateral nesta fase.

## Migration Plan

1. Aplicar em branch `develop` com PR único para os grupos de mudança na ordem: 0 (exception handler) → 3 (repositories) → 4 (services) → 5 (entidade) → 6 (cache) → 1 (segurança + profile local + testes) → 2 (remoção fallback) → 7 (migration)
2. Executar testes unitários e de integração existentes após cada grupo; a task 1.5 (mock JWT nos testes) deve estar concluída antes de executar os testes do grupo 1
3. Em caso de falha na migration V11 (duplicatas), executar query de diagnóstico antes de re-aplicar:
   ```sql
   SELECT tenant_id, fonte_dados, external_id, COUNT(*)
   FROM tb_treino_realizado
   WHERE fonte_dados IS NOT NULL AND external_id IS NOT NULL
   GROUP BY tenant_id, fonte_dados, external_id
   HAVING COUNT(*) > 1;
   ```
4. Rollback: reverter PR — o DROP INDEX da V11 é o único DDL destrutivo; em caso de rollback, recriar o índice global manualmente ou via migration de revert

## Open Questions

- **`findByIdForUpdate` em `AtletaRepository`:** usado em fluxo de lock otimista — avaliar se precisa de versão tenant-aware ou se o contexto de posse já é garantido antes dessa chamada. Postergado para P1.
